package io.liuwenbo.dto;

import lombok.Data;

@Data
public class OrderItemDTO {
    private Long dishId;
    private Integer quantity;
}
