package com.echcherqaoui.jobboard.jobservice.scheduler;

import com.echcherqaoui.jobboard.jobservice.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs every hour and closes any job whose expiresAt has passed.
 * Publishes JOB_EXPIRED events so Search Service de-indexes them
 * and Notification Service can alert the company.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobExpiryScheduler {

    private final JobService jobService;

    @Scheduled(cron = "0 0 * * * *")  // top of every hour
    public void closeExpiredJobs() {
        log.debug("Running job expiry check...");
        jobService.expireJobs();
    }
}
