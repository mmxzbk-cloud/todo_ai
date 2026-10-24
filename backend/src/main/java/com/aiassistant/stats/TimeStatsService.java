package com.aiassistant.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.project.Project;
import com.aiassistant.project.ProjectRepository;
import com.aiassistant.stats.dto.DayUsage;
import com.aiassistant.stats.dto.ProjectUsage;
import com.aiassistant.stats.dto.RunningTaskInfo;
import com.aiassistant.stats.dto.TaskUsage;
import com.aiassistant.stats.dto.TimeStatsResponse;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.timelog.TaskTimeLog;
import com.aiassistant.timelog.TaskTimeLogRepository;
import com.aiassistant.timelog.TimerService;

/**
 * 耗时统计（US-05）。只读、实时计算：task_time_logs 流水是唯一权威数据源，
 * 不引入第二份冗余存储（PRD 3.3），性能靠既有索引（owner_id, started_at）兜住。
 *
 * <p>口径三条硬规则：
 * <ol>
 * <li>归日按流水 started_at 的用户时区日期（Controller 传 zoneId），与今日视图同一口径；</li>
 * <li>进行中的计时不进合计，单独以 running 字段返回；</li>
 * <li>区间无流水返回空列表而不是报错。</li>
 * </ol>
 */
@Service
public class TimeStatsService {

	/** 区间上限：超过 31 天的查询对个人数据没有意义，直接拒绝而不是放任全表聚合。 */
	private static final int MAX_RANGE_DAYS = 31;

	/** 每天返回投入最高的前几个任务（PRD AC 5.4 要求至少前 3）。 */
	private static final int TOP_TASKS_PER_DAY = 3;

	private final TaskTimeLogRepository timeLogRepository;
	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final TimerService timerService;

	public TimeStatsService(
			TaskTimeLogRepository timeLogRepository,
			TaskRepository taskRepository,
			ProjectRepository projectRepository,
			TimerService timerService) {
		this.timeLogRepository = timeLogRepository;
		this.taskRepository = taskRepository;
		this.projectRepository = projectRepository;
		this.timerService = timerService;
	}

	@Transactional(readOnly = true)
	public TimeStatsResponse stats(Long ownerId, ZoneId zoneId, LocalDate from, LocalDate to) {
		validateRange(from, to);

		Instant start = from.atStartOfDay(zoneId).toInstant();
		Instant end = to.plusDays(1).atStartOfDay(zoneId).toInstant();
		List<TaskTimeLog> logs = timeLogRepository
				.findByOwnerIdAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(ownerId, start, end);

		Map<Long, String> taskTitles = taskTitles(ownerId, logs);
		List<DayUsage> days = dailyUsage(logs, zoneId, from, to, taskTitles);
		List<ProjectUsage> projects = projectUsage(ownerId, logs, taskTitles);
		RunningTaskInfo running = runningTask(ownerId);

		int totalMinutes = logs.stream().mapToInt(TaskTimeLog::getMinutes).sum();
		return new TimeStatsResponse(from, to, totalMinutes, days, projects, running);
	}

