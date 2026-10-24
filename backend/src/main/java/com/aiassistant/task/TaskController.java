package com.aiassistant.task;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUser;
import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.common.PageResponse;
import com.aiassistant.task.dto.BoardColumnResponse;
import com.aiassistant.task.dto.TagIdsRequest;
import com.aiassistant.task.dto.TaskDetailResponse;
import com.aiassistant.task.dto.TaskPriorityRequest;
import com.aiassistant.task.dto.TaskRequest;
import com.aiassistant.task.dto.TaskResponse;
import com.aiassistant.task.dto.TaskStatusRequest;

import jakarta.validation.Valid;

/**
 * 任务接口。Controller 只做协议转换：取当前用户、绑定并触发校验、把结果交给 Service。
 * 计时相关端点（/timers、/time-logs、/activities）与这里同基路径，由计时模块的 Controller 提供。
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

	private final TaskService service;
	private final CurrentUserProvider currentUserProvider;

	public TaskController(TaskService service, CurrentUserProvider currentUserProvider) {
		this.service = service;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public PageResponse<TaskResponse> list(
			@RequestParam(required = false) TaskStatus status,
			@RequestParam(required = false) Long projectId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.list(ownerId(), status, projectId, dueFrom, dueTo, q, page, size);
	}

	/** 今日视图要用用户的时区算「今天」，所以这里取整个 CurrentUser 而不是只取 id。 */
	@GetMapping("/today")
	public List<TaskResponse> today() {
		CurrentUser user = currentUserProvider.require();
		return service.today(user.id(), user.zoneId());
	}

	@GetMapping("/board")
	public List<BoardColumnResponse> board() {
		return service.board(ownerId());
	}

	@GetMapping("/{id}")
	public TaskDetailResponse get(@PathVariable Long id) {
		return service.getDetail(ownerId(), id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TaskResponse create(@Valid @RequestBody TaskRequest request) {
		return service.create(ownerId(), request);
	}

	/** 全量更新；请求带 version 时做乐观锁校验，冲突返回 409 VERSION_CONFLICT。 */
	@PutMapping("/{id}")
	public TaskResponse update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
		return service.update(ownerId(), id, request);
	}

	@PatchMapping("/{id}/status")
	public TaskResponse changeStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest request) {
		return service.changeStatus(ownerId(), id, request.status());
	}

	/**
	 * 只改优先级。看板与列表上的行内下拉打这个接口，不必为改一个枚举值回传整份任务。
	 * 与 /status 同形：单独一个端点，语义明确，也不会被「整体替换」的规则牵连。
	 */
	@PatchMapping("/{id}/priority")
	public TaskResponse changePriority(@PathVariable Long id, @Valid @RequestBody TaskPriorityRequest request) {
		return service.changePriority(ownerId(), id, request.priority());
	}

	/** 整体替换标签集合，返回更新后的详情，省掉前端一次重新拉详情。 */
	@PutMapping("/{id}/tags")
	public TaskDetailResponse assignTags(@PathVariable Long id, @Valid @RequestBody TagIdsRequest request) {
		return service.assignTags(ownerId(), id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		service.delete(ownerId(), id);
	}

	/** ownerId 只从认证上下文取：请求体与查询参数里都没有它，也就无从伪造。 */
	private Long ownerId() {
		return currentUserProvider.require().id();
	}
}
