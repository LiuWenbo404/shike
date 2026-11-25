package io.liuwenbo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.liuwenbo.dto.OrderDTO;
import io.liuwenbo.dto.OrderQueryDTO;
import io.liuwenbo.dto.PickupDTO;
import io.liuwenbo.entity.Orders;

import java.util.List;

public interface OrderService extends IService<Orders> {
    Orders insert(OrderDTO orderDTO);

    void cancel(Long orderId);

    void cancelTimeout(Long orderId);

    Orders pickup(PickupDTO pickupDTO);

    List<Orders> list(OrderQueryDTO orderQueryDTO);
}
