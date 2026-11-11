package io.liuwenbo.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class OrderUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter ORDER_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");


    /**
     * 生成订单号
     * @return
     */
    public String generateOrderNO(){
        return  LocalDateTime.now().format(ORDER_FMT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    /**
     * 取餐码：按「档口 + 日期」在 Redis 里自增，叫号也顺（A001、A002）。
     * Redis 不可用时退化成随机码，保证下单主链路不被缓存拖垮。
     */
    public String generatePickupCode(Long stallId){
        String prefix = String.format("%02d", stallId % 100);
        String seqKey = "pickup:seq:" + stallId + ":" + LocalDate.now();
        try {
            Long seq = stringRedisTemplate.opsForValue().increment(seqKey);
            stringRedisTemplate.expire(seqKey, Duration.ofDays(1));
            return prefix + String.format("%03d", seq % 1000);
        } catch (Exception e) {
            log.warn("Redis 取餐码生成失败，使用随机码兜底 stallId={}", stallId, e);
            return prefix + String.format("%05d", ThreadLocalRandom.current().nextInt(100000));
        }
    }
}
