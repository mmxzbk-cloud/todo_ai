package com.aiassistant.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.stats.dto.DayCompletion;
import com.aiassistant.stats.dto.TaskStatsResponse;
import com.aiassistant.task.Priority;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.task.TaskStatus;

/**
 * 任务概览统计。只读、实时计算，不引入第二份冗余存储（与耗时统计同一条设计约定）。
 *
 * <p>口径：
 * <ol>
 * <li>状态/优先级分布与逾期是「当前快照」，统计所有未删任务，不受查询区间影响；</li>
 * <li>新建与完成数按区间统计，归日按用户时区（Controller 传 zoneId），与今日视图同口径；</li>
 * <li>完成趋势的区间内每天一条（含 0），前端画图不用补空日期。</li>
 * </ol>
 */
@Service
public class TaskStatsService {

	/** 与耗时统计同一条上限：超过 31 天的区间对个人回顾没有意义，直接拒绝。 */
	private static final int MAX_RANGE_DAYS = 31;

	private final TaskRepository taskRepository;

	public TaskStatsService(TaskRepository taskRepository) {
		this.taskRepository = taskRepository;
	}

	@Transactional(readOnly = true)
	public TaskStatsResponse stats(Long ownerId, ZoneId zoneId, int days) {
		validateDays(days);
		LocalDate today = LocalDate.now(zoneId);
		LocalDate from = today.minusDays(days - 1L);
		Instant start = from.atStartOfDay(zoneId).toInstant();
		Instant end = today.plusDays(1).atStartOfDay(zoneId).toInstant();

		List<Task> completedTasks = taskRepository
				.findByOwnerIdAndDeletedAtIsNullAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
						ownerId, start, end);

		return new TaskStatsResponse(
				from,
				today,
				countByStatus(ownerId),
				countByPriority(ownerId),
				taskRepository.countByOwnerIdAndDeletedAtIsNullAndDueOnBeforeAndStatusIn(
						ownerId, today, TaskStatus.OPEN_STATUSES),
				taskRepository.countByOwnerIdAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
						ownerId, start, end),
				completedTasks.size(),
				completionsByDay(completedTasks, zoneId, from, today));
	}

	private static void validateDays(int days) {
		if (days < 1 || days > MAX_RANGE_DAYS) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "统计天数不正确，最长支持 31 天");
		}
	}

	/** GROUP BY 只会给出现过的枚举值，这里补齐全量键并转成 Map，前端拿到的键是完整的。 */
	private Map<TaskStatus, Long> countByStatus(Long ownerId) {
		Map<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
		for (TaskStatus status : TaskStatus.values()) {
			counts.put(status, 0L);
		}
		for (Object[] row : taskRepository.countGroupedByStatus(ownerId)) {
			counts.merge((TaskStatus) row[0], (Long) row[1], Long::sum);
		}
		return counts;
	}

	private Map<Priority, Long> countByPriority(Long ownerId) {
		Map<Priority, Long> counts = new EnumMap<>(Priority.class);
		for (Priority priority : Priority.values()) {
			counts.put(priority, 0L);
		}
		for (Object[] row : taskRepository.countGroupedByPriority(ownerId)) {
			counts.merge((Priority) row[0], (Long) row[1], Long::sum);
		}
		return counts;
	}

	/** 完成时刻按用户时区归日；区间内没有完成的日子也要有条目（count=0），与耗时统计同约定。 */
	private static List<DayCompletion> completionsByDay(List<Task> completedTasks, ZoneId zoneId,
			LocalDate from, LocalDate to) {
		Map<LocalDate, Long> countsByDay = new HashMap<>();
		for (Task task : completedTasks) {
			LocalDate day = LocalDateTime.ofInstant(task.getCompletedAt(), zoneId).toLocalDate();
			countsByDay.merge(day, 1L, Long::sum);
		}
		List<DayCompletion> days = new ArrayList<>();
		for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
			days.add(new DayCompletion(day, countsByDay.getOrDefault(day, 0L)));
		}
		return days;
	}
}
