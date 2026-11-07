package io.liuwenbo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Slf4j
@SpringBootApplication
@EnableTransactionManagement
public class ShikeApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShikeApplication.class, args);
        log.info("server started");
    }
}
