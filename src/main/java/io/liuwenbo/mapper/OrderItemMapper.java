package io.liuwenbo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.liuwenbo.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
