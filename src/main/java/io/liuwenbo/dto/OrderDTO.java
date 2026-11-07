package io.liuwenbo.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderDTO {
    private Long stallId;   // 档口id
    private Long slotId;    // 时段id
    private List<OrderItemDTO> orderItems; // 下单菜品
}
