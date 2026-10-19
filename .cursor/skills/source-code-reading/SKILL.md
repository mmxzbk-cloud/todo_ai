---
name: source-code-reading
description: 快速阅读并理解陌生项目源码。触发：接手新项目、阅读开源库、理解遗留代码、梳理业务流程、追踪核心链路、分析数据模型、排查调用关系。支持宏观梳理（项目全景）和微观追踪（核心路径），输出结构化认知文档。
---

# 源码快速阅读

> **核心原则**：先看地图再看路——自顶向下建立全景认知，再沿核心链路深入细节。  
> **定位**：将人类阅读源码数天才能形成的整体认知，通过结构化流程 + 并行子智能体加速到分钟级。

---

## 何时使用

| 场景 | 示例 |
|------|------|
| 接手新/旧项目 | 「帮我看看这个项目是干什么的」「梳理一下这个模块的架构」 |
| 阅读开源库 | 「这个库的核心机制是什么」「帮我理解 XX 框架的工作原理」 |
| 理解某个功能 | 「用户下单的完整流程是什么」「这个审批链路怎么走的」 |
| 排查调用关系 | 「谁调用了这个方法」「数据从哪里来到哪里去」 |
| 数据模型分析 | 「帮我理清这些表之间的关系」 |

**不适用**：已熟悉的项目中定位单个 bug → 用 `systematic-debugging`/`bug-diagnosis`。

---

## 两种模式

```
用户请求
  ├─ 「项目全景/整体架构/这个项目是干什么的」→ 宏观梳理（阶段 1-4）
  ├─ 「某个功能/某条链路怎么走的」          → 微观追踪（阶段 5-7）
  └─ 「全部都看看」                         → 先宏观，再按需微观
```

---

## 第一部分：宏观梳理（上帝视角）

> 目标：在不深入任何单个文件的前提下，建立对项目的 **商业目的 → 技术架构 → 数据模型 → 关键入口/出口** 的全景认知。

### 阶段 1：项目概览（30 秒定位）

**快速扫描，形成第一印象。**

| 动作 | 工具 | 关注点 |
|------|------|--------|
| 读 README / 项目文档 | `Read` README.md, CHANGELOG, docs/ | 商业目的、技术栈、部署方式 |
| 读构建/依赖配置 | `Read` pom.xml / package.json / build.gradle / go.mod / requirements.txt | 核心依赖、技术框架、多模块结构 |
| 扫描目录结构 | `Shell` `tree -L 2 -d` 或 `ls -R` (深度2层) | 模块划分、分层风格（DDD/MVC/六边形） |
| 检查 git 历史 | `Shell` `git log --oneline -20` + `git shortlog -sn --no-merges` | 活跃度、核心贡献者、近期关注点 |

**阶段 1 输出**：一段话总结——这个项目**做什么**、**用什么技术**、**怎么组织**。

### 阶段 2：系统边界探测

**找到代码库的"入口"和"出口"，理解了 I/O，中间的黑盒就更容易拆解。**

#### 2.1 入口探测

| 项目类型 | 搜索方式 |
|----------|----------|
| Spring Boot | `Grep` `@RestController` `@Controller` `@FeignClient` `@RabbitListener` `@KafkaListener` |
| Express/Koa/Nest | `Grep` `router.get` `router.post` `@Get` `@Post` `app.use` |
| CLI 工具 | `Grep` `argparse` `commander` `clap` `cobra` |
| gRPC | `Glob` `*.proto` |
| 消息队列消费者 | `Grep` `@StreamListener` `@RabbitHandler` `Consumer` `subscribe` |
| 定时任务 | `Grep` `@Scheduled` `cron` `setInterval` |

#### 2.2 出口探测

| 类别 | 搜索方式 |
|------|----------|
| 数据库 | `Grep` `@Entity` `@Table` `@Document` `Schema` `model` `Repository` `Mapper` |
| 外部 API | `Grep` `HttpClient` `RestTemplate` `WebClient` `fetch` `axios` `@FeignClient` |
| 消息发送 | `Grep` `rabbitTemplate` `kafkaTemplate` `emit` `publish` `send` |
| 文件/对象存储 | `Grep` `S3Client` `OSSClient` `FileOutputStream` `writeFile` `upload` |
| 缓存 | `Grep` `RedisTemplate` `@Cacheable` `cache.set` `redis` |

**技巧**：入口数量 ≈ 系统能力边界；出口类型 ≈ 系统依赖复杂度。

### 阶段 3：数据模型优先

> **数据结构比算法更重要。** 理清了数据之间的关联，业务流程的轮廓就自然浮现。

