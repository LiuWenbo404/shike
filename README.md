# 食刻（Shike）

园区食堂预约取餐系统。员工提前选好档口、菜品和取餐时段，到点直接取餐，减少午高峰排队。

后端项目，Spring Boot 3 + MyBatis-Plus + MySQL + Redis。核心不是 CRUD，而是两件事：**高并发下时段名额不超卖**，以及**待取订单超时后自动释放名额和库存**。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 3.5.7，Java 17 |
| MyBatis-Plus | 3.5.9，条件更新 + 分页插件 |
| MySQL | 8.x（本地验证用 MariaDB 10.6 兼容运行） |
| Redis | 7.x，StringRedisTemplate + Lua + ZSet |
| JWT | jjwt 0.11.5 |
| 接口文档 | Knife4j 4.5 + springdoc 2.8 |

## 功能

- 手机号 + 密码登录，签发 JWT；拦截器校验后把用户 id 放进 `BaseContext`
- 档口、菜品、时段与余量查询
- 下单：校验时段归属与是否结束、档口是否营业、菜品归属、库存与时段名额
- 时段名额：Redis + Lua 预扣，DB 条件更新兜底，失败补偿，每天凌晨对账
- 订单状态机：`0 待取 → 1 已取`，`0 待取 → 2 已取消`
- 用户取消订单：回补 DB 名额、菜品库存、Redis 余量和延迟任务，只能取消自己的待取订单
- 取餐核销：按「档口 + 取餐码」把当天待取订单改成已取
- 超时自动取消：Redis ZSet 延迟队列每秒消费；Redis 丢任务时由每小时扫表兜底
- 订单号毫秒时间戳 + 随机后缀；取餐码按「档口 + 日期」在 Redis 自增

## 订单状态机

```
            用户取消 / 超时
  0 待取  ───────────────▶  2 已取消
     │
     │ 取餐核销
     ▼
  1 已取
```

所有状态流转都用条件更新完成，例如：

```sql
UPDATE orders SET status = 2 WHERE id = ? AND status = 0;
```

重复点击、取消和超时并发、核销和超时并发，都只有第一个请求能改成功，后到的返回影响行数 0，天然幂等。

## 快速开始

1. 本地启动 MySQL 8 和 Redis 7。
2. 执行建库脚本（会重建 6 张表，只用于本地开发）：

```bash
mysql -uroot -p < src/main/resources/sql/shike.sql
```

3. 修改 `src/main/resources/application.yml` 里的数据库账号密码。
4. 启动：

```bash
mvn spring-boot:run
```

5. 打开接口文档：<http://localhost:8080/doc.html>

测试账号：`13800000001` / `123456`（脚本里三个种子用户密码相同）。

## 主要接口

| 方法 | 路径 | 说明 | 是否需要登录 |
| --- | --- | --- | --- |
| POST | `/api/user/login` | 登录，返回 token | 否 |
| GET | `/api/user/info` | 当前用户信息 | 是 |
| GET | `/api/stall/list` | 档口列表 | 是 |
| GET | `/api/dish/{stallId}/dishes` | 某档口菜品 | 是 |
| POST | `/api/time_slot/list` | 某档口某天的时段与余量 | 是 |
| POST | `/api/order` | 下单 | 是 |
| GET | `/api/order/list?page=1&pageSize=10` | 当前用户订单 | 是 |
| POST | `/api/order/{id}/cancel` | 取消订单（仅本人、仅待取） | 是 |
| POST | `/api/order/pickup` | 取餐核销，body：`{"stallId":1,"pickupCode":"01001"}` | 是 |

除登录外，请求头都需要带 `Authorization: Bearer <token>`。

## 核心设计

### 1. 库存扣减：条件更新，不做「先查再减」

```sql
UPDATE dish SET daily_stock = daily_stock - #{quantity}
WHERE id = #{id} AND status = 1 AND daily_stock >= #{quantity}
```

影响行数为 0 就是库存不足。判断和扣减在一条 SQL 里完成，并发下不会把库存扣成负数。

### 2. 时段名额：Redis 预扣 + DB 兜底

