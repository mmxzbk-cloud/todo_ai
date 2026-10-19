# 测试代码模板

## 目录
1. [Aggregate 层（含 @Nested 分组）](#1-aggregate-层)
2. [Controller 层](#2-controller-层)
3. [Service 层（含 @ParameterizedTest）](#3-service-层)
4. [Mapper 层说明](#4-mapper-层)

---

## 1. Aggregate 层

方法较多时用 `@Nested` 按被测方法分组，每组一个内部类。  
好处：测试报告按方法分层展示，IDE 折叠友好，单次只跑一个方法的测试更便捷。

```java
package com.example.aggregate;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link UserAggregate} 单元测试。
 *
 * <p>覆盖范围：getUserDetail（正常/异常/短路）、createUserWithRole（顺序/失败短路）、
 * listUsersPaged（有数据/空页）</p>
 */
@ExtendWith(MockitoExtension.class)
class UserAggregateTest {

    @Mock UserService  userService;
    @Mock RoleService  roleService;
    @Mock DeptService  deptService;

    @InjectMocks UserAggregate aggregate;

    // ────────────────────────────────────────────────
    // getUserDetail
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("getUserDetail")
    class GetUserDetail {

        /**
         * 【getUserDetail - 正常路径】所有依赖正常返回时，应组装完整 DTO。
         *
         * <p>前置条件：userService 返回 name="张三"；roleService 返回 1 条角色；
         * deptService 返回 name="技术部"。</p>
         * <p>预期行为：DTO 的 user.name、roles 数量、dept.name 均正确。</p>
         */
        @Test
        void shouldAssembleFullDTO_whenAllServicesReturn() {
            when(userService.findById(1L))
                .thenReturn(UserDTO.builder().id(1L).name("张三").build());
            when(roleService.findByUserId(1L))
                .thenReturn(List.of(RoleDTO.builder().name("ADMIN").build()));
            when(deptService.findByUserId(1L))
                .thenReturn(DeptDTO.builder().name("技术部").build());

            UserDetailDTO result = aggregate.getUserDetail(1L);

            assertThat(result.getUser().getName()).isEqualTo("张三");
            assertThat(result.getRoles()).hasSize(1);
            assertThat(result.getDept().getName()).isEqualTo("技术部");
        }

        /**
         * 【getUserDetail - 异常路径】用户不存在时，应传播业务异常，且不调用后续依赖。
         *
         * <p>前置条件：userService.findById() 抛出 BizException(USER_NOT_FOUND)。</p>
         * <p>预期行为：异常传播，roleService 和 deptService 无任何交互。</p>
         */
        @Test
        void shouldPropagateException_whenUserNotFound() {
            when(userService.findById(anyLong()))
                .thenThrow(new BizException(BizErrorEnum.USER_NOT_FOUND));

            assertThatThrownBy(() -> aggregate.getUserDetail(99L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                    .isEqualTo(BizErrorEnum.USER_NOT_FOUND.getErrorCode()));

            verifyNoInteractions(roleService, deptService);
        }
    }

    // ────────────────────────────────────────────────
    // createUserWithRole
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("createUserWithRole")
    class CreateUserWithRole {

        /**
         * 【createUserWithRole - 正常路径】应先创建用户再分配角色（严格顺序）。
         *
         * <p>前置条件：userService.create() 返回新用户 id=1L。</p>
         * <p>预期行为：userService.create() 严格早于 roleService.assignDefault(1L) 执行。</p>
         */
        @Test
        void shouldCallServicesInOrder_whenValidInput() {
            when(userService.create(any())).thenReturn(1L);

            // InOrder 必须在调用被测方法之前定义
            InOrder inOrder = inOrder(userService, roleService);
            aggregate.createUserWithRole(new CreateUserReq());

            inOrder.verify(userService).create(any());
            inOrder.verify(roleService).assignDefault(1L);
        }

        /**
         * 【createUserWithRole - 异常路径】创建用户失败时，应传播异常且不分配角色。
         *
         * <p>前置条件：userService.create() 抛出 BizException(USER_ALREADY_EXISTS)。</p>
         * <p>预期行为：异常传播，roleService 无任何交互。</p>
         */
        @Test
        void shouldNotAssignRole_whenUserCreationFails() {
            when(userService.create(any()))
                .thenThrow(new BizException(BizErrorEnum.USER_ALREADY_EXISTS));

            assertThatThrownBy(() -> aggregate.createUserWithRole(new CreateUserReq()))
                .isInstanceOf(BizException.class);

            verifyNoInteractions(roleService);
        }
    }

    // ────────────────────────────────────────────────
    // listUsersPaged
    // ────────────────────────────────────────────────
    @Nested
    @DisplayName("listUsersPaged")
    class ListUsersPaged {

        /**
         * 【listUsersPaged - 正常路径】有数据时，应为每条记录补充部门名称。
         *
         * <p>前置条件：分页返回 1 条记录（deptId=100L），deptService.findById(100L) 返回"技术部"。</p>
         * <p>预期行为：记录的 deptName="技术部"，deptService.findById() 被调用 1 次。</p>
         */
        @Test
        void shouldEnrichDeptName_whenPageHasData() {
            Page<UserDTO> page = new Page<>(1, 10);
            page.setRecords(List.of(UserDTO.builder().id(1L).deptId(100L).build()));
            when(userService.pageList(any())).thenReturn(page);
            when(deptService.findById(100L)).thenReturn(DeptDTO.builder().name("技术部").build());

            Page<UserDetailDTO> result = aggregate.listUsersPaged(1, 10);

            assertThat(result.getRecords().get(0).getDeptName()).isEqualTo("技术部");
            verify(deptService, times(1)).findById(100L);
        }

        /**
         * 【listUsersPaged - 边界场景】空页时不应调用 deptService，避免无意义查询。
         *
         * <p>前置条件：userService.pageList() 返回空记录页。</p>
         * <p>预期行为：deptService 无任何交互。</p>
         */
        @Test
        void shouldNotCallDeptService_whenPageIsEmpty() {
            Page<UserDTO> empty = new Page<>(1, 10);
            empty.setRecords(Collections.emptyList());
            when(userService.pageList(any())).thenReturn(empty);

            aggregate.listUsersPaged(1, 10);

            verifyNoInteractions(deptService);
        }
    }
}
```

---

## 2. Controller 层

```java
package com.example.controller;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link UserController} 单元测试。
 *
 * <p>覆盖范围：getUser（正常/异常）、createUser（正常）</p>
 */
@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock  UserAggregate aggregate;  // Controller 注入 Aggregate，不是 Service
    @InjectMocks  UserController controller;

    @Nested
    @DisplayName("getUser")
    class GetUser {

        /**
         * 【getUser - 正常路径】用户存在时，应返回 success=true 且 data.name 正确的 Result。
         *
         * <p>前置条件：aggregate.getUser(1L) 返回 name="张三" 的 DTO。</p>
         * <p>预期行为：result.isSuccess()=true，data.name="张三"，aggregate 被调用 1 次。</p>
         */
        @Test
        void shouldReturnSuccessResult_whenUserExists() {
            when(aggregate.getUser(1L))
                .thenReturn(UserDTO.builder().id(1L).name("张三").build());

            Result<UserDTO> result = controller.getUser(1L);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getName()).isEqualTo("张三");
            verify(aggregate).getUser(1L);
        }

        /**
         * 【getUser - 异常路径】Aggregate 抛业务异常时，Controller 应向上传播，不吞异常。
         *
         * <p>前置条件：aggregate.getUser() 抛出 BizException(USER_NOT_FOUND)。</p>
         * <p>预期行为：异常向上传播，调用方可感知。</p>
         */
        @Test
        void shouldPropagateException_whenAggregateThrows() {
            when(aggregate.getUser(anyLong()))
                .thenThrow(new BizException(BizErrorEnum.USER_NOT_FOUND));

            assertThatThrownBy(() -> controller.getUser(99L))
                .isInstanceOf(BizException.class);
        }
    }
}
```

---

## 3. Service 层

演示 `ArgumentCaptor` + `@ParameterizedTest` 的配合：

```java
package com.example.service.impl;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link UserServiceImpl} 单元测试。
 *
 * <p>覆盖范围：findById（正常/异常）、createUser（正常/多边界值）</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock  UserMapper mapper;
    @InjectMocks  UserServiceImpl service;

    @Nested
    @DisplayName("findById")
    class FindById {

        /**
         * 【findById - 正常路径】用户存在时，应返回经 Converter 转换的 DTO。
         *
         * <p>前置条件：mapper.selectById(1L) 返回 name="张三" 的实体。</p>
         * <p>预期行为：DTO.name="张三"（MapStruct INSTANCE 真实执行）。</p>
         */
        @Test
        void shouldReturnDTO_whenUserExists() {
            when(mapper.selectById(1L))
                .thenReturn(User.builder().id(1L).name("张三").build());

            UserDTO result = service.findById(1L);

            assertThat(result.getName()).isEqualTo("张三");
        }

        /**
         * 【findById - 异常路径】用户不存在时，应抛出错误码为 USER_NOT_FOUND 的业务异常。
         *
         * <p>前置条件：mapper.selectById() 返回 null。</p>
         * <p>预期行为：抛出 BizException，错误码精确为 USER_NOT_FOUND。</p>
         */
        @Test
        void shouldThrowBizException_whenUserNotFound() {
            when(mapper.selectById(anyLong())).thenReturn(null);

            assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                    .isEqualTo(BizErrorEnum.USER_NOT_FOUND.getErrorCode()));
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        /**
         * 【createUser - 正常路径】有效请求时，应将 name 字段正确的实体传入 Mapper。
         *
         * <p>前置条件：name="李四"，mapper.insert() 返回 1。</p>
         * <p>预期行为：捕获的实体 name="李四"，status=ACTIVE。</p>
         */
        @Test
        void shouldInsertEntityWithCorrectFields_whenValidRequest() {
            when(mapper.insert(any())).thenReturn(1);
            CreateUserReq req = new CreateUserReq();
            req.setName("李四");

            service.createUser(req);

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(mapper).insert(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("李四");
            assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        /**
         * 【createUser - 边界场景】非法年龄值均应抛出业务异常，且不触发 Mapper 写入。
         *
         * <p>前置条件：age 为边界非法值（0、负数、超出最大值 150）。</p>
         * <p>预期行为：抛出错误码 INVALID_AGE 的 BizException；mapper.insert() 不被调用。</p>
         */
        @ParameterizedTest
        @ValueSource(ints = {0, -1, 151, Integer.MIN_VALUE})
        void shouldThrowBizException_whenAgeIsInvalid(int age) {
            CreateUserReq req = new CreateUserReq();
            req.setName("李四");
            req.setAge(age);

            assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getCode())
                    .isEqualTo(BizErrorEnum.INVALID_AGE.getErrorCode()));

            verify(mapper, never()).insert(any());
        }
    }
}
```

---

## 4. Mapper 层

MyBatis Plus 内置 CRUD（`selectById`、`insert`、`updateById`等）**不写单元测试**，  
框架已自行保障；SQL 正确性用集成测试（`@MybatisTest` + H2 内存库）验证。

**只有**包含非平凡 Java 逻辑（如多条件动态拼接、结果集后处理）的自定义方法才需单测，  
但这类逻辑通常应提取到 Service 层，让 Mapper 保持纯粹的数据访问职责。

MPJBaseMapper Join 查询打桩示例：
```java
when(mapper.selectJoinOne(eq(XxxDTO.class), any(MPJLambdaWrapper.class)))
    .thenReturn(mockResult);
```