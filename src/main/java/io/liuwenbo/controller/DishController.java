package io.liuwenbo.controller;

import io.liuwenbo.common.Result;
import io.liuwenbo.entity.Dish;
import io.liuwenbo.service.DishService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "菜品接口")
@RestController
@RequestMapping("/api/dish")
public class DishController {

    @Autowired
    private DishService dishService;


    @Operation(summary = "查某档口下所有菜品")
    @GetMapping("/{stallId}/dishes")
    public Result<List<Dish>> list(@PathVariable Long stallId){
        List<Dish> dishes = dishService.listByStallId(stallId);
        return Result.ok(dishes);
    }

    @Operation(summary = "菜品详情")
    @GetMapping("/{id}")
    public Result<Dish> info(@PathVariable Long id){
        Dish dish = dishService.getById(id);
        return Result.ok(dish);
    }
}
