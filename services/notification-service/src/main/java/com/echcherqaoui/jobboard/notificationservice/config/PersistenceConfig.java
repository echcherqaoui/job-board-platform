package com.echcherqaoui.jobboard.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.time.OffsetDateTime;
import java.util.Optional;

import static java.time.ZoneOffset.UTC;

@Configuration
@EnableMongoAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class PersistenceConfig {
    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(UTC));
    }
}