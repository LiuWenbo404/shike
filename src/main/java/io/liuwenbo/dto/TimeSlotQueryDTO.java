package io.liuwenbo.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class TimeSlotQueryDTO {
    private Long stallId;       // 档口id
    private LocalDate slotDate; // 日期
}