| 动作 | 工具 |
|------|------|
| 收集所有实体/模型类 | `Glob` `**/entity/**/*.java` `**/model/**/*.ts` `**/models/**/*.py` `**/schema/**` |
| 数据库 Schema | `Glob` `**/migration/**` `**/flyway/**` `**/*.sql` 或通过 DB MCP 查表结构 |
| 核心 DTO/VO | `Glob` `**/dto/**` `**/vo/**` `**/types/**` |
| 分析关联关系 | `Grep` `@OneToMany` `@ManyToOne` `@ManyToMany` `references` `foreign key` `JOIN` |

**输出**：核心实体关系图（文字描述），标注一对多、多对多等关系。

### 阶段 4：汇总输出

用 [macro-analysis-template.md](references/macro-analysis-template.md) 模板输出，聚焦两件事：

1. **做什么 + 技术栈 + 模块职责**（一段话 + 一棵树，快速定位）
2. **业务场景与实体协作**（按场景组织，每个场景用叙述 + 实体协作流说清楚）

**不要堆表格**，人类读大量表格比读代码还累。

---

## 第二部分：微观追踪（核心链路）

> 目标：锁定一条具体链路，让人类不用读源码就能理解「这段代码做了什么、为什么这么做」。

### 阶段 5：锁定追踪目标

```
用户指定了具体功能/链路？
  ├─ 是 → 直接从入口开始追踪
  └─ 否 → 从阶段 2 的入口清单中选择最核心的 1-3 条链路
```

**选择核心链路的标准**：
- 用户/客户最常触发的操作（如下单、登录）
- 代码变更最频繁的模块（`git log --oneline -- <path>` 看提交量）
- 涉及最多模块间交互的流程

### 阶段 6：识别链路类型，选择输出方式

```
这条链路是什么类型的代码？
  ├─ 业务 CRUD（Controller→Service→DB，有明确实体流转）
  │   → 用【调用链模式】：一条带注释的调用链说清所有事
  │
  └─ 框架/基础设施（循环、状态机、管道、复杂控制流）
      → 用【分阶段叙述模式】：按阶段拆解，每阶段一段解释 + 关键代码
```

**判断标准**：
- 有 `while`/`for` 主循环，函数超过 500 行 → 框架模式
- 有实体（Order/User/Entity）从创建到持久化的完整流转 → 业务模式
- 混合的 → 外层用框架模式，内层调用用业务模式

### 阶段 7：链路追踪与输出

**通用原则**：第一遍只追 Happy Path，忽略 try/catch、日志、参数校验。

#### 模式 A：业务 CRUD 调用链

适用于：有明确实体的请求-处理-持久化流程。

用 [micro-tracing-template.md](references/micro-tracing-template.md) 模板输出。一条调用链内联数据转换（`>>`）、条件分支（`?`）、副作用（`[DB]` `[MQ]` `[RPC]` `[Cache]`）。

#### 模式 B：框架/长函数 分阶段叙述

适用于：循环、状态机、管道、4800 行的 `run_conversation` 这类代码。

用 [complex-flow-template.md](references/complex-flow-template.md) 模板输出。核心思路：

1. **先画流程图**（Mermaid flowchart），让人 10 秒看懂整体控制流
2. **按阶段拆解**，每个阶段包含：
   - 一句话说"做什么"
   - 一句话说"为什么"（这步解决什么问题）
   - 关键代码位置（`file:line`）
   - 阶段之间数据如何传递（核心变量在阶段间的变化）
3. **循环体单独展开**——循环是这类代码的灵魂，需要最多篇幅

**关键区别**：
- 业务模式的"数据流"是实体变换（`OrderReq → Order → OrderVO`）
- 框架模式的"数据流"是**核心数据结构的状态变化**（如 `messages[]` 数组在每个阶段被怎样修改）

**写作要求**：

| 要求 | 说明 |
|------|------|
| 每步必须有「为什么」 | 不能只写方法名，要写「为了防止 XXX」「因为 YYY 场景会导致 ZZZ」 |
| 关键步骤不能合并 | 如果 8 个子步骤各自解决不同问题，就是 8 步，不能压成「修复 message 序列」一行 |
| 注释密度 ≥ 代码密度 | 人类读的是注释，代码位置只是索引。每个步骤的注释应该比代码引用更多 |
| 标注不确定性 | 「推测这里是为了 XXX」比装懂更有价值 |

---

## 并行策略

> 使用 `dispatching-parallel-agents` 技能思想，多个子智能体并行探索。

### 宏观梳理可并行

```
子智能体 A（explore）：目录结构 + 构建文件 + README → 项目概览
子智能体 B（explore）：搜索所有入口（Controller/Router/Handler）
子智能体 C（explore）：搜索所有实体/模型 + 数据关系
```

