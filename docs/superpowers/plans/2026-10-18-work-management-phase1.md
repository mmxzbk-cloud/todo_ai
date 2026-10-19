# 工作管理系统 · 阶段一实施计划（主线）

日期：2026-10-18
状态：待执行
依据：`docs/2026-10-18-work-management-design.md`（表结构已由 `V1__init_schema.sql` 备好并通过 27 项校验）

**Goal**：用真实账号登录，把一件事从录入、计时、改状态走到完成，并在详情里看到完整耗时与变更历史。

**Architecture**：Spring Boot 单体按功能分包（`auth` / `task` / `project` / `tag` / `timelog` / `audit`），出参一律走 `dto` record；数据隔离靠 Service 层每条查询带 `owner_id`。前端 React SPA：URL 承载筛选条件、TanStack Query 承载服务端数据、组件 state 只放表单草稿。

**Tech Stack**：Spring Boot 4.1.1 / JDK 17 / Spring Security + 自签 HS256 JWT（不引第三方 JWT 库）/ Flyway + H2 2.4 文件模式 / React 19 + Vite 8 + TS 6 + Tailwind 4 + react-router 7 + TanStack Query 5。

## Global Constraints

- 接口前缀 `/api/v1`；错误体统一为 `ApiError`，必须带稳定 `code`。
- 主键 `BIGINT`，时刻用 `Instant`（H2 `TIMESTAMP WITH TIME ZONE`），只有日期语义用 `LocalDate`（`DATE`）。
- 所有查询带 `deleted_at IS NULL`；所有按 id 取数带 `owner_id` 条件，不做「取出来再比对」。
- 枚举存字符串，取值必须与 `V1__init_schema.sql` 的 CHECK 约束完全一致。
- 实体不出接口，一律经 `dto` record；Controller 不写业务判断。
- 前端约束：`erasableSyntaxOnly`（禁 `enum`，用 const 数组 + 联合类型）、`verbatimModuleSyntax`（类型导入必须 `import type`）、`noUnusedLocals`。
- 不提交、不推送、不部署。

## 0. 本轮已完成的准备（不计入开发预算）

| 项 | 状态 |
| --- | --- |
| `backend/pom.xml` 加 `spring-boot-starter-security` / `-flyway` / `-actuator` / `-security-test` | 已改，**坐标未实测**（见风险 R1） |
| `frontend` 加 `react-router-dom@^7`、`@tanstack/react-query@^5` 并安装 | 已完成 |
| `backend/data/` 为空 | 已确认，Flyway 首次执行即建 13 张表，无需删库 |

## 1. 冻结契约

派发子 agent 前先把跨任务的接口钉死，避免各写一套。

### 1.1 错误契约（T1 实现，全员依赖）

```java
public enum ErrorCode {
    VALIDATION_FAILED, BAD_REQUEST, NOT_FOUND, UNAUTHORIZED, FORBIDDEN, INTERNAL_ERROR,
    USERNAME_TAKEN, EMAIL_TAKEN, INVALID_CREDENTIALS, LOGIN_RATE_LIMITED,
    TASK_NOT_FOUND, PROJECT_NOT_FOUND, TAG_NOT_FOUND,
    VERSION_CONFLICT, TIMER_ALREADY_RUNNING, TIMER_NOT_RUNNING,
    PROJECT_NAME_TAKEN, TAG_NAME_TAKEN, PROJECT_HAS_TASKS
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(int status, String code, String error, String message,
                       Map<String, String> fieldErrors, Instant timestamp) {
    public static ApiError of(int status, ErrorCode code, String message);
    public static ApiError withFieldErrors(int status, ErrorCode code, String message, Map<String, String> fieldErrors);
}
```

异常 → 状态码映射（`GlobalExceptionHandler`）：`ResourceNotFoundException`→404（带自身 code）、`BadRequestException`→400、`ConflictException`→409、`RateLimitedException`→429、`ForbiddenException`→403、`UnauthorizedException`→401、`MethodArgumentNotValidException`→400 `VALIDATION_FAILED`、`HttpMessageNotReadableException`→400 `BAD_REQUEST`、`ObjectOptimisticLockingFailureException`→409 `VERSION_CONFLICT`、其余→500 `INTERNAL_ERROR`（不泄露堆栈，按 requestId 记日志）。

`PageResponse<T>`：`record PageResponse<T>(List<T> items, long total, int page, int size)`。

### 1.2 当前用户（T1 实现）

```java
public record CurrentUser(Long id, String username, String displayName, ZoneId zoneId) {}

@Component
public class CurrentUserProvider {
    public CurrentUser require();   // 从 SecurityContextHolder 取；缺失抛 UnauthorizedException(UNAUTHORIZED)
}
```

