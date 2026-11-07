package io.liuwenbo.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class OrderUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final DateTimeFormatter ORDER_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");


    public String generateOrderNO(){
        return  LocalDateTime.now().format(ORDER_FMT)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    public String generatePickupCode(Long stallId){
        String seqKey = "pickup:seq:" + stallId + ":" + LocalDate.now();
        Long seq = stringRedisTemplate.opsForValue().increment(seqKey);
        stringRedisTemplate.expire(seqKey, Duration.ofDays(1));
        return String.format("%02d", stallId % 100) + String.format("%03d", seq % 1000);
    }
}
