# 前后端分离脚手架设计文档

日期：2026-10-17
状态：已实现并通过验证

## 1. 目标与范围

在 `AI_assistant` 目录下建立一套可直接开发的前后端分离工程：前端 React 单页应用，后端 Spring Boot 单体服务，数据库 H2。

范围内：工程骨架、目录与分层约定、一个打通全链路的示例模块、开发期联调方式、一键启动。

范围外（本次刻意不做）：用户认证、前端路由与全局状态管理、容器化、CI/CD、接口文档、正式数据库迁移工具。这些在需要时按第 9 节的位置接入。

## 2. 技术选型

| 层 | 选型 | 选定理由 |
| --- | --- | --- |
| 前端框架 | React 19.3 + Vite 8.3 | 用户指定 React；Vite 是纯 SPA 的标准构建工具，不需要 Next.js 那样的自带后端，与前后端分离一致 |
| 前端语言 | TypeScript 6.0 | 类型能在编译期挡住前后端字段对不上的问题 |
| 样式 | Tailwind CSS 4.3 | 无运行时开销，不用额外维护样式文件与类名映射 |
| 后端 | Spring Boot 4.1.1 | 当前最新稳定版；官方 parent `java.version` 即为 17，与本机 JDK 17 匹配 |
| 构建 | Maven + Wrapper | 本机未装 Gradle；Wrapper 让协作者不必预装 Maven |
| 数据库 | H2 2.4 文件模式 | 用户指定 H2；文件模式重启不丢数据，便于本地连续开发 |
| JSON | Jackson 3.1（随 Spring Boot 4.1.1 带入） | 注意包名已从 `com.fasterxml.jackson` 迁到 `tools.jackson`，见第 7 节 |
| 包管理 | pnpm workspace | 根目录一次安装即可覆盖前后端依赖 |

## 3. 目录结构与职责

```
AI_assistant/
├── package.json            # 仅承载根级脚本（一键启动、构建、测试）
├── pnpm-workspace.yaml     # 声明 frontend 为 workspace 成员
├── docs/                   # 本设计文档
├── backend/
│   ├── mvnw / mvnw.cmd     # Maven Wrapper
│   ├── data/               # H2 数据文件目录（已加入 .gitignore）
│   └── src/
│       ├── main/java/com/aiassistant/
│       │   ├── AiAssistantBackendApplication.java   # 启动类
│       │   ├── common/     # 跨模块共用：ApiError、ResourceNotFoundException、GlobalExceptionHandler
│       │   └── task/       # 示例业务模块
│       │       └── dto/    # TaskRequest / TaskResponse
│       ├── main/resources/application.yml
│       └── test/java/com/aiassistant/               # 上下文加载冒烟测试
└── frontend/
    └── src/
        ├── api/            # client.ts（fetch 封装）、tasks.ts（接口定义）
        ├── components/     # TaskForm、TaskList
        ├── types/          # 与后端 DTO 对应的类型
        ├── App.tsx         # 页面装配与状态
        └── main.tsx        # 入口
```

## 4. 后端设计

### 4.1 分包规则

按**功能**分包，不按技术层分包。`task` 包内同时包含实体、仓储、服务、接口，`dto` 作为子包。理由：一个业务改动通常只落在一个包内，按功能分包能让改动收敛，也避免出现 `controller`/`service` 三个大包各几十个类的情况。

`common` 包只放真正跨模块的东西，目前是统一错误体与全局异常处理。不要把只有一个模块用到的东西放进来。

### 4.2 分层与数据流

```
HTTP -> TaskController -> TaskService -> TaskRepository -> H2
                              |
                           Task 实体 -> TaskResponse (record) -> JSON
```

- Controller 只做协议转换与校验触发，不写业务判断。
- Service 承担事务边界（`@Transactional`）与实体到 DTO 的转换，不存在实体时抛 `ResourceNotFoundException`。
- 实体不直接出参，统一经 `TaskResponse`。这样给实体加内部字段不会意外泄露给前端。
- 入参用 `TaskRequest` 承载 Bean Validation 注解，Controller 上用 `@Valid` 触发。

### 4.3 错误契约