`JwtAuthFilter` 校验通过后按 `sub` 从 `users` 表读一行填 `displayName`/`timezone`（顺带拦掉 DISABLED 账号），principal 即 `CurrentUser`。

### 1.3 鉴权（T1）

| 方法 | 路径 | 入参 | 出参 |
| --- | --- | --- | --- |
| POST | `/api/v1/auth/register` | `{username, password, displayName, email?}` | 201 `AuthUserResponse` |
| POST | `/api/v1/auth/login` | `{username, password}` | 200 `AuthSessionResponse` |
| POST | `/api/v1/auth/refresh` | `{refreshToken}` | 200 `AuthSessionResponse`（轮换） |
| POST | `/api/v1/auth/logout` | `{refreshToken}` | 204 |
| GET | `/api/v1/auth/me` | — | 200 `AuthUserResponse` |

```java
public record AuthUserResponse(Long id, String username, String email, String displayName, String timezone) {}
public record AuthSessionResponse(String accessToken, String refreshToken, long expiresIn, AuthUserResponse user) {}
```

- access token：HS256，2 小时，claims 只有 `sub`/`username`/`iat`/`exp`。
- refresh token：32 字节随机 → base64url 返回前端，库里只存 SHA-256 十六进制，30 天，refresh 时旧记录置 `revoked_at` 并新发一条。
- 密码 bcrypt(cost 10)；登录失败 5 次锁 15 分钟（内存计数，key=`username|clientIp`）→ 429 `LOGIN_RATE_LIMITED`。
- 注册开关 `app.auth.registration-enabled`（默认 `true`），关闭时注册返回 403 `FORBIDDEN`。
- JWT 自签实现不读 token 头里的 `alg`，恒定用 HMAC-SHA256 验签，签名比对用 `MessageDigest.isEqual`。

### 1.4 任务主线（T2 / T3）

| 方法 | 路径 | 归属 | 出参 |
| --- | --- | --- | --- |
| GET | `/api/v1/tasks` | T2 | `PageResponse<TaskResponse>`，参数 `status/projectId/dueFrom/dueTo/q/page/size` |
| GET | `/api/v1/tasks/today` | T2 | `TaskResponse[]` |
| GET | `/api/v1/tasks/board` | T2 | `BoardColumnResponse[]`（固定 4 列 TODO/DOING/BLOCKED/DONE） |
| GET | `/api/v1/tasks/{id}` | T2 | `TaskDetailResponse` |
| POST | `/api/v1/tasks` | T2 | 201 `TaskResponse` |
| PUT | `/api/v1/tasks/{id}` | T2 | 200 `TaskResponse`（带 `version` 做乐观锁） |
| PATCH | `/api/v1/tasks/{id}/status` | T2 | 200 `TaskResponse` |
| PUT | `/api/v1/tasks/{id}/tags` | T2 | 200 `TaskDetailResponse`（`{tagIds:[]}`） |
| DELETE | `/api/v1/tasks/{id}` | T2 | 204（软删） |
| GET | `/api/v1/projects` | T2 | `ProjectResponse[]`（`includeArchived` 默认 false） |
| POST/PUT | `/api/v1/projects`、`/api/v1/projects/{id}` | T2 | 201 / 200 |
| POST | `/api/v1/projects/{id}/archive`、`/unarchive` | T2 | 200 |
| GET/POST/PUT/DELETE | `/api/v1/tags`、`/api/v1/tags/{id}` | T2 | 列表 / 201 / 200 / 204（软删并改写 name） |
| GET | `/api/v1/timers/running` | T3 | 200 `RunningTimerResponse` 或 204 |
| POST | `/api/v1/tasks/{id}/timers` | T3 | 201 `TimeLogResponse`；已有计时 → 409 `TIMER_ALREADY_RUNNING` |
| POST | `/api/v1/tasks/{id}/timers/{logId}/stop` | T3 | 200 `TimerStopResponse`（`markDone` 查询参数，默认 false） |
| GET/POST | `/api/v1/tasks/{id}/time-logs` | T3 | `TimeLogResponse[]` / 201 |
| GET | `/api/v1/tasks/{id}/activities` | T3 | `ActivityResponse[]` |

