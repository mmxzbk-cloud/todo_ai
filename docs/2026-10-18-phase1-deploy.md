# 工作管理系统 · 阶段一部署文档

日期：2026-10-18
对象：阶段一交付物（`docs/superpowers/plans/2026-10-18-work-management-phase1.md`）——账号登录 + 任务主线（项目、标签、计时、耗时流水、变更审计、今日视图、看板）。
形态：Spring Boot 4.1.1 单体 + H2 2.4 文件模式 + React SPA，**单实例**部署。
口径：只写能在本机照着敲的命令（Windows PowerShell，路径为本仓库真实路径）。没有实测过的命令都在文中标了「待确认」，不要当成结论。

本文独立成篇，每节都能单独照做，不依赖其它文档。示例里出现的日期（2026-10-18）、备份目录（`C:\aiassistant-backup`）、域名（`app.example.com`）请按实际环境替换。

## 0. 事实基础：哪些已验证、哪些没有

**已实测通过（可以直接采信）**

| 项 | 结果 |
| --- | --- |
| `cd backend && .\mvnw.cmd -B -DskipTests compile` | BUILD SUCCESS |
| `pnpm --dir frontend build`（= `tsc -b && vite build`） | 成功，产物在 `frontend\dist` |
| 后端启动日志 | `Successfully applied 1 migration to schema "PUBLIC", now at version v1`；`Tomcat started on port 8080`；`ddl-auto: validate` 无报错 |
| Flyway 建表数量 | 公开 schema 下 13 张业务表；加上 Flyway 自己的 `flyway_schema_history` 共 14 张（脚本 `V1__init_schema.sql` 已在临时库上跑通，查询结果为 13 与 14） |
| 非主键索引数量（空库） | 49 |
| Flyway 对 H2 2.4.240 的 WARN | 日志原文：`Using H2 2.4.240 which is newer than the version Flyway has been verified with. The latest verified version of H2 is 2.3.232.` —— 只是提示，迁移正常完成 |
| H2 命令行的导出 / 恢复 / 查询 | 已在本机临时库上跑通：`org.h2.tools.RunScript`（建库）→ `org.h2.tools.Script`（导出 SQL）→ `org.h2.tools.RunScript`（导入新库）→ `org.h2.tools.Shell`（计数查询），导出文件是可读 SQL |
| 应用运行中能否碰数据库文件 | **不能**。应用在跑时从另一个进程复制 `backend\data\aiassistant.mv.db` 直接失败（`IOException: 文件正被另一进程使用`），用 H2 命令行连同一个文件库同样会被拒。这是「备份必须停机做」的直接证据 |
| 当前工具链版本 | `java -version` → 17.0.12；`node -v` → v22.22.2；`pnpm -v` → 10.7.0；H2 jar → `C:\Users\lenovo\.m2\repository\com\h2database\h2\2.4.240\h2-2.4.240.jar` |

**未实测（文中标「待确认」）**

- `.\mvnw.cmd -DskipTests package` 产出的 jar 文件名：`backend\target` 下当前没有 jar，只有 `classes`/`surefire-reports` 等，说明打包命令还没在本机跑过。按 pom（`artifactId=ai-assistant-backend`、`version=0.0.1-SNAPSHOT`）+ Spring Boot 插件的默认 `repackage` 行为，预期是 `ai-assistant-backend-0.0.1-SNAPSHOT.jar`，但以实际产出为准。
- 经环境变量覆盖配置项的具体变量名（下一节的表里推导的最小形式与下划线形式都符合 Spring Boot 的宽松绑定规则，但没有用环境变量实跑过）。
- Nginx 侧配置与版本：仓库里没有任何 Nginx 配置文件，也没有 Dockerfile / compose，下面的 Nginx 片段是模板。
- 生产上「应用怎么常驻」（Windows 服务、NSSM、计划任务……）：仓库里没有任何服务化脚本，需要运维侧决定。

## 1. 发布前置

### 1.1 工具链

| 项 | 要求 | 检查命令 |
| --- | --- | --- |
| JDK | 17（pom 里 `<java.version>17</java.version>`，本机 17.0.12 通过） | `java -version` |
| Node | 22（本机 v22.22.2 通过；脚手架文档只要求 20+） | `node -v` |
| pnpm | 10（本机 10.7.0 通过） | `pnpm -v` |
| Maven | 不需要预装，仓库自带 Wrapper | `cd backend; .\mvnw.cmd -v` |

### 1.2 构建

后端（编译，已实测）：

```powershell
cd C:\Users\lenovo\IdeaProjects\AI_assistant\backend
.\mvnw.cmd -B -DskipTests compile
```

后端（出可执行 jar，**待确认**：命令可用，jar 文件名以实际产出为准）：

```powershell
cd C:\Users\lenovo\IdeaProjects\AI_assistant\backend
.\mvnw.cmd -B -DskipTests package
Get-ChildItem .\target\*.jar        # 确认产出的 jar 名字
```

前端（已实测）：

```powershell
cd C:\Users\lenovo\IdeaProjects\AI_assistant
pnpm --dir frontend install --frozen-lockfile   # 按 pnpm-lock.yaml 还原依赖，避免「本地能跑、服务器装出别的版本」
pnpm --dir frontend build                       # = tsc -b && vite build
```

产物：

| 产物 | 路径 |
| --- | --- |
| 前端静态站点 | `C:\Users\lenovo\IdeaProjects\AI_assistant\frontend\dist`（`index.html` + `assets\*`） |
| 后端可执行 jar | `C:\Users\lenovo\IdeaProjects\AI_assistant\backend\target\ai-assistant-backend-0.0.1-SNAPSHOT.jar`（**待确认**，见上） |
| 数据库文件 | `C:\Users\lenovo\IdeaProjects\AI_assistant\backend\data\aiassistant.mv.db` |
| 迁移脚本（随 jar 打包，不需要单独拷） | `backend\src\main\resources\db\migration\V1__init_schema.sql` |

