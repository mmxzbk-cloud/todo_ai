package com.aiassistant.task;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aiassistant.audit.TaskAction;
import com.aiassistant.audit.TaskActivityLogRepository;
import com.aiassistant.audit.TaskActivityRecorder;
import com.aiassistant.audit.dto.ActivityResponse;
import com.aiassistant.common.BadRequestException;
import com.aiassistant.common.ConflictException;
import com.aiassistant.common.ErrorCode;
import com.aiassistant.common.PageResponse;
import com.aiassistant.common.ResourceNotFoundException;
import com.aiassistant.project.Project;
import com.aiassistant.project.ProjectRepository;
import com.aiassistant.tag.Tag;
import com.aiassistant.tag.TagRepository;
import com.aiassistant.tag.dto.TagResponse;
import com.aiassistant.task.dto.BoardColumnResponse;
import com.aiassistant.task.dto.TagIdsRequest;
import com.aiassistant.task.dto.TaskDetailResponse;
import com.aiassistant.task.dto.TaskRequest;
import com.aiassistant.task.dto.TaskResponse;
import com.aiassistant.timelog.TaskTimeLogRepository;
import com.aiassistant.timelog.dto.TimeLogResponse;

/**
 * 任务域的唯一写入口。
 *
 * 三个约定贯穿全类：
 * 1. ownerId 由 Controller 从认证上下文取好传进来。Service 不碰 SecurityContextHolder，
 *    这样它可以在没有 Spring 上下文的单元测试里直接构造（TaskServiceTest 就是这么跑的）。
 * 2. 所有按 id 取数都走 owner 条件 + deleted_at IS NULL 的查询，查不到就是 404：
 *    他人的任务与已软删的任务对外表现完全一样，不泄露「这个 id 存在」。
 * 3. 状态、优先级、软删、审计的规则只在这里落地，别处（含计时模块）不再自己拼一套。
 */
@Service
public class TaskService {

	/**
	 * 详情页的耗时明细取最近多少条。变更历史不用这个常量：它的 50 写在仓储方法名里
	 * （与 /activities 端点共用），避免两处各有一个「最近多少条」的答案。
	 */
	private static final int DETAIL_HISTORY_LIMIT = 50;

	/** 单页上限：没有上限时一个请求就能把整库拉出来。 */
	private static final int MAX_PAGE_SIZE = 100;

	private final TaskRepository taskRepository;
	private final ProjectRepository projectRepository;
	private final TagRepository tagRepository;
	private final TaskTagRepository taskTagRepository;
	private final TaskActivityLogRepository activityLogRepository;
	private final TaskActivityRecorder activityRecorder;
	private final TaskTimeLogRepository timeLogRepository;