```java
public record TaskResponse(Long id, String title, String description, Long projectId, String projectName,
        Long parentTaskId, String source, Importance importance, Urgency urgency, Priority priority,
        TaskStatus status, LocalDate dueOn, Integer estimatedMinutes, int actualMinutes,
        Boolean aiReplaceable, String aiReplacementNote, String nextAction, String notes,
        Instant completedAt, int sortOrder, int version, Instant createdAt, Instant updatedAt) {}

public record TaskDetailResponse(TaskResponse task, List<TagResponse> tags, List<TaskResponse> children,
        List<TimeLogResponse> timeLogs, List<ActivityResponse> activities) {}

public record TimeLogResponse(Long id, Long taskId, Instant startedAt, Instant endedAt, int minutes,
        String source, String note) {}
// 放在 audit/dto/ActivityResponse.java（由 T2 拥有）：变更历史的读模型跟审计实体同包
public record ActivityResponse(Long id, String action, String field, String oldValue, String newValue,
        Long actorId, Instant createdAt) {}
public record RunningTimerResponse(TimeLogResponse log, TaskResponse task) {}
public record TimerStopResponse(TimeLogResponse log, TaskResponse task) {}
public record BoardColumnResponse(TaskStatus status, List<TaskResponse> tasks) {}
```

**状态与优先级**：`TaskStatus{TODO,DOING,BLOCKED,DONE,CANCELLED}`、`Importance{HIGH,MEDIUM,LOW}`、`Urgency` 同、`Priority{P0,P1,P2,P3}`。

优先级推导（`HIGH=0, MEDIUM=1, LOW=2`，`Priority.derive(importance, urgency)`）：`min(sum,3)` → 0=P0、1=P1、2=P2、3=P3。写入规则：新建时请求带 `priority` 用它，否则推导；更新时请求带 `priority` 用它，未带且 `importance`/`urgency` 变了才重推，否则保持原值（这样手工覆盖不会被无意抹掉，无需加列）。

**排序**（列表/今日/看板一致）：`CASE WHEN dueOn IS NULL THEN 1 ELSE 0 END, dueOn ASC, priority ASC, id DESC`。

**今日视图**：未删 + `dueOn <= 今天`（今天按 `users.timezone` 算）+ 状态不在 `(DONE, CANCELLED)`。

**软删**：`deletedAt = now`；标签软删先把 `name` 改写为 `原名 (已删除 #id)` 再置 `deletedAt`（绕开 H2 无部分唯一索引）。

**实体与仓储冻结**（T2 建）：`Task` 字段严格对应 `V1` 的 `tasks` 列，`@Version private Integer version`；外键用 `Long` 存 id，不做 JPA 关联（避免懒加载与跨包耦合）。仓储至少包含：

```java
Optional<Task> findByIdAndOwnerIdAndDeletedAtIsNull(Long id, Long ownerId);
List<Task> findByOwnerIdAndDeletedAtIsNullAndParentTaskIdOrderByIdDesc(Long ownerId, Long parentTaskId);

@Query("""
    SELECT t FROM Task t
    WHERE t.ownerId = :ownerId AND t.deletedAt IS NULL
      AND (:hasStatus = FALSE OR t.status = :status)
      AND (:hasProject = FALSE OR t.projectId = :projectId)
      AND (:hasDueFrom = FALSE OR t.dueOn >= :dueFrom)
      AND (:hasDueTo = FALSE OR t.dueOn <= :dueTo)
      AND (:hasQ = FALSE OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')))
    """)
Page<Task> search(Long ownerId, boolean hasStatus, TaskStatus status, boolean hasProject, Long projectId,
                  boolean hasDueFrom, LocalDate dueFrom, boolean hasDueTo, LocalDate dueTo,
                  boolean hasQ, String q, Pageable pageable);
```

（用 `hasXxx` 布尔开关而不是 `:param IS NULL`：后者在 Hibernate 上会撞「无法推断参数类型」。）

**审计**（T2 建，T2 与 T3 都调用）：

```java
public enum TaskAction { CREATED, UPDATED, STATUS_CHANGED, DELETED, TIMER_STARTED, TIMER_STOPPED, TIME_LOGGED }

@Component
public class TaskActivityRecorder {
    public void record(Long taskId, Long actorId, TaskAction action, String field, String oldValue, String newValue);
}
```

旧值/新值截断到 500 字符。用显式调用而不是 AOP：本轮只有 2 个写入口（`TaskService`、`TimerService`），显式调用少一层织入、更好排查（对设计文档 1.2 节「AOP 织入」的有意偏离，理由记此）。

**计时事务**（T3）：

