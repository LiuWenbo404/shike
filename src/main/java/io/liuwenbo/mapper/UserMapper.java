package io.liuwenbo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.liuwenbo.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
