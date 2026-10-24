package com.aiassistant.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.project.Project;
import com.aiassistant.project.ProjectRepository;
import com.aiassistant.stats.dto.ProjectUsage;
import com.aiassistant.stats.dto.RunningTaskInfo;
import com.aiassistant.stats.dto.TimeStatsResponse;
import com.aiassistant.task.Task;
import com.aiassistant.task.TaskRepository;
import com.aiassistant.timelog.TaskTimeLog;
import com.aiassistant.timelog.TaskTimeLogRepository;
import com.aiassistant.timelog.TimerService;
import com.aiassistant.timelog.dto.RunningTimerResponse;
import com.aiassistant.timelog.dto.TimeLogResponse;
import com.aiassistant.task.dto.TaskResponse;

/**
 * 统计聚合的口径验证：归日时区、无项目归属、区间校验、进行中不进合计。
 * 流水来自仓储 stub——仓储层「只取已结束流水」的过滤在 SQL 侧，这里验证服务层对结果的处理。
 *
 * 注意：会创建 mock 的辅助方法一律在 when(...) 外先调用。Mockito 不允许在外层
 * stubbing 未完成时又对另一个 mock 做 stub（UnfinishedStubbing）。
 */
@ExtendWith(MockitoExtension.class)
class TimeStatsServiceTest {

	private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
	private static final Long OWNER = 1L;

	@Mock
	private TaskTimeLogRepository timeLogRepository;
	@Mock
	private TaskRepository taskRepository;
	@Mock
	private ProjectRepository projectRepository;
	@Mock
	private TimerService timerService;

	private TimeStatsService service;