前端生产有两种托管方式，二选一（两种都保持前后端同源，因此都不需要配 CORS）：

1. **Nginx 托管 `dist` 并把 `/api` 反代到后端**（推荐，静态资源与后端进程解耦）。Nginx 里必须给 SPA 加 history 回退，否则刷新 `/today`、`/tasks/1` 这类前端路由会 404（前端用的是 `BrowserRouter`，路由由浏览器 history 承载，服务端并不存在这些路径的文件）：

   ```nginx
   # 待确认：本仓库没有 Nginx 配置，下面是模板；端口/域名/证书按实际环境改
   server {
       listen 80;
       server_name app.example.com;

       root C:/Users/lenovo/IdeaProjects/AI_assistant/frontend/dist;
       index index.html;

       location / {
           try_files $uri $uri/ /index.html;   # SPA 深链接回退，缺了这行刷新子页面会 404
       }

       location /api/ {
           proxy_pass http://127.0.0.1:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;                 # 登录限流按 username|clientIp 计数，拿不到真实 IP 会让限流退化成全局
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
       }
   }
   ```

2. **把 `dist` 拷进后端的 `static` 目录，由后端一起提供**。这条路仓库里**没有**现成配置，落地时要注意两点：拷贝后必须重新打包（或重启）才会生效；后端没有为 SPA 配任何「未知路径转发到 `index.html`」的兜底，所以这条路下刷新子路由同样会 404（仓库里没有 `WebMvcConfigurer` / `addViewController` / 自定义 `ErrorController`，已确认）。除非补这段配置，否则生产建议走第 1 条。

   ```powershell
   # 仅供参考：选这条时要自己补 SPA 回退，并重新打包后端
   $dist = 'C:\Users\lenovo\IdeaProjects\AI_assistant\frontend\dist'
   $static = 'C:\Users\lenovo\IdeaProjects\AI_assistant\backend\src\main\resources\static'
   New-Item -ItemType Directory -Force -Path $static | Out-Null
   Copy-Item "$dist\*" $static -Recurse -Force
   ```

### 1.3 `APP_AUTH_JWT_SECRET` 必须覆盖默认值（硬性）

`application.yml` 里写的是 `${APP_AUTH_JWT_SECRET:dev-only-jwt-secret-do-not-use-in-production-2026}`。这个默认值**公开在仓库里**，而 access token 是自签 HS256、服务端验签只认这把密钥——**不改密钥等于任何人都能拿仓库里那串字自己签出任意账号的合法令牌**，服务端会照单全收，也不会留任何异常痕迹。所以它是本清单里唯一「不改就不能上线」的配置。

两点补充事实（都来自代码）：

- 密钥**至少 32 字节**，否则启动直接失败并抛出 `IllegalStateException: app.auth.jwt-secret 至少需要 32 字节，当前 N 字节`（`JwtService` 构造里校验）。所以不要把变量设成空字符串，那会解析成 0 字节，服务起不来。
- 默认值本身长度 ≥32 字节，所以**忘设密钥不会报错**，只会静默地用公开密钥签发令牌。这是最危险的地方：检查项在清单里单列一条。

生成一把密钥（PowerShell，本机可用）：

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
```

设置方式（当前会话生效，适合手工启动）：

```powershell
$env:APP_AUTH_JWT_SECRET = '<上一步生成的字符串>'
```

或写进用户级环境变量（长期生效，需新开窗口才可见；注意它对这台机器的所有进程可见）：

```powershell
[Environment]::SetEnvironmentVariable('APP_AUTH_JWT_SECRET', '<生成的字符串>', 'User')
```

密钥一旦更换，**此前签发的所有 access token 立即失效**（用户需重新登录；refresh token 不受影响，因为它们存的是数据库里的哈希，与签名密钥无关）。密钥要纳入密钥管理保存，丢了就只能换、换完所有人重登。

## 2. 部署时必须确认的配置

下表逐项对应 `backend\src\main\resources\application.yml` 的当前值。「环境变量」一列是覆盖方式：最小形式（去掉短横线的大写形式）是 Spring Boot 的规范写法；下划线分隔形式按宽松绑定规则同样能解析，但**没有实测过**（本次应用是用文件里的默认值启动的），生产上线前请按下节命令实际覆盖一次并确认生效。

| 配置项 | 文件里的当前值 | 部署要求 | 环境变量（覆盖用） |
| --- | --- | --- | --- |
| `spring.datasource.url` | `jdbc:h2:file:./data/aiassistant` | 相对路径以**进程工作目录**为基准。从 `backend\` 启动即为 `backend\data\`。生产建议改成绝对路径，避免「从别的目录启动就在别处新建了一个空库」 | `SPRING_DATASOURCE_URL`（值形如 `jdbc:h2:file:C:/aiassistant/data/aiassistant`） |
| `spring.datasource.username` / `password` | `sa` / 空 | 保持默认，靠文件系统权限保护库文件。**待确认**：给已有的无密码 H2 库改密码是否只需改这两个值（未实测，不要在没演练的情况下动它） | `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` |
| `spring.jpa.hibernate.ddl-auto` | `validate` | **必须保持 `validate`**。表结构由 Flyway 的迁移脚本管；改成 `update` 会让实体反推结构、悄悄改表并与迁移脚本分叉，改成 `none` 则失去「实体与 DDL 不一致时启动即失败」这道保护 | `SPRING_JPA_HIBERNATE_DDL_AUTO` |
| `spring.flyway.enabled` | `true` | 保持 `true`。关掉它启动会因为 `ddl-auto: validate` 找不到表而失败（新库场景），也没有任何地方会建表 | `SPRING_FLYWAY_ENABLED` |
| `spring.flyway.locations` | `classpath:db/migration` | 保持默认，脚本随 jar 走，不需要在服务器上摆 SQL 文件 | `SPRING_FLYWAY_LOCATIONS` |
| `spring.h2.console.enabled` | `true` | **生产必须改成 `false`**（理由见第 8 节：控制台在安全配置里是匿名可达的）。若希望开发机继续用，就只在开发 profile 里开 | `SPRING_H2_CONSOLE_ENABLED=false` |
| `app.auth.jwt-secret` | `${APP_AUTH_JWT_SECRET:dev-only-...}` | 必须用环境变量覆盖，≥32 字节（见 1.3） | `APP_AUTH_JWT_SECRET` |
| `app.auth.access-token-ttl` | `PT2H` | 默认 2 小时，按需调整。ISO-8601 写法（`PT2H`=2 小时、`PT30M`=30 分钟）。**待确认**：`2h` 这类简写 Spring Boot 也支持，但本机只验证过 ISO-8601 | `APP_AUTH_ACCESSTOKENTTL`（或 `APP_AUTH_ACCESS_TOKEN_TTL`） |
| `app.auth.refresh-token-ttl` | `P30D` | 默认 30 天，按需调整（`P7D`=7 天） | `APP_AUTH_REFRESHTOKENTTL`（或 `APP_AUTH_REFRESH_TOKEN_TTL`） |
| `app.auth.registration-enabled` | `true` | 注册入口开放。生产建议 `false`：关闭后 `/api/v1/auth/register` 返回 403，已有账号不受影响，账号改为手工创建 | `APP_AUTH_REGISTRATION_ENABLED=false` |
| `server.port` | `8080` | 按实际改；改完 Nginx 的 `proxy_pass` 与冒烟脚本里的地址要一起改（冒烟脚本把 `http://localhost:8080/api/v1` 写死在文件里） | `SERVER_PORT` |
| `management.endpoints.web.exposure.include` | `health,info,metrics` | 保持这三个。管理端点里 `shutdown`、`env` 之类能改运行状态，不要图省事放开 | `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` |
| `logging.pattern.console` | 带 `%X{requestId}` 的控制台格式 | 保持。应用**只配了控制台输出，没有文件输出与轮转**，生产需要把 stdout 重定向到文件并自行做轮转/清理（见第 8 节） | `LOGGING_FILE_NAME`（要让 Boot 写文件时才设） |

