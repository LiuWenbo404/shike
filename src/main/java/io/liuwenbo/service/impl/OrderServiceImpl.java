package io.liuwenbo.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.dto.OrderDTO;
import io.liuwenbo.entity.Orders;
import io.liuwenbo.mapper.OrderMapper;
import io.liuwenbo.service.OrderService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Orders> implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Override
    public void insert(OrderDTO orderDTO) {
        Orders order = new Orders();
        BeanUtils.copyProperties(orderDTO,order);
        String orderNo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + orderDTO.getStallId();
        order.setOrderNo(orderNo);
        order.setCreateTime(LocalDateTime.now());
        save(order);
    }

    @Override
    public List<Orders> list(Long userId) {
        return lambdaQuery()
                .eq(Orders::getUserId,userId)
                .list();

    }
}
