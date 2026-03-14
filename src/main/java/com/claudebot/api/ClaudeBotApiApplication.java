package com.claudebot.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
@org.springframework.scheduling.annotation.EnableScheduling
public class ClaudeBotApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaudeBotApiApplication.class, args);
    }
}
