package io.liuwenbo.controller;

import io.liuwenbo.common.Result;
import io.liuwenbo.entity.Stall;
import io.liuwenbo.service.StallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@Tag(name = "档口接口")
@RestController
@RequestMapping("/api/stall/")
public class StallController {

    @Autowired
    private StallService stallService;

    @Operation(summary = "档口列表")
    @GetMapping("/list")
    public Result<List<Stall>> list(){
        List<Stall> list = stallService.list();
        return Result.ok(list);
    }

    @Operation(summary = "档口详情")
    @GetMapping("/{id}")
    public Result<Stall> query(@PathVariable Long id){
        Stall stall = stallService.getById(id);
        return Result.ok(stall);
    }

}
