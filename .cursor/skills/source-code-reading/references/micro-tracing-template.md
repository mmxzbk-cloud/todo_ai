# 微观链路追踪输出模板

---

## 链路：`{链路名称}`

**触发**：{如：用户点击「提交订单」} → `{HTTP Method} {URL}`

### 调用链

> 所有信息整合在一条链里：方法调用、数据变换、关键分支、副作用，一目了然。
> 
> 标注约定：
> - `[DB]` 数据库操作  `[MQ]` 消息  `[RPC]` 远程调用  `[Cache]` 缓存
> - `>>` 数据类型转换  `?` 条件分支

```
→ OrderController.createOrder(OrderCreateReq)              @ controller/OrderController.java:42
  │ >> OrderCreateReq → Order（MapStruct 转换）
  → OrderService.create(Order)                             @ service/OrderService.java:58
    │ ? order.type == PRESALE → 走预售逻辑，跳过库存扣减
    → InventoryService.deduct(productId, qty)              @ service/InventoryService.java:30
    │   [DB] UPDATE t_inventory SET stock = stock - #{qty}
    → orderRepository.save(order)                          @ repository/OrderRepository.java:15
    │   [DB] INSERT INTO t_order (...)
    → eventPublisher.publish(OrderCreatedEvent)            @ service/OrderService.java:72
    │   [MQ] → exchange.order.created
    → PaymentFeign.createPayment(orderId, amount)          @ feign/PaymentFeign.java:12
    │   [RPC] → payment-service
    │ >> Order → OrderVO
  ← 返回 OrderVO { orderId, status, payUrl }
← 200 OK + OrderVO JSON
```

### 关键实体

| 实体 | 角色 | 核心状态流转 |
|------|------|-------------|
| Order | 主体 | CREATED → PAID → SHIPPED |
| Inventory | 被扣减 | stock 减少 |

### 未深入

- 异常处理 / 权限校验 / 日志 — 第一遍跳过，需要时再追
- {其他跳过的部分}
