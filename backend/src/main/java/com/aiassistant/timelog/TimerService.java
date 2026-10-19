package com.aiassistant.timelog;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.audit.TaskAction;
import com.aiassistant.audit.TaskActivityLogRepository;
import com.aiassistant.audit.TaskActivityRecorder;
import com.aiassistant.audit.dto.ActivityResponse;
import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ConflictException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.task.TaskStatus;
import com.aiassistant.task.dto.TaskResponse;
import com.aiassistant.timelog.dto.ManualTimeLogRequest;
import com.aiassistant.timelog.dto.RunningTimerResponse;
import com.aiassistant.timelog.dto.TimeLogResponse;
import com.aiassistant.timelog.dto.TimerStopResponse;

/**
 * 计时、手工补录耗时与任务变更历史。
 *
 * <p>两条硬规则贯穿本类：
 * <ol>
 * <li>所有按 id 取数的查询都带 owner_id 条件（见 TaskRepository / TaskTimeLogRepository 的方法名），
 * 不用「取出来再比对」的写法——那种写法漏一个分支就是越权。</li>
 * <li>明细（task_time_logs.minutes）是权威数据，累加冗余列（tasks.actual_minutes）与写审计
 * 必须在同一个事务里，否则会出现「流水有、累计没有」的漂移。</li>
 * </ol>
 */
@Service
public class TimerService {

	private static final Logger log = LoggerFactory.getLogger(TimerService.class);

	/**
	 * 审计里字段名的取值。状态变更写 field=status、旧值新值放状态名；
	 * 耗时类的动作（TIMER_STOPPED / TIME_LOGGED）不挂字段，分钟数放在 newValue 里。
	 */
	private static final String FIELD_STATUS = "status";

	private final TaskTimeLogRepository timeLogRepository;
	private final TaskRepository taskRepository;
	private final TaskActivityRecorder activityRecorder;
	private final TaskActivityLogRepository activityLogRepository;

	public TimerService(TaskTimeLogRepository timeLogRepository, TaskRepository taskRepository,
			TaskActivityRecorder activityRecorder, TaskActivityLogRepository activityLogRepository) {
		this.timeLogRepository = timeLogRepository;
		this.taskRepository = taskRepository;
		this.activityRecorder = activityRecorder;
		this.activityLogRepository = activityLogRepository;
	}

	/**
	 * 当前用户有没有正在跑的计时。没有未结束记录时返回 empty，接口层据此回 204。
	 *
	 * <p>计时期间任务被软删的情况：这条记录已经不该再展示（前端按 204 隐藏计时条），
	 * 但也不在这里关掉它——收尾交给 TimerSweeper，避免两个地方都写「结束计时」的逻辑。
	 */
	@Transactional(readOnly = true)
	public Optional<RunningTimerResponse> findRunning(Long ownerId) {
		Optional<TaskTimeLog> running = timeLogRepository.findFirstByOwnerIdAndEndedAtIsNull(ownerId);
		if (running.isEmpty()) {
			return Optional.empty();
		}
		TaskTimeLog timeLog = running.get();
		Optional<Task> task = taskRepository.findByIdAndOwnerIdAndDeletedAtIsNull(timeLog.getTaskId(), ownerId);
		if (task.isEmpty()) {
			log.warn("未结束的计时指向的任务不可见，本次不返回计时状态：timeLogId={}, taskId={}",
					timeLog.getId(), timeLog.getTaskId());
			return Optional.empty();
		}
		return Optional.of(new RunningTimerResponse(TimeLogResponse.from(timeLog), toResponse(task.get())));
	}

	/**
	 * 开始计时。同一个人只允许有一段未结束的计时：H2 没有部分唯一索引拦不住并发插入，
	 * 只能靠这条校验 + 超 12 小时的兜底截断来兜住（设计文档 6 节的风险项）。
	 */
	@Transactional
	public TimeLogResponse start(Long taskId, Long ownerId) {
		Task task = requireTask(taskId, ownerId);
		Optional<TaskTimeLog> running = timeLogRepository.findFirstByOwnerIdAndEndedAtIsNull(ownerId);
		if (running.isPresent()) {
			throw new ConflictException(ErrorCode.TIMER_ALREADY_RUNNING,
					"已有计时在进行中（任务 id=" + running.get().getTaskId() + "），请先结束");
		}

		TaskTimeLog timeLog = new TaskTimeLog();
		timeLog.setTaskId(taskId);
		timeLog.setOwnerId(ownerId);
		timeLog.setStartedAt(Instant.now());
		timeLog.setMinutes(0);
		timeLog.setSource(TimeLogSource.TIMER);
		// endedAt 留空即为「正在计时」，不要写成 now
		TaskTimeLog saved = timeLogRepository.save(timeLog);
		activityRecorder.record(taskId, ownerId, TaskAction.TIMER_STARTED, null, null, null);

		if (task.getStatus() == TaskStatus.TODO) {
			// 「一开始计时就是进行中」：TODO 的任务点开计时后还要手工改一次状态，
			// 是一件每天都要做、且没有第二种意图的操作，顺手做掉。
			task.setStatus(TaskStatus.DOING);
			taskRepository.save(task);
			activityRecorder.record(taskId, ownerId, TaskAction.STATUS_CHANGED, FIELD_STATUS,
					TaskStatus.TODO.name(), TaskStatus.DOING.name());
		}
		return TimeLogResponse.from(saved);
	}