	public TaskService(
			TaskRepository taskRepository,
			ProjectRepository projectRepository,
			TagRepository tagRepository,
			TaskTagRepository taskTagRepository,
			TaskActivityLogRepository activityLogRepository,
			TaskActivityRecorder activityRecorder,
			TaskTimeLogRepository timeLogRepository) {
		this.taskRepository = taskRepository;
		this.projectRepository = projectRepository;
		this.tagRepository = tagRepository;
		this.taskTagRepository = taskTagRepository;
		this.activityLogRepository = activityLogRepository;
		this.activityRecorder = activityRecorder;
		this.timeLogRepository = timeLogRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<TaskResponse> list(Long ownerId, TaskStatus status, Long projectId,
			LocalDate dueFrom, LocalDate dueTo, String q, int page, int size) {
		String keyword = (q == null || q.isBlank()) ? null : q.trim();
		PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
		Page<Task> found = taskRepository.search(
				ownerId,
				status != null, status,
				projectId != null, projectId,
				dueFrom != null, dueFrom,
				dueTo != null, dueTo,
				keyword != null, keyword,
				pageable);
		List<TaskResponse> items = TaskMapper.toResponses(found.getContent(), projectNames(ownerId, found.getContent()));
		return new PageResponse<>(items, found.getTotalElements(), found.getNumber(), found.getSize());
	}

	/**
	 * 今日视图：截止日不晚于「今天」且状态未结束。
	 * 「今天」按调用方给的时区算（Controller 传当前用户的 zoneId），服务器时区只影响日志时间戳。
	 */
	@Transactional(readOnly = true)
	public List<TaskResponse> today(Long ownerId, ZoneId zoneId) {
		LocalDate today = LocalDate.now(zoneId);
		List<Task> tasks = taskRepository.findToday(ownerId, today, TaskStatus.OPEN_STATUSES);
		return TaskMapper.toResponses(tasks, projectNames(ownerId, tasks));
	}

	@Transactional(readOnly = true)
	public List<BoardColumnResponse> board(Long ownerId) {
		List<Task> tasks = taskRepository.findBoard(ownerId, TaskStatus.BOARD_ORDER);
		Map<Long, String> projectNames = projectNames(ownerId, tasks);

		// 一次查询按状态分组：全局排序已经保证了列内顺序，分组不会打乱它
		Map<TaskStatus, List<TaskResponse>> columns = new EnumMap<>(TaskStatus.class);
		for (Task task : tasks) {
			columns.computeIfAbsent(task.getStatus(), key -> new ArrayList<>())
					.add(TaskResponse.from(task, TaskMapper.projectName(task, projectNames)));
		}
		return TaskStatus.BOARD_ORDER.stream()
				.map(status -> new BoardColumnResponse(status, columns.getOrDefault(status, List.of())))
				.toList();
	}

	@Transactional(readOnly = true)
	public TaskDetailResponse getDetail(Long ownerId, Long id) {
		Task task = require(ownerId, id);
		Map<Long, String> ownProjectName = projectNames(ownerId, List.of(task));
		TaskResponse response = TaskResponse.from(task, TaskMapper.projectName(task, ownProjectName));

		List<Long> tagIds = taskTagRepository.findTagIdsByTaskId(id);
		List<TagResponse> tags = tagIds.isEmpty()
				? List.of()
				: tagRepository.findByOwnerIdAndIdInAndDeletedAtIsNullOrderByNameAscIdAsc(ownerId, tagIds).stream()
						.map(TagResponse::from)
						.toList();

		List<Task> children = taskRepository.findByOwnerIdAndDeletedAtIsNullAndParentTaskIdOrderByIdDesc(ownerId, id);
		List<TaskResponse> childResponses = TaskMapper.toResponses(children, projectNames(ownerId, children));

		// 耗时明细走计时模块冻死的单参方法（已按 startedAt 倒序），条数在这里截：
		// 契约里没有分页重载，为了一个「最近 50 条」去改对方的仓储不值得，先取全部再 limit 的代价可以忽略
		// （一次计时一段流水，50 条以上的任务极少）。
		List<TimeLogResponse> timeLogs = timeLogRepository
				.findByTaskIdOrderByStartedAtDesc(id)
				.stream()
				.limit(DETAIL_HISTORY_LIMIT)
				.map(TimeLogResponse::from)
				.toList();
		// 变更历史的「最近 50 条」写在仓储方法名里（findTop50...），与 /activities 端点共用同一个查询，
		// 两个入口对同一件事必须给出同样的条数与顺序。
		List<ActivityResponse> activities = activityLogRepository
				.findTop50ByTaskIdOrderByCreatedAtDesc(id)
				.stream()
				.map(ActivityResponse::from)
				.toList();

		return new TaskDetailResponse(response, tags, childResponses, timeLogs, activities);
	}

	@Transactional
	public TaskResponse create(Long ownerId, TaskRequest request) {
		Instant now = Instant.now();
		Task task = new Task();
		task.setOwnerId(ownerId);
		task.setTitle(request.title());
		task.setDescription(request.description());
		task.setSource(request.source());
		task.setDueOn(request.dueOn());
		task.setEstimatedMinutes(request.estimatedMinutes());
		task.setAiReplaceable(request.aiReplaceable());
		task.setAiReplacementNote(request.aiReplacementNote());
		task.setNextAction(request.nextAction());
		task.setNotes(request.notes());

		task.setImportance(request.importance() == null ? Importance.MEDIUM : request.importance());
		task.setUrgency(request.urgency() == null ? Urgency.MEDIUM : request.urgency());
		// 新建：请求带 priority 就用它，否则由重要性 × 紧急性推导
		task.setPriority(request.priority() == null
				? Priority.derive(task.getImportance(), task.getUrgency())
				: request.priority());

		if (request.projectId() != null) {
			requireProject(ownerId, request.projectId());
		}
		task.setProjectId(request.projectId());

		if (request.parentTaskId() != null) {
			// 新建时任务还没有 id，所以只可能是「挂到别人下面」，不存在自引用
			requireParentTask(ownerId, request.parentTaskId());
		}
		task.setParentTaskId(request.parentTaskId());

		TaskStatus status = request.status() == null ? TaskStatus.TODO : request.status();
		task.setStatus(status);
		if (status == TaskStatus.DONE) {
			// 直接建成已完成的也要有完成时刻，否则周复盘的完成数会漏掉它
			task.setCompletedAt(now);
		}

		Task saved = taskRepository.save(task);
		// 审计要写在 save 之后：此刻才有任务 id，且库里的行已存在（外键是 RESTRICT，不会静默丢历史）
		activityRecorder.record(saved.getId(), ownerId, TaskAction.CREATED, null, null, saved.getTitle());
		return TaskResponse.from(saved, projectName(ownerId, saved.getProjectId()));
	}

	@Transactional
	public TaskResponse update(Long ownerId, Long id, TaskRequest request) {
		Task task = require(ownerId, id);
		checkVersion(task, request.version());
		Instant now = Instant.now();

		// 整体替换语义：文案类字段传 null 就是清空（前端清空输入框后提交 null 必须真的清掉）
		applyAndAudit(task, ownerId, "title", task.getTitle(), request.title(), task::setTitle);
		applyAndAudit(task, ownerId, "description", task.getDescription(), request.description(), task::setDescription);
		applyAndAudit(task, ownerId, "source", task.getSource(), request.source(), task::setSource);
		applyAndAudit(task, ownerId, "dueOn", task.getDueOn(), request.dueOn(), task::setDueOn);
		applyAndAudit(task, ownerId, "estimatedMinutes", task.getEstimatedMinutes(), request.estimatedMinutes(),
				task::setEstimatedMinutes);
		applyAndAudit(task, ownerId, "aiReplaceable", task.getAiReplaceable(), request.aiReplaceable(),
				task::setAiReplaceable);
		applyAndAudit(task, ownerId, "aiReplacementNote", task.getAiReplacementNote(), request.aiReplacementNote(),
				task::setAiReplacementNote);
		applyAndAudit(task, ownerId, "nextAction", task.getNextAction(), request.nextAction(), task::setNextAction);
		applyAndAudit(task, ownerId, "notes", task.getNotes(), request.notes(), task::setNotes);

		if (request.projectId() != null) {
			requireProject(ownerId, request.projectId());
		}
		applyAndAudit(task, ownerId, "projectId", task.getProjectId(), request.projectId(), task::setProjectId);

		if (request.parentTaskId() != null) {
			requireParentTask(ownerId, id, request.parentTaskId());
		}
		applyAndAudit(task, ownerId, "parentTaskId", task.getParentTaskId(), request.parentTaskId(),
				task::setParentTaskId);

		// 重要性 / 紧急性：传 null 表示不修改（它们没有「清空」的语义），所以单独处理
		boolean importanceChanged = request.importance() != null
				&& applyAndAudit(task, ownerId, "importance", task.getImportance(), request.importance(),
						task::setImportance);
		boolean urgencyChanged = request.urgency() != null
				&& applyAndAudit(task, ownerId, "urgency", task.getUrgency(), request.urgency(), task::setUrgency);
		if (request.priority() != null) {
			applyAndAudit(task, ownerId, "priority", task.getPriority(), request.priority(), task::setPriority);
		} else if (importanceChanged || urgencyChanged) {
			// 只有两个轴真的变了才重推：否则手工覆盖过的优先级会被下一次普通编辑悄悄改掉
			applyAndAudit(task, ownerId, "priority", task.getPriority(),
					Priority.derive(task.getImportance(), task.getUrgency()), task::setPriority);
		}

		// status 传 null 表示不修改；改状态一律走 PATCH /status，这里兼容整体替换的场景
		if (request.status() != null) {
			applyStatusChange(task, ownerId, request.status(), now);
		}

		// saveAndFlush 而不是 save：@Version 在 flush 时才自增，若把 flush 交给事务提交，
		// 下面映射出的 version 会是更新前的旧值（是否踩到还取决于本次事务里之后有没有别的查询触发隐式 flush）。
		Task saved = taskRepository.saveAndFlush(task);
		return TaskResponse.from(saved, projectName(ownerId, saved.getProjectId()));
	}

	/** 改状态。列表/看板上的下拉都打这个接口，所以它必须自己处理完成时刻，而不是要求前端拼。 */
	@Transactional
	public TaskResponse changeStatus(Long ownerId, Long id, TaskStatus status) {
		Task task = require(ownerId, id);
		applyStatusChange(task, ownerId, status, Instant.now());
		Task saved = taskRepository.saveAndFlush(task);
		return TaskResponse.from(saved, projectName(ownerId, saved.getProjectId()));
	}

	/**
	 * 改优先级。列表/看板上的行内下拉都打这个接口。
	 * 与 update 里那条「手工覆盖」是同一个含义：落库的值优先，不会被下次矩阵推导改掉。
	 */
	@Transactional
	public TaskResponse changePriority(Long ownerId, Long id, Priority priority) {
		Task task = require(ownerId, id);
		applyAndAudit(task, ownerId, "priority", task.getPriority(), priority, task::setPriority);
		Task saved = taskRepository.saveAndFlush(task);
		return TaskResponse.from(saved, projectName(ownerId, saved.getProjectId()));
	}

	/**
	 * 整体替换任务的标签集合：先删后插。
	 * 每个 id 都校验「属于当前 owner 且未软删」，缺一个就整批拒绝——
	 * 部分成功会让前端拿到一个它没提交过的集合。
	 */
	@Transactional
	public TaskDetailResponse assignTags(Long ownerId, Long id, TagIdsRequest request) {
		Task task = require(ownerId, id);
		List<Long> tagIds = request.tagIds() == null
				? List.of()
				: request.tagIds().stream().filter(Objects::nonNull).distinct().toList();
		if (!tagIds.isEmpty()) {
			List<Long> owned = tagRepository.findByOwnerIdAndIdInAndDeletedAtIsNullOrderByNameAscIdAsc(ownerId, tagIds)
					.stream()
					.map(Tag::getId)
					.toList();
			List<Long> missing = tagIds.stream().filter(tagId -> !owned.contains(tagId)).toList();
			if (!missing.isEmpty()) {
				throw new ResourceNotFoundException(ErrorCode.TAG_NOT_FOUND, "标签不存在：id=" + missing);
			}
		}

		List<Long> before = taskTagRepository.findTagIdsByTaskId(id);
		// 批量删除语句调用即执行，所以这里删、下面插的顺序是确定的；
		// 反过来（先插再删）会撞 task_tags 的复合主键
		taskTagRepository.deleteByTaskId(id);
		Instant now = Instant.now();
		for (Long tagId : tagIds) {
			taskTagRepository.save(new TaskTag(new TaskTagId(id, tagId), now));
		}
		if (!before.stream().sorted().toList().equals(tagIds.stream().sorted().toList())) {
			activityRecorder.record(id, ownerId, TaskAction.UPDATED, "tags", before.toString(), tagIds.toString());
		}
		// 列表里的标签名与顺序都由服务端给，避免前端自己拼出错
		return getDetail(ownerId, id);
	}

	/** 软删。查不到、别人的、已删的都走同一条 404：不透露「这个 id 存在过」。 */
	@Transactional
	public void delete(Long ownerId, Long id) {
		Task task = require(ownerId, id);
		task.softDelete(Instant.now());
		taskRepository.save(task);
		activityRecorder.record(id, ownerId, TaskAction.DELETED, null, null, null);
	}

	private Task require(Long ownerId, Long id) {
		return taskRepository.findByIdAndOwnerIdAndDeletedAtIsNull(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND, "任务不存在：id=" + id));
	}

