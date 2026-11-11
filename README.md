# 食刻（Shike）

园区食堂预约取餐系统。员工提前选好档口、菜品和取餐时段，到点直接取餐，减少午高峰排队。

后端项目，Spring Boot 3 + MyBatis-Plus + MySQL + Redis，包含登录鉴权、菜品与时段查询、下单（库存 + 时段名额双重扣减）、订单查询。

## 技术栈

| 组件 | 版本 / 说明 |
| --- | --- |
| Spring Boot | 3.5.7，Java 17 |
| MyBatis-Plus | 3.5.9，条件更新 + 分页插件 |
| MySQL | 8.x |
| Redis | 7.x，StringRedisTemplate + Lua |
| JWT | jjwt 0.11.5 |
| 接口文档 | Knife4j 4.5 + springdoc 2.8 |

## 功能

- 手机号 + 密码登录，签发 JWT，拦截器校验并写入 `BaseContext`
- 档口、菜品、时段查询
- 下单：校验时段归属与是否结束、档口是否营业、菜品是否属于该档口、库存与名额
- 订单号毫秒级 + 随机后缀，取餐码按档口每日自增
- 当前用户订单分页查询，按下单时间倒序

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
| GET | `/api/stall/list` | 档口列表 | 是 |
| GET | `/api/dish/{stallId}/dishes` | 某档口菜品 | 是 |
| POST | `/api/time_slot/list` | 某档口某天的时段与余量 | 是 |
| POST | `/api/order` | 下单 | 是 |
| GET | `/api/order/list?page=1&pageSize=10` | 当前用户订单 | 是 |

除登录外，请求头都需要带 `Authorization: Bearer <token>`。

## 核心设计

**库存扣减**用条件更新而不是"先查再减"：

```sql
UPDATE dish SET daily_stock = daily_stock - #{quantity}
WHERE id = #{id} AND status = 1 AND daily_stock >= #{quantity}
```

影响行数为 0 就说明库存不足，并发下不会扣成负数。

**时段名额**分三层：

1. Redis + Lua 预扣，把无效请求挡在数据库外面；
2. DB 条件更新 `capacity = capacity - 1 WHERE capacity >= 1` 作为最终防线；
3. 落单失败时 `INCR` 补偿，每天凌晨 3 点用 DB 余量重建 Redis。

Redis 不可用时自动降级为纯 DB 校验，接口仍然可用。详细设计见 `src/main/java/io/liuwenbo/service/SlotCapacityService.java`。

**订单号**用毫秒时间戳 + 3 位随机数，避免秒级精度下的并发重复；**取餐码**用 Redis 按「档口 + 日期」自增，重启也不会重号。

## 目录结构

```
src/main/java/io/liuwenbo
├── common      Result、BizException、BaseContext、JWT 拦截器、全局异常
├── config      MyBatis-Plus 分页、Redis 序列化、Web 拦截器
├── controller  用户 / 档口 / 菜品 / 时段 / 订单
├── dto         请求参数
├── entity      表映射
├── job         时段名额对账
├── mapper      MyBatis-Plus Mapper
├── service     业务接口与实现
└── util        JWT、订单号与取餐码
```

## 测试与压测

`docs/` 下有本地测试用的数据脚本和压测脚本（该目录不进 git）：

- `test-data.sql`：20 个压测用户、每轮重置订单和库存的 SQL
- `load-test.ps1`：20 用户 × 10 单并发下单，输出成功数、RPS 和失败原因
- `第3天-压测记录.md`：三轮对比（超卖复现 / DB-only / Redis+Lua）的模板

## 后续计划

订单状态机与取餐核销、取消与超时释放名额、菜品缓存、每人每时段限一单、限流、单元测试、Docker Compose 部署。
