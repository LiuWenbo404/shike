package io.liuwenbo.controller;

import io.liuwenbo.common.Result;
import io.liuwenbo.dto.TimeSlotQueryDTO;
import io.liuwenbo.entity.TimeSlot;
import io.liuwenbo.service.TimeSlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Tag(name = "时间段接口")
@RestController
@RequestMapping("/api/time_slot")
public class TimeSlotController {

    @Autowired
    private TimeSlotService timeSlotService;

    @Operation(summary = "根据档口id和日期查时间段和余量")
    @PostMapping("/list")
    public Result<List<TimeSlot>> list(@RequestBody TimeSlotQueryDTO dto){
        Long stallId = dto.getStallId();
        LocalDate slotData = dto.getSlotDate();
        List<TimeSlot> timeSlots  = timeSlotService.getByStallIdAndDate(stallId,slotData);
        return Result.ok(timeSlots);
    }
}
