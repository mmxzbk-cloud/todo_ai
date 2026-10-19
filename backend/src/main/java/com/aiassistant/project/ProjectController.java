package com.aiassistant.project;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aiassistant.auth.CurrentUserProvider;
import com.aiassistant.project.dto.ProjectRequest;
import com.aiassistant.project.dto.ProjectResponse;

import jakarta.validation.Valid;

/**
 * 项目接口。归档/取消归档用 POST /{id}/archive、/unarchive 这种动词化路径，
 * 而不是 PATCH 一个 archivedAt 字段：动作语义明确，前端不必知道字段名。
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

	private final ProjectService service;
	private final CurrentUserProvider currentUserProvider;

	public ProjectController(ProjectService service, CurrentUserProvider currentUserProvider) {
		this.service = service;
		this.currentUserProvider = currentUserProvider;
	}

	@GetMapping
	public List<ProjectResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
		return service.list(ownerId(), includeArchived);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectResponse create(@Valid @RequestBody ProjectRequest request) {
		return service.create(ownerId(), request);
	}

	@PutMapping("/{id}")
	public ProjectResponse update(@PathVariable Long id, @Valid @RequestBody ProjectRequest request) {
		return service.update(ownerId(), id, request);
	}

	@PostMapping("/{id}/archive")
	public ProjectResponse archive(@PathVariable Long id) {
		return service.archive(ownerId(), id);
	}

	@PostMapping("/{id}/unarchive")
	public ProjectResponse unarchive(@PathVariable Long id) {
		return service.unarchive(ownerId(), id);
	}

	private Long ownerId() {
		return currentUserProvider.require().id();
	}
}
