package com.echcherqaoui.jobboard.notificationservice.scheduler;

import com.echcherqaoui.jobboard.notificationservice.service.NotificationOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRetryJob {
    private final NotificationOrchestrator orchestrator;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void retryBatches() {
        log.info("Starting scheduled retry for failed notification batches");

        orchestrator.retryBatches();
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT3M")
    public void retrySingles() {
        log.info("Starting scheduled retry for single failed notifications");

        orchestrator.retrySingles();
    }
}