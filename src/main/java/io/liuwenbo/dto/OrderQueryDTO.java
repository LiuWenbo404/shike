package io.liuwenbo.dto;

import lombok.Data;

@Data
public class OrderQueryDTO {
    private int page = 1;
    private int pageSize = 10;
}
