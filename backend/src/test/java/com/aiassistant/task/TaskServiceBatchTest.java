package com.aiassistant.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;

import com.aiassistant.audit.TaskActivityLogRepository;
import com.aiassistant.audit.TaskActivityRecorder;
import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.project.ProjectRepository;
import com.aiassistant.tag.TagRepository;
import com.aiassistant.task.dto.TaskRequest;
import com.aiassistant.task.dto.TaskResponse;
import com.aiassistant.timelog.TaskTimeLogRepository;

/**
 * 批量导入的规则：容量上限与非空校验、逐条复用单条创建（含审计）、输入输出同序。
 * 事务原子性由 @Transactional 保证，单元层只验证「失败在写库之前就被拦下」。
 */
class TaskServiceBatchTest {

	private static final Long OWNER = 1L;

	private TaskRepository taskRepository;
	private TaskActivityRecorder activityRecorder;
	private TaskService service;

	@BeforeEach
	void setUp() {
		taskRepository = mock(TaskRepository.class);
		activityRecorder = mock(TaskActivityRecorder.class);
		service = new TaskService(taskRepository, mock(ProjectRepository.class), mock(TagRepository.class),
				mock(TaskTagRepository.class), mock(TaskActivityLogRepository.class),
				activityRecorder, mock(TaskTimeLogRepository.class));
	}

	private static TaskRequest request(String title) {
		return new TaskRequest(title, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null);
	}

	/**
	 * save 的最小仿真：补一个自增 id 再原样返回。真实链路里 id 由 JPA 生成且 save 后必有值，
	 * 出参组装（tagsByTaskId → getOrDefault）依赖这一点，单元仿真必须保持同样的不变量。
	 */
	private void saveAssignsId() {
		AtomicLong idSeq = new AtomicLong(100);
		when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
			Task task = invocation.getArgument(0);
			if (task.getId() == null) {
				ReflectionTestUtils.setField(task, "id", idSeq.incrementAndGet());
			}
			// @Version 同理由持久化层填充，出参组装会拆箱它
			if (task.getVersion() == null) {
				ReflectionTestUtils.setField(task, "version", 0);
			}
			return task;
		});
	}

	@Test
	void batchCreatesEveryTaskInInputOrder() {
		saveAssignsId();

		List<TaskResponse> created = service.createBatch(OWNER,
				List.of(request("买牛奶"), request("写周报"), request("预约体检")));

		assertThat(created).extracting(TaskResponse::title)
				.containsExactly("买牛奶", "写周报", "预约体检");
		verify(taskRepository, times(3)).save(any(Task.class));
		// 审计与单条创建同一套：每条一条 CREATED，否则批量入口会漏操作历史
		verify(activityRecorder, times(3)).record(any(), any(), any(), any(), any(), any());
	}

	@Test
	void batchAcceptsExactlyMaxSize() {
		saveAssignsId();

		List<TaskRequest> requests = java.util.stream.IntStream.rangeClosed(1, TaskService.MAX_BATCH_SIZE)
				.mapToObj(index -> request("任务 " + index))
				.toList();

		assertThat(service.createBatch(OWNER, requests)).hasSize(TaskService.MAX_BATCH_SIZE);
	}

	@Test
	void emptyBatchIsRejectedBeforeAnyWrite() {
		assertThatThrownBy(() -> service.createBatch(OWNER, List.of()))
				.isInstanceOfSatisfying(BadRequestException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED));
		verify(taskRepository, never()).save(any(Task.class));
	}

	@Test
	void oversizedBatchIsRejectedBeforeAnyWrite() {
		List<TaskRequest> requests = java.util.stream.IntStream
				.rangeClosed(1, TaskService.MAX_BATCH_SIZE + 1)
				.mapToObj(index -> request("任务 " + index))
				.toList();

		assertThatThrownBy(() -> service.createBatch(OWNER, requests))
				.isInstanceOfSatisfying(BadRequestException.class, ex -> {
					assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
					assertThat(ex.getMessage()).contains(String.valueOf(TaskService.MAX_BATCH_SIZE));
				});
		verify(taskRepository, never()).save(any(Task.class));
	}
}
