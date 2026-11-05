package io.liuwenbo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.liuwenbo.entity.Dish;

import java.util.List;

public interface DishService extends IService<Dish> {
    List<Dish> listByStallId(Long stallId);
}
