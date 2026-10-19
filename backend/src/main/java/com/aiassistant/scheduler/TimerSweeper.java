package com.aiassistant.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.aiassistant.audit.TaskAction;
import com.aiassistant.audit.TaskActivityRecorder;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.timelog.TaskTimeLog;
import com.aiassistant.timelog.TaskTimeLogRepository;

/**
 * 计时兜底：把超过 12 小时还没结束的记录按 12 小时截断。
 *
 * <p>忘点「结束」是真实会发生的事（设计文档 6 节列为风险项：H2 没有部分唯一索引，
 * 数据库拦不住并行计时）。没有这段兜底，第二天看板上的「实际耗时」会变成几十小时，
 * 而用户一旦不信这个数字，整个耗时统计就废了。
 *
 * <p>@EnableScheduling 由 config 包的 SchedulingConfig 提供，这里不重复加。
 */
@Component
public class TimerSweeper {

	private static final Logger log = LoggerFactory.getLogger(TimerSweeper.class);

	/** 单段计时的上限：超过它基本可以断定是忘点结束，而不是真的连续干了 12 小时。 */
	private static final Duration MAX_DURATION = Duration.ofHours(12);

	private static final int MAX_MINUTES = (int) MAX_DURATION.toMinutes();

	private final TaskTimeLogRepository timeLogRepository;
	private final TaskRepository taskRepository;
	private final TaskActivityRecorder activityRecorder;

	/**
	 * 用 TransactionTemplate 而不是在 @Scheduled 方法上写 @Transactional：事务边界与错误边界
	 * 需要分开——把异常 catch 在 @Transactional 方法内部会让已写的内容照样提交，
	 * 而把边界交给代理则异常会抛到调度线程上、没机会按「跳过这一条」处理。
	 */
	private final TransactionTemplate transactionTemplate;

	public TimerSweeper(TaskTimeLogRepository timeLogRepository, TaskRepository taskRepository,
			TaskActivityRecorder activityRecorder, PlatformTransactionManager transactionManager) {
		this.timeLogRepository = timeLogRepository;
		this.taskRepository = taskRepository;
		this.activityRecorder = activityRecorder;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/** 每 10 分钟跑一次（启动后等 1 分钟，避开应用启动时的初始化高峰）。 */
	@Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
	public void sweep() {
		List<TaskTimeLog> stale;
		try {
			stale = timeLogRepository.findByEndedAtIsNullAndStartedAtBefore(Instant.now().minus(MAX_DURATION));
		} catch (RuntimeException ex) {
			// 扫描失败（库被锁、连接池耗尽等）只记日志：异常不能冒到调度线程上，下一个周期重来
			log.error("计时兜底扫描失败，本轮不做任何截断，下个周期重试", ex);
			return;
		}
		// 空库、或没有超时记录：什么都不做，也不开事务
		if (stale.isEmpty()) {
			return;
		}

		List<Long> truncated = new ArrayList<>(stale.size());
		for (TaskTimeLog timeLog : stale) {
			// 一条记录一个事务：某条记录有问题（任务行已被硬删等）不该连累其余记录，
			// 也不能因此让未结束的计时一直堆着——它们正是「实际耗时」失真的源头。
			try {
				transactionTemplate.executeWithoutResult(status -> truncate(timeLog));
				truncated.add(timeLog.getId());
			} catch (RuntimeException ex) {
				log.error("计时兜底截断失败，跳过该条，下个周期重试：timeLogId={}, taskId={}",
						timeLog.getId(), timeLog.getTaskId(), ex);
			}
		}
		if (!truncated.isEmpty()) {
			// 日志里带上具体 id：用户看到某天多了 12 小时，需要能查出是哪几条被截断的
			log.warn("计时兜底截断：{} 条计时超过 {} 小时未结束，按 startedAt+{} 小时截断并记满 {} 分钟，timeLogIds={}",
					truncated.size(), MAX_DURATION.toHours(), MAX_DURATION.toHours(), MAX_MINUTES, truncated);
		}
	}

	private void truncate(TaskTimeLog timeLog) {
		// 截断点固定在 startedAt + 上限，而不是「现在」：这样同一条记录不管哪次扫描到，
		// 算出来的分钟数都一样，重跑不会把耗时越滚越大。
		timeLog.setEndedAt(timeLog.getStartedAt().plus(MAX_DURATION));
		timeLog.setMinutes(MAX_MINUTES);
		timeLogRepository.save(timeLog);

		Optional<Task> task = taskRepository
				.findByIdAndOwnerIdAndDeletedAtIsNull(timeLog.getTaskId(), timeLog.getOwnerId());
		if (task.isEmpty()) {
			// 任务被软删：流水照样收尾（不能让未结束记录永远挂着），但不再改任务、也不再写审计
			log.warn("兜底截断的计时指向不可见的任务，仅收尾计时流水：timeLogId={}, taskId={}",
					timeLog.getId(), timeLog.getTaskId());
			return;
		}
		task.get().addActualMinutes(MAX_MINUTES);
		taskRepository.save(task.get());
		activityRecorder.record(timeLog.getTaskId(), timeLog.getOwnerId(), TaskAction.TIMER_STOPPED,
				null, null, String.valueOf(MAX_MINUTES));
	}
}
