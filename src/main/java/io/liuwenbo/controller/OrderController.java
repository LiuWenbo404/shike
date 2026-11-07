package io.liuwenbo.controller;

import io.liuwenbo.common.BaseContext;
import io.liuwenbo.common.Result;
import io.liuwenbo.dto.OrderDTO;
import io.liuwenbo.entity.Orders;
import io.liuwenbo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "订单接口")
@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Operation(summary = "下单接口")
    @PostMapping
    public Result<Orders> order(@RequestBody OrderDTO orderDTO){
        Orders orders = orderService.insert(orderDTO);
        return Result.ok(orders);
    }

    @Operation(summary = "查询当前用户订单")
    @GetMapping("/list")
    public Result<List<Orders>> list(){
        log.info("查询当前用户订单");
        Long userId = BaseContext.getUserId();
        List<Orders> list = orderService.list(userId);
        return Result.ok(list);
    }
}