覆盖后自检（启动日志里能直接看到是否生效）：

```powershell
# 期望：日志中出现 H2 console available at '/h2-console' 这行消失（说明控制台已关），
# 且 Tomcat 端口与 SERVER_PORT 一致
```

## 3. 首次发布的一次性步骤：让 Flyway 接管旧库

脚手架的开发库里可能有 `ddl-auto: update` 时代建出来的同名的旧表（例如 6 列的 `tasks`）。这种情况下首次启动会被 Flyway 拒绝，日志原文：

```
Found non-empty schema(s) "PUBLIC" but no schema history table. Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

这是因为库里已经有对象、却没有 Flyway 的迁移历史表。**本机已经踩过一次**，处理办法是把旧库删掉重建（旧库里只有脚手架验证数据）：

```powershell
# 1) 确认应用已停（见第 4 节停机步骤），8080 上没有任何监听
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue

# 2) 备份再删：即使是验证数据也先留一份，删库不可逆
New-Item -ItemType Directory -Force -Path 'C:\aiassistant-backup\predeploy' | Out-Null
Move-Item 'C:\Users\lenovo\IdeaProjects\AI_assistant\backend\data\aiassistant.mv.db' `
          'C:\aiassistant-backup\predeploy\aiassistant-pre-flyway.mv.db' -Force

# 3) 重启，Flyway 会执行 V1 建出 13 张业务表 + flyway_schema_history
```

启动后必须能看到这两行（本机实测原文）：

```
JdbcTableSchemaHistory - Creating Schema History table "PUBLIC"."flyway_schema_history" ...
DbMigrate - Successfully applied 1 migration to schema "PUBLIC", now at version v1
```

**如果这个库里已经有真实数据**（不是脚手架验证数据），不要删文件：先按第 5 节导出 SQL，再人工核对哪些表是旧结构、把旧表 drop 掉，然后启动让 V1 建齐。不建议用 `baseline-on-migrate` 这类做法绕过——它会把库标记成「已处于 v1」，于是 V1 不再执行，但库里的表结构其实是旧的，之后所有依赖新列的查询都会在运行期才炸。

## 4. 启动与停机

### 4.1 启动

方式 A：Maven 直接跑（开发/预演，已实测可用）

```powershell
cd C:\Users\lenovo\IdeaProjects\AI_assistant\backend
.\mvnw.cmd -B -DskipTests spring-boot:run
```

方式 B：跑打包好的 jar（生产推荐，**待确认**：jar 未打包过，先按 1.2 打包确认文件名）

```powershell
# 必须在 backend 目录下启动：数据源是相对路径 ./data/aiassistant，
# 换个工作目录就会在别处新建空库（或者按第 2 节把 URL 改成绝对路径）
cd C:\Users\lenovo\IdeaProjects\AI_assistant\backend
java -jar .\target\ai-assistant-backend-0.0.1-SNAPSHOT.jar
```

带环境变量的完整启动示例（生产口径）：

```powershell
$env:APP_AUTH_JWT_SECRET = '<生产密钥>'
$env:APP_AUTH_REGISTRATION_ENABLED = 'false'   # 关闭自助注册
$env:SPRING_H2_CONSOLE_ENABLED = 'false'       # 关闭 H2 控制台（上线检查项）
cd C:\Users\lenovo\IdeaProjects\AI_assistant\backend
java -jar .\target\ai-assistant-backend-0.0.1-SNAPSHOT.jar *>> C:\aiassistant-logs\app-2026-10-18.log
```

日志只到 stdout，生产建议像上面这样重定向到带日期的文件（`*>>` 同时收 stdout 与 stderr），并自己做轮转；仓库里没有 logrotate/日志大小限制的配置。

启动成功的样子（本机实测原文，字段顺序一致）：

