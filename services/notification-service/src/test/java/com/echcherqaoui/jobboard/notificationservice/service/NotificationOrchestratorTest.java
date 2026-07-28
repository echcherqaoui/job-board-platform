package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.projection.NotificationIdOnly;
import com.echcherqaoui.jobboard.notificationservice.repository.NotificationRepository;
import com.mongodb.DuplicateKeyException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcernResult;
import org.bson.BsonDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.time.OffsetDateTime;
import java.util.List;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATIONS_CANCELED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationOrchestratorTest {

    private NotificationRepository notificationRepository;
    private MongoTemplate mongoTemplate;
    private SingleNotificationSender singleSender;
    private BatchNotificationSender batchSender;
    private NotificationOrchestrator orchestrator;

    DuplicateKeyException dupEx = new DuplicateKeyException(
          new BsonDocument(),
          new ServerAddress(),
          WriteConcernResult.acknowledged(0, false, null)
    );

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        mongoTemplate = mock(MongoTemplate.class);
        singleSender = mock(SingleNotificationSender.class);
        batchSender = mock(BatchNotificationSender.class);
        orchestrator = new NotificationOrchestrator(notificationRepository, mongoTemplate, singleSender, batchSender);
    }

    private Notification buildNotification(String id, String jobId) {
        return new Notification()
              .setId(id)
              .setJobId(jobId)
              .setType(APPLICATIONS_CANCELED)
              .setStatus(PENDING);
    }

    // ---------- submitBatch ----------

    @Test
    void submitBatch_doesNothing_whenNotificationsNullOrEmpty() {
        orchestrator.submitBatch(null, "job-1");
        orchestrator.submitBatch(List.of(), "job-1");

        verify(mongoTemplate, never()).bulkOps(any(), eq(Notification.class));
    }

    @Test
    void submitBatch_insertsThenQueriesForGenuinelyNewPendingRecords_andSendsBatch() {
        Notification n1 = buildNotification("n1", "job-1");
        BulkOperations bulkOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Notification.class)).thenReturn(bulkOps);
        when(bulkOps.insert(anyList())).thenReturn(bulkOps);
        when(mongoTemplate.find(any(Query.class), eq(Notification.class))).thenReturn(List.of(n1));

        orchestrator.submitBatch(List.of(n1), "job-1");

        verify(bulkOps).insert(List.of(n1));
        verify(bulkOps).execute();
        verify(batchSender).executeBatchSend(List.of(n1), "job-1");
    }

    @Test
    void submitBatch_swallowsDuplicateKeyException_andStillQueriesForRealInserts() {
        Notification n1 = buildNotification("n1", "job-1");
        BulkOperations bulkOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Notification.class)).thenReturn(bulkOps);
        when(bulkOps.insert(anyList())).thenReturn(bulkOps);
        doThrow(dupEx).when(bulkOps).execute();
        when(mongoTemplate.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());

        orchestrator.submitBatch(List.of(n1), "job-1");

        verify(batchSender, never()).executeBatchSend(anyList(), anyString());
    }

    @Test
    void submitBatch_rethrowsUnexpectedException_fromBulkInsert() {
        Notification n1 = buildNotification("n1", "job-1");
        BulkOperations bulkOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Notification.class)).thenReturn(bulkOps);
        when(bulkOps.insert(anyList())).thenReturn(bulkOps);
        doThrow(new RuntimeException("mongo down")).when(bulkOps).execute();

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () ->
              orchestrator.submitBatch(List.of(n1), "job-1")
        );

        verify(mongoTemplate, never()).find(any(Query.class), eq(Notification.class));
    }

    @Test
    void submitBatch_doesNotCallBatchSender_whenNoInsertedNotificationsFound() {
        Notification n1 = buildNotification("n1", "job-1");
        BulkOperations bulkOps = mock(BulkOperations.class);
        when(mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Notification.class)).thenReturn(bulkOps);
        when(bulkOps.insert(anyList())).thenReturn(bulkOps);
        when(mongoTemplate.find(any(Query.class), eq(Notification.class))).thenReturn(List.of());

        orchestrator.submitBatch(List.of(n1), "job-1");

        verify(batchSender, never()).executeBatchSend(anyList(), anyString());
    }

    // ---------- submit ----------

    @Test
    void submit_insertsThenExecutesSend_onHappyPath() {
        Notification notification = new Notification().setId("n1");
        when(notificationRepository.insert(notification)).thenReturn(notification);

        orchestrator.submit(notification);

        verify(singleSender).executeSend("n1");
    }

    @Test
    void submit_swallowsDuplicateKeyException_andSkipsSend() {
        Notification notification = new Notification().setId("n1");
        when(notificationRepository.insert(notification)).thenThrow(dupEx);

        orchestrator.submit(notification);

        verify(singleSender, never()).executeSend(anyString());
    }

    // ---------- retrySingles ----------

    @Test
    void retrySingles_doesNothing_whenNoStaleRecordsFound() {
        when(notificationRepository.findByStatusAndUpdatedAtBeforeAndTypeNot(
              eq(PENDING), any(OffsetDateTime.class), eq(APPLICATIONS_CANCELED)
        )).thenReturn(List.of());

        orchestrator.retrySingles();

        verify(singleSender, never()).attemptSend(anyString());
    }

    @Test
    void retrySingles_callsAttemptSend_forEachStaleId() {
        NotificationIdOnly id1 = mock(NotificationIdOnly.class);
        NotificationIdOnly id2 = mock(NotificationIdOnly.class);
        when(id1.getId()).thenReturn("n1");
        when(id2.getId()).thenReturn("n2");

        when(notificationRepository.findByStatusAndUpdatedAtBeforeAndTypeNot(
              eq(PENDING), any(OffsetDateTime.class), eq(APPLICATIONS_CANCELED)
        )).thenReturn(List.of(id1, id2));

        orchestrator.retrySingles();

        verify(singleSender).attemptSend("n1");
        verify(singleSender).attemptSend("n2");
    }

    // ---------- retryBatches ----------

    @Test
    void retryBatches_doesNothing_whenNoStaleRecordsFound() {
        when(notificationRepository.findByStatusAndUpdatedAtBeforeAndType(
              eq(PENDING), any(OffsetDateTime.class), eq(APPLICATIONS_CANCELED)
        )).thenReturn(List.of());

        orchestrator.retryBatches();

        verify(batchSender, never()).attemptBatchSend(anyList(), anyString());
    }

    @Test
    void retryBatches_groupsStaleNotificationsByJobId_andCallsAttemptBatchSendPerGroup() {
        Notification n1 = buildNotification("n1", "job-1");
        Notification n2 = buildNotification("n2", "job-1");
        Notification n3 = buildNotification("n3", "job-2");

        when(notificationRepository.findByStatusAndUpdatedAtBeforeAndType(
              eq(PENDING), any(OffsetDateTime.class), eq(APPLICATIONS_CANCELED)
        )).thenReturn(List.of(n1, n2, n3));

        orchestrator.retryBatches();

        verify(batchSender).attemptBatchSend(List.of(n1, n2), "job-1");
        verify(batchSender).attemptBatchSend(List.of(n3), "job-2");
    }

    @Test
    void retryBatches_silentlyDropsNotifications_withNullJobId() {
        // Documents real gap: a stale APPLICATIONS_CANCELED notification with no jobId
        // is filtered out before grouping and never retried, with no log or error.
        Notification withJobId = buildNotification("n1", "job-1");
        Notification noJobId = buildNotification("n2", null);

        when(notificationRepository.findByStatusAndUpdatedAtBeforeAndType(
              eq(PENDING), any(OffsetDateTime.class), eq(APPLICATIONS_CANCELED)
        )).thenReturn(List.of(withJobId, noJobId));

        orchestrator.retryBatches();

        verify(batchSender, times(1)).attemptBatchSend(anyList(), anyString());
        verify(batchSender).attemptBatchSend(List.of(withJobId), "job-1");
    }
}