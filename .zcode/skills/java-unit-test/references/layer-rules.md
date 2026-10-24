# 各层测试规则详解

## 目录
- [Aggregate 层](#aggregate-层) — 编排顺序、条件短路、Feign、分页 enrich
- [Controller 层](#controller-层)
- [Service 层](#service-层)
- [Mapper / DAO 层](#mapper--dao-层)
- [MapStruct Converter](#mapstruct-converter)
- [BizException 断言](#bizexception-断言)
- [特殊场景](#特殊场景) — ThreadLocal、@Transactional、静态工具、构造器注入
- [STRICT_STUBS 常见报错](#strict_stubs-常见报错)

---

## Aggregate 层

Aggregate 是编排层，协调多个 Service / Facade / Feign 完成一个业务动作。  
测试重点是**编排逻辑**，不是单个 Service 内部逻辑（那是 Service 测试的职责）。

### Mock 清单
所有注入的 Service / Repository / Facade / Feign 客户端都要 `@Mock`。  
Aggregate 不应直接调用 Mapper；若存在视为设计问题，但测试时仍需 mock。

### 必须验证的四类行为

| 行为 | 验证方式 |
|------|---------|
| 正常 DTO 组装 | `assertThat(result.getXxx()).isEqualTo(...)` |
| 多依赖调用顺序 | `InOrder`（**必须在调用被测方法之前定义**） |
| 早期失败不触发后续 | `verifyNoInteractions(serviceB)` |
| 异常向上传播 | `assertThatThrownBy(...).isInstanceOf(BizException.class)` |

### InOrder 正确写法

```java
// ❌ 错误：调用之后定义，且混入无关 mock —— 顺序验证失效
aggregate.createOrder(req);
InOrder inOrder = inOrder(serviceA, serviceB, unrelatedMock);

// ✅ 正确：先定义（只放顺序相关依赖），再调用被测方法
InOrder inOrder = inOrder(serviceA, serviceB);
aggregate.createOrder(req);
inOrder.verify(serviceA).validateStock(req);
inOrder.verify(serviceB).save(any());
```

### 条件短路验证

```java
when(serviceA.check(anyLong())).thenThrow(new BizException(BizErrorEnum.RESOURCE_LOCKED));

assertThatThrownBy(() -> aggregate.delete(1L))
    .isInstanceOf(BizException.class)
    .satisfies(e -> assertThat(((BizException) e).getCode())
        .isEqualTo(BizErrorEnum.RESOURCE_LOCKED.getErrorCode()));

verifyNoInteractions(serviceB);                    // 第一个依赖失败，后续不应被触碰
verify(repository, never()).deleteById(anyLong()); // 写操作不应执行
```

### void 方法

```java
// void 方法无需 when() 打桩（MockitoExtension 默认 doNothing）
aggregate.deleteUser(1L);

verify(userService).delete(1L);
verify(roleService).removeByUserId(1L);
// ⚠️ void 方法没有返回值，不要写 assertThat
```

### 参数透传（Req 对象传给多个 Service）

```java
// Req 类有 @Data（Lombok 自动生成 equals()）→ 可用 eq() 精确匹配
verify(orderService).create(eq(req));

// Req 类无 @Data / 未重写 equals() → eq() 永远不匹配，改用 Captor
ArgumentCaptor<CreateOrderReq> captor = ArgumentCaptor.forClass(CreateOrderReq.class);
verify(orderService).create(captor.capture());
assertThat(captor.getValue().getUserId()).isEqualTo(1L);
```

### Feign 客户端 Mock

与 Service mock 方式相同，但需额外关注两个边界：

```java
@Mock UserFeign userFeign;

// 边界 1：返回空列表时，下游字段应为 null 而非 NPE
when(userFeign.getUserNameByIds(any())).thenReturn(Collections.emptyList());
IPage<DTO> result = aggregate.list(req);
assertThat(result.getRecords().get(0).getCreatorName()).isNull(); // 确认不抛 NPE

// 边界 2：Feign 异常是否正确传播
when(userFeign.getUserNameByIds(any())).thenThrow(new FeignException.ServiceUnavailable(...));
assertThatThrownBy(() -> aggregate.list(req)).isInstanceOf(BizException.class);
```

### 分页 enrich（查询后逐条补充字段）

```java
// 有数据 — 验证补充逻辑
@Test
void list_shouldEnrichDeptName_whenPageHasData() {
    Page<UserDTO> page = new Page<>(1, 10);
    page.setRecords(List.of(UserDTO.builder().id(1L).deptId(100L).build()));
    when(userService.pageList(any())).thenReturn(page);
    when(deptService.findById(100L)).thenReturn(DeptDTO.builder().name("技术部").build());

    Page<UserDetailDTO> result = aggregate.list(1, 10);

    assertThat(result.getRecords().get(0).getDeptName()).isEqualTo("技术部");
    verify(deptService, times(1)).findById(100L);
}

// 空页 — 验证下游不被调用
@Test
void list_shouldNotCallDeptService_whenPageIsEmpty() {
    Page<UserDTO> empty = new Page<>(1, 10);
    empty.setRecords(Collections.emptyList());
    when(userService.pageList(any())).thenReturn(empty);

    aggregate.list(1, 10);

    verifyNoInteractions(deptService);
}
```

---

## Controller 层

- Controller 注入 **Aggregate**，不是 Service —— 只 Mock Aggregate
- 验证 `Result<T>` 包装：`result.isSuccess()`、`result.getData()`
- 不加载 Spring 容器，不使用 `@SpringBootTest`

```java
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock  UserAggregate userAggregate;
    @InjectMocks  UserController controller;

    @Test
    void getUser_shouldReturnSuccess_whenUserExists() {
        when(userAggregate.getUser(1L))
            .thenReturn(UserDTO.builder().id(1L).name("张三").build());

        Result<UserDTO> result = controller.getUser(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getName()).isEqualTo("张三");
        verify(userAggregate).getUser(1L);
    }
}
```

---

## Service 层

- Service 注入 Mapper —— Mock Mapper
- 每个 `if/else` 分支独立测试，不要在一个测试里 stub 多条分支路径
- 写入操作必须用 `ArgumentCaptor` 验证关键字段，不能只写 `verify(mapper).insert(any())`
- 分页：mock `selectPage()`；`LambdaQueryWrapper` 用 `any()` 匹配，不深入断言 wrapper 内部条件

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock  OrderMapper orderMapper;
    @InjectMocks  OrderServiceImpl service;

    @Test
    void createOrder_shouldInsertWithCorrectStatus_whenValid() {
        when(orderMapper.insert(any())).thenReturn(1);
        service.createOrder(new CreateOrderReq());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
```

---

## Mapper / DAO 层

- **不测** MyBatis Plus 内置 CRUD（`selectById`、`insert`、`updateById` 等）—— 框架自身保障
- 只测含非平凡 Java 逻辑的自定义方法（如手写多条件拼接、结果转换等）
- SQL 正确性属于集成测试，使用 `@MybatisTest` + H2 内存数据库

> MPJBaseMapper Join 查询的 mock 写法：
> ```java
> when(mapper.selectJoinOne(eq(XxxDTO.class), any(MPJLambdaWrapper.class)))
>     .thenReturn(mockResult);
> ```

---

## MapStruct Converter

### 方案选择决策树

```
Converter 调用方式？
├── 静态 INSTANCE（XxxConverter.INSTANCE.toDTO(e)）
│   → @Mock 无效。让其真实执行，直接断言转换结果的关键字段。
├── Spring 注入（接口字段注入）
│   → 正常 @Mock，stub toDTO()。
└── Spring 注入 + 想用真实逻辑
    → @Spy + new XxxConverterImpl()
      ⚠️ 仅当 XxxConverterImpl 构造器无参且无 @Autowired 嵌套 Converter 时才安全
      否则 new 出来的实例内部字段为 null → NPE，优先选 @Mock
```

```java
// 方案 A：@Mock（推荐，隔离最彻底）
@Mock UserConverter converter;
when(converter.toDTO(entity)).thenReturn(mockDTO);

// 方案 B：静态 INSTANCE（断言结果字段）
UserDTO result = service.findById(1L);
assertThat(result.getName()).isEqualTo(entity.getName());

// 方案 C：@Spy（谨慎）
@Spy UserConverterImpl converter = new UserConverterImpl();
```

---

## BizException 断言

```java
// ✅ 推荐：精确到枚举错误码（最严格，能区分不同业务错误）
assertThatThrownBy(() -> service.method(param))
    .isInstanceOf(BizException.class)
    .satisfies(e -> assertThat(((BizException) e).getCode())
        .isEqualTo(BizErrorEnum.USER_NOT_FOUND.getErrorCode()));

// ⚠️ 备选：验证消息关键词（错误码不可访问时）
assertThatThrownBy(() -> service.method(param))
    .isInstanceOf(BizException.class)
    .hasMessageContaining("用户不存在");

// ❌ 不推荐：只断言类型，无法区分 USER_NOT_FOUND 和 STOCK_INSUFFICIENT
assertThatThrownBy(() -> service.method(param))
    .isInstanceOf(BizException.class);
```

---

## 特殊场景

### ThreadLocal / 安全上下文

```java
// 方案 A（推荐）：@BeforeEach 初始化，@AfterEach 清理，防止污染其他测试
@BeforeEach void setUp()    { SecurityContextHolder.setCurrentUserId(1L); }
@AfterEach  void tearDown() { SecurityContextHolder.clear(); }

// 方案 B：将上下文抽为可注入 Bean，然后 @Mock
@Mock SecurityContextProvider ctx;
when(ctx.getCurrentUserId()).thenReturn(1L);
```

### @Transactional 方法

```java
// 事务注解在 Mockito 单元测试中不生效，直接测业务逻辑，加注释说明即可
// 注：@Transactional 在单元测试中不生效，回滚行为由集成测试（@SpringBootTest）验证
@Test
void createOrder_shouldCallServices_whenValidInput() { ... }
```

### 静态工具调用（Hutool 等）

```java
// 纯计算（格式化、类型转换）→ 不 mock，直接测输入输出

// 有副作用（发邮件、写文件）→ MockedStatic
try (MockedStatic<EmailUtil> mocked = mockStatic(EmailUtil.class)) {
    mocked.when(() -> EmailUtil.send(anyString(), anyString())).thenAnswer(inv -> null);
    aggregate.registerAndNotify(req);
    mocked.verify(() -> EmailUtil.send(eq("user@example.com"), anyString()));
} // try 结束自动 close，不污染其他测试
```

### 构造器注入 vs 字段注入

```java
// 源码使用构造器注入时，@Mock 类型须与构造器参数精确匹配（顺序不要求）
public UserAggregate(UserService us, RoleService rs) { ... }

@Mock UserService userService;  // 类型匹配即可
@Mock RoleService roleService;
@InjectMocks UserAggregate aggregate;
```

---

## STRICT_STUBS 常见报错

`MockitoExtension` 默认开启 `STRICT_STUBS`，会在以下情况报错，这是好事——它发现了测试写法问题：

| 报错 | 原因 | 修复 |
|------|------|------|
| `UnnecessaryStubbingException` | stub 了一个本用例从未调用的方法 | 删掉多余的 `when()`；该分支用独立测试方法 |
| `PotentialStubbingProblem` | stub 的参数与实际调用参数不匹配 | 检查参数匹配器；注意 `eq()` 依赖 `equals()` |

> 遇到 `UnnecessaryStubbingException` 的第一反应：**不要用 `lenient()`**。  
> 正确做法是把多余的 stub 拆分到各自对应的测试方法中。  
> `lenient()` 只应用于极少数确有必要的场景（如 `@BeforeEach` 中的通用 stub）。