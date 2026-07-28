package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.grpc.JobSeekerProfileClient;
import com.echcherqaoui.jobboard.notificationservice.service.BulkEmailService;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationStateService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENDING;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchNotificationSenderImplTest {

    private NotificationStateService stateService;
    private BulkEmailService bulkEmailService;
    private JobSeekerProfileClient jobSeekerProfileClient;
    private Executor emailTaskExecutor;
    private BatchNotificationSenderImpl sender;

    @BeforeEach
    void setUp() {
        stateService = mock(NotificationStateService.class);
        bulkEmailService = mock(BulkEmailService.class);
        jobSeekerProfileClient = mock(JobSeekerProfileClient.class);
        emailTaskExecutor = mock(Executor.class);
        sender = new BatchNotificationSenderImpl(stateService, bulkEmailService, jobSeekerProfileClient, emailTaskExecutor);
    }

    private Notification buildNotification(String id, String recipientId, int attempts) {
        return new Notification()
              .setId(id)
              .setRecipientId(recipientId)
              .setAttempts(attempts)
              .setStatus(PENDING);
    }

    @Test
    void executeBatchSend_submitsTaskToExecutor() {
        List<Notification> notifications = List.of(buildNotification("n1", "u1", 0));

        sender.executeBatchSend(notifications, "job-1");

        verify(emailTaskExecutor).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
    }

    @Test
    void attemptBatchSend_marksSending_andIncrementsAttempts_beforeGrpcCall() {
        Notification n1 = buildNotification("n1", "u1", 0);
        when(jobSeekerProfileClient.getEmailsByUserIds(anyList())).thenReturn(Map.of());

        sender.attemptBatchSend(List.of(n1), "job-1");

        verify(stateService).bulkMarkStatus(List.of("n1"), SENDING, null);
        org.assertj.core.api.Assertions.assertThat(n1.getAttempts()).isEqualTo(1);
    }

    @Test
    void attemptBatchSend_callsHandleBatchFailure_whenGrpcReturnsEmptyMap() throws MessagingException {
        Notification n1 = buildNotification("n1", "u1", 0);
        when(jobSeekerProfileClient.getEmailsByUserIds(anyList())).thenReturn(Map.of());

        sender.attemptBatchSend(List.of(n1), "job-1");

        verify(stateService).handleBatchFailure(List.of(n1), "gRPC email resolution returned empty map");
        verify(bulkEmailService, never()).sendBulkCancellationEmails(anyList(), org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void attemptBatchSend_neverCallsBulkUpdateSuccess_becauseStatusIsNeverMutatedToSent() throws MessagingException {
        Notification n1 = buildNotification("n1", "u1", 0);
        when(jobSeekerProfileClient.getEmailsByUserIds(anyList()))
              .thenReturn(Map.of("u1", "u1@example.com"));
        when(bulkEmailService.sendBulkCancellationEmails(anyList(), org.mockito.ArgumentMatchers.anyMap()))
              .thenReturn(List.of()); // no failures reported

        sender.attemptBatchSend(List.of(n1), "job-1");

        verify(stateService, never()).bulkUpdateSuccess(anyList());
    }

    @Test
    void attemptBatchSend_callsHandleBatchFailure_forNotificationsReturnedAsFailed() throws MessagingException {
        Notification n1 = buildNotification("n1", "u1", 0);
        Notification n2 = buildNotification("n2", "u2", 0);
        when(jobSeekerProfileClient.getEmailsByUserIds(anyList()))
              .thenReturn(Map.of("u1", "u1@example.com", "u2", "u2@example.com"));
        when(bulkEmailService.sendBulkCancellationEmails(anyList(), org.mockito.ArgumentMatchers.anyMap()))
              .thenReturn(List.of(n2)); // n2 failed to send

        sender.attemptBatchSend(List.of(n1, n2), "job-1");

        verify(stateService).handleBatchFailure(List.of(n2), "SMTP pipeline delivery disruption");
    }

    @Test
    void attemptBatchSend_catchesException_andMarksNonSentAsFailed() {
        Notification n1 = buildNotification("n1", "u1", 0);
        when(jobSeekerProfileClient.getEmailsByUserIds(anyList()))
              .thenThrow(new RuntimeException("gRPC channel down"));

        sender.attemptBatchSend(List.of(n1), "job-1");

        verify(stateService).handleBatchFailure(List.of(n1), "gRPC channel down");
    }
}