package io.liuwenbo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.liuwenbo.entity.Dish;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {
}