所有错误响应统一为 `ApiError`：

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "请求参数校验失败",
  "fieldErrors": { "title": "标题不能为空" },
  "timestamp": "..."
}
```

`fieldErrors` 为空时不输出，前端只需处理一种结构。已覆盖：404 资源不存在、400 字段校验失败、400 请求体不可解析（含枚举取值非法）。

### 4.4 配置要点

- `spring.datasource.url=jdbc:h2:file:./data/aiassistant`，相对路径基于后端工作目录，即 `backend/data/`。H2 会自动创建该目录。
- `spring.jpa.hibernate.ddl-auto=update`：脚手架阶段让表结构跟实体走，方便起步；多人协作后应换成 Flyway/Liquibase，否则表结构变更无版本记录。
- `spring.jpa.open-in-view=false`：关掉 OSIV，避免实体在视图层被意外懒加载。
- `spring.h2.console.enabled=true`：开发期可直接查表。

## 5. 前端设计

### 5.1 目录职责

- `api/client.ts`：唯一的 `fetch` 出入口。统一拼 header、把非 2xx 转成 `ApiError`、处理 204 无响应体、把「连不上后端」识别为 `status=0` 与业务错误区分。
- `api/tasks.ts`：按资源列出接口方法，不放任何 UI 逻辑。
- `types/task.ts`：与后端 DTO 对齐的类型，以及状态枚举与中文标签。
- `components/`：无状态的展示与表单组件，通过 props 拿到数据与回调。
- `App.tsx`：持有任务列表状态，负责调用 API 并把结果分发下去。目前只有这一层状态，够用。

### 5.2 类型定义的约束

`tsconfig.app.json` 开了 `erasableSyntaxOnly`，因此状态枚举不能写成 TS 的 `enum`（enum 会生成运行时代码）。这里用 `const` 数组 + 联合类型：

```ts
export const TASK_STATUSES = ['TODO', 'DOING', 'DONE'] as const
export type TaskStatus = (typeof TASK_STATUSES)[number]
```

`verbatimModuleSyntax` 要求类型导入必须写 `import type`，`noUnusedLocals` 要求不留未使用的变量。

### 5.3 样式

Tailwind 4 通过 `@tailwindcss/vite` 插件接入，`src/index.css` 里只有一行 `@import 'tailwindcss';`。不额外建样式文件，避免出现「类名改了一处、样式文件忘了改」的双份维护。

可访问性上：表单控件都有显式 `label`，列表里的状态下拉与删除按钮带 `aria-label`，错误横幅用 `role="alert"`。

## 6. 前后端契约与联调

开发期由 Vite 代理 `/api` 到 `http://localhost:8080`（见 `frontend/vite.config.ts`）。这样处理带来三个好处：

1. 前端代码里直接写 `/api/tasks`，不需要按环境区分的 baseURL；
2. 后端完全不需要 CORS 配置，少一处容易配错的跨域白名单；
3. 浏览器看到的是同源请求，不会有预检请求。

生产部署两条路：`frontend/dist` 交给 Nginx 并把 `/api` 反代到后端；或者把产物拷进 `backend/src/main/resources/static` 由后端一起提供。两种方式都保持同源，仍然不需要 CORS。

## 7. 实施中实际发现并修正的问题

这几处是照着文档或直觉写会踩到的坑，记录下来避免重复：

1. **Spring Initializr 生成的父版本号不可用**。请求 `bootVersion=4.1.1.RELEASE` 后，它把内部 id 原样写进了 `<version>`，而 Maven 中央仓库里只有 `4.1.1`（实测 `4.1.1.RELEASE` 三个 artifact 全部 404）。已改为 `4.1.1` 并验证构建通过。

2. **Spring Boot 4.x 的 starter 已重构**。不再有 `spring-boot-starter-web` 这种笼统入口，改用 `spring-boot-starter-webmvc`；测试 starter 按技术拆分（`-data-jpa-test`、`-webmvc-test`、`-validation-test`）；H2 控制台被拆成独立的 `spring-boot-h2console` 模块。手写 pom 时很容易沿用旧名。

3. **非法枚举值会绕过统一错误体**。原本只处理了 `MethodArgumentNotValidException` 与 `ResourceNotFoundException`，实际压测发现传 `status: "NOT_A_STATUS"` 时返回的是 Spring 默认结构 `{"timestamp","status","error","path"}`，前端要处理两种格式。已补 `HttpMessageNotReadableException` 处理。