### 微观追踪可并行（多条链路时）

```
子智能体 A（explore）：追踪链路 1（如用户注册）
子智能体 B（explore）：追踪链路 2（如下单支付）
子智能体 C（explore）：追踪链路 3（如权限校验）
```

**派发模板**：

```markdown
你是一个源码阅读助手。请阅读以下项目的 [具体范围]。

项目路径：[绝对路径]
技术栈：[已知的技术栈]
分析目标：[宏观概览 / 某条链路追踪]

请按以下结构输出：
[粘贴对应模板的关键字段]

要求：
- 带文件路径和行号引用
- 区分事实（代码中看到的）和推测（需进一步验证的）
- 标记未深入但可能重要的区域
```

---

## 适配不同技术栈

### Java / Spring Boot

| 层级 | 关键词 | 典型路径 |
|------|--------|----------|
| 入口 | `@RestController` `@GetMapping` `@PostMapping` | `controller/` `api/` `web/` |
| 编排 | `@Service` `Aggregate` `Facade` | `service/` `domain/aggregate/` `facade/` |
| 持久化 | `@Repository` `Mapper` `@Entity` | `repository/` `mapper/` `entity/` `infrastructure/` |
| 配置 | `@Configuration` `@Bean` `application.yml` | `config/` `resources/` |
| 跨服务 | `@FeignClient` | `feign/` `client/` |

### Node.js / TypeScript

| 层级 | 关键词 | 典型路径 |
|------|--------|----------|
| 入口 | `router.get/post` `@Get` `@Post` `app.use` | `routes/` `controllers/` `pages/api/` |
| 业务 | `service` `useCase` `handler` | `services/` `usecases/` `handlers/` |
| 数据 | `Schema` `model` `prisma` `typeorm` `drizzle` | `models/` `schemas/` `prisma/` |
| 中间件 | `middleware` `guard` `interceptor` | `middleware/` `guards/` |

### Python

| 层级 | 关键词 | 典型路径 |
|------|--------|----------|
| 入口 | `@app.route` `@router.get` `urlpatterns` `def main` | `views/` `routes/` `api/` |
| 业务 | `def` `class *Service` `class *UseCase` | `services/` `core/` `domain/` |
| 数据 | `class Meta` `models.Model` `Base` `SQLModel` | `models/` `schemas/` |

---

## 输出质量要求

- **带路径行号**：结论可追溯（`path/to/File.java:42`）
- **事实 vs 推测**：推测的标注「待验证」
- **沿用项目术语**：不翻译类名/包名
- **注释 > 代码**：人类读的是你的解释，代码位置只是索引。每步的「为什么」比「是什么」更重要
- **粒度匹配复杂度**：简单链路可以精简，但 4800 行的函数不能压成 20 行——关键步骤丢失比冗余更严重
- **复杂流程先画图**：超过 3 个分支或含循环的控制流，必须先用 Mermaid flowchart 给全局鸟瞰

---

## 常见误区

| 误区 | 正确做法 |
|------|----------|
| 一上来就逐行读代码 | 先扫目录结构和构建文件，建立全局地图 |
| 读了很多代码但没有输出结构 | 边读边填模板，强制结构化输出 |
| 在错误处理/日志中迷路 | 第一遍只追 Happy Path，标记异常处理待后续深入 |
| 试图理解所有代码 | 聚焦核心链路（20% 代码承载 80% 业务价值） |
| 把推测当事实 | 代码中看到的 ≠ 运行时行为；标注推测需验证 |
| **精简过度丢关键步骤** | 4800 行函数不能压成 20 行。先识别"这个函数有几个阶段"，每阶段内部再看有几个不可省略的步骤 |
| **只写方法名不写为什么** | `_sanitize_tool_call_arguments` 不如「修复上轮可能损坏的 JSON 参数，否则 API 会 400 拒绝」 |
| **所有代码都用调用链格式** | 循环/状态机/管道类代码用调用链看不清控制流；应先画流程图，再按阶段展开 |
| **忽略核心数据结构的生命周期** | 框架代码没有 Order 实体，但有 `messages[]` 数组——它在每个阶段怎么变，就是框架代码的"数据流" |

---

## 相关技能

| 技能 | 用途 |
|------|------|
| `dispatching-parallel-agents` | 多条链路并行追踪 |
| `systematic-debugging` | 从阅读切换到排错时 |
| `bug-diagnosis` | 从阅读切换到修 bug 时 |
| `code-review-expert` | 阅读后发现问题需 Review 时 |
| `fireworks-tech-graph` | 将架构/数据模型可视化为图 |
