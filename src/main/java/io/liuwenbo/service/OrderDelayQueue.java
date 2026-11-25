package io.liuwenbo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis ZSet 的轻量延迟队列。
 *
 * <p>下单成功后写入，score 是取餐截止时间的时间戳；定时任务每秒取一次到期订单。
 * 写失败只记日志，不阻断下单——订单已经在数据库里，超时取消还有每小时扫表兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayQueue {

    private static final String KEY = "order:delay";

    private final StringRedisTemplate stringRedisTemplate;

    public void add(Long orderId, LocalDateTime expireAt) {
        try {
            long score = expireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            stringRedisTemplate.opsForZSet().add(KEY, String.valueOf(orderId), score);
        } catch (Exception e) {
            log.warn("订单延迟任务写入失败，等待扫表兜底 orderId={}", orderId, e);
        }
    }

    public void remove(Long orderId) {
        try {
            stringRedisTemplate.opsForZSet().remove(KEY, String.valueOf(orderId));
        } catch (Exception e) {
            log.warn("订单延迟任务取消失败 orderId={}", orderId, e);
        }
    }

    /**
     * 取出并认领到期任务。
     *
     * <p>多实例同时扫到同一个 orderId 时，只有 ZREM 返回 1 的实例能拿到它，
     * 这是最轻量的分布式锁；后续数据库的条件更新再做一层幂等。</p>
     */
    public List<Long> pollDue(int limit) {
        List<Long> claimed = new ArrayList<>();
        try {
            Set<String> due = stringRedisTemplate.opsForZSet()
                    .rangeByScore(KEY, 0, System.currentTimeMillis(), 0, limit);
            if (due == null || due.isEmpty()) {
                return claimed;
            }
            for (String value : due) {
                Long removed = stringRedisTemplate.opsForZSet().remove(KEY, value);
                if (removed != null && removed > 0) {
                    claimed.add(Long.valueOf(value));
                }
            }
        } catch (Exception e) {
            log.warn("订单延迟队列读取失败，等待下一轮", e);
        }
        return claimed;
    }
}
