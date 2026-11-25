package io.liuwenbo.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.entity.Dish;
import io.liuwenbo.entity.OrderItem;
import io.liuwenbo.entity.Stall;
import io.liuwenbo.mapper.DishMapper;
import io.liuwenbo.mapper.OrderItemMapper;
import io.liuwenbo.service.DishService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {
    private final OrderItemMapper orderItemMapper;
    private final DishMapper dishMapper;

    public DishServiceImpl(OrderItemMapper orderItemMapper, DishMapper dishMapper) {
        this.orderItemMapper = orderItemMapper;
        this.dishMapper = dishMapper;
    }

    /**
     * 根据档口id查询菜品
     * @param stallId
     * @return
     */
    @Override
    public List<Dish> listByStallId(Long stallId) {
        return lambdaQuery()
                .eq(Dish::getStallId, stallId)
                .eq(Dish::getStatus,1)
                .list();
    }


    @Override
    public void restoreByOrder(Long orderId) {
        List<OrderItem> items = orderItemMapper.listByOrderId(orderId);
        for (OrderItem item : items) {
            Long dishId = item.getDishId();
            Integer quantity = item.getQuantity();
            dishMapper.incrStock(dishId,quantity);
        }
    }
}
