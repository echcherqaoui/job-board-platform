package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.echcherqaoui.jobboard.notificationservice.grpc.CompanyProfileClient;
import com.echcherqaoui.jobboard.notificationservice.grpc.JobSeekerProfileClient;
import com.echcherqaoui.jobboard.notificationservice.service.EmailService;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationStateService;
import com.echcherqaoui.jobboard.notificationservice.service.SingleNotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SingleNotificationSenderImpl implements SingleNotificationSender {

    private final NotificationStateService stateService;
    private final EmailService emailService;
    private final CompanyProfileClient companyProfileClient;
    private final JobSeekerProfileClient jobSeekerProfileClient;
    private final Executor emailTaskExecutor;

    private String resolveEmail(@NonNull NotificationType type, String recipientId) {
        return switch (type) {
            case APPLICATION_RECEIVED -> companyProfileClient.getRecruiterEmail(recipientId);
            case APPLICATION_STATUS_UPDATED -> jobSeekerProfileClient.getJobSeekerEmail(recipientId);
            case WELCOME -> throw new IllegalStateException("WELCOME notifications must carry recipientEmail already");

            default -> throw new IllegalStateException("No email resolver for type: " + type);
        };
    }

    @Override
    public void executeSend(String notificationId) {
        try {
            emailTaskExecutor.execute(() -> attemptSend(notificationId));
        } catch (RejectedExecutionException ex) {
            log.warn("Executor saturated, notification {} stays PENDING for retry job", notificationId);
        }
    }

    @Override
    public void attemptSend(String notificationId) {
        Notification notification = stateService.claim(notificationId);

        if (notification == null) return;

        try {
            if (notification.getRecipientEmail() == null) {
                String email = resolveEmail(notification.getType(), notification.getRecipientId());
                notification.setRecipientEmail(email);
            }

            emailService.sendHtml(notification);

            stateService.markSent(notificationId, notification.getRecipientEmail());
        } catch (Exception ex) {
            log.error("Send failed for notification {}:{}", notification.getId(), notification.getType(), ex);
            stateService.markFailed(notificationId, ex.getMessage(), notification.getRecipientEmail());
        }
    }
}