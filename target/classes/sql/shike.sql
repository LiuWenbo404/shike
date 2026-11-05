CREATE DATABASE IF NOT EXISTS shike DEFAULT CHARSET utf8mb4;
USE shike;

-- 用户
CREATE TABLE `user` (
                        id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
                        nickname    VARCHAR(32)  NOT NULL                COMMENT '昵称',
                        phone       VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
                        password    VARCHAR(32)  NOT NULL                COMMENT '密码',
                        create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        PRIMARY KEY (id),
                        KEY idx_phone (phone)
)  COMMENT ='用户';

-- 档口
CREATE TABLE stall (
                       id              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
                       name            VARCHAR(32) NOT NULL                COMMENT '档口名，如 一号窗口',
                       avg_prepare_sec INT         NOT NULL DEFAULT 180    COMMENT '平均出餐秒数',
                       open_status     TINYINT     NOT NULL DEFAULT 1      COMMENT '营业状态：1 营业，0 休息',
                       PRIMARY KEY (id),
                       KEY idx_open (open_status)
) COMMENT ='档口';

-- 菜品
CREATE TABLE dish (
                      id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                      stall_id    BIGINT        NOT NULL                COMMENT '所属档口id',
                      name        VARCHAR(64)   NOT NULL                COMMENT '菜品名',
                      price       DECIMAL(10,2) NOT NULL                COMMENT '价格',
                      daily_stock INT           NOT NULL DEFAULT 200    COMMENT '当天份数',
                      status      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 上架，0 下架',
                      PRIMARY KEY (id),
                      KEY idx_stall (stall_id)
) COMMENT ='菜品';

-- 时段：每个档口每天按 10 分钟切一段
CREATE TABLE time_slot (
                           id         BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                           stall_id   BIGINT NOT NULL                COMMENT '所属档口id',
                           slot_date  DATE   NOT NULL                COMMENT '日期',
                           start_time TIME   NOT NULL                COMMENT '开始时间',
                           end_time   TIME   NOT NULL                COMMENT '结束时间',
                           capacity   INT    NOT NULL                COMMENT '该时段最多接多少单',
                           PRIMARY KEY (id),
                           UNIQUE KEY uk_stall_slot (stall_id, slot_date, start_time),
                           KEY idx_date (slot_date)
)COMMENT ='时段（10分钟一段）';

-- 订单
CREATE TABLE orders (
                        id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                        order_no    VARCHAR(32)   NOT NULL                COMMENT '订单号',
                        user_id     BIGINT        NOT NULL                COMMENT '下单用户id',
                        stall_id    BIGINT        NOT NULL                COMMENT '档口id',
                        slot_id     BIGINT        NOT NULL                COMMENT '取餐时段id',
                        pickup_code VARCHAR(8)    NOT NULL                COMMENT '取餐码',
                        status      TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0 待取，1 已取，2 已取消',
                        amount      DECIMAL(10,2) NOT NULL                COMMENT '订单金额',
                        create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        expire_time DATETIME      NOT NULL                COMMENT '取餐截止时间',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_order_no (order_no),
                        UNIQUE KEY uk_pickup   (stall_id, pickup_code, create_time),
                        KEY idx_user          (user_id),
                        KEY idx_status_expire (status, expire_time)
) COMMENT ='订单';

-- 订单明细
CREATE TABLE order_item (
                            id       BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
                            order_id BIGINT        NOT NULL                COMMENT '订单id',
                            dish_id  BIGINT        NOT NULL                COMMENT '菜品id',
                            quantity INT           NOT NULL                COMMENT '数量',
                            price    DECIMAL(10,2) NOT NULL                COMMENT '下单时单价',
                            PRIMARY KEY (id),
                            KEY idx_order (order_id),
                            KEY idx_dish  (dish_id)
) COMMENT ='订单明细';

-- 初始化数据
START TRANSACTION;

