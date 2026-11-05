package io.liuwenbo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.liuwenbo.dto.OrderDTO;
import io.liuwenbo.entity.Orders;

import java.util.List;

public interface OrderService extends IService<Orders> {
    void insert(OrderDTO orderDTO);

    List<Orders> list(Long userId);
}
