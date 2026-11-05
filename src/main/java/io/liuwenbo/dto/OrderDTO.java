package io.liuwenbo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderDTO {
    private Long id;
    private Long userId;
    private Long stallId;
    private Long slotId;
    private String pickupCode;
    private String status;
    private BigDecimal amount;
    private LocalDateTime expireTime;
}