```
org.flywaydb.core.FlywayExecutor - Database: jdbc:h2:file:./data/aiassistant (H2 2.4)
DbValidate - Successfully validated 1 migration (execution time 00:00.010s)
DbMigrate - Successfully applied 1 migration to schema "PUBLIC", now at version v1
Tomcat started on port 8080 (http) with context path '/'
Started AiAssistantBackendApplication in 5.202 seconds (process running for 5.489)
```

启动日志里两条会被误当成故障的噪音，都可以忽略：

- `WARN ... Using H2 2.4.240 which is newer than the version Flyway has been verified with.` —— Flyway 只验证到 H2 2.3.232，对 2.4.240 打的提示。迁移实际正常完成（本次即如此）。
- `WARN ... Using generated security password: <uuid>` —— 那是 Spring Security 自动配的内存用户的随机密码。安全配置里 `formLogin` 与 `httpBasic` 都关了，这条内存用户没有任何可用的登录入口，不会引入认证口子。

### 4.2 停机

停机顺序是：**先停进程，确认端口与文件锁都释放，再动数据库文件**。H2 文件模式是独占地打开库文件的，进程没停就碰文件会失败——本机实测：应用运行期间从另一个进程复制 `aiassistant.mv.db` 直接报「文件正被另一进程使用」。

```powershell
# 1) 找出占用 8080 的进程（PID 即 OwningProcess）
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue |
    Select-Object LocalAddress, LocalPort, OwningProcess

# 2) 正常停机：在跑 spring-boot:run / java -jar 的那个窗口按 Ctrl+C，等它退出
#    窗口已经找不到时，按 PID 结束：
Stop-Process -Id <OwningProcess 的值>

# 3) 确认端口已释放（没有任何输出即已停）
Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue

# 4) 确认文件锁已释放（能成功复制说明锁已放开）
Copy-Item 'C:\Users\lenovo\IdeaProjects\AI_assistant\backend\data\aiassistant.mv.db' `
          "$env:TEMP\lock-check.mv.db" -Force
Remove-Item "$env:TEMP\lock-check.mv.db" -Force
```

第 4 步是判断「现在能不能安全地做备份 / 换库文件」的最直接办法，比看进程列表可靠。

## 5. 发布后验证

三步，顺序别颠倒：先探活，再看数据落位，最后跑主线冒烟。

### 5.1 探活（不需要令牌）

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

期望：`status = UP`。这是唯一一个匿名可达的 actuator 端点（安全配置里 `permitAll`），所以它能直接反映「进程起来了、依赖就绪」；`/actuator/info` 与 `/actuator/metrics` 需要带 `Authorization: Bearer <access token>` 才通。

### 5.2 数据库落位检查（首次发布必做）

有两条路，任选：

- 后端内置控制台（**仅在控制台没关的情况下可用**）：浏览器打开 `http://localhost:8080/h2-console`，JDBC URL 填 `jdbc:h2:file:./data/aiassistant`，用户 `sa`，密码留空，然后执行下面的查询。注意生产按要求应当已关闭控制台，那这条就不可用。
- 停机后用 H2 命令行（推荐，与是否开控制台无关）：

```powershell
$h2 = 'C:\Users\lenovo\.m2\repository\com\h2database\h2\2.4.240\h2-2.4.240.jar'
cd C:\Users\lenovo\IdeaProjects\AI_assistant\backend

java -cp $h2 org.h2.tools.Shell -url "jdbc:h2:file:./data/aiassistant" -user sa `
     -sql "SELECT COUNT(*) AS BUSINESS_TABLES FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME <> 'FLYWAY_SCHEMA_HISTORY'"

java -cp $h2 org.h2.tools.Shell -url "jdbc:h2:file:./data/aiassistant" -user sa `
     -sql "SELECT VERSION, DESCRIPTION, SUCCESS FROM FLYWAY_SCHEMA_HISTORY ORDER BY INSTALLED_RANK"
```

期望：业务表 13；`flyway_schema_history` 里有一行 `1 / init schema / TRUE`。（本机实测：空库 13 张业务表，含历史表共 14 张。）

### 5.3 主线冒烟脚本

```powershell
powershell -File C:\Users\lenovo\IdeaProjects\AI_assistant\backend\scripts\smoke-phase1.ps1
```

前置：后端已在 `http://localhost:8080` 运行；脚本自己用时间戳生成唯一用户名，不需要清库，可以反复跑。脚本把地址写死成 `http://localhost:8080/api/v1`，所以要在**后端所在那台机器上**执行。全部通过时最后一行是 `=== 通过 N 项，失败 0 项 ===`，有任何一项失败会以退出码 1 结束（可直接接进流水线）。

它覆盖的断言（按脚本执行顺序，共 24 项）：

| 分组 | 断言内容 |
| --- | --- |
| 注册与登录 | 注册返回用户名与默认时区 `Asia/Shanghai`；登录同时返回 access/refresh 两个令牌与用户信息；`/auth/me` 能用令牌解析出当前用户 |
| 未认证与越权 | 不带令牌访问 `/tasks` → 401；用第二个账号的令牌读第一个账号的任务 → 404 且 `code=TASK_NOT_FOUND`（越权不泄露「存在与否」） |
| 项目与任务 | 新建项目返回 id；新建任务的重要性/紧急性都取 HIGH 时优先级推导为 `P0`；默认状态 `TODO`；新建时 `actualMinutes` 为 0 |
| 状态与并发 | `PATCH .../status` 改成 `DOING` 生效；用过期 `version` 再 PUT → 409 `VERSION_CONFLICT`（乐观锁） |
| 计时 | 开始计时返回一条 `endedAt` 为空的流水；`/timers/running` 能查到且指向同一任务；重复开始计时 → 409 `TIMER_ALREADY_RUNNING`；结束并 `markDone=true` 后任务状态 `DONE` 且有完成时刻、流水分钟数 ≥1 且有结束时刻；结束后 `/timers/running` 返回空 |
| 详情 | 详情里 `actualMinutes` 已累加、耗时流水非空、变更历史同时含 `CREATED`/`STATUS_CHANGED`/`TIMER_STARTED`/`TIMER_STOPPED`、标题与写入值一致 |
| 列表与看板 | 已完成任务不出现在今日视图；看板恰为 4 列；已完成任务落在 DONE 列；`?status=DONE` 的分页列表能命中 |
| 标签 | 新建标签返回 id；关联到任务后详情里读得到；同名标签 → 409 `TAG_NAME_TAKEN` |
| 会话 | 登出后旧 refresh token 再用 → 401（刷新令牌确实被吊销） |

