package io.liuwenbo.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.common.BaseContext;
import io.liuwenbo.common.BizException;
import io.liuwenbo.dto.OrderDTO;
import io.liuwenbo.dto.OrderItemDTO;
import io.liuwenbo.dto.OrderQueryDTO;
import io.liuwenbo.dto.PickupDTO;
import io.liuwenbo.entity.Dish;
import io.liuwenbo.entity.OrderItem;
import io.liuwenbo.entity.Orders;
import io.liuwenbo.entity.Stall;
import io.liuwenbo.entity.TimeSlot;
import io.liuwenbo.mapper.DishMapper;
import io.liuwenbo.mapper.OrderItemMapper;
import io.liuwenbo.mapper.OrderMapper;
import io.liuwenbo.mapper.StallMapper;
import io.liuwenbo.mapper.TimeSlotMapper;
import io.liuwenbo.service.DishService;
import io.liuwenbo.service.OrderDelayQueue;
import io.liuwenbo.service.OrderService;
import io.liuwenbo.service.SlotCapacityService;
import io.liuwenbo.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;
    private final TimeSlotMapper timeSlotMapper;
    private final StallMapper stallMapper;
    private final OrderUtil orderUtil;
    private final DishService dishService;
    private final SlotCapacityService slotCapacityService;
    private final OrderDelayQueue orderDelayQueue;

    /**
     * 下单主链路：
     * 校验 -> Redis 预扣名额 -> DB 条件扣名额 -> 扣菜品库存 -> 落库 -> 延迟队列。
     * Redis 只做前置拦截，DB 的 capacity 才是最终真相。
     */
    @Transactional
    @Override
    public Orders insert(OrderDTO orderDTO) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BizException("请先登录");
        }
        if (orderDTO == null || orderDTO.getStallId() == null || orderDTO.getSlotId() == null) {
            throw new BizException("参数不完整");
        }
        if (orderDTO.getOrderItems() == null || orderDTO.getOrderItems().isEmpty()) {
            throw new BizException("请选择菜品");
        }

        // 1. 时段校验：存在、属于该档口、尚未结束
        TimeSlot slot = timeSlotMapper.selectById(orderDTO.getSlotId());
        if (slot == null) {
            throw new BizException("时段不存在");
        }
        if (!slot.getStallId().equals(orderDTO.getStallId())) {
            throw new BizException("时段不属于该档口");
        }
        LocalDateTime slotEnd = LocalDateTime.of(slot.getSlotDate(), slot.getEndTime());
        if (LocalDateTime.now().isAfter(slotEnd)) {
            throw new BizException("该时段已结束");
        }

        // 2. 档口校验：存在且营业中
        Stall stall = stallMapper.selectById(orderDTO.getStallId());
        if (stall == null || stall.getOpenStatus() == null || stall.getOpenStatus() != 1) {
            throw new BizException("该档口休息中");
        }

        // 3. 合并同一菜品，并校验数量（合并之后才能挡住"拆成多行绕过上限"）
        Map<Long, Integer> quantityByDish = getQuantityByDish(orderDTO);

        // 4. 固定加锁顺序：按菜品 id 排序，避免多菜品订单互相死锁
        List<Long> dishIds = new ArrayList<>(quantityByDish.keySet());
        dishIds.sort(Long::compareTo);

        // 5. Redis 预扣名额；TAKEN 表示真的占了一个，DEGRADED 交给 DB 兜底
        SlotCapacityService.AcquireResult acquire = slotCapacityService.tryAcquire(slot);
        if (acquire == SlotCapacityService.AcquireResult.FULL) {
            throw new BizException("该时段名额已满");
        }
        // 只有真的在 Redis 里减过，才需要为它负责
        boolean permitTaken = (acquire == SlotCapacityService.AcquireResult.TAKEN);

        try {
            // 6. DB 条件扣名额 最终防线
            if (timeSlotMapper.decrCapacity(slot.getId()) == 0) {
                // Redis 放行但 DB 已经没有名额：两边漂移了。DB 才是真相，删缓存让下次请求按 DB 重建。
                // 这里不能 INCR，否则会把已满的时段又放出一个名额（catch 里也不要再归还）。
                slotCapacityService.invalidate(slot.getId());
                permitTaken = false;
                throw new BizException("该时段名额已满");
            }

            // 7. 扣菜品库存并计算金额
            BigDecimal amount = BigDecimal.ZERO;
            List<OrderItem> items = new ArrayList<>();
            for (Long dishId : dishIds) {
                int quantity = quantityByDish.get(dishId);
                Dish dish = dishMapper.selectById(dishId);
                if (dish == null || !dish.getStallId().equals(orderDTO.getStallId())) {
                    throw new BizException("菜品不属于该档口");
                }
                if (dishMapper.decrStock(dish.getId(), quantity) == 0) {
                    throw new BizException("菜品库存不足：" + dish.getName());
                }
                amount = amount.add(dish.getPrice().multiply(BigDecimal.valueOf(quantity)));

                OrderItem orderItem = new OrderItem();
                orderItem.setDishId(dishId);
                orderItem.setQuantity(quantity);
                orderItem.setPrice(dish.getPrice());
                items.add(orderItem);
            }

            // 8. 保存订单信息，取餐截止时间 = 该时段结束 + 30 分钟
            Orders order = new Orders();
            order.setOrderNo(orderUtil.generateOrderNO());
            order.setUserId(userId);
            order.setStallId(orderDTO.getStallId());
            order.setSlotId(orderDTO.getSlotId());
            order.setPickupCode(orderUtil.generatePickupCode(orderDTO.getStallId()));
            order.setStatus(0);
            order.setAmount(amount);
            order.setCreateTime(LocalDateTime.now());
            order.setExpireTime(slotEnd.plusMinutes(30L));
            orderMapper.insert(order);

            // 9. 事务提交后再写延迟队列：避免订单回滚、队列里留下幽灵任务
            afterCommit(() -> orderDelayQueue.add(order.getId(), order.getExpireTime()));

            for (OrderItem item : items) {
                item.setOrderId(order.getId());
                orderItemMapper.insert(item);
            }
            return order;
        } catch (RuntimeException e) {
            // DB 事务会回滚；Redis 已经预扣的名额必须手动还回去
            if (permitTaken) {
                slotCapacityService.release(slot);
            }
            throw e;
        }
    }

    /** 用户主动取消：只能取消自己的、仍待取的订单 */
    @Transactional
    @Override
    public void cancel(Long orderId) {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BizException("请先登录");
        }
        if (orderId == null) {
            throw new BizException("订单不存在");
        }
        // 带 user_id 和 status 的条件更新，既防越权取消，也保证重复点击只成功一次
        if (orderMapper.cancelIfPendingByUser(orderId, userId) == 0) {
            throw new BizException("订单不存在或已不能取消");
        }
        Orders order = orderMapper.selectById(orderId);
        restoreResources(order);
    }

    /** 超时取消：由延迟队列或兜底扫表触发，本身不区分用户 */
    @Transactional
    @Override
    public void cancelTimeout(Long orderId) {
        // 已经被取餐或已取消时返回 0，直接结束，保证幂等
        if (orderMapper.cancelIfPending(orderId) == 0) {
            return;
        }
        Orders order = orderMapper.selectById(orderId);
        restoreResources(order);
    }

    /**
     * 取餐核销：按「档口 + 取餐码」找到当天待取订单。
     * 演示项目没有员工角色表，登录用户即可调用；生产应校验员工身份和档口归属。
     */
    @Transactional
    @Override
    public Orders pickup(PickupDTO pickupDTO) {
        if (pickupDTO == null || pickupDTO.getStallId() == null
                || pickupDTO.getPickupCode() == null || pickupDTO.getPickupCode().isBlank()) {
            throw new BizException("请提供档口和取餐码");
        }
        Orders order = orderMapper.findPendingByPickupCode(
                pickupDTO.getStallId(), pickupDTO.getPickupCode().trim(), LocalDate.now());
        if (order == null) {
            throw new BizException("没有找到待取订单");
        }
        if (order.getExpireTime() != null && LocalDateTime.now().isAfter(order.getExpireTime())) {
            throw new BizException("订单已超时");
        }
        if (orderMapper.pickupIfPending(order.getId()) == 0) {
            throw new BizException("订单已核销或已取消");
        }
        afterCommit(() -> orderDelayQueue.remove(order.getId()));
        order.setStatus(1);
        return order;
    }

    /** 取消成功后回补资源：DB 名额、菜品库存、Redis 余量、延迟任务 */
    private void restoreResources(Orders order) {
        if (order == null) {
            return;
        }
        if (timeSlotMapper.incrCapacity(order.getSlotId()) == 0) {
            log.warn("时段名额回补未生效，可能已经等于总量 slotId={}", order.getSlotId());
        }
        dishService.restoreByOrder(order.getId());
        // Redis 和延迟队列放到事务提交后处理：DB 回滚时不能让缓存先行
        afterCommit(() -> {
            slotCapacityService.syncFromDb(order.getSlotId());
            orderDelayQueue.remove(order.getId());
        });
    }

    // 合并同一菜品
    private static Map<Long, Integer> getQuantityByDish(OrderDTO orderDTO) {
        Map<Long, Integer> quantityByDish = new HashMap<>();
        for (OrderItemDTO item : orderDTO.getOrderItems()) {
            if (item == null || item.getDishId() == null || item.getQuantity() == null
                    || item.getQuantity() <= 0 || item.getQuantity() > 10) {
                throw new BizException("数量不合法");
            }
            int total = quantityByDish.getOrDefault(item.getDishId(), 0) + item.getQuantity();
            if (total > 10) {
                throw new BizException("单个菜品最多 10 份");
            }
            quantityByDish.put(item.getDishId(), total);
        }
        return quantityByDish;
    }

    @Override
    public List<Orders> list(OrderQueryDTO orderQueryDTO) {
        int page = Math.max(orderQueryDTO.getPage(), 1);
        int pageSize = Math.min(Math.max(orderQueryDTO.getPageSize(), 1), 50);
        Page<Orders> ordersPage = new Page<>(page, pageSize);
        Page<Orders> result = orderMapper
                .selectPage(ordersPage,
                        lambdaQuery()
                                .eq(Orders::getUserId, BaseContext.getUserId())         // 当前用户
                                .orderByDesc(Orders::getCreateTime));                   // 时间倒序
        return result.getRecords();
    }

    /** 事务提交后再执行缓存 / 队列操作；没有事务时直接执行 */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
