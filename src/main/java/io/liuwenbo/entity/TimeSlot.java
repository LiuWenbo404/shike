package io.liuwenbo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("time_slot")
public class TimeSlot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long stallId;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer capacity;
    private Integer totalCapacity;
}