**注意：冒烟脚本会往库里写真实数据**，每跑一次留下两个账号（`smoke_a_*`、`smoke_b_*`）、一个项目、一个任务、一个标签，脚本自己不清理。生产上跑完要把这些测试账号停掉（`users.status` 置 `DISABLED` 后，JWT 过滤器会拦掉它们）：

```sql
UPDATE users SET status = 'DISABLED' WHERE username LIKE 'smoke%';
```

更稳妥的做法是：正式发布前先在预演环境跑冒烟，生产上线时只跑 `GET /actuator/health` + 5.2 的落位检查 + 一次真实账号登录。

### 5.4 前端

构建通过即 `pnpm --dir frontend build` 返回 0（它内含 `tsc -b` 类型检查，已实测）。上线后按托管方式验证：

- Nginx 方式：浏览器打开站点，能加载登录页；**在 `/today` 上按 F5 不出现 404**（这是 SPA 回退配置是否生效的判据）。
- 人工走一遍主线：登录 → 今日视图建任务 → 开始计时 → 详情看 `mm:ss` 走动 → 结束并标记完成 → 详情出现耗时与变更历史 → 看板用行内下拉改状态 → 设置页建项目/标签 → 登出后访问 `/today` 被重定向到 `/login`。

## 6. 备份与恢复演练

口径先写在前面：**没演练过的备份不算备份**。改迁移脚本、升级应用版本、或调整数据目录之后，都要重新演练一次，并把演练记录（时间、备份文件、目标库、核对结果）写进值班记录。

### 6.1 两条备份腿

两条都要做：SQL 导出便于跨版本恢复和人工查看，库文件快照便于整体回退。

**腿一：`SCRIPT TO` 导出可读 SQL**

方式 A（应用运行中，走内置控制台）：浏览器打开 `http://localhost:8080/h2-console`（**这条只在控制台开着的环境下可用**），执行：

```sql
SCRIPT TO 'C:\aiassistant-backup\aiassistant-2026-10-18.sql';
```

可选修饰：`NODATA`（只导结构）、`COMPRESSION ZIP`（导出成压缩包，此时不再是可读文本）、`NO PASSWORDS`（不带密码哈希）。应用运行期间导出时，业务写入可能同时在发生，用于灾备演练足够；要求更严格的场景就在停机窗口里导。

方式 B（停机后用命令行，推荐，本机已实测）：

```powershell
$h2  = 'C:\Users\lenovo\.m2\repository\com\h2database\h2\2.4.240\h2-2.4.240.jar'
$out = 'C:\aiassistant-backup\aiassistant-2026-10-18.sql'
New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null

cd C:\Users\lenovo\IdeaProjects\AI_assistant\backend
java -cp $h2 org.h2.tools.Script -url "jdbc:h2:file:./data/aiassistant" -user sa -script $out
```

说明：用户 `sa`、**密码为空，因此省略 `-password` 参数**（工具的默认密码就是空串；在 PowerShell 里传 `-password ""` 容易被吞掉）。导出的是整个 `PUBLIC` schema 的对象与数据——本机演练中导出文件里能看到 `CREATE USER`、`CREATE CACHED TABLE`、`INSERT INTO` 三类语句，是纯文本 SQL；空库导出为 20,832 字节。同一次演练里也验证了：给库加一张 `FLYWAY_SCHEMA_HISTORY` 表后再导出，它的 DDL 与数据**也在文件里**，这点很关键（见 6.3 的告警）。

如果这台机器的 Maven 本地仓库不在默认位置，用这两条定位 jar：

```powershell
Get-ChildItem "$env:USERPROFILE\.m2\repository\com\h2database\h2" -Recurse -Filter 'h2-*.jar' | Select-Object FullName
# 或者让 Maven 输出运行时 classpath：.\mvnw.cmd -q dependency:build-classpath -Dmdep.outputFile=cp.txt
```

不采用 `org.h2.tools.Backup` / `Restore`（**待确认**：那套工具面向已废弃的 PageStore 引擎，本库是 2.4 默认的 MVStore，未在本仓库实测，建议不要依赖它）。

**腿二：库文件快照**

```powershell
# 前提：应用已停，且第 4.2 节第 4 步的 Copy-Item 自检通过
Stop-Process -Id <PID>            # 或 Ctrl+C
Copy-Item 'C:\Users\lenovo\IdeaProjects\AI_assistant\backend\data\aiassistant.mv.db' `
          'C:\aiassistant-backup\aiassistant-2026-10-18.mv.db' -Force
```

注意：应用运行期间这条会失败（实测 `IOException: 文件正被另一进程使用`），这不是命令写错，是 H2 独占文件导致的。如果数据目录下还有 `aiassistant.trace.db`，一并拷走。

### 6.2 恢复演练步骤

在**非生产**目录演练，别拿生产目录当试验场。下面用 `C:\aiassistant-drill` 做目标目录。

```powershell
$h2   = 'C:\Users\lenovo\.m2\repository\com\h2database\h2\2.4.240\h2-2.4.240.jar'
$bk   = 'C:\aiassistant-backup\aiassistant-2026-10-18.sql'
$drill = 'C:\aiassistant-drill'
New-Item -ItemType Directory -Force -Path $drill | Out-Null