- 开始：`findFirstByOwnerIdAndEndedAtIsNull(ownerId)` 有记录 → 409 `TIMER_ALREADY_RUNNING`（消息里带那条记录的任务 id）；无则插入 `started_at=now, ended_at=NULL, minutes=0, source=TIMER`；若任务状态是 `TODO` 顺带置 `DOING`。
- 结束（单事务）：补 `ended_at=now`、`minutes = max(1, round(秒/60))`、`tasks.actual_minutes += minutes`、`markDone` 为真时置 `DONE` + `completed_at`，写两条审计（`TIMER_STOPPED`，必要再写 `STATUS_CHANGED`）。
- 手工补录：`source=MANUAL`，同样累加 `actual_minutes` 并写 `TIME_LOGGED`。
- 兜底截断：`@Scheduled` 每 10 分钟扫 `ended_at IS NULL AND started_at < now-12h`，按 `started_at+12h` 截断、`minutes=720`、累加、写审计。
- 对账：每日 03:30 用 `SUM(minutes)` 与 `tasks.actual_minutes` 比对，不一致按明细修正并 `log.warn`。

### 1.4.1 跨包 import 约定（并行 agent 之间唯一的耦合面）

允许的单向引用，其它方向一律不许出现：

| 方向 | 引用内容 | 用途 |
| --- | --- | --- |
| T2 → T3 | `timelog.dto.TimeLogResponse`、`timelog.TaskTimeLogRepository` | 组装任务详情里的耗时明细 |
| T3 → T2 | `task.Task`、`task.TaskRepository`、`audit.TaskActivityRecorder`、`audit.TaskAction`、`audit.TaskActivityLogRepository`、`audit.dto.ActivityResponse` | 计时改任务、写审计、返回变更历史 |
| T2/T3 → T1 | `auth.CurrentUserProvider`、`auth.CurrentUser`、`common.ErrorCode`、`common.*Exception`、`common.PageResponse` | 当前用户、错误码、分页 |

`GET /api/v1/tasks/{id}/activities` 归 T3（计时/轨迹类端点同族），`GET /api/v1/tasks/{id}` 详情组装归 T2。

### 1.5 前端契约（T4 / T5 / T6 共同依赖）

`src/api/client.ts`（T4）：

```ts
export interface ApiErrorBody { status: number; code?: string; error: string; message: string; fieldErrors?: Record<string, string> }
export class ApiError extends Error {
  readonly status: number; readonly code: string;
  readonly fieldErrors: Record<string, string>; readonly requestId?: string;
}
export function request<T>(path: string, init?: RequestInit): Promise<T>
export function setTokens(tokens: { accessToken: string; refreshToken: string } | null): void
export function getAccessToken(): string | null
export function getRefreshToken(): string | null
export const AUTH_LOGOUT_EVENT = 'auth:logout'
```

行为：令牌放 `localStorage`（`aiassistant.accessToken` / `aiassistant.refreshToken`）；有令牌就附 `Authorization: Bearer`；每请求生成 `crypto.randomUUID()` 作 `X-Request-Id`；返回 401 且有 refresh token 时做**单飞**刷新（模块级 `refreshPromise`）并重放原请求一次，刷新失败则清令牌 + `window.dispatchEvent(new Event(AUTH_LOGOUT_EVENT))`；`status=0` 仍表示连不上后端。

`src/api/queryKeys.ts`（T5，唯一坐标）：

```ts
export const queryKeys = {
  tasks: (p: unknown) => ['tasks', 'list', p] as const,
  today: ['tasks', 'today'] as const,
  board: ['tasks', 'board'] as const,
  task: (id: number) => ['tasks', 'detail', id] as const,
  runningTimer: ['timers', 'running'] as const,
  projects: ['projects'] as const,
  tags: ['tags'] as const,
}
```

`src/types/task.ts`（T5）：`TASK_STATUSES=['TODO','DOING','BLOCKED','DONE','CANCELLED']`、`BOARD_STATUSES=['TODO','DOING','BLOCKED','DONE']`、`TASK_STATUS_LABELS`（待办/进行中/阻塞/已完成/已取消）、`IMPORTANCE_LEVELS`、`URGENCY_LEVELS`、`PRIORITIES`，以及 `TaskSummary`（字段与后端 `TaskResponse` 一一对应，`dueOn: string | null` 为 `YYYY-MM-DD`）、`TaskDetail`（**嵌套结构**，与后端 `TaskDetailResponse` 一致：`interface TaskDetail { task: TaskSummary; tags: Tag[]; children: TaskSummary[]; timeLogs: TimeLog[]; activities: Activity[] }`——不是 `extends TaskSummary`，页面读标题要走 `detail.task.title`）。、`TimeLog`、`Activity`、`RunningTimer`、`TimerStopResult`、`TaskInput`、`TaskUpdateInput`（含 `version?: number`）、`TaskListParams`、`PageResponse<T>`、`BoardColumn`。`Tag` 从 `./tag.ts` 导入（`import type { Tag } from './tag.ts'`）。

