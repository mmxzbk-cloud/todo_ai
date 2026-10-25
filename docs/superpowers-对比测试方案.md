# Superpowers 使用对比测试方案（AI_assistant 项目）

- 版本：v1.0（2026-10-25）
- 目的：以两个难度相当的 PRD 功能点为样本，对比「使用 superpowers 流程技能」与「不使用（本项目现有 AGENTS.md / small-feature 工作流）」两种执行方式在**任务耗时、Token 用量、功能实现完整度**上的差异，为团队 agent 工作流选择提供数据。
- 方法先例：迁移自 CBES_AI 项目《M05 Superpowers 使用对比测试方案》v1.0（2026-10-24），变量定义、隔离措施、度量口径与其保持同构，跨项目结果可对照。
- 注意：本对比的对照组不是「裸奔」，而是本项目现有工作流（AGENTS.md small-feature 快速通道路由），结论回答的是「superpowers 相对现有流程的增益/成本」。

## 1. 变量定义

| 组别 | 是否加载 superpowers | 说明 |
| --- | --- | --- |
| 对照组（N） | 否 | 会话按 AGENTS.md 现有规则自然执行（符合条件的功能点走 small-feature 快速通道）；看不到也无法引用 superpowers 技能 |
| 实验组（S） | 是 | 任务卡追加一行指示：先加载 `.cursor/skills/using-superpowers/SKILL.md` 并遵循其流程执行（其链式技能按其规则触发） |

除该变量外，两组环境完全一致：同一模型、同一权限模式、同一基线 commit、同一任务卡（仅组别追加行不同）。

