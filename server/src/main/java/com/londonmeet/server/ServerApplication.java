package com.londonmeet.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * LondonMeet backend application.
 */
@Slf4j
@SpringBootApplication(scanBasePackages = "com.londonmeet")
@EntityScan(basePackages = "com.londonmeet.pojo.entity")
@EnableJpaRepositories(basePackages = "com.londonmeet.server.repository")
@EnableTransactionManagement
@EnableCaching
@EnableScheduling
public class ServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
        log.info("LondonMeet backend server started successfully.");
    }
}
