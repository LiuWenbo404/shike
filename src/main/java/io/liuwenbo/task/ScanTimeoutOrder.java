package io.liuwenbo.task;

import io.liuwenbo.mapper.OrderMapper;
import io.liuwenbo.service.OrderDelayQueue;
import io.liuwenbo.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超时订单处理。
 *
 * <p>主链路：每秒从 Redis ZSet 取出到期任务，交给 OrderService 做条件更新；
 * 兜底链路：每小时扫一次数据库，处理 Redis 丢任务或写入失败留下的过期订单。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScanTimeoutOrder {

    private static final int BATCH_SIZE = 100;

    private final OrderDelayQueue orderDelayQueue;
    private final OrderService orderService;
    private final OrderMapper orderMapper;

    @Scheduled(fixedDelay = 1000)
    public void consumeDelay() {
        for (Long orderId : orderDelayQueue.pollDue(BATCH_SIZE)) {
            cancelQuietly(orderId, "延迟队列");
        }
    }

    /** Redis 不保证持久化，延迟任务可能丢；数据库里 status/expire_time 才是兜底依据 */
    @Scheduled(cron = "0 0 * * * ?")
    public void sweepExpired() {
        List<Long> orderIds = orderMapper.listExpiredPendingIds(200);
        for (Long orderId : orderIds) {
            cancelQuietly(orderId, "兜底扫表");
        }
        if (!orderIds.isEmpty()) {
            log.info("兜底扫表处理过期订单 {} 单", orderIds.size());
        }
    }

    private void cancelQuietly(Long orderId, String source) {
        try {
            orderService.cancelTimeout(orderId);
        } catch (Exception e) {
            // 单笔失败不能影响整批，下一轮扫表还会再处理
            log.error("{}取消超时订单失败 orderId={}", source, orderId, e);
        }
    }
}