	private static void validateRange(LocalDate from, LocalDate to) {
		if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "时间范围不正确，最长支持 31 天");
		}
	}

	/** 区间内每天一条（含 0 分钟的日子），前端画柱状列表不用自己补空日期。 */
	private List<DayUsage> dailyUsage(List<TaskTimeLog> logs, ZoneId zoneId, LocalDate from, LocalDate to,
			Map<Long, String> taskTitles) {
		Map<LocalDate, Map<Long, Integer>> minutesByDayAndTask = new HashMap<>();
		for (TaskTimeLog log : logs) {
			LocalDate day = LocalDateTime.ofInstant(log.getStartedAt(), zoneId).toLocalDate();
			minutesByDayAndTask.computeIfAbsent(day, key -> new HashMap<>())
					.merge(log.getTaskId(), log.getMinutes(), Integer::sum);
		}

		List<DayUsage> days = new ArrayList<>();
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			Map<Long, Integer> byTask = minutesByDayAndTask.getOrDefault(day, Map.of());
			int dayMinutes = byTask.values().stream().mapToInt(Integer::intValue).sum();
			List<TaskUsage> topTasks = byTask.entrySet().stream()
					.sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
					.limit(TOP_TASKS_PER_DAY)
					.map(entry -> new TaskUsage(entry.getKey(), taskTitles.getOrDefault(entry.getKey(), "(未知任务)"),
							entry.getValue()))
					.toList();
			days.add(new DayUsage(day, dayMinutes, topTasks));
		}
		return days;
	}

	/** 按项目聚合；未归属项目的分钟数全部归入「无项目」（projectId=null）。 */
	private List<ProjectUsage> projectUsage(Long ownerId, List<TaskTimeLog> logs, Map<Long, String> taskTitles) {
		Map<Long, Task> tasksById = taskRepository.findByIdInAndOwnerId(logs.stream()
				.map(TaskTimeLog::getTaskId)
				.distinct()
				.toList(), ownerId).stream()
				.collect(Collectors.toMap(Task::getId, task -> task));

		Map<Long, Integer> minutesByProject = new LinkedHashMap<>();
		int noProjectMinutes = 0;
		for (TaskTimeLog log : logs) {
			Task task = tasksById.get(log.getTaskId());
			Long projectId = task == null ? null : task.getProjectId();
			if (projectId == null) {
				noProjectMinutes += log.getMinutes();
			} else {
				minutesByProject.merge(projectId, log.getMinutes(), Integer::sum);
			}
		}

		int totalMinutes = logs.stream().mapToInt(TaskTimeLog::getMinutes).sum();

		// 项目名一次查回：区间里出现几个项目就查几个，不逐项打库（与 TaskMapper.projectNames 同思路）
		Set<Long> projectIds = minutesByProject.keySet();
		Map<Long, String> projectNames = projectIds.isEmpty()
				? Map.of()
				: projectRepository.findByOwnerIdAndIdIn(ownerId, projectIds).stream()
						.collect(Collectors.toMap(Project::getId, Project::getName));

		List<ProjectUsage> projects = new ArrayList<>();
		for (Map.Entry<Long, Integer> entry : minutesByProject.entrySet()) {
			String name = projectNames.getOrDefault(entry.getKey(), "(未知项目)");
			projects.add(new ProjectUsage(entry.getKey(), name, entry.getValue(), share(entry.getValue(), totalMinutes)));
		}
		if (noProjectMinutes > 0) {
			projects.add(new ProjectUsage(null, "无项目", noProjectMinutes, share(noProjectMinutes, totalMinutes)));
		}
		projects.sort(Comparator.comparingInt(ProjectUsage::minutes).reversed());
		return projects;
	}

	/** 0–1 的占比，保留 4 位小数；区间无流水时恒为 0（该分支实际到不了：无流水时列表为空）。 */
	private static double share(int minutes, int totalMinutes) {
		if (totalMinutes <= 0) {
			return 0;
		}
		return Math.round(minutes * 10000.0 / totalMinutes) / 10000.0;
	}

	/** 流水归属的任务标题。软删任务的流水仍在统计里，标题照常回填。 */
	private Map<Long, String> taskTitles(Long ownerId, List<TaskTimeLog> logs) {
		List<Long> taskIds = logs.stream().map(TaskTimeLog::getTaskId).distinct().toList();
		if (taskIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> titles = new HashMap<>();
		for (Task task : taskRepository.findByIdInAndOwnerId(taskIds, ownerId)) {
			titles.put(task.getId(), task.getTitle());
		}
		return titles;
	}

	/**
	 * 正在跑的计时。交给 TimerService：它已处理「计时指向的任务被软删」的边界（返回 empty），
	 * 这里不复制那套判断。
	 */
	private RunningTaskInfo runningTask(Long ownerId) {
		return timerService.findRunning(ownerId)
				.map(running -> new RunningTaskInfo(running.task().id(), running.task().title(), running.log().startedAt()))
				.orElse(null);
	}
}