	/**
	 * 结束计时。补 ended_at 与 minutes、累加 tasks.actual_minutes、可选地把任务置为已完成、
	 * 写审计，全部在一个事务里，任一步失败都不留下「流水闭合但累计没加」的中间态。
	 */
	@Transactional
	public TimerStopResponse stop(Long taskId, Long logId, boolean markDone, Long ownerId) {
		Task task = requireTask(taskId, ownerId);
		TaskTimeLog timeLog = timeLogRepository.findByIdAndOwnerId(logId, ownerId)
				.orElseThrow(() -> notRunning(logId));
		if (!timeLog.getTaskId().equals(taskId)) {
			// 这条记录属于别的任务：按「不存在」处理，不透露它挂在哪个任务上
			throw notRunning(logId);
		}
		if (timeLog.getEndedAt() != null) {
			throw new ConflictException(ErrorCode.TIMER_NOT_RUNNING, "该计时已结束");
		}

		Instant now = Instant.now();
		// now 早于 startedAt 只可能来自系统时钟回拨；按 startedAt 截断，避免算出负分钟数
		Instant endedAt = now.isBefore(timeLog.getStartedAt()) ? timeLog.getStartedAt() : now;
		int minutes = toMinutes(timeLog.getStartedAt(), endedAt);
		timeLog.setEndedAt(endedAt);
		timeLog.setMinutes(minutes);
		task.addActualMinutes(minutes);
		activityRecorder.record(taskId, ownerId, TaskAction.TIMER_STOPPED, null, null, String.valueOf(minutes));

		TaskStatus before = task.getStatus();
		if (markDone) {
			task.markDone(now);
			if (before != TaskStatus.DONE) {
				activityRecorder.record(taskId, ownerId, TaskAction.STATUS_CHANGED, FIELD_STATUS,
						before.name(), TaskStatus.DONE.name());
			}
		}
		// saveAndFlush 而不是 save：响应里带 task，而 @Version 只在 flush 时自增。
		// 不显式 flush 就会把更新前的版本回给前端，前端拿它去保存详情必然撞 VERSION_CONFLICT。
		taskRepository.saveAndFlush(task);
		return new TimerStopResponse(TimeLogResponse.from(timeLogRepository.save(timeLog)), toResponse(task));
	}

	/** 手工补录一段已经发生过的耗时，同样累加实际耗时并留审计。 */
	@Transactional
	public TimeLogResponse addManualLog(Long taskId, ManualTimeLogRequest request, Long ownerId) {
		Task task = requireTask(taskId, ownerId);
		if (request.startedAt() == null || request.endedAt() == null) {
			// 接口层有 @NotNull 兜着，这里再挡一次：直接调 Service 的地方也不该拿到 500
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "开始时间与结束时间不能为空");
		}
		if (request.endedAt().isBefore(request.startedAt())) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "结束时间不能早于开始时间");
		}

		Instant startedAt = request.startedAt();
		Instant endedAt = request.endedAt();
		int minutes = toMinutes(startedAt, endedAt);

		TaskTimeLog timeLog = new TaskTimeLog();
		timeLog.setTaskId(taskId);
		timeLog.setOwnerId(ownerId);
		timeLog.setStartedAt(startedAt);
		timeLog.setEndedAt(endedAt);
		timeLog.setMinutes(minutes);
		timeLog.setSource(TimeLogSource.MANUAL);
		timeLog.setNote(request.note());
		task.addActualMinutes(minutes);
		activityRecorder.record(taskId, ownerId, TaskAction.TIME_LOGGED, null, null, String.valueOf(minutes));
		taskRepository.save(task);
		return TimeLogResponse.from(timeLogRepository.save(timeLog));
	}

	/** 某个任务的耗时明细，最近的一段在最前面。 */
	@Transactional(readOnly = true)
	public List<TimeLogResponse> listTimeLogs(Long taskId, Long ownerId) {
		requireTask(taskId, ownerId);
		return timeLogRepository.findByTaskIdOrderByStartedAtDesc(taskId).stream()
				.map(TimeLogResponse::from)
				.toList();
	}

	/**
	 * 任务变更历史，最近 50 条。条数写在仓储方法名里（findTop50...），
	 * 与任务详情共用同一个查询：两个入口对同一件事必须给出同样的条数与顺序。
	 *
	 * <p>先按 owner 校验任务，避免用别人的任务 id 把审计读出来：越权防护必须在取数之前。
	 */
	@Transactional(readOnly = true)
	public List<ActivityResponse> listActivities(Long taskId, Long ownerId) {
		requireTask(taskId, ownerId);
		return activityLogRepository.findTop50ByTaskIdOrderByCreatedAtDesc(taskId).stream()
				.map(ActivityResponse::from)
				.toList();
	}

	private Task requireTask(Long taskId, Long ownerId) {
		return taskRepository.findByIdAndOwnerIdAndDeletedAtIsNull(taskId, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND, "任务不存在：id=" + taskId));
	}

	private static ResourceNotFoundException notRunning(Long logId) {
		return new ResourceNotFoundException(ErrorCode.TIMER_NOT_RUNNING, "计时记录不存在：id=" + logId);
	}

	/**
	 * 毫秒差转分钟，至少 1 分钟。
	 * 下限取 1 而不是 0 是有意的：点开又立刻结束如果记 0 分钟，明细里会堆一串 0，
	 * 也会让「实际耗时」看起来像根本没干活；而 0 分钟的记录在展示上与没记录无法区分。
	 */
	private static int toMinutes(Instant startedAt, Instant endedAt) {
		long millis = Duration.between(startedAt, endedAt).toMillis();
		return Math.max(1, (int) Math.round(millis / 60_000.0));
	}

	/**
	 * 任务出参的唯一出口。TaskResponse 的组装（含 projectName 等要查别的表的字段）
	 * 归 T2 的 task 包，本包只调用不复制，避免两处各写一套映射后逐渐长歪。
	 */
	private static TaskResponse toResponse(Task task) {
		return TaskResponse.from(task);
	}
}
