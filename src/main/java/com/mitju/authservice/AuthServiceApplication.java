package com.mitju.authservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Mitju Auth Service — entry point.
 *
 * @EnableScheduling  — activates @Scheduled jobs (token cleanup, account unlock).
 * @ConfigurationPropertiesScan — picks up JwtProperties and any future @ConfigurationProperties.
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(AuthServiceApplication.class);
        app.run(args);
        log.info("╔══════════════════════════════════════════╗");
        log.info("║   Mitju Auth Service started             ║");
        log.info("║   Swagger UI → /swagger-ui.html          ║");
        log.info("║   Health    → /actuator/health           ║");
        log.info("╚══════════════════════════════════════════╝");
    }
}
