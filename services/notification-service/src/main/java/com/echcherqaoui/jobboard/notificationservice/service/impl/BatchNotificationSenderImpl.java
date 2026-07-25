package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.grpc.JobSeekerProfileClient;
import com.echcherqaoui.jobboard.notificationservice.service.BatchNotificationSender;
import com.echcherqaoui.jobboard.notificationservice.service.BulkEmailService;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchNotificationSenderImpl implements BatchNotificationSender {

    private final NotificationStateService stateService;
    private final BulkEmailService bulkEmailService;
    private final JobSeekerProfileClient jobSeekerProfileClient;
    private final Executor emailTaskExecutor;

    @Override
    public void executeBatchSend(List<Notification> notifications, String jobId) {
        try {
            emailTaskExecutor.execute(() -> attemptBatchSend(notifications, jobId));
        } catch (RejectedExecutionException ex) {
            log.error("Executor saturated! Mass notification batch dropped for Job ID: {}", jobId);
        }
    }

    @Override
    public void attemptBatchSend(@NonNull List<Notification> notifications, String jobId) {
        List<String> notificationIds = notifications.stream().map(Notification::getId).toList();

        // Mark batch as active in DB and increment retry counters in memory
        stateService.bulkMarkStatus(notificationIds, SENDING, null);
        notifications.forEach(n -> n.setAttempts(n.getAttempts() + 1));

        List<String> recipientIds = notifications.stream()
              .map(Notification::getRecipientId)
              .toList();

        try {
            //One single batch gRPC trip to get all emails at once
            Map<String, String> idToEmailMap = jobSeekerProfileClient.getEmailsByUserIds(recipientIds);

            if (idToEmailMap.isEmpty()) {
                stateService.handleBatchFailure(notifications, "gRPC email resolution returned empty map");
                return;
            }

            // Stream all emails sequentially over a single open SMTP socket connection pipe
            List<Notification> failedNotifications = bulkEmailService.sendBulkCancellationEmails(notifications, idToEmailMap);

            // Extract what succeeded based on memory changes and commit to database
            List<Notification> successfulNotifications = notifications.stream()
                  .filter(n -> n.getStatus() == SENT)
                  .toList();

            if (!successfulNotifications.isEmpty())
                stateService.bulkUpdateSuccess(successfulNotifications);

            if (!failedNotifications.isEmpty())
                stateService.handleBatchFailure(failedNotifications, "SMTP pipeline delivery disruption");
        } catch (Exception e) {
            log.error("Critical failure during batch notification processing for Job: {}", jobId, e);

            List<Notification> nonSent = notifications.stream()
                  .filter(n -> n.getStatus() != SENT)
                  .toList();

            stateService.handleBatchFailure(nonSent, e.getMessage());
        }
    }
}