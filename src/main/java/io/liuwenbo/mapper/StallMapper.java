package io.liuwenbo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.liuwenbo.entity.Stall;
import io.liuwenbo.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StallMapper extends BaseMapper<Stall> {
}
