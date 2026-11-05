package io.liuwenbo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.liuwenbo.entity.TimeSlot;

import java.time.LocalDate;
import java.util.List;

public interface TimeSlotService extends IService<TimeSlot> {
    List<TimeSlot> getByStallIdAndDate(Long stallId, LocalDate slotDate);
}
