package io.liuwenbo.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.liuwenbo.entity.TimeSlot;
import io.liuwenbo.mapper.TimeSlotMapper;
import io.liuwenbo.service.TimeSlotService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class TimeSlotServiceImpl extends ServiceImpl<TimeSlotMapper, TimeSlot> implements TimeSlotService {

    @Override
    public List<TimeSlot> getByStallIdAndDate(Long stallId, LocalDate slotDate) {
        return lambdaQuery()
                .eq(TimeSlot::getStallId, stallId)
                .eq(TimeSlot::getSlotDate, slotDate)
                .list();
    }
}
