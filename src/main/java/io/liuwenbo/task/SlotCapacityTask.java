package io.liuwenbo.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.liuwenbo.entity.TimeSlot;
import io.liuwenbo.mapper.TimeSlotMapper;
import io.liuwenbo.service.SlotCapacityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 每天凌晨 3 点用 DB 的余量重建 Redis 名额，防止 Redis 重启、被清空或补偿失败导致两边不一致。
 * 选在凌晨是因为营业时段是 10:00-13:00，不会和下单并发。
 */
@Slf4j
@Component
public class SlotCapacityTask {

    @Autowired
    private TimeSlotMapper timeSlotMapper;

    @Autowired
    private SlotCapacityService slotCapacityService;

    @Scheduled(cron = "0 0 3 * * ?")
    public void reconcile() {
        LocalDate today = LocalDate.now();
        List<TimeSlot> slots = timeSlotMapper.selectList(
                Wrappers.<TimeSlot>lambdaQuery()
                        .ge(TimeSlot::getSlotDate, today)
                        .le(TimeSlot::getSlotDate, today.plusDays(1)));
        for (TimeSlot slot : slots) {
            slotCapacityService.syncFromDb(slot.getId());
        }
        log.info("时段名额对账完成，共 {} 个时段", slots.size());
    }
}