`src/types/tag.ts`（T6）：`export interface Tag { id: number; name: string; color: string | null }`。`src/types/project.ts`（T6）：`export interface Project { id: number; name: string; color: string | null; sortOrder: number; archivedAt: string | null }`。

`src/api/tasks.ts`（T5，函数名冻结，T6 只 import 不再新增）：

```ts
export const tasksApi = {
  list: (params: TaskListParams) => ...            // GET /api/v1/tasks
  today: () => ...                                 // GET /api/v1/tasks/today
  board: () => ...                                 // GET /api/v1/tasks/board
  get: (id: number) => ...                         // GET /api/v1/tasks/{id}
  create: (input: TaskInput) => ...
  update: (id: number, input: TaskUpdateInput) => ...
  changeStatus: (id: number, status: TaskStatus) => ...
  assignTags: (id: number, tagIds: number[]) => ...
  remove: (id: number) => ...
  runningTimer: () => ...                          // 204 时返回 null
  startTimer: (id: number) => ...
  stopTimer: (id: number, logId: number, markDone: boolean) => ...
  addTimeLog: (id: number, input: { startedAt: string; endedAt: string; note?: string }) => ...
}
```

`src/api/auth.ts`（T4）：`register / login / refresh / logout / me`。`src/api/projects.ts`、`src/api/tags.ts`（T6）：`projectsApi.list(params?)/(create/update/archive/unarchive)`、`tagsApi.list/create/update/remove`。

**路由与页面**（T4 布路由，T5/T6 填页面）：`/login`（未登录）、`/today`、`/board`、`/tasks/:id`、`/settings`（后四个在 `AppLayout` 下，未登录重定向 `/login`）；`/` 与未匹配 → `/today`。

**交互约定**：改状态勾完成走乐观更新，失败回滚并提示；计时器每秒只刷**显示**（由 `startedAt` 本地推算），落库只在开始/结束两次请求；计时中的任务用 `aria-live="polite"` 播报；看板不做拖拽，改状态一律用行内下拉（键盘可达）。

## 2. 任务与派发

三波，每波 3 个并行子 agent，文件所有权互不重叠。

| 波 | 任务 | 交付物 |
| --- | --- | --- |
| A | T1 基建+鉴权 / T2 任务域+项目标签+审计 / T3 计时+定时任务 | 后端可编译 |
| B | T4 前端外壳+登录 / T5 今日+看板 / T6 详情+计时+设置 | 前端可构建 |
| C | T8 部署文档 | 部署文档 |

### 预算调整（2026-10-18 晚，按你的决定）

**本轮跳过单元测试**：T1/T2/T3 的 Files 里 `src/test/` 下的测试文件不写，T1–T6 的「先写失败测试→跑→实现→跑」步骤全部跳过，只写实现代码。第 3 节里 3.1 的 `mvnw test` 与 3.2 的浏览器全流程降级为：集成时只做「必须能编译 + 能启动 + 主线一次跑通」的最小验证。单元测试与全流程验证列为后续补做项，不当作已完成。

### Task T1：基建与鉴权（owner: 后端）

**Files**：改 `backend/src/main/resources/application.yml`；新增 `common/ErrorCode.java`、`common/BadRequestException.java`、`common/ConflictException.java`、`common/RateLimitedException.java`、`common/ForbiddenException.java`、`common/UnauthorizedException.java`、`common/PageResponse.java`；改 `common/ApiError.java`、`common/ResourceNotFoundException.java`、`common/GlobalExceptionHandler.java`；新增 `config/SecurityConfig.java`、`config/JwtAuthFilter.java`、`config/RequestIdFilter.java`、`config/SchedulingConfig.java`；新增 `auth/User.java`、`auth/AuthRefreshToken.java`、`auth/UserRepository.java`、`auth/AuthRefreshTokenRepository.java`、`auth/JwtService.java`、`auth/LoginRateLimiter.java`、`auth/CurrentUserProvider.java`、`auth/CurrentUser.java`、`auth/AuthService.java`、`auth/AuthController.java`、`auth/dto/*`；测试 `auth/JwtServiceTest.java`、`auth/AuthServiceTest.java`。

**Interfaces**：见 1.1–1.3。T1 是唯一改动 `application.yml` 与 `common/` 的任务。