# 1) 停应用：确认 8080 没有监听、库文件可复制（见 4.2）

# 2) 用 SQL 导出重建一个库（H2 会自动创建文件）
java -cp $h2 org.h2.tools.RunScript -url "jdbc:h2:file:$drill/aiassistant" -user sa -script $bk

# 3) 核对恢复结果（期望：业务表 13；含历史表 14；非主键索引 49）
java -cp $h2 org.h2.tools.Shell -url "jdbc:h2:file:$drill/aiassistant" -user sa `
     -sql "SELECT COUNT(*) AS BUSINESS_TABLES FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME <> 'FLYWAY_SCHEMA_HISTORY'"
java -cp $h2 org.h2.tools.Shell -url "jdbc:h2:file:$drill/aiassistant" -user sa `
     -sql "SELECT COUNT(*) AS NONPK_INDEXES FROM INFORMATION_SCHEMA.INDEXES WHERE INDEX_TYPE_NAME IS NOT NULL AND INDEX_TYPE_NAME <> 'PRIMARY KEY'"
java -cp $h2 org.h2.tools.Shell -url "jdbc:h2:file:$drill/aiassistant" -user sa `
     -sql "SELECT VERSION, DESCRIPTION, SUCCESS FROM FLYWAY_SCHEMA_HISTORY ORDER BY INSTALLED_RANK"

# 4) 让应用连到这个恢复库跑一次（绝对路径，避免踩相对路径的坑）
$env:SPRING_DATASOURCE_URL = "jdbc:h2:file:$drill/aiassistant"
cd C:\Users\lenovo\IdeaProjects\AI_assistant\backend
java -jar .\target\ai-assistant-backend-0.0.1-SNAPSHOT.jar    # 待确认：jar 名以实际产出为准

# 5) 启动日志必须满足：Flyway 不再执行 V1（历史表里已有 v1、SUCCESS=TRUE），
#    且 ddl-auto: validate 没有报错 —— 这两点同时成立，才说明「恢复出来的结构」是对的
```

要走「文件快照」这条恢复路径，就是停机后把快照拷回去（先把现有文件移到一边，别直接覆盖）：

```powershell
Move-Item 'C:\Users\lenovo\IdeaProjects\AI_assistant\backend\data\aiassistant.mv.db' `
          'C:\aiassistant-backup\current-before-restore.mv.db' -Force
Copy-Item 'C:\aiassistant-backup\aiassistant-2026-10-18.mv.db' `
          'C:\Users\lenovo\IdeaProjects\AI_assistant\backend\data\aiassistant.mv.db' -Force
```

### 6.3 恢复后应该核对什么

| 核对项 | 期望值 / 判据 | 本机演练实测 |
| --- | --- | --- |
| 业务表数量 | `INFORMATION_SCHEMA.TABLES` 里 `PUBLIC` 下排除 `FLYWAY_SCHEMA_HISTORY` 为 13 | 13 |
| 含历史表的表数量 | 14 | 14（含后续单独加过历史表的场景） |
| 非主键索引数量 | 49 | 49 |
| 迁移历史 | `FLYWAY_SCHEMA_HISTORY` 首行 `VERSION=1`、`SUCCESS=TRUE` | 恢复后应用能正常启动（不重跑 V1） |
| 关键表行数 | `users`、`projects`、`tasks`、`task_time_logs`、`task_activity_logs`、`tags` 的行数与**备份那一刻记录的值**一致 | 需在备份时同步记下这些数字，否则事后无从比对 |
| 索引是否读过同一段 SQL | 每条计数查询都要带 `TABLE_SCHEMA='PUBLIC'`，否则会把系统表也算进去 | — |

一条必须知道的告警：**恢复要整库恢复，不要只恢复业务表**。导出文件里包含 `FLYWAY_SCHEMA_HISTORY`（本机演练已验证其 DDL 与数据都在文件里），恢复后 Flyway 看到 v1 已应用就不会重跑 V1。如果只把业务表建回去、漏掉历史表，启动时 Flyway 会尝试重跑 V1，并因「表已存在」直接失败。

记录行数的参考 SQL（备份前、恢复后各跑一次，把结果贴进记录）：

```sql
SELECT 'users' AS t, COUNT(*) FROM users
UNION ALL SELECT 'projects', COUNT(*) FROM projects
UNION ALL SELECT 'tasks', COUNT(*) FROM tasks
UNION ALL SELECT 'task_time_logs', COUNT(*) FROM task_time_logs
UNION ALL SELECT 'task_activity_logs', COUNT(*) FROM task_activity_logs
UNION ALL SELECT 'tags', COUNT(*) FROM tags;
```

## 7. 回滚

Flyway 的 `undo` 是商业版能力，H2 上也没有可用的撤销路径（社区版对 H2 不支持 `undo`）。所以本系统的回滚口径只有一个：

**停服 → 恢复备份 → 回退到上一个应用版本。**

### 7.1 应用侧要回退的东西

| 组件 | 回退动作 |
| --- | --- |
| 后端 | 换回上一版可执行 jar（推荐，前提是发布前留了旧 jar）；没有 jar 时回退上一版源码并重新 `.\mvnw.cmd -B -DskipTests package` |
| 前端 | 换回上一版 `frontend\dist`（Nginx 直接读这个目录；如果用的是「拷进后端 static」那条路，还要重新打包后端） |
| 依赖锁定 | `pnpm-lock.yaml`（以及 `frontend\package.json`、`backend\pom.xml`）必须跟产物一起回退：装出另一套依赖版本，前端行为可能和回退的产物对不上 |
| 配置 | `application.yml` 的改动如果是随版本发布的，也会被 jar 替换；用环境变量覆盖的那部分要在启动脚本里一起改回来 |

前端产物回退要**提前留副本**（`dist` 是构建产物，不会自动留旧版本）：

```powershell
# 发布前
Copy-Item 'C:\Users\lenovo\IdeaProjects\AI_assistant\frontend\dist' `
          'C:\aiassistant-backup\dist-2026-10-18' -Recurse -Force
