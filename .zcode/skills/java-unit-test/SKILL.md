---
name: java-unit-test
description: >
  为 Spring Boot 项目（Controller / Aggregate / Service / Mapper 四层）生成 JUnit 5 + Mockito + AssertJ
  单元测试。当用户要求写单测、生成/补测试、提高测试覆盖率（add tests / test coverage），或粘贴
  Java 源文件希望为其编写测试时触发；措辞随意也算（如「顺手给这个加个测试」「这几个方法还没测试」）。
---

# Java 单元测试生成 Skill

## 技术栈快照

| 组件 | 选型 |
|------|------|
| 语言 / 框架 | Java 17 + Spring Boot 3.x |
| 测试框架 | JUnit 5（`@ExtendWith(MockitoExtension.class)`，默认 `STRICT_STUBS` 模式） |
| Mock 框架 | Mockito — `@Mock` / `@InjectMocks` / `@Spy` / `@Captor` |
| 断言库 | AssertJ — `assertThat(...)` 优先，**禁用** JUnit 原生断言 |
| ORM | MyBatis Plus（`BaseMapper` / `MPJBaseMapper`） |
| 对象转换 | MapStruct（静态 `INSTANCE` 或 Spring 注入两种模式，策略不同） |
| 架构分层 | `Controller → Aggregate → Service → DAO(Mapper)` |
| 业务异常 | `BizException`（含错误码枚举，断言须精确到枚举值） |
| 统一响应 | `Result<T>` |

**参考文档：**
- `references/layer-rules.md` — 各层详细规则 + 高频陷阱（InOrder、Captor、MapStruct、Feign、ThreadLocal 等）
- `references/test-templates.md` — 四层完整带 Javadoc 模板
- `references/mockito-cheatsheet.md` — Mockito / AssertJ API 速查

---

## 第零步 —— 启动前确认（每次必做）

**先问，再动手。** 避免生成完再返工：

```
1. 生成范围？
   a. 单个类（请告知类名 / 路径）
   b. 指定层级的所有类（Controller / Aggregate / Service）
   c. 整个项目全量扫描
2. 项目根路径（默认当前目录）？多模块项目请指定子模块路径。
3. 有无特殊约定？（BizException 字段名、ThreadLocal 工具类名、自定义基类等）
```

> 是否已有测试无需询问，第一步自动扫描判断。

---

## 第一步 —— 扫描（2 分钟内完成）

### 扫描已有测试 → 判断模式

```bash
find src/test/java -name "*Test.java" | sort
```

| 结果 | 模式 |
|------|------|
| 有文件 | **补测模式** — 只新建缺失文件；已有文件只追加缺失方法，不动现有代码 |
| 无文件 | **全量生成模式** |

### 根据生成范围扫描待测类

```bash
# Controller 层
find src/main/java -name "*Controller.java" | grep -Ev "(Test|Abstract)"

# Aggregate 层
find src/main/java -name "*Aggregate.java" | grep -Ev "(Test|Abstract)"

# Service 层（排除接口，只取实现）
find src/main/java -name "*ServiceImpl.java" | grep -Ev "(Test|Abstract)"

# 整个项目：三条合并执行
```

列出扫描结果，**等用户确认范围**后再进入下一步。

### 补测模式：快速定位缺口

```bash
grep -n "^\s*public " src/main/java/.../XxxClass.java    # 源码所有 public 方法
grep -n "@Test"        src/test/java/.../XxxClassTest.java # 已有测试方法
```

---

## 第二步 —— 深度阅读源文件（不可跳过）

逐行阅读每个待测文件，提取以下信息后再写代码——**缺少这步会导致遗漏分支**：

| 必须提取 | 直接决定 |
|---------|---------|
| 所有 `public` 方法（含单行委托方法） | 测试数量下限 |
| 注入依赖（`@Autowired` / `@Resource` / 构造器参数） | `@Mock` 字段清单 |
| 每个方法的全部分支（`if/else`、`null` 判断、`isEmpty`） | 每分支 = 独立用例 |
| 所有 `throw new BizException(...)` | 每处 = 一个异常路径用例 |
| 有顺序依赖的调用链（A 返回值传给 B） | 需要 `InOrder` 验证 |
| MapStruct 调用方式（静态 `INSTANCE` vs Spring 注入） | 能否 `@Mock`，见 layer-rules.md §MapStruct |
| 特殊依赖（ThreadLocal / 静态工具 / `@Transactional` / Feign） | 需特殊处理，见 layer-rules.md §特殊场景 |

---

## 第三步 —— 规划：场景清单 + 分支矩阵

**先规划，再写代码。** 把思考写在注释里，既防遗漏，也是代码文档。

### 场景清单（每个方法都要列）

```java
// processOrder() 场景规划：
// [正常]  库存充足 + 用户存在     → 返回 OrderDTO，调用顺序：validate → save → notify
// [分支]  needNotify=false       → 不调用 notifyService
// [异常]  库存不足               → 抛 BizException(STOCK_INSUFFICIENT)，save 不执行
// [异常]  用户不存在             → 抛 BizException(USER_NOT_FOUND)
// [边界]  items 为空列表         → 抛 BizException(EMPTY_ORDER)
```

### 分支矩阵（方法有 ≥2 个独立条件时必画）

