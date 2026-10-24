---
name: writing-prd
description: Generates a Markdown PRD that follows the team's seven-chapter template (背景 / 用户故事 / 非功能 / 逻辑断言矩阵 / 异常处理 / 附录 / 版本记录) and saves it to `prd/<主题>-PRD-<可选副标题>.md`. Use when the user describes a product or feature requirement, asks to write or update a PRD, references a meeting note that should become a spec, or mentions dropping a requirement document into the `prd/` folder.
---

# Writing PRD (产品需求文档)

## Overview

把口头/碎片化的需求描述，落成一份团队统一格式的 PRD Markdown，保存到 `prd/`。所有 PRD 共用同一套七章节结构、相同的验收标准（AC）格式、相同的版本记录约定，方便评审、追溯和复用。

**Save PRDs to:** `prd/<主题>-PRD-<可选副标题>.md`

- 主题用中文短词；如有版本/方案/范围限定，加副标题
- 例：`prd/考试身份验证-PRD-对比基准照片版.md`、`prd/学生端-成绩查询.md`

## Quick Start

收到需求后，立即按这个清单跟踪进度，**所有 PRD 都要走完这五步**：

```
Task Progress:
- [ ] Step 1: Read the team baseline PRD as the format anchor
- [ ] Step 2: Check existing PRDs to decide new vs revise
- [ ] Step 3: Map the user's input onto the seven chapters
- [ ] Step 4: Draft the PRD into prd/<主题>-PRD-<副标题>.md
- [ ] Step 5: Run the self-check, report the file path
```

**Step 1: Read the team baseline PRD**

每次都先 Read 一遍基线样例，作为格式标尺：`prd/考试身份验证-PRD-对比基准照片版.md`。这是当前团队约定的"长什么样"参照物，**不要**凭印象写。若该文件已不存在，从 `prd/` 目录选最新一篇 PRD 作为格式参照并向用户确认。

**Step 2: Check existing PRDs**

列出 `prd/` 目录：

- 同主题已存在 → 进入修订模式，参见下方 "Revision Mode"
- 同主题不存在 → 新建 v1.0

**Step 3: Map the user's input onto the seven chapters**

按七章节（见下节）把用户给的素材分类。**信息缺口要标记成"待确认"**，不要凭想象补阈值、技术选型、隔离方式。

**Step 4: Draft the PRD**

详细模板、可复制片段、ASCII 流程图样例：见 [reference.md](reference.md)。  
**必读时机**：新建 PRD 之前；编写第四章/第五章/附录之前；不确定 AC 类别枚举时。

**Step 5: Self-check**

跑完 reference.md 的「PRD Self-Check Checklist」每一项再交付。

## The Seven Chapters (固定结构)

每篇 PRD 必须按顺序包含这七章。如果某章在当前需求下确实无内容，**保留章节标题并写"本期不涉及"**，不要删章——保证所有 PRD 结构一致。

```
一、需求背景
   1.1 用户痛点
   1.2 典型用户场景
   1.3 目标与范围（P0 / P1 / 本期不做）
   1.4 环境/兼容/前置结论（按需）

二、用户故事
   US-01、US-02 ...（每个故事三段式：用户故事 + 故事详述 + 验收标准）

三、非功能性需求
   3.1 安全性 / 3.2 性能指标 / 3.3 数据一致性 / 3.4 存储架构（建议）

四、逻辑断言矩阵
   Given / When / Then 三列表格，覆盖核心分支

五、异常处理规范
   分场景子表 + 一个异常返回 JSON 示例

六、附录
   A.1 流程图（ASCII） / A.2 角色与权限矩阵 / A.3 与其他模块关系 / A.4 待确认项

七、版本记录
   v1.0 / v1.1 ... + 日期 + 一句话变更说明
```

## Authoring Rules (硬约束)

写 PRD 时严格遵守：

1. **统一中文**：正文使用简体中文；术语、字段名、状态码可保留英文
2. **每个用户故事三段式**，顺序不可换：
   - 「用户故事」一句话 "作为 X，我希望 Y，以便 Z"
   - 「故事详述」分点描述关键流程/边界
   - 「验收标准」用 `AC X.N` 编号 + 类别标注
3. **AC 类别只能用这 7 个枚举**：
   `[前端]` `[系统]` `[性能]` `[交互]` `[审计]` `[文档]` `[交付]`
   - 例：``AC 2.4 `[性能]` 单次比对接口响应 ≤ 3s (P95)``
4. **优先级只用 P0 / P1 / 本期不做**，不写 P2+
5. **表格优先**：角色矩阵、性能指标、异常处理、断言矩阵都用 Markdown 表格
6. **断言矩阵必填**：第四章每个用户故事至少覆盖一条"成功路径" + 一条"失败/边界"
7. **异常处理 JSON 示例必填**：第五章末尾给一个 ```json` 代码块示例
8. **流程图用 ASCII**：附录里的流程图用代码块 + ASCII 字符画，不引外部图床
9. **版本记录只追加不删行**：升版本时**追加**新行，不要改写历史版本说明
10. **不下定论的写到 A.4 待确认项**：评审里没拍板的，不要在正文里写死阈值或技术选型

## Revision Mode (修订已有 PRD)

收到"改 PRD"/"补充需求"/"升版本"类请求时：

1. Read 完整的现有 PRD 文件，记下当前最新版本号
2. 列出"准备改的章节清单"给用户确认（哪些 US 新增/修改、哪些 AC 调整、哪些 A.4 关闭）
3. 用 `StrReplace` 精确改动受影响段落，**不要重写整篇**
4. 在第七章版本记录里**追加**一行新版本（如 v1.0 → v1.1），写明本次变更；不改写历史版本说明
5. 把改后文件路径告诉用户

## Key Principles

- **结构稳定 > 自由发挥**：所有 PRD 长得一样，团队评审/搜索/二次加工都依赖这个一致性
- **断言驱动验收**：第四章是开发和测试的契约，不能省
- **信息缺口要问不要编**：用户没说的 → 写进 A.4 待确认项，不要脑补
- **一篇 PRD 聚焦一个主题**：多主题需求拆多篇 PRD

## Additional Resources

- 完整字段级模板、可复制片段、自检清单、典型反例：[reference.md](reference.md)
- 团队基线样例：`prd/考试身份验证-PRD-对比基准照片版.md`
