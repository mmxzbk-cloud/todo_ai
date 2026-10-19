package com.aiassistant.project;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.common.ConflictException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.project.dto.ProjectRequest;
import com.aiassistant.project.dto.ProjectResponse;

/**
 * 项目服务。本轮不提供删除：项目只归档（archived_at），
 * 因此不需要「项目下还有任务就不许删」这类校验。
 */
@Service
public class ProjectService {

	private final ProjectRepository repository;

	public ProjectService(ProjectRepository repository) {
		this.repository = repository;
	}

	/** includeArchived 默认 false：设置页默认只显示在用的项目，归档的要显式要。 */
	@Transactional(readOnly = true)
	public List<ProjectResponse> list(Long ownerId, boolean includeArchived) {
		List<Project> projects = includeArchived
				? repository.findByOwnerIdOrderBySortOrderAscIdAsc(ownerId)
				: repository.findByOwnerIdAndArchivedAtIsNullOrderBySortOrderAscIdAsc(ownerId);
		return projects.stream().map(ProjectResponse::from).toList();
	}

	@Transactional
	public ProjectResponse create(Long ownerId, ProjectRequest request) {
		requireNameAvailable(ownerId, request.name(), null);
		Project project = new Project();
		project.setOwnerId(ownerId);
		project.setName(request.name());
		project.setColor(request.color());
		project.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
		return ProjectResponse.from(repository.save(project));
	}

	@Transactional
	public ProjectResponse update(Long ownerId, Long id, ProjectRequest request) {
		Project project = require(ownerId, id);
		requireNameAvailable(ownerId, request.name(), id);
		project.setName(request.name());
		project.setColor(request.color());
		if (request.sortOrder() != null) {
			project.setSortOrder(request.sortOrder());
		}
		return ProjectResponse.from(repository.save(project));
	}

	@Transactional
	public ProjectResponse archive(Long ownerId, Long id) {
		Project project = require(ownerId, id);
		project.setArchivedAt(Instant.now());
		return ProjectResponse.from(repository.save(project));
	}

	/** 取消归档只看「当前有没有归档」，不报错：重复点两次不该给用户一个 409。 */
	@Transactional
	public ProjectResponse unarchive(Long ownerId, Long id) {
		Project project = require(ownerId, id);
		project.setArchivedAt(null);
		return ProjectResponse.from(repository.save(project));
	}

	private Project require(Long ownerId, Long id) {
		return repository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在：id=" + id));
	}

	/** selfId 为 null 表示新建；更新时要放过自己，否则改颜色也会被判重名。 */
	private void requireNameAvailable(Long ownerId, String name, Long selfId) {
		repository.findByOwnerIdAndName(ownerId, name)
				.filter(existing -> !Objects.equals(existing.getId(), selfId))
				.ifPresent(existing -> {
					throw new ConflictException(ErrorCode.PROJECT_NAME_TAKEN, "项目名称已存在：" + name);
				});
	}
}