```
update() 分支矩阵：
| 用例 | 实体存在 | 名称变化 | 名称冲突 | 状态升级 | 预期结果          |
|------|--------|--------|--------|--------|-------------------|
| T1   | ✓      | ✗      | —      | ✗      | 直接更新           |
| T2   | ✓      | ✓      | ✗      | ✗      | 校验唯一性后更新    |
| T3   | ✓      | ✓      | ✓      | —      | 抛异常，不更新      |
| T4   | ✓      | ✗      | —      | ✓      | 先重置再更新        |
| T5   | ✗      | —      | —      | —      | 抛异常，无后续      |
```

---

## 第四步 —— 生成测试类

> 完整代码模板见 `references/test-templates.md`。  
> 各层详细规则见 `references/layer-rules.md`。

### 文件结构

```
源文件：  src/main/java/com/example/service/impl/OrderServiceImpl.java
测试文件：src/test/java/com/example/service/impl/OrderServiceImplTest.java
```

### 类声明规范

```java
@ExtendWith(MockitoExtension.class)          // 不加 public
class OrderServiceImplTest {

    @Mock  DependencyA depA;                 // 每个注入依赖一个 @Mock
    @InjectMocks  OrderServiceImpl target;   // 被测对象
```

> 方法超过 6 个时，用 `@Nested` 按被测方法分组，每组一个内部类，大幅提升可读性。  
> 示例见 `references/test-templates.md §Nested 分组`。

### 测试方法命名

```
{方法名}_should{预期行为}_when{前置条件}
```

示例：`createOrder_shouldThrowBizException_whenStockInsufficient()`

### Javadoc 格式（每个测试方法必须添加）

```java
/**
 * 【{方法名} - {场景类型}】{一句话描述意图}。
 *
 * <p>前置条件：{关键 Mock 配置和入参，读者不看代码也能理解场景}。</p>
 * <p>预期行为：{核心断言 — 返回值 / 调用方法 / 不调用方法 / 异常类型与错误码}。</p>
 */
```

**场景类型**固定四种：`正常路径` / `异常路径` / `边界场景` / `分支路径`

---

## 各层速查：Mock 什么、验证什么

| 层级 | Mock 目标 | 核心验证 | 详细规则 |
|------|----------|---------|---------|
| **Controller** | Aggregate（非 Service） | `Result<T>` success/data、调用次数 | layer-rules.md §Controller |
| **Aggregate** | Service / Facade / Feign 客户端 | 调用顺序（InOrder）、短路（never）、异常传播 | layer-rules.md §Aggregate |
| **Service** | Mapper / DAO | 写入字段（Captor）、分支逻辑 | layer-rules.md §Service |
| **Mapper** | ❌ 不写单元测试 | 自定义 SQL 用集成测试 `@MybatisTest` | — |

---

## 质量检查清单

生成每个文件后过一遍，不通过不提交：

**覆盖率**
- [ ] 所有 `public` 方法至少 1 个测试
- [ ] 每个 `if/else` / 短路分支有独立用例（已画分支矩阵）
- [ ] 每处 `throw new BizException(...)` 有对应异常路径测试
- [ ] 空集合 / `null` 入参等边界已覆盖
- [ ] 同类多边界值合并为 `@ParameterizedTest`

**断言质量（五个高频错误逐条防住，详见 layer-rules.md）**
- [ ] `InOrder` 在调用被测方法**之前**定义（之后定义=顺序验证形同虚设），只含顺序相关的 mock
- [ ] 写入操作用 `ArgumentCaptor` 断言关键字段（`verify(mapper).insert(any())` 单独使用是无效断言）
- [ ] `BizException` 断言精确到错误码枚举（只断 `.isInstanceOf(BizException.class)` 无法区分业务错误）
- [ ] 只 stub 本用例实际调用的方法（STRICT_STUBS 下未用 stub 抛 `UnnecessaryStubbingException`；不同分支拆不同测试方法）
- [ ] Req/DTO 无 `@Data` 时 `eq()` 匹配失效（equals 未生成），改用 `any()` + Captor
- [ ] 条件短路用 `never()` 或 `verifyNoInteractions()` 明确断言
- [ ] 静态 `INSTANCE` Converter 不 Mock，直接断言结果字段

**代码规范**
- [ ] 无 `@SpringBootTest`；依赖 `@Mock`，被测对象 `@InjectMocks`
- [ ] 全用 AssertJ，无 `assertEquals` / `assertTrue`
- [ ] 测试类无 `public`；无 `Thread.sleep()` / 真实 I/O
- [ ] 每个测试方法有【方法名 - 场景类型】格式 Javadoc
- [ ] 测试数据只设置本用例关心的字段；复用对象提为私有工厂方法
- [ ] 方法数 ≥6 时用 `@Nested` 按被测方法分组

---

## 批量生成汇总格式

```
| 类名             | 层级      | 测试方法数 | 覆盖场景                        |
|-----------------|-----------|----------|-------------------------------|
| OrderServiceImpl | Service   | 10       | 正常 ×3、异常 ×4、边界 ×2、分支 ×1 |
| OrderAggregate   | Aggregate | 8        | 正常 ×2、异常 ×3、分支 ×2、边界 ×1 |
```