	/**
	 * 乐观锁第一道防线：请求带了 version 就先比一次，能给出「请刷新后重试」这种用户能看懂的话。
	 * @Version 是第二道防线，拦的是「两个请求同时读到同一个版本」这种比完才发生的冲突。
	 */
	private void checkVersion(Task task, Integer requestedVersion) {
		if (requestedVersion != null && !requestedVersion.equals(task.getVersion())) {
			throw new ConflictException(ErrorCode.VERSION_CONFLICT, "任务已被其他页面修改，请刷新后重试");
		}
	}

	/** 状态与完成时刻是绑在一起的：置 DONE 记时刻，从 DONE 改走就清空，否则周复盘会一直把这条算成已完成。 */
	private void applyStatusChange(Task task, Long actorId, TaskStatus target, Instant at) {
		TaskStatus previous = task.getStatus();
		if (previous == target) {
			return;
		}
		task.setStatus(target);
		task.setCompletedAt(target == TaskStatus.DONE ? at : null);
		activityRecorder.record(task.getId(), actorId, TaskAction.STATUS_CHANGED, "status", previous.name(), target.name());
	}

	/** 字段改了才落库、才写审计；没变就不写，否则每次保存都会刷出一堆「无变化」的历史。 */
	private <T> boolean applyAndAudit(Task task, Long actorId, String field, T oldValue, T newValue,
			Consumer<T> setter) {
		if (Objects.equals(oldValue, newValue)) {
			return false;
		}
		setter.accept(newValue);
		activityRecorder.record(task.getId(), actorId, TaskAction.UPDATED, field, auditValue(oldValue), auditValue(newValue));
		return true;
	}