-- ---------- 用户 ----------
INSERT INTO `user` (id, nickname, phone) VALUES
                                             (1, '张三', '13800000001'),
                                             (2, '李四', '13800000002'),
                                             (3, '王五', '13800000003');

-- ---------- 档口 ----------
INSERT INTO stall (id, name, avg_prepare_sec, open_status) VALUES
                                                               (1, '一号窗口', 180, 1),
                                                               (2, '二号窗口', 240, 1),
                                                               (3, '三号窗口', 120, 0);   -- 休息中

-- ---------- 菜品 ----------
INSERT INTO dish (id, stall_id, name, price, daily_stock, status) VALUES
                                                                      (1, 1, '红烧肉盖饭',  15.00, 100, 1),
                                                                      (2, 1, '番茄炒蛋盖饭', 12.00, 120, 1),
                                                                      (3, 1, '紫菜蛋花汤',    3.00, 200, 1),
                                                                      (4, 2, '兰州拉面',     13.00, 150, 1),
                                                                      (5, 2, '牛肉面',       16.00, 120, 1),
                                                                      (6, 3, '麻辣香锅',     25.00,  80, 1),
                                                                      (7, 3, '酸辣粉',       10.00, 150, 1);

-- ---------- 时段：为每个档口生成当天 10:00~13:00 共 18 段 ----------
-- 每段 10 分钟，capacity = 20
INSERT INTO time_slot (stall_id, slot_date, start_time, end_time, capacity)
SELECT  s.id,
        CURDATE(),
        SEC_TO_TIME(36000 + n.i * 600),        -- 10:00 + i*10min
        SEC_TO_TIME(36000 + (n.i + 1) * 600),  -- 10:00 + (i+1)*10min
        20
FROM stall s
         CROSS JOIN (
    SELECT 0 AS i UNION ALL SELECT 1  UNION ALL SELECT 2  UNION ALL SELECT 3
    UNION ALL SELECT 4  UNION ALL SELECT 5  UNION ALL SELECT 6  UNION ALL SELECT 7
    UNION ALL SELECT 8  UNION ALL SELECT 9  UNION ALL SELECT 10 UNION ALL SELECT 11
    UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17
) n;

-- ---------- 订单 ----------
-- 注意：时间段的 id 依赖上面生成顺序：
--   档口1 -> slot_id 1..18   档口2 -> slot_id 19..36   档口3 -> slot_id 37..54
INSERT INTO orders (id, order_no, user_id, stall_id, slot_id, pickup_code, status, amount, expire_time) VALUES
                                                                                                            (1, 'SO20260101001', 1, 1, 3,  'A001', 0, 18.00, NOW() + INTERVAL 30 MINUTE),
                                                                                                            (2, 'SO20260101002', 2, 2, 21, 'B001', 0, 26.00, NOW() + INTERVAL 30 MINUTE),
                                                                                                            (3, 'SO20260101003', 3, 3, 40, 'C001', 0, 35.00, NOW() + INTERVAL 30 MINUTE),
                                                                                                            (4, 'SO20260101004', 1, 1, 5,  'A002', 1, 15.00, NOW() - INTERVAL 10 MINUTE), -- 已取
                                                                                                            (5, 'SO20260101005', 2, 2, 22, 'B002', 2, 13.00, NOW() - INTERVAL 60 MINUTE); -- 已取消

-- ---------- 订单明细 ----------
INSERT INTO order_item (order_id, dish_id, quantity, price) VALUES
                                                                (1, 1, 1, 15.00), (1, 3, 1,  3.00),   -- 15 + 3  = 18
                                                                (2, 4, 2, 13.00),                      -- 13*2    = 26
                                                                (3, 6, 1, 25.00), (3, 7, 1, 10.00),   -- 25 + 10 = 35
                                                                (4, 1, 1, 15.00),                      -- 15
                                                                (5, 4, 1, 13.00);                      -- 13

COMMIT;