名额和菜品库存是两个维度：库存防超卖，时段容量防「后厨来不及做」。所以 `dish.daily_stock` 和 `time_slot.capacity` 分开扣。

1. **Redis + Lua 预扣**：把「判断余量 + 扣减」做成一次原子操作，无效请求挡在数据库外面；
2. **DB 条件更新**：`UPDATE time_slot SET capacity = capacity - 1 WHERE id = ? AND capacity >= 1`，这是最终防线；
3. **失败补偿**：预扣成功但落单失败，`INCR` 还回名额；DB 拒绝时反而要删缓存，让下次请求按 DB 重建；
4. **定时对账**：每天凌晨 3 点用 DB 余量重建 Redis，修复 Redis 重启、被清空或补偿失败造成的不一致。

Redis 不可用时自动降级为纯 DB 校验，接口仍然可用。详细实现见 `service/impl/SlotCapacityServiceImpl.java`。

### 3. 超时订单：ZSet 延迟队列 + 扫表兜底

下单成功后，把 `orderId` 写入 ZSet `order:delay`，score 是取餐截止时间的时间戳。定时任务每秒取到期任务：

```java
Set<String> due = redis.opsForZSet()
        .rangeByScore("order:delay", 0, now, 0, 100);
```

关键点：

- `ZREM` 返回 1 才说明当前实例抢到了这一单，多实例部署不会重复处理；
- 取消/核销后要把订单从 ZSet 移除，避免无意义消费；
- Redis 不保证持久化，所以保留每小时扫 `status = 0 AND expire_time < NOW()` 的兜底任务；
- 取消成功后回补 DB 名额、菜品库存，并在**事务提交后**同步 Redis 和队列，避免 DB 回滚而缓存先行。

### 4. 事务与缓存的一致性

- 订单状态、时段名额、菜品库存的修改在同一个本地事务里；
- Redis 和延迟队列的操作注册在 `afterCommit`，只有 DB 提交成功才执行；
- 接口失败时补偿 Redis 名额；补偿失败也不阻断主流程，交给每日对账修复。

## 目录结构

```
src/main/java/io/liuwenbo
├── common      Result、BizException、BaseContext、JWT 拦截器、全局异常
├── config      MyBatis-Plus 分页、Redis 序列化、Web 拦截器
├── controller  用户 / 档口 / 菜品 / 时段 / 订单
├── dto         请求参数
├── entity      表映射
├── mapper      MyBatis-Plus Mapper 与条件更新 SQL
├── service     业务接口、时段名额、延迟队列
├── task        超时订单消费与名额对账
└── util        JWT、订单号与取餐码
```

## 测试与压测

已验证的关键结果：

| 场景 | 结果 |
| --- | --- |
| 200 并发 / 20 名额，Redis+Lua | 成功 20 单，DB `capacity = 0`，Redis = 0，无超卖 |
| 50 并发 / 5 名额 | 成功 5 单，DB `capacity = 0`，Redis = 0 |
| 下单后取消 | 名额 19→20，库存 99→100，Redis 同步为 20，延迟任务被移除 |
| 超时订单 | 3 秒内被消费，状态变为 2，名额和库存回补 |
| 取餐核销 | 状态 0→1，名额保持占用；重复核销/未知取餐码返回业务错误 |
| 重复取消 | 第二次返回「订单不存在或已不能取消」 |

## 已知限制

- 没有员工/管理员角色表，取餐核销接口只要求登录；生产环境应增加角色与档口归属校验。
- 业务异常统一返回 HTTP 200，业务码放在响应体 `code` 字段（如 400）；接前端时可以改成对应的 HTTP 状态码。
- 定时任务按单实例设计；多实例部署时延迟队列消费是安全的，但扫表任务会重复扫，可加分布式锁或分片。
- 取餐码按「档口 + 日期」自增，只在当天有意义；跨天不保证全局唯一。
- 密码使用 MD5，仅用于演示；生产应改为 BCrypt/Argon2 并加盐。
- 没有单元测试，验证以压测脚本和接口联调为主。