	@BeforeEach
	void setUp() {
		service = new TimeStatsService(timeLogRepository, taskRepository, projectRepository, timerService);
		lenient().when(timeLogRepository.findByOwnerIdAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
				any(), any(), any())).thenReturn(List.of());
		lenient().when(timerService.findRunning(OWNER)).thenReturn(Optional.empty());
	}

	private static TaskTimeLog log(long taskId, String startedAt, int minutes) {
		TaskTimeLog timeLog = new TaskTimeLog();
		timeLog.setTaskId(taskId);
		timeLog.setOwnerId(OWNER);
		timeLog.setStartedAt(Instant.parse(startedAt));
		timeLog.setEndedAt(Instant.parse(startedAt).plusSeconds(minutes * 60L));
		timeLog.setMinutes(minutes);
		return timeLog;
	}

	/** 实体的 id 由数据库生成、没有 setter，用 mock 提供聚合要读的字段。 */
	private static Task task(long id, Long projectId, String title) {
		Task task = org.mockito.Mockito.mock(Task.class);
		lenient().when(task.getId()).thenReturn(id);
		lenient().when(task.getProjectId()).thenReturn(projectId);
		lenient().when(task.getTitle()).thenReturn(title);
		return task;
	}

	@Test
	void rejectsInvertedRange() {
		assertThatThrownBy(() -> service.stats(OWNER, ZONE, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 1)))
				.isInstanceOfSatisfying(BadRequestException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
	}

	@Test
	void rejectsRangeLongerThan31Days() {
		assertThatThrownBy(() -> service.stats(OWNER, ZONE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 5)))
				.isInstanceOf(BadRequestException.class);
	}

	@Test
	void emptyRangeYieldsZeroesWithoutError() {
		TimeStatsResponse response = service.stats(OWNER, ZONE, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));

		assertThat(response.totalMinutes()).isZero();
		assertThat(response.days()).hasSize(3);
		assertThat(response.days()).allSatisfy(day -> assertThat(day.minutes()).isZero());
		assertThat(response.projects()).isEmpty();
		assertThat(response.running()).isNull();
	}

	@Test
	void attributesLogsByUserTimezoneDay() {
		// 2026-10-23T18:00Z 在东八区是 10-24 02:00：这条流水必须归到 10-24
		TaskTimeLog night = log(101, "2026-10-23T18:00:00Z", 30);
		TaskTimeLog morning = log(101, "2026-10-24T02:00:00Z", 60);
		Task dataTask = task(101, null, "整理数据");
		when(timeLogRepository.findByOwnerIdAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
				any(), any(), any())).thenReturn(List.of(night, morning));
		when(taskRepository.findByIdInAndOwnerId(List.of(101L), OWNER)).thenReturn(List.of(dataTask));

		TimeStatsResponse response = service.stats(OWNER, ZONE, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 24));

		assertThat(response.totalMinutes()).isEqualTo(90);
		assertThat(response.days()).hasSize(1);
		assertThat(response.days().get(0).date()).isEqualTo(LocalDate.of(2026, 10, 24));
		assertThat(response.days().get(0).minutes()).isEqualTo(90);
		assertThat(response.days().get(0).topTasks()).hasSize(1);
		assertThat(response.days().get(0).topTasks().get(0).title()).isEqualTo("整理数据");
		// 无项目归属：整段归入「无项目」
		assertThat(response.projects()).hasSize(1);
		assertThat(response.projects().get(0).projectName()).isEqualTo("无项目");
		assertThat(response.projects().get(0).minutes()).isEqualTo(90);
		assertThat(response.projects().get(0).share()).isEqualTo(1.0);
	}

	@Test
	void splitsProjectsAndSharesRoundedToFourDecimals() {
		TaskTimeLog onProject = log(201, "2026-10-24T01:00:00Z", 75);
		TaskTimeLog offProject = log(202, "2026-10-24T02:00:00Z", 25);
		Task projectTask = task(201, 5L, "项目任务");
		Task offProjectTask = task(202, null, "琐事");
		Project project = org.mockito.Mockito.mock(Project.class);
		when(timeLogRepository.findByOwnerIdAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
				any(), any(), any())).thenReturn(List.of(onProject, offProject));
		when(taskRepository.findByIdInAndOwnerId(List.of(201L, 202L), OWNER))
				.thenReturn(List.of(projectTask, offProjectTask));
		when(project.getId()).thenReturn(5L);
		when(project.getName()).thenReturn("AI 助手");
		when(projectRepository.findByOwnerIdAndIdIn(OWNER, java.util.Set.of(5L))).thenReturn(List.of(project));

		TimeStatsResponse response = service.stats(OWNER, ZONE, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 24));

		assertThat(response.projects()).hasSize(2);
		ProjectUsage first = response.projects().get(0);
		assertThat(first.projectName()).isEqualTo("AI 助手");
		assertThat(first.minutes()).isEqualTo(75);
		assertThat(first.share()).isEqualTo(0.75);
		ProjectUsage second = response.projects().get(1);
		assertThat(second.projectId()).isNull();
		assertThat(second.minutes()).isEqualTo(25);
		assertThat(second.share()).isEqualTo(0.25);
	}

	@Test
	void reportsRunningTimerSeparately() {
		TaskTimeLog closed = log(301, "2026-10-24T01:00:00Z", 20);
		Task writeTask = task(301, null, "写周报");
		TimeLogResponse runningLog = new TimeLogResponse(999L, 301L, Instant.parse("2026-10-24T03:00:00Z"), null, 0,
				"TIMER", null);
		// from() 会调用 mock 的读取方法，必须在外层 when(...) 完成之前执行
		RunningTimerResponse runningResponse = new RunningTimerResponse(runningLog, TaskResponse.from(writeTask));
		when(timeLogRepository.findByOwnerIdAndEndedAtIsNotNullAndStartedAtGreaterThanEqualAndStartedAtLessThan(
				any(), any(), any())).thenReturn(List.of(closed));
		when(taskRepository.findByIdInAndOwnerId(List.of(301L), OWNER)).thenReturn(List.of(writeTask));
		when(timerService.findRunning(OWNER)).thenReturn(Optional.of(runningResponse));

		TimeStatsResponse response = service.stats(OWNER, ZONE, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 24));

		RunningTaskInfo running = response.running();
		assertThat(running).isNotNull();
		assertThat(running.taskId()).isEqualTo(301L);
		assertThat(running.startedAt()).isEqualTo(Instant.parse("2026-10-24T03:00:00Z"));
		// 闭合流水照常计入合计；进行中段落不在返回的流水列表里，天然不进合计
		assertThat(response.totalMinutes()).isEqualTo(20);
	}
}
