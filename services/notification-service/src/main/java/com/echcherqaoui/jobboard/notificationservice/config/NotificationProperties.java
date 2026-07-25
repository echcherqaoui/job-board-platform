package com.echcherqaoui.jobboard.notificationservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(Mail mail,
                                     String frontendUrl,
                                     Retry retry) {

    public record Mail(String from, String fromName) {
    }

    public record Retry(int maxAttempts, long delayMs) {
    }
}