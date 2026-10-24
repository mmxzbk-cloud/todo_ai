# AI Assistant

前后端分离的开发脚手架。前端是 React 单页应用，后端是 Spring Boot 单体服务，数据库用 H2（文件模式，重启不丢数据）。

脚手架里带了一个 `Task` 示例模块，从数据库一路打通到页面，用来证明整套链路是通的；换成真实业务时按同样的形状替换即可。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | React 19.3 · Vite 8.3 · TypeScript 6.0 · Tailwind CSS 4.3 |
| 后端 | Spring Boot 4.1.1 · Java 17 · Maven（自带 Wrapper） |
| 数据库 | H2 2.4（文件模式） |
| 包管理 | pnpm（workspace） |
| 一键启动 | concurrently |

## 目录结构

```
AI_assistant/
├── package.json            # 根目录脚本：pnpm dev 同时起前后端
├── pnpm-workspace.yaml     # 把 frontend 纳入 workspace
├── docs/                   # 设计文档
├── backend/                # Spring Boot 单体服务
│   ├── mvnw / mvnw.cmd     # Maven Wrapper，不需要本机预装 Maven
│   ├── data/               # H2 文件库（不入版本库）
│   └── src/main/java/com/aiassistant/
│       ├── AiAssistantBackendApplication.java
│       ├── common/         # 跨模块共用：统一错误体、全局异常处理
│       └── task/           # 示例业务模块（实体/仓储/服务/接口同包）
│           └── dto/
└── frontend/               # React SPA
    └── src/
        ├── api/            # fetch 封装与接口定义
        ├── components/     # 页面组件
        └── types/          # 与后端 DTO 对应的类型
```

## 环境要求

- JDK 17 或更高（`java -version`）
- Node.js 20+ 和 pnpm（`node -v`、`pnpm -v`）
- 不需要预装 Maven，`backend/mvnw` 会自动下载所需版本

## 快速开始

```bash
pnpm install     # 在根目录执行一次，同时装好根依赖和前端依赖
pnpm dev         # 同时启动后端(8080) 和前端(5173)
```

然后打开 http://localhost:5173 。

只想起一端：

```bash
pnpm dev:backend    # 仅后端，http://localhost:8080
pnpm dev:frontend   # 仅前端，http://localhost:5173
```

端口被占用、进程残留或改完配置需要重来时，用 `restart:` 系列命令：先按端口结束旧进程，确认端口释放后再启动。

```bash
pnpm restart:all       # 前后端都重启
pnpm restart:backend   # 仅重启后端
pnpm restart:frontend  # 仅重启前端
```

脚本本身是 `scripts/restart-dev.ps1`，也可以直接执行 `.\scripts\restart-dev.ps1 -Target backend`。
注意不要写成 `pnpm restart`：那是 pnpm 内置的生命周期命令，会去找 `stop` 脚本。

## 常用命令

| 命令 | 说明 |
| --- | --- |
| `pnpm dev` | 同时启动前后端 |
| `pnpm dev:backend` | 仅启动后端 |
| `pnpm dev:frontend` | 仅启动前端 |
| `pnpm restart:all` | 先停止 8080 和 5173 上的进程，再同时启动前后端 |
| `pnpm restart:backend` | 只重启后端，前端不受影响 |
| `pnpm restart:frontend` | 只重启前端，后端不受影响 |
| `pnpm test:backend` | 跑后端测试 |
| `pnpm build` | 前端生产构建（先 `tsc` 类型检查再打包） |

## 接口一览

示例模块挂在 `/api` 前缀下：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/tasks` | 列表，按 id 倒序 |
| GET | `/api/tasks/{id}` | 单条，不存在返回 404 |
| POST | `/api/tasks` | 新建，成功返回 201 |
| PUT | `/api/tasks/{id}` | 全量更新 |
| DELETE | `/api/tasks/{id}` | 删除，成功返回 204 |

```bash
curl http://localhost:8080/api/tasks
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"写脚手架","description":"验证链路","status":"TODO"}'
```

错误响应统一成同一种结构，前端只需处理一种格式：

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "请求参数校验失败",
  "fieldErrors": { "title": "标题不能为空" },
  "timestamp": "2026-10-17T07:53:09.023Z"
}
```

## H2 数据库

- 数据文件：`backend/data/aiassistant.mv.db`
- 控制台：http://localhost:8080/h2-console
  - JDBC URL 填 `jdbc:h2:file:./data/aiassistant`
  - 用户名 `sa`，密码留空

换成内存库只需改 `backend/src/main/resources/application.yml` 里的 `spring.datasource.url`。

## 前后端如何连起来

开发期前端把 `/api` 代理到后端（见 `frontend/vite.config.ts`），所以：

- 前端代码里直接写 `/api/tasks`，不需要区分环境的 baseURL
- 后端不需要任何 CORS 配置

生产部署两种方式二选一：

1. `pnpm build` 的产物在 `frontend/dist`，交给 Nginx 托管，并把 `/api` 反向代理到后端；
2. 把 `frontend/dist` 的内容拷进 `backend/src/main/resources/static`，由后端一起提供。此时前后端同源，同样不需要 CORS。

## 代码约定

- 后端按**功能**分包，不按技术层分包：一个 `task` 包内同时放实体、仓储、服务、接口，`dto` 子包放入参出参。
- 实体不直接出参，统一走 `dto` 包下的 record，避免后续给实体加字段时把内部结构泄露给前端。
- 入参校验用 Bean Validation 注解，错误由 `GlobalExceptionHandler` 统一转成 `ApiError`。
- 前端状态管理目前只用 `useState`/`useEffect`。等出现跨页面共享状态时再引入路由和状态库，现在不提前搭。

## 后续扩展位置

| 要做的事 | 改哪里 |
| --- | --- |
| 加一个新的业务模块 | 在 `backend/src/main/java/com/aiassistant/` 下新建功能包，参照 `task` 包 |
| 换成正式数据库 | 改 `application.yml` 的 datasource，并加上对应驱动依赖 |
| 管理表结构变更 | 脚手架用 `ddl-auto: update`（方便但不适合多人协作），接入团队后换成 Flyway 或 Liquibase |
| 加接口文档 | 引入 springdoc-openapi，暴露 `/swagger-ui.html` |
| 加前端路由/状态库 | `frontend/package.json` 里装 react-router、zustand 等 |

## 平台差异

根目录的 `dev:backend` 脚本写的是 `mvnw`（Windows 下 cmd 会在当前目录找到 `mvnw.cmd`）。在 macOS 或 Linux 上请把它改成 `./mvnw`。

`restart` 系列命令依赖 `scripts/restart-dev.ps1`，目前只支持 Windows（PowerShell 5.1+ 或 PowerShell 7）。macOS/Linux 上请手动结束占用 8080/5173 的进程后再执行 `pnpm dev`。