	/** 审计列是字符串：枚举存名字，日期存 ISO 格式，其余走 toString。 */
	private static String auditValue(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Enum<?> constant) {
			return constant.name();
		}
		return String.valueOf(value);
	}

	/** 项目必须属于当前 owner：任务上的 project_id 是跨表的业务引用，指向别人的项目等于越权读取。 */
	private void requireProject(Long ownerId, Long projectId) {
		projectRepository.findByIdAndOwnerId(projectId, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在：id=" + projectId));
	}

	/** 新建时用：父任务必须属于当前 owner，且它自己不能再是子任务（层级只做两层）。 */
	private void requireParentTask(Long ownerId, Long parentTaskId) {
		Task parent = taskRepository.findByIdAndOwnerIdAndDeletedAtIsNull(parentTaskId, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException(ErrorCode.TASK_NOT_FOUND, "父任务不存在：id=" + parentTaskId));
		if (parent.getParentTaskId() != null) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "子任务不能再挂子任务");
		}
	}

	/** 更新时用：先拦自引用（数据库的 CHECK 只是兜底，越过它去撞约束只会得到一个 500）。 */
	private void requireParentTask(Long ownerId, Long taskId, Long parentTaskId) {
		if (parentTaskId.equals(taskId)) {
			throw new BadRequestException(ErrorCode.VALIDATION_FAILED, "任务不能把自己设为父任务");
		}
		requireParentTask(ownerId, parentTaskId);
	}

	/**
	 * 批量回填项目名：先收集本页出现过的 projectId，一次查回。
	 * 逐行查项目名在 100 条分页下是 100 次查询，而这个方法的成本与项目数无关。
	 */
	private Map<Long, String> projectNames(Long ownerId, Collection<Task> tasks) {
		Set<Long> projectIds = tasks.stream()
				.map(Task::getProjectId)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (projectIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> names = new HashMap<>();
		for (Project project : projectRepository.findByOwnerIdAndIdIn(ownerId, projectIds)) {
			names.put(project.getId(), project.getName());
		}
		return names;
	}

	/** 单个任务出参的项目名（新建/更新/改状态用），返回 null 表示没有项目。 */
	private String projectName(Long ownerId, Long projectId) {
		if (projectId == null) {
			return null;
		}
		return projectRepository.findByIdAndOwnerId(projectId, ownerId).map(Project::getName).orElse(null);
	}
}
