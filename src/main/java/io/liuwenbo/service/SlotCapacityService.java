package io.liuwenbo.service;

import io.liuwenbo.entity.TimeSlot;

/**
 * 时段名额的预扣服务。
 *
 * <p>Redis 只做前置拦截，数据库的 capacity 才是最终真相：
 * 预扣成功不等于下单成功，落单失败必须回补；Redis 不可用时降级为纯 DB 校验。</p>
 */
public interface SlotCapacityService {

    /** 预扣结果，三种状态必须区分清楚，否则会出现"占位没还"或"多还一个" */
    enum AcquireResult {
        /** 确实在 Redis 里减了 1，调用方无论成败都要负责回补 */
        TAKEN,
        /** Redis 判定名额已满，没有扣减 */
        FULL,
        /** Redis 不可用或功能关闭，没有占位，交给 DB 条件更新兜底 */
        DEGRADED
    }

    /**
     * 尝试占用一个名额。
     *
     * @param slot 已校验过的时段（必须尚未结束）
     * @return 预扣结果，调用方按 {@link AcquireResult} 决定是否继续下单
     */
    AcquireResult tryAcquire(TimeSlot slot);

    /**
     * 回补一个名额。只能与 {@link AcquireResult#TAKEN} 配对调用。
     */
    void release(TimeSlot slot);

    /**
     * 删除缓存，让下一个请求按数据库的真实余量重新预热。
     * 用于发现 Redis 与 DB 不一致时，或压测/重置数据后。
     */
    void invalidate(Long slotId);

    /**
     * 以数据库为准重建缓存，供定时对账任务调用（必须在事务外调用）。
     */
    void syncFromDb(Long slotId);
}
