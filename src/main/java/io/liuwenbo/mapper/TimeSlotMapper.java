package io.liuwenbo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.liuwenbo.entity.TimeSlot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TimeSlotMapper extends BaseMapper<TimeSlot > {

    /**
     * 一个订单只占一个名额，所以固定减 1。
     * 条件更新是最后一道防线：并发下只有一个请求能把最后 1 个名额扣成功。
     */
    @Update("UPDATE time_slot SET capacity = capacity - 1 WHERE id = #{id} AND capacity >= 1")
    int decrCapacity(@Param("id") Long id);
}
