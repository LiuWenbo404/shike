package io.liuwenbo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.liuwenbo.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Orders> {

    /** 超时取消：只有仍是待取（0）的订单才会被改成已取消（2） */
    @Update("UPDATE orders SET status = 2 WHERE id = #{orderId} AND status = 0")
    int cancelIfPending(@Param("orderId") Long orderId);

    /** 用户取消：多一个 user_id 条件，避免越权取消别人的订单 */
    @Update("UPDATE orders SET status = 2 WHERE id = #{orderId} AND user_id = #{userId} AND status = 0")
    int cancelIfPendingByUser(@Param("orderId") Long orderId, @Param("userId") Long userId);

    /** 取餐核销：只有待取订单能变成已取（1） */
    @Update("UPDATE orders SET status = 1 WHERE id = #{orderId} AND status = 0")
    int pickupIfPending(@Param("orderId") Long orderId);

    /** 按档口 + 取餐码找当天待取订单；取餐码只在当天有意义 */
    @Select("""
            SELECT o.*
            FROM orders o
            JOIN time_slot t ON o.slot_id = t.id
            WHERE o.stall_id = #{stallId}
              AND o.pickup_code = #{pickupCode}
              AND o.status = 0
              AND t.slot_date = #{slotDate}
            ORDER BY o.create_time DESC
            LIMIT 1
            """)
    Orders findPendingByPickupCode(@Param("stallId") Long stallId,
                                   @Param("pickupCode") String pickupCode,
                                   @Param("slotDate") LocalDate slotDate);

    /** 兜底扫表：捞出已经过期但还没有被处理的待取订单 */
    @Select("""
            SELECT id
            FROM orders
            WHERE status = 0 AND expire_time < NOW()
            ORDER BY expire_time
            LIMIT #{limit}
            """)
    List<Long> listExpiredPendingIds(@Param("limit") int limit);
}
