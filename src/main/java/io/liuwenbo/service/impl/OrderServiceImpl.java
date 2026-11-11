package io.liuwenbo.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.common.BaseContext;
import io.liuwenbo.common.BizException;
import io.liuwenbo.dto.OrderDTO;
import io.liuwenbo.dto.OrderItemDTO;
import io.liuwenbo.dto.OrderQueryDTO;
import io.liuwenbo.entity.Dish;
import io.liuwenbo.entity.OrderItem;
import io.liuwenbo.entity.Orders;
import io.liuwenbo.entity.Stall;
import io.liuwenbo.entity.TimeSlot;
import io.liuwenbo.mapper.*;
import io.liuwenbo.service.OrderService;
import io.liuwenbo.service.SlotCapacityService;
import io.liuwenbo.util.OrderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private StallMapper stallMapper;

    @Autowired
    private OrderUtil orderUtil;

    @Autowired
    private SlotCapacityService slotCapacityService;

    /**
     * 下单主链路：
     * 校验 -> Redis 预扣名额 -> DB 条件扣名额 -> 扣菜品库存 -> 落库。
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
                // Redis 放行但 DB 已经没有名额：两边漂移了。DB 才是真相，删掉缓存让下次按 DB 重建。
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

    @Override
    public List<Orders> list(OrderQueryDTO orderQueryDTO) {
        Page<Orders> ordersPage = new Page<>(orderQueryDTO.getPage(), orderQueryDTO.getPageSize());
        Page<Orders> result = orderMapper
                .selectPage(ordersPage,
                        lambdaQuery()
                                .eq(Orders::getUserId, BaseContext.getUserId())         // 当前用户
                                .orderByDesc(Orders::getCreateTime));                   // 时间倒序
        return result.getRecords();
    }
}
