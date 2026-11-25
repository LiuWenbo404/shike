package io.liuwenbo.controller;

import io.liuwenbo.common.Result;
import io.liuwenbo.dto.OrderDTO;
import io.liuwenbo.dto.OrderQueryDTO;
import io.liuwenbo.dto.PickupDTO;
import io.liuwenbo.entity.Orders;
import io.liuwenbo.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Tag(name = "订单接口")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "下单")
    @PostMapping
    public Result<Orders> order(@RequestBody OrderDTO orderDTO) {
        return Result.ok(orderService.insert(orderDTO));
    }

    @Operation(summary = "查询当前用户订单")
    @GetMapping("/list")
    public Result<List<Orders>> list(OrderQueryDTO orderQueryDTO) {
        return Result.ok(orderService.list(orderQueryDTO));
    }

    @Operation(summary = "取消订单（仅本人、仅待取）")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "取餐核销（档口 + 取餐码）")
    @PostMapping("/pickup")
    public Result<Orders> pickup(@RequestBody PickupDTO pickupDTO) {
        return Result.ok(orderService.pickup(pickupDTO));
    }
}