superpowers 技能源放置方式：从 `C:\Users\lenovo\IdeaProjects\CBES_AI\.cursor\skills\` 复制以下 12 个 superpowers 工作流技能目录到本项目 `.cursor\skills\`（ZCode 不加载 `.cursor`，对 N 组天然不可见；S 组按任务卡显式读取）：

```
using-superpowers、brainstorming、writing-plans、executing-plans、
subagent-driven-development、test-driven-development、systematic-debugging、
verification-before-completion、dispatching-parallel-agents、
using-git-worktrees、finishing-a-development-branch、writing-skills
```

复制后执行闭环自检（缺哪个补哪个）：

```bash
grep -rhoE "[a-z-]+/" .cursor/skills/using-superpowers/ | sort -u
# 与 .cursor/skills/ 目录清单比对，确保 SKILL.md 中引用的技能都已在本地
```

## 2. 对比任务选取

来源：《智能任务管理增强 PRD（AI 能力包与耗时统计）》（`prd/智能任务管理增强-PRD-AI能力包与耗时统计.md`）。基线已实现 US-01～US-05（AI 总开关断路器、三类建议端点、耗时统计），四个 P1 建议项中选取两个：

| | 任务 A：US-06 AI 拆解子任务 | 任务 B：US-09 周复盘草稿生成 |
| --- | --- | --- |
| 后端 | AI 生成 3–5 条子任务建议 + 勾选项批量创建（写 tasks 表） | 上周任务/耗时聚合 + AI 生成三段式复盘草稿（只读不落库） |
| 前端 | 任务详情页：勾选列表、行内编辑、创建确认 | 今日页：入口按钮、草稿卡片、复制/关闭 |
| 复杂点 | 批量写库、两层父子任务约束、可编辑交互 | 按周聚合口径（与 US-05 一致）、空态判定、不落库断言 |
| 难度评估 | 写库 + 前端交互重 | 统计口径 + 生成质量重，端点少 |

选 pair 理由：难度相当（约各 1～2 小时量级、5/6 条原始 AC）但实现路径不同，A/B 互为难度参照，且先跑者对后跑者的迁移效应小。US-07（每日总结）与 US-09 结构过近会产生强迁移，US-08（计时备注建议）嵌在结束计时流程内体量偏小，均不入选。

**本方案拍板**（消除 PRD 待拍板项歧义，两组任务卡一致）：
- US-09 入口位置：今日页（PRD A.4 第 5 条，取推荐项）。
- 性能类验收口径：不要求严格 P95 报告，按「单次实测响应 ≤ 15s 且等待期间不阻塞页面其他操作」执行。

## 3. 轮次设计

| 轮次 | 任务 | 组别 |
| --- | --- | --- |
| R1 | A（AI 拆解子任务） | 对照组 N |
| R2 | B（周复盘草稿） | 对照组 N |
| R3 | A（AI 拆解子任务） | 实验组 S |
| R4 | B（周复盘草稿） | 实验组 S |

同一任务先 N 后 S 横向对比；A 与 B 互为难度参照。每轮一个**全新 ZCode 会话**（当前已有项目上下文的会话不得代跑任何一轮），一次只跑一轮，会话内不夹带无关请求，开跑前将任务卡全文粘贴为会话首条消息。

## 4. 环境隔离与基线冻结

1. 提交当前未提交改动（批量导入 / 耗时统计，内容先自查），作为基线的一部分：
   ```bash
   git add -A && git commit -m "WIP: 批量导入与耗时统计（实验基线前置）"
   ```
2. 复制 superpowers 技能源并入库（见 §1 清单与自检命令）：
   ```bash
   mkdir -p .cursor/skills
   cp -r /c/Users/lenovo/IdeaProjects/CBES_AI/.cursor/skills/{using-superpowers,brainstorming,writing-plans,executing-plans,subagent-driven-development,test-driven-development,systematic-debugging,verification-before-completion,dispatching-parallel-agents,using-git-worktrees,finishing-a-development-branch,writing-skills} .cursor/skills/
   git add .cursor && git commit -m "chore: 引入 superpowers 技能源（实验组专用，不进 ZCode 上下文）"
   git tag exp-baseline
   ```
3. 从基线为每轮切独立 worktree 分支，跑完即冻结该轮成果 commit 供验收，不合入：
   ```bash
   git worktree add ../AI_assistant-exp-n-a -b exp/n-a exp-baseline
   git worktree add ../AI_assistant-exp-n-b -b exp/n-b exp-baseline
   git worktree add ../AI_assistant-exp-s-a -b exp/s-a exp-baseline
   git worktree add ../AI_assistant-exp-s-b -b exp/s-b exp-baseline
   ```
4. 执行顺序：先 N（R1、R2）后 S（R3、R4），避免验收经验先污染对照组。
5. 每轮记录：会话 sessionId、开始/结束墙钟时间、验收耗时、改动文件数。
6. 人工验收人四轮为同一人；验收动作（跑 `mvnw test`、逐条打分）尽量等长，验收时间单独计时，不计入 agent 耗时。
7. 实验结束后由项目所有者决定采纳哪一轮的产物合入主线；其余轮次分支保留存档。

## 5. 度量口径

### 5.1 耗时
- **Agent 耗时**：任务卡发送时刻 → agent 声明完成时刻（墙钟）。可用会话流水首尾 `completedAt` 差值复核（token_stats.py 会输出）。
- **人工验收耗时**：验收开始 → 结束，单独记录，不入 agent 口径。

### 5.2 Token 用量（真实统计）
- 来源：`~/.zcode/cli/rollout/model-io-session_<sessionId>.jsonl`。每条模型请求记录 `response.usage`（camelCase：`inputTokens / outputTokens / cacheReadTokens / cacheWriteTokens / totalTokens`）与 `model.modelId`。
- 统计脚本（四轮通用）：`python scripts/token_stats.py <rollout-jsonl 路径>`，输出 `requests / input / output / cache_read / cache_write / total`、modelId 分布、首尾 completedAt。
- **口径说明（重要）**：本机 rollout 中 `inputTokens` 已包含缓存读取部分（实测 `totalTokens = inputTokens + outputTokens`），`cache_read` 是 input 的子集。报告五列分开呈现，对比时重点看 `output`（净生成量）与 `total`。
- **公平性规则**：subagent 请求与主会话落同一会话文件，S 组的 subagent 开销不会漏计；每轮必须核对 `models` 唯一且四轮为同一模型，否则该轮作废重跑。

### 5.3 功能实现完整度
- 验收清单：附录 B（每任务拆分为 11～12 条子检查项，实验开跑前冻结，四轮一致），逐条「通过 / 不通过 / 未实现」。
- 得分：`通过项数 / 总项数 × 100%`；另记录「不通过但代码存在」与「完全未实现」的分布（区分做错与没做）。
- 客观旁证：后端 `mvnw test` 绿灯数（本项目前端无 vitest，前端条目靠人工验收）。

### 5.4 定性记录
每轮附记：返工次数、是否自行验证（跑测试/自查）、提交次数、偏离任务卡的行为。

## 6. 结果记录模板

| 轮次 | 组别 | Agent 耗时 | 验收耗时 | input | output | cache_read | total | 完整度 | mvnw 绿灯 | 返工/自验/提交 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| R1 | N-A |  |  |  |  |  |  |  |  |  |  |
| R2 | N-B |  |  |  |  |  |  |  |  |  |  |
| R3 | S-A |  |  |  |  |  |  |  |  |  |  |
| R4 | S-B |  |  |  |  |  |  |  |  |  |  |

结论输出：同一任务 N vs S 的耗时比、token 比、完整度差，以及定性过程差异。

## 7. 附录 A：任务卡（四轮共用模板，仅组别追加行不同）

```markdown
## 任务：AI 拆解子任务（US-06）
依据：prd/智能任务管理增强-PRD-AI能力包与耗时统计.md 的「US-06：AI 拆解子任务」章节
（功能描述、核心规则、验收标准均以 PRD 为准）。
范围：后端（AI 建议相关模块与单元测试）+ 前端（任务详情页）。
补充拍板（与 PRD 冲突时以本条为准）：
- 「AI 拆解」入口位于任务详情页子任务区域。
- 性能验收按「单次实测响应 ≤ 15s 且不阻塞页面其他操作」口径，不要求严格 P95 报告。
要求：按 AGENTS.md 执行；完成后列出改动文件、测试结果（mvnw test）、验收标准自评。
[实验组追加]：先加载 .cursor/skills/using-superpowers/SKILL.md 并遵循其流程执行。
[对照组追加]：（无）
### 验收标准
（附录 B.1 十二条原样粘贴）
```

```markdown
## 任务：周复盘草稿生成（US-09）
依据：prd/智能任务管理增强-PRD-AI能力包与耗时统计.md 的「US-09：周复盘草稿生成」章节。
范围：后端（上周聚合统计、复盘草稿生成与单元测试）+ 前端（今日页入口与草稿卡片）。
补充拍板（与 PRD 冲突时以本条为准）：
- 「生成本周复盘草稿」入口位于今日页（PRD A.4 第 5 条取推荐项）。
- 性能验收按「单次实测响应 ≤ 15s 且不阻塞页面其他操作」口径，不要求严格 P95 报告。
要求：按 AGENTS.md 执行；完成后列出改动文件、测试结果（mvnw test）、验收标准自评。
[实验组追加]：先加载 .cursor/skills/using-superpowers/SKILL.md 并遵循其流程执行。
[对照组追加]：（无）
### 验收标准
（附录 B.2 十一条原样粘贴）
```

## 8. 附录 B：验收清单（开跑前冻结，不得中途增删）

### B.1 任务 A：US-06 AI 拆解子任务（12 条）

| # | 检查项 | 结果（通过/不通过/未实现） |
| --- | --- | --- |
| 1 | `[前端]` 开关打开时，顶层任务详情页子任务区域出现「AI 拆解」入口 |  |
| 2 | `[前端]` 已有父任务的子任务详情页不出现「AI 拆解」入口 |  |
| 3 | `[系统]` 请求成功后返回 3–5 条子任务建议 |  |
| 4 | `[系统]` 每条建议包含标题与预估分钟数 |  |
| 5 | `[交互]` 建议列表支持逐条勾选/取消勾选 |  |
| 6 | `[交互]` 创建前，标题与预估分钟数可在输入框内修改 |  |
| 7 | `[系统]` 确认后仅将勾选项批量创建为子任务，未勾选项被丢弃 |  |
| 8 | `[系统]` 创建的子任务默认状态 TODO，父任务数据不变 |  |
| 9 | `[性能]` 拆解接口单次实测响应 ≤ 15s，等待期间页面其他操作不被阻塞 |  |
| 10 | `[前端]` 开关关闭时入口不出现 |  |
| 11 | `[前端]` 开关关闭时前端不发出拆解请求 |  |
| 12 | `[系统]` 开关关闭时接口返回 AI_DISABLED，系统行为与现状一致 |  |

### B.2 任务 B：US-09 周复盘草稿生成（11 条）

| # | 检查项 | 结果（通过/不通过/未实现） |
| --- | --- | --- |
| 1 | `[前端]` 开关打开时，今日页出现「生成本周复盘草稿」入口 |  |
| 2 | `[系统]` 请求成功后返回三段式草稿（进展 / 卡点 / 下周建议） |  |
| 3 | `[系统]` 草稿基于上周任务完成情况与耗时汇总的统计数字生成，口径与 US-05 一致（流水为权威、用户时区归日） |  |
| 4 | `[系统]` 上周无任何任务与流水时返回空态文案，不调用 AI |  |
| 5 | `[交互]` 草稿卡片提供「复制」动作，复制成功有提示 |  |
| 6 | `[交互]` 草稿卡片提供「关闭」动作 |  |
| 7 | `[系统]` 草稿只读展示，不写入任何数据（任务、时间日志、审计均不变更） |  |
| 8 | `[性能]` 复盘接口单次实测响应 ≤ 15s |  |
| 9 | `[前端]` 开关关闭时入口不出现 |  |
| 10 | `[前端]` 开关关闭时前端不发出复盘请求 |  |
| 11 | `[系统]` 开关关闭时接口返回 AI_DISABLED，系统行为与现状一致 |  |

## 9. 风险与注意事项

1. **样本量**：单样本对比只看趋势，不下统计结论；如需更强证据，可用 US-07 / US-08 复测。
2. **能力泄漏**：对照组任务卡严禁出现「分步执行」「先写测试」「子任务拆分」等流程暗示；验收清单按附录 B 原样粘贴，不加修饰。
3. **缓存干扰**：同一工作区先后执行，后跑者可能受益于文件系统/索引缓存；token 的 cache_read 列单独列示，耗时差异以功能实现内容为主解读。
4. **基线独立**：四轮基于同一 exp-baseline，各自实现同一功能点，互不依赖；每轮结束只冻结分支供验收，不合入。
5. **验收一致性**：验收清单在四轮开跑前冻结（即本文件附录 B），不得中途增删条目；验收人四轮同一人。
6. **技能闭环**：S 组开跑前确认 `.cursor/skills/` 自检通过，避免实验组因技能缺失而中断（中断会污染该轮耗时数据）。
