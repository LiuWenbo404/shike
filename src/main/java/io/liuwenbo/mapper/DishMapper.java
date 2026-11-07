package io.liuwenbo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.liuwenbo.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {
    @Update("UPDATE dish SET daily_stock = daily_stock - #{quantity} WHERE id = #{id} AND status = 1 AND daily_stock >= #{quantity}")
    int decrStock(Long id, Integer quantity);
}
