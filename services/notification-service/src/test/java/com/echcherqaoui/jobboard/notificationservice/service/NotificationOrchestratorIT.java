package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATIONS_CANCELED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATION_RECEIVED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATION_STATUS_UPDATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class NotificationOrchestratorIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationOrchestrator notificationOrchestrator;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoBean
    private SingleNotificationSender singleSender;

    @MockitoBean
    private BatchNotificationSender batchSender;

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.remove(new Query(), Notification.class);
    }

    @Nested
    class Submit {

        @Test
        void submit_WhenNewNotification_ShouldInsertAndTriggerSingleSender() {
            Notification notification = new Notification()
                  .setId("notif-100")
                  .setRecipientId("user-1")
                  .setType(APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING);

            notificationOrchestrator.submit(notification);

            Notification saved = mongoTemplate.findById("notif-100", Notification.class);
            assertThat(saved).isNotNull();
            assertThat(saved.getStatus()).isEqualTo(PENDING);

            verify(singleSender).executeSend("notif-100");
        }

        @Test
        void submit_WhenDuplicateId_ShouldCatchDuplicateKeyExceptionAndSkipDispatch() {
            mongoTemplate.save(new Notification()
                  .setId("notif-dup")
                  .setRecipientId("user-1")
                  .setType(APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING));

            Notification duplicate = new Notification()
                  .setId("notif-dup")
                  .setRecipientId("user-1")
                  .setType(APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING);

            notificationOrchestrator.submit(duplicate);

            verifyNoInteractions(singleSender);
        }
    }

    @Nested
    class SubmitBatch {

        @Test
        void submitBatch_WhenNullOrEmptyList_ShouldDoNothing() {
            notificationOrchestrator.submitBatch(null, "job-1");
            notificationOrchestrator.submitBatch(List.of(), "job-1");

            verifyNoInteractions(batchSender);
            assertThat(mongoTemplate.findAll(Notification.class)).isEmpty();
        }

        @Test
        void submitBatch_WhenValidNotifications_ShouldInsertAllAndTriggerBatchSender() {
            Notification n1 = new Notification()
                  .setId("b-1")
                  .setJobId("job-100")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING)
                  .setAttempts(0);

            Notification n2 = new Notification()
                  .setId("b-2")
                  .setJobId("job-100")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING)
                  .setAttempts(0);

            notificationOrchestrator.submitBatch(List.of(n1, n2), "job-100");

            List<Notification> savedList = mongoTemplate.findAll(Notification.class);
            assertThat(savedList).hasSize(2);

            verify(batchSender).executeBatchSend(anyList(), eq("job-100"));
        }

        @Test
        void submitBatch_WhenSomeAreDuplicates_ShouldInsertNewOnesAndPassOnlyPendingToSender() {
            mongoTemplate.save(new Notification()
                  .setId("b-existing")
                  .setJobId("job-100")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(SENT)
                  .setAttempts(1));

            Notification dup = new Notification()
                  .setId("b-existing")
                  .setJobId("job-100")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING)
                  .setAttempts(0);

            Notification brandNew = new Notification()
                  .setId("b-new")
                  .setJobId("job-100")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING)
                  .setAttempts(0);

            notificationOrchestrator.submitBatch(List.of(dup, brandNew), "job-100");

            verify(batchSender).executeBatchSend(
                  org.mockito.ArgumentMatchers.argThat(list ->
                        list.size() == 1 && list.get(0).getId().equals("b-new")
                  ),
                  eq("job-100")
            );
        }
    }

    @Nested
    class RetrySingles {

        @Test
        void retrySingles_WhenStaleNonCanceledNotificationsExist_ShouldAttemptSendForEach() {
            OffsetDateTime staleCutoff = OffsetDateTime.now().minusMinutes(15);

            mongoTemplate.save(new Notification()
                  .setId("single-stale")
                  .setType(APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING));

            mongoTemplate.save(new Notification()
                  .setId("single-fresh")
                  .setType(APPLICATION_STATUS_UPDATED)
                  .setStatus(PENDING));

            mongoTemplate.save(new Notification()
                  .setId("canceled-stale")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING));

            mongoTemplate.save(new Notification()
                  .setId("single-completed")
                  .setType(APPLICATION_STATUS_UPDATED)
                  .setStatus(SENT));

            // Direct MongoDB update to bypass Spring Data Auditing
            Query query = Query.query(Criteria.where("id").in("single-stale", "canceled-stale", "single-completed"));
            Update update = Update.update("updatedAt", staleCutoff);
            mongoTemplate.updateMulti(query, update, Notification.class);

            notificationOrchestrator.retrySingles();

            verify(singleSender).attemptSend("single-stale");
            verify(singleSender, never()).attemptSend("single-fresh");
            verify(singleSender, never()).attemptSend("canceled-stale");
            verify(singleSender, never()).attemptSend("single-completed");
        }

        @Test
        void retrySingles_WhenNoStaleNotifications_ShouldDoNothing() {
            mongoTemplate.save(new Notification()
                  .setId("single-fresh")
                  .setType(APPLICATION_RECEIVED)
                  .setStatus(PENDING)
                  .setUpdatedAt(OffsetDateTime.now()));

            notificationOrchestrator.retrySingles();

            verifyNoInteractions(singleSender);
        }
    }

    @Nested
    class RetryBatches {

        @Test
        void retryBatches_WhenStaleCanceledNotificationsExist_ShouldGroupAndTriggerBatchSend() {
            OffsetDateTime staleCutoff = OffsetDateTime.now().minusMinutes(15);

            mongoTemplate.save(new Notification()
                  .setId("batch-1")
                  .setJobId("job-A")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING));

            mongoTemplate.save(new Notification()
                  .setId("batch-2")
                  .setJobId("job-A")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING));

            mongoTemplate.save(new Notification()
                  .setId("batch-3")
                  .setJobId("job-B")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING));

            mongoTemplate.save(new Notification()
                  .setId("batch-fresh")
                  .setJobId("job-A")
                  .setType(APPLICATIONS_CANCELED)
                  .setStatus(PENDING));

            // Force the stale updatedAt directly in Mongo, bypassing Spring Data Auditing
            Query query = Query.query(Criteria.where("id").in("batch-1", "batch-2", "batch-3"));
            Update update = Update.update("updatedAt", staleCutoff);
            mongoTemplate.updateMulti(query, update, Notification.class);

            notificationOrchestrator.retryBatches();

            verify(batchSender).attemptBatchSend(
                  org.mockito.ArgumentMatchers.argThat(list -> list.size() == 2),
                  eq("job-A")
            );

            verify(batchSender).attemptBatchSend(
                  org.mockito.ArgumentMatchers.argThat(list -> list.size() == 1 && list.get(0).getId().equals("batch-3")),
                  eq("job-B")
            );
        }

        @Test
        void retryBatches_WhenNoStaleBatchNotifications_ShouldDoNothing() {
            notificationOrchestrator.retryBatches();

            verifyNoInteractions(batchSender);
        }
    }
}