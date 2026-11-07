package io.liuwenbo.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.common.BaseContext;
import io.liuwenbo.common.BizException;
import io.liuwenbo.dto.OrderDTO;
import io.liuwenbo.dto.OrderItemDTO;
import io.liuwenbo.entity.Dish;
import io.liuwenbo.entity.OrderItem;
import io.liuwenbo.entity.Orders;
import io.liuwenbo.entity.TimeSlot;
import io.liuwenbo.mapper.*;
import io.liuwenbo.service.OrderService;
import io.liuwenbo.util.OrderUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
    private OrderUtil orderUtil;

    @Transactional
    @Override
    public Orders insert(OrderDTO orderDTO) {
        TimeSlot slot = timeSlotMapper.selectById(orderDTO.getSlotId());
        if (slot == null) throw new BizException("时段不存在");
        // 扣减库存
        BigDecimal amount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemDTO i : orderDTO.getOrderItems()) {
            if (i.getQuantity() == null || i.getQuantity() <= 0 || i.getQuantity() > 10) {
                throw new BizException("数量不合法");
            }
            Dish dish = dishMapper.selectById(i.getDishId());
            if (dish == null || !dish.getStallId().equals(orderDTO.getStallId())) {
                throw new BizException("菜品不属于该档口");
            }
            amount = amount.add(dish.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())));

            OrderItem orderItem = new OrderItem();
            orderItem.setDishId(i.getDishId());
            orderItem.setQuantity(i.getQuantity());
            orderItem.setPrice(dish.getPrice());
            items.add(orderItem);

            int rows = dishMapper.decrStock(dish.getId(), i.getQuantity());
            if (rows == 0) throw new BizException("菜品库存不足：" + dish.getName());
        }

        // 保存订单信息
        Orders order = new Orders();

        order.setOrderNo(orderUtil.generateOrderNO());
        order.setUserId(BaseContext.getUserId());
        order.setStallId(orderDTO.getStallId());
        order.setSlotId(orderDTO.getSlotId());
        order.setPickupCode(orderUtil.generatePickupCode(orderDTO.getStallId()));
        order.setStatus(0);
        order.setAmount(amount);
        order.setCreateTime(LocalDateTime.now());
        order.setExpireTime(LocalDateTime.now().plusMinutes(30L));
        orderMapper.insert(order);

        items.forEach(it -> {
            it.setOrderId(order.getId());
            orderItemMapper.insert(it);
        });

        return order;
    }

    @Override
    public List<Orders> list(Long userId) {
        return lambdaQuery()
                .eq(Orders::getUserId,userId)
                .list();

    }
}
