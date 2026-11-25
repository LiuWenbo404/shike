package io.liuwenbo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.liuwenbo.entity.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {
    @Update("UPDATE dish SET daily_stock = daily_stock - #{quantity} WHERE id = #{id} AND status = 1 AND daily_stock >= #{quantity}")
    int decrStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    /** 回补库存不要带 status 条件：菜品中途下架时也要把份数还回去 */
    @Update("UPDATE dish SET daily_stock = daily_stock + #{quantity} WHERE id = #{id}")
    void incrStock(@Param("id") Long id, @Param("quantity") Integer quantity);
}
