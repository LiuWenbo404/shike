package io.liuwenbo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class ShikeApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShikeApplication.class, args);
        log.info("server started");
    }
}