**Steps**：
- [ ] `application.yml`：`ddl-auto: validate`、`spring.flyway.enabled: true`（`locations` 默认即 `classpath:db/migration`）、`app.auth.jwt-secret`（默认开发值，注明生产必须用 `APP_AUTH_JWT_SECRET` 覆盖）、`app.auth.access-token-ttl: PT2H`、`app.auth.refresh-token-ttl: P30D`、`app.auth.registration-enabled: true`、logging pattern 带 `%X{requestId}`、actuator 只暴露 `health,info,metrics`。
- [ ] `JwtServiceTest`：签发后可解出 userId/username；改一个字符的签名 → 空；过期 token → 空；`alg: none` 的伪造 token → 空。
- [ ] `AuthServiceTest`：重复用户名 → `ConflictException(USERNAME_TAKEN)`；密码错误 → `UnauthorizedException(INVALID_CREDENTIALS)`；refresh 轮换后旧 refresh 再用 → 401；5 次失败 → 第 6 次 `RateLimitedException`。
- [ ] 跑 `mvnw test` 上述两个测试类通过。

**Verify**：`mvnw -DskipTests compile` 通过；`mvnw test -Dtest=JwtServiceTest,AuthServiceTest` 通过。

### Task T2：任务域 + 项目/标签 + 审计（owner: 后端）

**Files**：改 `task/Task.java`、`task/TaskStatus.java`、`task/TaskController.java`、`task/TaskRepository.java`、`task/TaskService.java`、`task/dto/TaskRequest.java`、`task/dto/TaskResponse.java`；新增 `task/Importance.java`、`task/Urgency.java`、`task/Priority.java`、`task/TaskMapper.java`、`task/dto/TaskDetailResponse.java`、`task/dto/TaskStatusRequest.java`、`task/dto/TagIdsRequest.java`、`task/TaskTag.java`、`task/TaskTagId.java`、`task/TaskTagRepository.java`；新增 `project/*`（`Project`、`ProjectRepository`、`ProjectService`、`ProjectController`、`dto/ProjectRequest`、`dto/ProjectResponse`）；新增 `tag/*`（`Tag`、`TagRepository`、`TagService`、`TagController`、`dto/TagRequest`、`dto/TagResponse`）；新增 `audit/TaskActivityLog.java`、`audit/TaskAction.java`、`audit/TaskActivityLogRepository.java`、`audit/TaskActivityRecorder.java`、`audit/dto/ActivityResponse.java`；测试 `task/PriorityTest.java`、`task/TaskServiceTest.java`。

**Interfaces**：见 1.1、1.2、1.4。依赖 T1 的 `CurrentUserProvider`、`ErrorCode`、`ConflictException`、`ResourceNotFoundException`、`PageResponse`。

**Steps**：
- [ ] `PriorityTest`：9 组 importance×urgency 全覆盖，断言 `P0/P1/P1/P2/P2/P2/P3/P3/P3`。
- [ ] `TaskServiceTest`（Mockito）：`update` 传过期 `version` → `ConflictException(VERSION_CONFLICT)`；`findById` 用他人 id → `ResourceNotFoundException(TASK_NOT_FOUND)`；软删后再查 → 404；软删后 `deletedAt` 非空且 `getById` 用 owner 条件调用仓储（验越权防护）。
- [ ] 先让两个测试失败，再实现到通过（TDD）。
- [ ] 路径前缀 `/api/v1`；所有 Controller 方法用 `currentUserProvider.require().id()` 作为 owner，不接受请求体里的 ownerId。

**Verify**：`mvnw test -Dtest=PriorityTest,TaskServiceTest` 通过。

### Task T3：计时 + 定时任务（owner: 后端）

**Files**：新增 `timelog/TaskTimeLog.java`、`timelog/TaskTimeLogRepository.java`、`timelog/TimerService.java`、`timelog/TimerController.java`、`timelog/dto/TimeLogResponse.java`、`timelog/dto/RunningTimerResponse.java`、`timelog/dto/TimerStopResponse.java`、`timelog/dto/ManualTimeLogRequest.java`；新增 `scheduler/TimerSweeper.java`、`scheduler/ReconciliationJob.java`；测试 `timelog/TimerServiceTest.java`。

**Interfaces**：见 1.4；依赖 T2 的 `Task`（用 `addActualMinutes(int)` / `markDone(Instant)`，由 T2 提供）、`TaskRepository`、`TaskActivityRecorder`、`TaskAction`。

