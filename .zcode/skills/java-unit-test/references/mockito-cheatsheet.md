# Mockito + AssertJ 速查表

## 注解速查

```java
@ExtendWith(MockitoExtension.class)  // 加在测试类上
@Mock           // 创建 Mock 对象
@InjectMocks    // 创建被测对象，自动注入 @Mock 字段
@Spy            // 包装真实对象（部分 Mock）
@Captor         // ArgumentCaptor 的注解简写
```

---

## 打桩（when/then）

```java
// 有返回值的方法
when(mock.method()).thenReturn(value);
when(mock.method()).thenThrow(new BizException("msg"));
when(mock.method()).thenAnswer(inv -> inv.getArgument(0));

// void 方法
doNothing().when(mock).voidMethod();
doThrow(new BizException()).when(mock).voidMethod();

// 多次调用返回不同值
when(mock.method())
    .thenReturn(first)    // 第 1 次调用
    .thenReturn(second)   // 第 2 次调用
    .thenThrow(ex);       // 第 3 次调用
```

---

## 参数匹配器

```java
any()                    // 任意对象（含 null）
any(User.class)          // 任意 User 实例
anyLong()                // 任意 long
anyString()              // 任意 String
eq(value)                // 精确匹配某个值
isNull()                 // null
isNotNull()              // 非 null
argThat(u -> u.getId() == 1L)  // 自定义断言
```

> ⚠️ 规则：只要某个参数用了匹配器，所有参数都必须用匹配器

```java
// ❌ 混用会报错
when(mapper.findBy(1L, any()));

// ✅ 全部使用匹配器
when(mapper.findBy(eq(1L), any()));
```

---

## 验证交互（verify）

```java
verify(mock).method();                    // 被调用恰好 1 次
verify(mock, times(2)).method();          // 被调用恰好 2 次
verify(mock, never()).method();           // 从未被调用
verify(mock, atLeastOnce()).method();     // 至少调用 1 次
verify(mock, atMost(3)).method();         // 最多调用 3 次

verifyNoInteractions(mock);              // 没有任何交互
verifyNoMoreInteractions(mock);          // 没有未被验证的交互
```

---

## ArgumentCaptor（捕获参数）

```java
// 方式一：手动创建
ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
verify(mapper).insert(captor.capture());
User captured = captor.getValue();
assertThat(captured.getName()).isEqualTo("张三");

// 方式二：注解方式
@Captor
ArgumentCaptor<User> captor;

// 多次调用时获取所有捕获值
List<User> allCaptured = captor.getAllValues();
```

---

## 验证调用顺序（InOrder）

```java
InOrder order = inOrder(serviceA, serviceB);
order.verify(serviceA).step1();
order.verify(serviceB).step2();
```

---

## Spy（部分 Mock）

```java
@Spy
UserConverter converter = new UserConverterImpl();

// 只 stub 特定方法，其余走真实实现
doReturn(mockDTO).when(converter).toDTO(any());
```

---

## 静态方法 Mock（谨慎使用）

```java
try (MockedStatic<SomeUtil> mocked = mockStatic(SomeUtil.class)) {
    mocked.when(() -> SomeUtil.doSomething(any())).thenReturn(value);
    // 在此处写测试代码
}
```

---

## AssertJ 常用断言

```java
// 基本断言
assertThat(value).isEqualTo(expected);
assertThat(value).isNotNull();
assertThat(value).isNull();
assertThat(value).isTrue();
assertThat(value).isFalse();

// 集合断言
assertThat(list).hasSize(3);
assertThat(list).isEmpty();
assertThat(list).isNotEmpty();
assertThat(list).contains(element);
assertThat(list).containsExactly(a, b, c);
assertThat(list).containsExactlyInAnyOrder(a, b, c);

// 字符串断言
assertThat(str).contains("子串");
assertThat(str).startsWith("前缀");
assertThat(str).endsWith("后缀");

// 异常断言
assertThatThrownBy(() -> method())
    .isInstanceOf(BizException.class)
    .hasMessageContaining("关键词");

assertThatCode(() -> method()).doesNotThrowAnyException();

// 数值断言
assertThat(num).isGreaterThan(0);
assertThat(num).isBetween(1, 10);
```