4. **文件模式 H2 会独占数据库文件**。后端进程运行期间再执行 `mvnw test` 会因文件被锁而失败。本地验证时需要先停服务。

5. **create-vite 锁定的版本低于 npm 上的最新版**（模板给 TypeScript 6.0，而 npm 上已是 7.0.2），且默认用 `oxlint` 而非 ESLint。用官方生成器而不是手写配置，可以避免版本与配置不匹配。

6. **Spring Boot 4 默认用 Jackson 3，包名整体迁移**，这是本次踩得最重的一处。`com.fasterxml.jackson.databind` 已不存在，实际是 `tools.jackson.databind`（artifactId 也从 `jackson-databind` 变为 `tools.jackson.core:jackson-databind`）；`JsonMappingException` 更名为 `DatabindException`，路径引用类 `Reference` 移到了 `tools.jackson.core.JacksonException` 下，取字段名的方法由 `getFieldName()` 变成 `getPropertyName()`。

   有个容易误判的地方：**注解包仍是 `com.fasterxml.jackson.annotation`**（依赖树里是 `com.fasterxml.jackson.core:jackson-annotations:2.21`），所以 `@JsonInclude` 这类注解不用改，会让人以为「Jackson 没变」。本次是靠编译报错才发现的。

   接口里出现原始异常文案也有问题：Jackson 默认信息含包名、类名与解析偏移量（形如 ``Cannot deserialize value of type `com.aiassistant.task.TaskStatus` ... byte offset: #10``），而前端会把它直接显示给用户。现已改为只回字段名：`请求体解析失败：字段 status 的取值不在允许范围内`。

## 8. 已执行的验证

| 验证项 | 结果 |
| --- | --- |
| `mvnw test`（含 Spring 上下文加载） | 通过 |
| 后端启动并监听 8080 | 通过 |
| `GET /api/tasks` 空库返回 `[]` | 通过 |
| `POST /api/tasks` 返回 201，中文正确往返 | 通过 |
| 不传 `status` 时落到默认值 `TODO` | 通过 |
| 列表按 id 倒序 | 通过 |
| `PUT /api/tasks/{id}` 更新状态返回 200 | 通过 |
| `GET /api/tasks/99999` 返回 404 且为统一错误体 | 通过 |
| 空标题返回 400 且 `fieldErrors.title` 为中文提示 | 通过 |
| 非法枚举值返回 400，统一错误体且只回字段名（不泄露内部类名） | 通过 |
| `DELETE` 返回 204，再查返回 404 | 通过 |
| `/h2-console` 可访问（HTTP 200） | 通过 |
| `pnpm build`（含 `tsc` 类型检查） | 通过 |
| 浏览器内：列表加载、新建（含中文与状态字段）、改状态、删除 | 通过 |
| 浏览器内：标题留空提交，显示后端返回的中文校验提示且输入框标记 invalid | 通过 |
| 浏览器内：窄视口下任务行不塌陷（标题不被按钮挤压） | 通过 |
| 后端重启后数据仍在（H2 文件持久化生效） | 通过 |
| `pnpm dev` 一键同时拉起前后端（Windows 下 `mvnw` 能正确解析） | 通过 |

## 9. 后续扩展位置

| 要做的事 | 改哪里 |
| --- | --- |
| 新增业务模块 | 在 `com.aiassistant` 下新建功能包，照 `task` 包的四件套复制 |
| 换正式数据库 | 改 `application.yml` 的 datasource 与驱动依赖；分页与查询可继续用 Spring Data JPA |
| 表结构版本化 | 引入 Flyway，把 `ddl-auto` 改为 `validate`，用迁移脚本建表 |
| 接口文档 | 引入 springdoc-openapi，暴露 `/swagger-ui.html`；注解加在 Controller 上 |
| 前端多页面 | 引入 react-router；`api/` 与 `components/` 的结构可原样保留 |
| 前端共享状态 | 出现跨页面共享需求时再引入状态库，目前 `App.tsx` 的局部状态够用 |
| 统一鉴权 | 后端加 Spring Security，前端在 `api/client.ts` 统一附加凭证头 |
| 容器化与流水线 | 两端各写 Dockerfile，根目录加 compose；后端已有可执行 jar 与 Wrapper |