**Steps**：
- [ ] `TimerServiceTest`：已有未结束记录时开始 → `ConflictException(TIMER_ALREADY_RUNNING)`；结束按 `max(1, round(秒/60))` 算分钟并累加 `actualMinutes`、写 `TIMER_STOPPED`；`markDone=true` 时状态 `DONE` 且 `completedAt` 非空；结束他人记录 → 404。
- [ ] `TimerController` 用 `@RequestMapping("/api/v1/tasks")` 与 T2 的 `TaskController` 同基路径、不同完整路径（Spring 允许）。
- [ ] 定时任务只做日志与兜底，不依赖外部调度器。

**Verify**：`mvnw test -Dtest=TimerServiceTest` 通过。

### Task T4：前端外壳 + 登录（owner: 前端）

**Files**：改 `frontend/src/main.tsx`、`frontend/src/App.tsx`、`frontend/src/api/client.ts`；新增 `frontend/src/api/auth.ts`、`frontend/src/types/auth.ts`、`frontend/src/hooks/useAuth.tsx`、`frontend/src/components/RequireAuth.tsx`、`frontend/src/components/AppLayout.tsx`、`frontend/src/pages/LoginPage.tsx`。

**Interfaces**：见 1.5。T4 是唯一写 `client.ts` 与路由的任务。

**Steps**：
- [ ] `main.tsx`：`QueryClientProvider` + `AuthProvider` + `BrowserRouter`。
- [ ] `App.tsx` 只留路由表；`RequireAuth` 未登录时 `<Navigate to="/login" state={{ from }} replace />`，登录后回跳。
- [ ] `LoginPage`：登录/注册同页切换；`ApiError.code === 'INVALID_CREDENTIALS'` 显示「用户名或密码不正确」，429 显示锁定提示；成功后 `navigate(from ?? '/today')`。
- [ ] `AppLayout`：导航（今日/看板/设置）+ 当前用户名 + 登出；`useAuth` 监听 `AUTH_LOGOUT_EVENT`。

**Verify**：`pnpm --dir frontend exec tsc -b --noEmit` 无错（构建留到集成时统一跑）。

### Task T5：今日视图 + 看板（owner: 前端）

**Files**：改 `frontend/src/types/task.ts`、新增 `frontend/src/api/tasks.ts`、`frontend/src/api/queryKeys.ts`、`frontend/src/hooks/useTasks.ts`、`frontend/src/components/TaskCard.tsx`、`frontend/src/components/StatusSelect.tsx`、`frontend/src/components/QuickTaskForm.tsx`、`frontend/src/pages/TodayPage.tsx`、`frontend/src/pages/BoardPage.tsx`。

**Interfaces**：见 1.5；`TaskDetail` 里 `import type { Tag } from './tag.ts'`（文件由 T6 提供）。

**Steps**：
- [ ] 今日视图：`useQuery(queryKeys.today)`；行内显示标题、项目、截止日、优先级徽标、状态下拉、计时按钮、进入详情链接；顶部 `QuickTaskForm` 只填标题（+ 可选截止日/重要性/紧急性）。
- [ ] 看板：`useQuery(queryKeys.board)`；固定 4 列，空列提示「暂无」；`StatusSelect` 改状态走 `useMutation` + 乐观更新（`onMutate` 改缓存、`onError` 回滚并提示、`onSettled` 失效 `today` 与 `board`）。
- [ ] 计时按钮调 `tasksApi.startTimer`，409 `TIMER_ALREADY_RUNNING` 时提示「已有计时在进行中」并失效 `runningTimer`。

**Verify**：`tsc -b` 无错。

### Task T6：详情 + 计时面板 + 设置（owner: 前端）

**Files**：新增 `frontend/src/types/tag.ts`、`frontend/src/types/project.ts`、`frontend/src/api/projects.ts`、`frontend/src/api/tags.ts`、`frontend/src/pages/TaskDetailPage.tsx`、`frontend/src/pages/SettingsPage.tsx`、`frontend/src/components/TimerPanel.tsx`、`frontend/src/components/TimeLogList.tsx`、`frontend/src/components/ActivityTimeline.tsx`、`frontend/src/components/TagPicker.tsx`。

**Interfaces**：只 import T4 的 `client`/`useAuth` 与 T5 的 `tasksApi`/`queryKeys`/类型，不修改它们的文件。

**Steps**：
- [ ] 详情：展示全部任务字段 + 编辑表单（标题、备注、下一步、截止日、预计耗时、重要性/紧急性、项目、AI 可替代三态）；保存带 `version`，409 `VERSION_CONFLICT` 提示「已被其他页面修改，请刷新」。
- [ ] `TimerPanel`：从 `tasksApi.runningTimer()` 取状态；计时中显示 `mm:ss`（每秒本地 tick，`aria-live="polite"`）；结束按钮带「同时标记完成」选项，用服务端返回结果覆盖本地。
- [ ] `TimeLogList`（起止时间 + 分钟 + 来源）、`ActivityTimeline`（动作 + 字段 + 旧值→新值 + 时间）。
- [ ] 设置页：项目列表/新建/归档，标签列表/新建/删除；删除标签成功后失效 `tags`。

