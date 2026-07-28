package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.ExecutableUpdateOperation;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.CriteriaDefinition;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.ArrayList;
import java.util.List;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.FAILED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationStateServiceImplTest {

    private MongoTemplate mongoTemplate;
    private NotificationProperties.Retry retry;
    private NotificationStateServiceImpl service;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        NotificationProperties notificationProperties = mock(NotificationProperties.class);
        retry = mock(NotificationProperties.Retry.class);
        when(notificationProperties.retry()).thenReturn(retry);
        service = new NotificationStateServiceImpl(mongoTemplate, notificationProperties);
    }

    private Notification buildNotification(String id, int attempts) {
        return new Notification()
              .setId(id)
              .setAttempts(attempts)
              .setStatus(PENDING);
    }

    @Test
    void claim_callsFindAndModify_withPendingQuery_andSendingUpdate_returnNewTrue() {
        Notification expected = buildNotification("n1", 1).setStatus(SENDING);
        when(mongoTemplate.findAndModify(
              any(Query.class),
              any(Update.class),
              any(FindAndModifyOptions.class),
              eq(Notification.class)
        )).thenReturn(expected);

        Notification result = service.claim("n1");

        assertThat(result).isEqualTo(expected);

        verify(mongoTemplate).findAndModify(
              any(Query.class),
              any(Update.class),
              argThat(FindAndModifyOptions::isReturnNew),
              eq(Notification.class)
        );
    }

    @Test
    void claim_returnsNull_whenNoMatchingPendingDocument() {
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(Notification.class)))
              .thenReturn(null);

        Notification result = service.claim("missing");

        assertThat(result).isNull();
    }

    @SuppressWarnings("unchecked")
    private ExecutableUpdateOperation.ExecutableUpdate<Notification> mockUpdateChain() {
        ExecutableUpdateOperation.ExecutableUpdate<Notification> executableUpdate = mock(ExecutableUpdateOperation.ExecutableUpdate.class);
        ExecutableUpdateOperation.UpdateWithUpdate<Notification> updateWithUpdate = mock(ExecutableUpdateOperation.UpdateWithUpdate.class);
        ExecutableUpdateOperation.TerminatingUpdate<Notification> terminatingUpdate = mock(ExecutableUpdateOperation.TerminatingUpdate.class);

        when(mongoTemplate.update(Notification.class)).thenReturn(executableUpdate);
        when(executableUpdate.matching(any(CriteriaDefinition.class))).thenReturn(updateWithUpdate);
        when(updateWithUpdate.apply(any(Update.class))).thenReturn(terminatingUpdate);

        return executableUpdate;
    }

    @Test
    void markSent_setsSentStatus_unsetsLastError_setsRecipientEmail_whenProvided() {
        ExecutableUpdateOperation.ExecutableUpdate<Notification> executableUpdate = mockUpdateChain();

        service.markSent("n1", "user@example.com");

        verify(mongoTemplate).update(Notification.class);
        verify(executableUpdate).matching(any(CriteriaDefinition.class));
    }

    @Test
    void markFailed_setsFailed_whenAttemptsReachMaxAttempts() {
        when(retry.maxAttempts()).thenReturn(3);
        Notification existing = buildNotification("n1", 3);
        when(mongoTemplate.findById("n1", Notification.class)).thenReturn(existing);
        mockUpdateChain();

        service.markFailed("n1", "smtp timeout", "user@example.com");

        verify(mongoTemplate).findById("n1", Notification.class);
        verify(mongoTemplate).update(Notification.class);
    }

    @Test
    void markFailed_setsPending_whenAttemptsBelowMaxAttempts() {
        when(retry.maxAttempts()).thenReturn(3);
        Notification existing = buildNotification("n1", 2);
        when(mongoTemplate.findById("n1", Notification.class)).thenReturn(existing);
        mockUpdateChain();

        service.markFailed("n1", "smtp timeout", null);

        verify(mongoTemplate).findById("n1", Notification.class);
        verify(mongoTemplate).update(Notification.class);
    }

    @Test
    void markFailed_defaultsToPending_whenNotificationNotFound() {
        when(retry.maxAttempts()).thenReturn(3);
        when(mongoTemplate.findById("ghost", Notification.class)).thenReturn(null);
        mockUpdateChain();

        service.markFailed("ghost", "error", null);

        verify(mongoTemplate).findById("ghost", Notification.class);
        verify(mongoTemplate).update(Notification.class);
    }

    @Test
    void bulkMarkStatus_setsLastError_whenErrorProvided() {
        service.bulkMarkStatus(List.of("n1", "n2"), FAILED, "boom");

        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Notification.class));
    }

    @Test
    void bulkMarkStatus_unsetsLastError_whenErrorNull() {
        service.bulkMarkStatus(List.of("n1", "n2"), SENT, null);

        verify(mongoTemplate).updateMulti(any(Query.class), any(Update.class), eq(Notification.class));
    }

    @Test
    void bulkMarkStatus_incrementsAttempts_onlyWhenStatusIsSending() {
        service.bulkMarkStatus(List.of("n1"), SENDING, null);
        service.bulkMarkStatus(List.of("n2"), SENT, null);

        verify(mongoTemplate, times(2)).updateMulti(any(Query.class), any(Update.class), eq(Notification.class));
    }

    @Test
    void bulkUpdateSuccess_buildsOneUpdateOnePerNotification_andExecutesOnce() {
        BulkOperations bulkOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Notification.class)).thenReturn(bulkOps);
        when(bulkOps.updateOne(any(Query.class), any(Update.class))).thenReturn(bulkOps);

        List<Notification> notifications = List.of(
              buildNotification("n1", 1).setRecipientEmail("a@example.com"),
              buildNotification("n2", 2).setRecipientEmail("b@example.com")
        );

        service.bulkUpdateSuccess(notifications);

        verify(bulkOps, times(2)).updateOne(any(Query.class), any(Update.class));
        verify(bulkOps, times(1)).execute();
    }

    @Test
    void bulkUpdateSuccess_executesOnce_evenWithEmptyList() {
        BulkOperations bulkOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Notification.class)).thenReturn(bulkOps);

        service.bulkUpdateSuccess(new ArrayList<>());

        verify(bulkOps, never()).updateOne(any(Query.class), any(Update.class));
        verify(bulkOps, times(1)).execute();
    }

    @Test
    void handleBatchFailure_doesNothing_whenListEmpty() {
        service.handleBatchFailure(new ArrayList<>(), "err");

        verify(mongoTemplate, never()).updateMulti(any(Query.class), any(Update.class), eq(Notification.class));
    }

    @Test
    void handleBatchFailure_partitionsByMaxAttempts_intoFailedAndRetryBuckets() {
        when(retry.maxAttempts()).thenReturn(3);

        Notification exhausted = buildNotification("n1", 3);   // attempts >= max -> FAILED bucket
        Notification retryable = buildNotification("n2", 1);   // attempts < max  -> PENDING bucket

        service.handleBatchFailure(List.of(exhausted, retryable), "smtp down");

        // Two separate updateMulti calls: one for failedIds, one for retryIds.
        verify(mongoTemplate, times(2)).updateMulti(any(Query.class), any(Update.class), eq(Notification.class));
    }

    @Test
    void handleBatchFailure_skipsFailedBucket_whenAllRetryable() {
        when(retry.maxAttempts()).thenReturn(5);
        Notification retryable = buildNotification("n1", 1);

        service.handleBatchFailure(List.of(retryable), "smtp down");

        verify(mongoTemplate, times(1)).updateMulti(any(Query.class), any(Update.class), eq(Notification.class));
    }

    @Test
    void handleBatchFailure_skipsRetryBucket_whenAllExhausted() {
        when(retry.maxAttempts()).thenReturn(1);
        Notification exhausted = buildNotification("n1", 1);

        service.handleBatchFailure(List.of(exhausted), "smtp down");

        verify(mongoTemplate, times(1)).updateMulti(any(Query.class), any(Update.class), eq(Notification.class));
    }
}