```

**重要缺口（实测）**：仓库根目录有 `.git`，`main` 分支存在，但**一个提交都没有**（`git log` 报 `your current branch 'main' does not have any commits yet`）。也就是说现在没有可用的版本回退点。发布前必须先把当前可用状态留成可回退的东西——备份 `dist`、备份/打包当前 jar，或先做一次提交并打 tag——否则「回退到上一个应用版本」这句话在明天是无从执行的。

### 7.2 哪些改动不可回滚

- **V1 之后新增的迁移脚本一旦执行，就不能靠应用层回退撤销**。回退应用版本不会回退库结构：旧版本应用连上新结构的库，轻则行为不一致，重则启动即失败（`ddl-auto: validate` 会因为实体与表不匹配直接拦下，这算是好事——至少在启动阶段就暴露，而不是运行期出错）。正确做法就是第 6 节的恢复备份。
- **恢复备份会丢掉备份之后写入的数据**。所以「备份时间点」必须在记录里写清楚，它同时界定了数据丢失窗口的起点。
- 计时中的流水、刷新令牌这些运行期状态同样按备份时间点回退；被回退掉的 refresh token 会让对应用户需要重新登录（access token 在有效期内仍可用，除非同时换了 JWT 密钥）。

### 7.3 回滚步骤

```powershell
# 1) 停服（见 4.2），确认端口释放、库文件可复制
# 2) 恢复数据库：走 6.2 的 SQL 重建或文件快照替换
# 3) 换回上一版后端产物（jar 或重新打包源码）
# 4) 换回上一版 frontend\dist
# 5) 用与上一版一致的启动脚本（含环境变量）启动
# 6) 验证：GET /actuator/health 为 UP；Flyway 未重跑已应用的迁移；按 6.3 核对表数量与关键表行数
# 7) 记录：回滚原因、恢复的备份文件、丢失的数据窗口、核对结果
```

## 8. 单实例约束与风险清单

**H2 嵌入式文件模式同一时刻只能被一个进程打开**，由此派生出一串约束：

- **不能多实例水平扩展**：起第二个实例会在打开库文件时失败。要扩容只能换数据库（PostgreSQL 是明确的演进路径，表与索引结构可直接映射）。
- **滚动 / 灰度发布做不了**：新实例在旧实例还持有文件锁时起不来，所以发布必然有停机窗口。**发布安排在低峰期并停机进行**。
- **跑测试要先停应用**：`mvnw test` 与运行中的应用会抢同一个文件锁。本机实测：应用运行期间连复制库文件都会失败，H2 命令行连接同样被拒。所以顺序永远是「停应用 → 跑测试 / 做备份 → 启动应用」。
- **备份必须在停机窗口做**（或退而求其次，用应用内置控制台导出，两者都在第 6 节）。

**上线检查项（醒目）：`/h2-console` 生产必须关。**

安全配置里 `/h2-console/**` 是 `permitAll` 的（连同 `/actuator/health` 与 `/error`），也就是说**任何能访问到这个端口的人都能打开数据库控制台，用 `sa` + 空密码连上文件库、读写任意表**。两种关闭方式，任选其一：

1. 关掉控制台本身（推荐，无需改代码）：`SPRING_H2_CONSOLE_ENABLED=false`，或在 `application.yml` 里把 `spring.h2.console.enabled` 改成 `false`；关闭后 `/h2-console/**` 只会返回 404。
2. 只在开发 profile 里保留控制台（例如把 `spring.h2.console.enabled: true` 移到一个 `dev` profile 的配置文件里），生产 profile 不加载它。

更彻底的做法是把 `permitAll` 里的 `/h2-console/**` 一并删掉（属于代码改动，本轮范围外）——只关控制台不删 permitAll 时，配置一旦被改回来，控制台就又暴露了。

**其它风险**

| 风险 | 说明与应对 |
| --- | --- |
| JWT 密钥默认值公开 | 不设 `APP_AUTH_JWT_SECRET` 不会报错（默认值长度达标），只会静默地用公开密钥签发令牌。上线检查清单里单列一条，必须人工确认 |
| `APP_AUTH_JWT_SECRET` 被设成空串 | 解析成 0 字节，启动直接失败。设完检查一下变量非空 |
| actuator 权限粒度 | `/actuator/health` 匿名可达（探活需要）；`/actuator/info`、`/actuator/metrics` 只要求「已登录」，**任何已注册用户都能读**（系统里没有管理员角色）。不要往里加会更敏感的端点 |
| 注册入口默认开放 | `app.auth.registration-enabled` 默认 `true`，任何人都能自助注册账号。生产建议关闭后手工建号 |
| 登录限流是内存计数 | 失败 5 次锁 15 分钟，计数在进程内存里，**重启即清零**；单实例下有效，多实例时各算各的 |
| 日志只有控制台输出 | 没有文件输出与轮转配置，需要部署侧重定向到文件并自行轮转/清理，否则磁盘会满 |
| 定时任务与停机 | `@Scheduled` 每 10 分钟做超 12 小时计时兜底截断、每日 03:30 用明细对账 `tasks.actual_minutes`。停机期间它们不运行；恢复备份后这些冗余值以备份里的状态为准 |
| 相对路径数据源 | `jdbc:h2:file:./data/aiassistant` 相对进程工作目录：从别处启动会在别处新建一个空库，看起来像「数据全丢了」。启动脚本里固定工作目录，或改用绝对路径 |
| 端口与限流的配合 | 如果前面加了代理却忘了传 `X-Real-IP` / `X-Forwarded-For`，登录限流的 `clientIp` 会变成代理地址，所有用户共享同一个限流键（更容易被误锁） |

## 9. 已知未完成项（如实记录）

本轮按用户要求**跳过了单元测试**。验证只做到「能编译 + 能启动 + 冒烟脚本覆盖主线」，没有单测、没有并发压测、没有浏览器端到端的自动化回归。以下都不应当成「已完成」：

- **单元测试缺失**：计划里 T1–T3 的测试文件（`JwtServiceTest`、`AuthServiceTest`、`PriorityTest`、`TaskServiceTest`、`TimerServiceTest`）本轮未写。回归只能靠冒烟脚本，而脚本覆盖的是主线，边界与异常分支没有自动化保护。
- **阶段二四张表只有结构、没有接口**：`ai_opportunities`、`skills`、`skill_usage_logs`、`weekly_reviews` 由 V1 建了表，但本轮**没有只读接口**，前端也没有对应页面（页面在阶段二）。
- **注册是开放的**：`app.auth.registration-enabled` 默认 `true`；生产建议关闭后手工建号。
- **登录限流计数在内存里**：重启清零（见第 8 节）。
- **看板不支持拖拽**：改状态只能用行内下拉（键盘可达），这是有意为之，不是缺陷。
- **无附件上传、无 WebSocket 实时推送**（前端靠查询失效重取）、**无模型调用**（`AI可替代?` / `AI替代方案` 等字段本轮靠人工填写）。
- **无容器化、无 CI 流水线**：仓库里没有 Dockerfile / compose / 流水线配置；阶段三的内容。
- **没有版本回退点**：`.git` 已初始化但 `main` 分支无任何提交（见 7.1）。

## 10. 发布检查清单

上线前逐条打勾（把 `[ ]` 改成 `[x]` 并写上执行人/时间）：

- [ ] **生产已用环境变量覆盖 `APP_AUTH_JWT_SECRET`**，长度 ≥32 字节、非空；生成的密钥已存进密钥管理（不是只留在某个终端窗口里）
- [ ] **`APP_AUTH_REGISTRATION_ENABLED=false`**（如按计划关闭自助注册）；需要建号时走手工创建，建完确认能登录
- [ ] **H2 控制台已关闭**：`SPRING_H2_CONSOLE_ENABLED=false`，启动日志里不再出现 `H2 console available at '/h2-console'`；用浏览器直接访问 `/h2-console` 确认不是可用的控制台
- [ ] `spring.jpa.hibernate.ddl-auto` 保持 `validate`（启动日志无实体/DDL 不匹配报错）
- [ ] `spring.flyway.enabled=true`，启动日志出现 `Successfully applied N migration` 或 `Successfully validated`
- [ ] 数据源路径已确认（相对路径时工作目录固定为 `backend\`，或已改成绝对路径）
- [ ] `server.port` 与 Nginx 的 `proxy_pass`、冒烟脚本里的地址一致
- [ ] **发布前已做备份**：`SCRIPT TO` 导出的 SQL + 停服后的 `aiassistant.mv.db` 快照，两者都在，文件带日期
- [ ] 已记录备份时间点与关键表行数（`users`/`projects`/`tasks`/`task_time_logs`/`task_activity_logs`/`tags`），供恢复后比对
- [ ] 已演练过一次恢复（在非生产目录），核对通过：业务表 13、含历史表 14、非主键索引 49、`flyway_schema_history` 首行 `1 / TRUE`
- [ ] **上一版应用产物已留副本**：后端 jar、`frontend\dist`、`pnpm-lock.yaml`（回滚依赖它们；当前 git 无提交，不能靠版本库）
- [ ] 前端托管方式已确认，且 SPA 深链接回退生效（在 `/today` 上刷新不 404）
- [ ] 应用已确认停在发布窗口内（H2 单实例，无停机窗口做不了发布）；发布期间没有 `mvnw test` 之类会来抢文件锁的操作
- [ ] 发布后 `GET /actuator/health` 返回 `UP`
- [ ] 发布后落位检查通过：业务表 13、`flyway_schema_history` 版本 1 且 `SUCCESS=TRUE`
- [ ] 发布后跑过冒烟：`powershell -File backend\scripts\smoke-phase1.ps1` 输出「失败 0 项」；如写过测试数据，已按 5.3 的 SQL 把测试账号置为 `DISABLED`
- [ ] 前端人工走通主线：登录 → 建任务 → 计时 → 结束并完成 → 详情看到耗时与变更历史 → 登出后访问 `/today` 被重定向到 `/login`
- [ ] 日志已重定向到文件并有轮转/清理策略（应用自身不落盘）

## 附录：待确认项汇总

| # | 待确认内容 | 为什么待确认 |
| --- | --- | --- |
| 1 | 可执行 jar 的实际文件名与路径 | `mvnw package` 未在本机跑过（`backend\target` 下暂无 jar），预期名来自 pom 的 `artifactId`/`version` + Spring Boot 插件默认 `repackage` |
| 2 | 环境变量覆盖配置项的确切变量名（`APP_AUTH_ACCESSTOKENTTL` 等） | 按 Spring Boot 宽松绑定规则推导，未用环境变量实跑验证；文中同时给了下划线形式 |
| 3 | `APP_AUTH_ACCESSTOKENTTL` 是否接受 `2h`/`30d` 这类简写 | 本机只验证过 ISO-8601（`PT2H`/`P30D`）写法，建议沿用 |
| 4 | Nginx 侧配置（版本、证书、真实 IP 透传细节） | 仓库里没有 Nginx 配置，文中片段是模板 |
| 5 | 应用在生产如何常驻（Windows 服务 / 计划任务 / 容器） | 仓库里没有服务化脚本，取决于运维方案 |
| 6 | `org.h2.tools.Backup` / `Restore` 是否适用于本库 | 该工具面向 PageStore，本库是 MVStore，未实测；建议只用 `SCRIPT TO` 与文件快照 |
| 7 | 给现有无密码 H2 库改密码的具体影响 | 未实测；在没有演练的情况下不要动 `sa` 的密码 |
| 8 | 生产库文件权限与目录规划 | 需要按服务器实际情况定，本文只给了本机路径示例 |
