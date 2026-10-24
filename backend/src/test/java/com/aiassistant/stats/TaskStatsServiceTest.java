package com.aiassistant.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.stats.dto.TaskStatsResponse;
import com.aiassistant.task.Priority;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.task.TaskStatus;

/**
 * 任务概览统计的口径验证：快照补零、逾期口径、完成时刻按用户时区归日、区间校验。
 * 仓储结果一律 stub——「只数未删任务」在 SQL 侧，这里验证服务层对结果的处理。
 */
@ExtendWith(MockitoExtension.class)
class TaskStatsServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
	private static final Long OWNER = 1L;

	@Mock
	private TaskRepository taskRepository;

	private TaskStatsService service;

	@BeforeEach
	void setUp() {
		service = new TaskStatsService(taskRepository);
	}

	/** 实体的 id 由数据库生成、没有 setter，用 mock 提供聚合要读的字段。 */
	private static Task completedTask(Instant completedAt) {
		Task task = org.mockito.Mockito.mock(Task.class);
		org.mockito.Mockito.lenient().when(task.getCompletedAt()).thenReturn(completedAt);
		return task;
	}

	@Test
	void rejectsInvalidDays() {
		assertThatThrownBy(() -> service.stats(OWNER, ZONE, 0))
				.isInstanceOfSatisfying(BadRequestException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		assertThatThrownBy(() -> service.stats(OWNER, ZONE, 32))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void snapshotCountsFillMissingKeysWithZero() {
		// 库里只有 TODO 与 P0：其余状态/优先级必须补 0，前端拿到的键才是全量的
		when(taskRepository.countGroupedByStatus(OWNER))
				.thenReturn(List.<Object[]>of(new Object[] { TaskStatus.TODO, 2L }));
		when(taskRepository.countGroupedByPriority(OWNER))
				.thenReturn(List.<Object[]>of(new Object[] { Priority.P0, 1L }));
		when(taskRepository.findByOwnerIdAndDeletedAtIsNullAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
				org.mockito.ArgumentMatchers.eq(OWNER), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

		TaskStatsResponse response = service.stats(OWNER, ZONE, 7);

		assertThat(response.byStatus()).hasSize(TaskStatus.values().length);
		assertThat(response.byStatus().get(TaskStatus.TODO)).isEqualTo(2L);
		assertThat(response.byStatus().get(TaskStatus.DOING)).isZero();
		assertThat(response.byPriority()).hasSize(Priority.values().length);
		assertThat(response.byPriority().get(Priority.P0)).isEqualTo(1L);
		assertThat(response.byPriority().get(Priority.P3)).isZero();
		assertThat(response.completedInRange()).isZero();
	}

	@Test
	void bucketsCompletionsByUserTimezoneDay() {
		// 东八区凌晨 2 点的 UTC 时刻落在前一天 18:00：按 UTC 归日会归错天，
		// 这条完成必须按用户时区归到「今天」
		LocalDate today = LocalDate.now(ZONE);
		Task night = completedTask(today.atTime(2, 0).atZone(ZONE).toInstant());
		Task morning = completedTask(today.atTime(10, 0).atZone(ZONE).toInstant());
		when(taskRepository.findByOwnerIdAndDeletedAtIsNullAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
				org.mockito.ArgumentMatchers.eq(OWNER), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any())).thenReturn(List.of(night, morning));

		// 区间取单日「今天」（days=1）：两条完成都落在这一天
		TaskStatsResponse response = service.stats(OWNER, ZONE, 1);

		assertThat(response.from()).isEqualTo(today);
		assertThat(response.to()).isEqualTo(today);
		assertThat(response.completedInRange()).isEqualTo(2);
		assertThat(response.completedByDay()).hasSize(1);
		assertThat(response.completedByDay().get(0).date()).isEqualTo(today);
		assertThat(response.completedByDay().get(0).count()).isEqualTo(2);
	}

	@Test
	void overdueUsesOpenStatusesAndTodayInUserZone() {
		LocalDate today = LocalDate.now(ZONE);
		// 口径：截止日严格早于「今天」且仍未结束——参数直接用服务层算出的同一天
		when(taskRepository.countByOwnerIdAndDeletedAtIsNullAndDueOnBeforeAndStatusIn(
				OWNER, today, TaskStatus.OPEN_STATUSES)).thenReturn(3L);
		when(taskRepository.countByOwnerIdAndDeletedAtIsNullAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
				org.mockito.ArgumentMatchers.eq(OWNER), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any())).thenReturn(5L);
		when(taskRepository.findByOwnerIdAndDeletedAtIsNullAndCompletedAtGreaterThanEqualAndCompletedAtLessThan(
				org.mockito.ArgumentMatchers.eq(OWNER), org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any())).thenReturn(List.of());

		TaskStatsResponse response = service.stats(OWNER, ZONE, 7);

		assertThat(response.overdue()).isEqualTo(3L);
		assertThat(response.createdInRange()).isEqualTo(5L);
		assertThat(response.completedByDay()).hasSize(7);
		assertThat(response.completedByDay()).allSatisfy(day -> assertThat(day.count()).isZero());
	}
}
