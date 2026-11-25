package io.liuwenbo.service.impl;

import io.liuwenbo.entity.TimeSlot;
import io.liuwenbo.mapper.TimeSlotMapper;
import io.liuwenbo.service.SlotCapacityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * 时段名额预扣实现。
 *
 * <p>扣减顺序：Redis 原子预扣 → DB 条件更新兜底 → 落单。任何一步失败都要保证
 * Redis 与 DB 的余量回到一致状态；无法判断时以 DB 为准重建缓存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotCapacityServiceImpl implements SlotCapacityService {

    private static final String KEY_PREFIX = "slot:cap:";

    /** Lua 返回值：key 不存在，需要先预热 */
    private static final long NOT_WARMED = -1L;

    /** Lua 返回值：名额已满，未扣减 */
    private static final long SOLD_OUT = -2L;

    private static final DefaultRedisScript<Long> TRY_ACQUIRE_SCRIPT = buildScript();

    private final StringRedisTemplate stringRedisTemplate;

    private final TimeSlotMapper timeSlotMapper;

    /** 压测对比用：关掉后所有请求都走 DB 条件更新，方便对比 Redis 前后的差异 */
    @Value("${shike.slot.redis-enabled:true}")
    private boolean redisEnabled;

    @Override
    public AcquireResult tryAcquire(TimeSlot slot) {
        if (!redisEnabled) {
            return AcquireResult.DEGRADED;
        }
        String key = key(slot.getId());
        try {
            Long left = execute(key);
            if (left == null) {
                return AcquireResult.DEGRADED;
            }
            if (left == NOT_WARMED) {
                // 第一次访问这个时段：按 DB 当前余量预热，再重试一次
                warmUp(slot);
                left = execute(key);
            }
            if (left == null || left == NOT_WARMED) {
                // 预热失败，交给 DB 条件更新
                return AcquireResult.DEGRADED;
            }
            if (left == SOLD_OUT) {
                return AcquireResult.FULL;
            }
            // >= 0 都算成功，0 表示正好拿走最后一个名额
            return AcquireResult.TAKEN;
        } catch (Exception e) {
            log.warn("Redis 名额预扣失败，降级为数据库校验 slotId={}", slot.getId(), e);
            return AcquireResult.DEGRADED;
        }
    }

    @Override
    public void release(TimeSlot slot) {
        if (!redisEnabled) {
            return;
        }
        String key = key(slot.getId());
        try {
            stringRedisTemplate.opsForValue().increment(key);
            stringRedisTemplate.expire(key, ttl(slot));
        } catch (Exception e) {
            log.error("Redis 名额回补失败，等待对账任务修复 slotId={}", slot.getId(), e);
        }
    }

    @Override
    public void invalidate(Long slotId) {
        if (!redisEnabled) {
            return;
        }
        try {
            stringRedisTemplate.delete(key(slotId));
        } catch (Exception e) {
            log.warn("Redis 名额缓存删除失败 slotId={}", slotId, e);
        }
    }

    @Override
    public void syncFromDb(Long slotId) {
        if (!redisEnabled) {
            return;
        }
        try {
            TimeSlot latest = timeSlotMapper.selectById(slotId);
            if (latest == null) {
                return;
            }
            stringRedisTemplate.opsForValue()
                    .set(key(slotId), String.valueOf(latest.getCapacity()), ttl(latest));
        } catch (Exception e) {
            log.warn("Redis 名额重建失败 slotId={}", slotId, e);
        }
    }

    /**
     * 预热时用的是下单请求开始前读到的余量，理论上可能比 DB 最新值大一点；
     * 真正的防超卖由 {@code timeSlotMapper.decrCapacity} 的条件更新保证，这里只做加速。
     */
    private void warmUp(TimeSlot slot) {
        try {
            stringRedisTemplate.opsForValue().setIfAbsent(
                    key(slot.getId()), String.valueOf(slot.getCapacity()), ttl(slot));
        } catch (Exception e) {
            log.warn("Redis 名额预热失败 slotId={}", slot.getId(), e);
        }
    }

    private Long execute(String key) {
        return stringRedisTemplate.execute(TRY_ACQUIRE_SCRIPT, Collections.singletonList(key));
    }

    /** 缓存保留到时段结束后一天，保证当天的重复请求都能命中，第二天自动清理 */
    private Duration ttl(TimeSlot slot) {
        LocalDateTime slotEnd = LocalDateTime.of(slot.getSlotDate(), slot.getEndTime());
        Duration ttl = Duration.between(LocalDateTime.now(), slotEnd.plusDays(1));
        return ttl.isNegative() || ttl.isZero() ? Duration.ofHours(1) : ttl;
    }

    private String key(Long slotId) {
        return KEY_PREFIX + slotId;
    }

    private static DefaultRedisScript<Long> buildScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/slot_capacity.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