**Verify**：`tsc -b` 无错。

### Task T7：冒烟验证（我执行，不派发）

按 3.1 的清单跑，结果写进 `docs/2026-10-18-phase1-verification.md`。

### Task T8：部署文档（owner: 任意）

`docs/2026-10-18-phase1-deploy.md`：构建命令、运行命令、环境变量表（`APP_AUTH_JWT_SECRET` 必填、`app.auth.registration-enabled`、H2 路径、端口）、H2 `SCRIPT TO` 备份与恢复演练步骤、Flyway 对 H2 无 `undo` 的回滚口径（停服→恢复备份→回退应用版本）、`/actuator/health` 探活、单实例与文件锁限制、发布窗口建议。

## 3. 验证与测试

### 3.1 后端（我执行）

1. `cd backend && .\mvnw.cmd test`（先停掉一切占用 `data/aiassistant.mv.db` 的进程）。
2. `.\mvnw.cmd spring-boot:run` 启动，确认 Flyway 建出 13 张表、`ddl-auto: validate` 不报错。
3. `curl` 冒烟：注册 → 登录取 token → `GET /api/v1/auth/me` → 建项目 → 建任务 → `PATCH .../status` → 开始计时 → 结束计时（`markDone=true`）→ `GET /api/v1/tasks/{id}` 断言 `actualMinutes > 0`、`status=DONE`、`timeLogs` 非空、`activities` 含 `CREATED/STATUS_CHANGED/TIMER_STARTED/TIMER_STOPPED`。
4. 越权：用第二个账号的 token 取第一个账号的任务 → 404 `TASK_NOT_FOUND`。
5. 并发与冲突：同一任务用旧 `version` 再 PUT → 409 `VERSION_CONFLICT`；重复开始计时 → 409 `TIMER_ALREADY_RUNNING`。
6. 无 token 访问 `/api/v1/tasks` → 401 且为统一错误体。

### 3.2 前端（我执行）

`pnpm --dir frontend build`（含 `tsc`）通过后，浏览器按 `webapp-testing` 走一遍：登录 → 今日视图建任务 → 开始计时 → 详情看 `mm:ss` 走动 → 结束并标记完成 → 详情出现耗时与变更历史 → 看板改状态 → 设置页建项目/标签 → 登出后访问 `/today` 被重定向到 `/login`。

### 3.3 不做

阶段二四张表的只读接口本轮**不做**（表已由 V1 建好，页面也在阶段二，现在没有消费方）；拖拽、附件、WebSocket、模型调用不做。

## 4. 风险与预案

| # | 风险 | 预案 |
| --- | --- | --- |
| R1 | `spring-boot-starter-flyway` 的 4.1.1 坐标未实测（本地 `.m2` 里查不到） | 集成第一步就编译验证；失败则改用 `org.flywaydb:flyway-core` 直接依赖 |
| R2 | `ddl-auto: validate` 与实体映射不一致（`Instant`/`Instant`↔`TIMESTAMP WITH TIME ZONE`、`CLOB`、`VARCHAR` 长度） | 报错列名逐个对齐；实在不齐则退回 `ddl-auto: none` 并在验证记录里写明 |
| R3 | 三个后端 agent 并发写同一 Maven 模块，无法各自编译 | 契约先冻结，各自只保证「按契约写」；编译与修错集中在我这做一次性集成，进 bug 修复预算 |
| R4 | H2 文件锁：测试与应用同时打开会失败 | 跑测试前先停应用；本次不并行跑两者 |
| R5 | 15 分钟开发预算偏紧：后端约 60 个文件、前端约 25 个文件 | 波内并行 + 契约冻结压掉沟通成本；超出预算时先保住 3.1 的第 3、4 条（主线 + 越权）通过的路径 |
| R6 | 乐观锁只在显式传 `version` 时拦截 | 前端详情页保存必带 `version`；后端 `@Version` 作为第二道防线 |

## 5. 回滚

未提交、未部署，回滚即还原工作区：`pom.xml`/`application.yml` 的依赖与配置改动、`backend/src/main/java` 新增包、`frontend/src` 新增文件、`package.json`/`pnpm-lock.yaml` 的两个依赖。数据库可整体删除 `backend/data/`，Flyway 下次启动重建 13 张表（当前库内只有脚手架验证数据）。
