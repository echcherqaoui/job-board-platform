package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.FAILED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationStateServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationStateServiceImpl stateService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private NotificationProperties notificationProperties;

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.remove(new Query(), Notification.class);
    }

    @Nested
    class Claim {

        @Test
        void claim_WhenNotificationIsPending_ShouldAtomicallyUpdateStatusToSendingAndIncrementAttempts() {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-1")
                  .setStatus(PENDING)
                  .setAttempts(0));

            Notification claimed = stateService.claim(notification.getId());

            assertThat(claimed).isNotNull();
            assertThat(claimed.getStatus()).isEqualTo(SENDING);
            assertThat(claimed.getAttempts()).isEqualTo(1);

            Notification dbState = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(dbState).isNotNull();
            assertThat(dbState.getStatus()).isEqualTo(SENDING);
            assertThat(dbState.getAttempts()).isEqualTo(1);
        }

        @Test
        void claim_WhenNotificationIsNotPending_ShouldReturnNull() {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-1")
                  .setStatus(SENDING)
                  .setAttempts(1));

            Notification claimed = stateService.claim(notification.getId());

            assertThat(claimed).isNull();
        }
    }

    @Nested
    class MarkSent {

        @Test
        void markSent_ShouldUpdateStatusToSentAndUnsetLastError() {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-1")
                  .setStatus(SENDING)
                  .setLastError("Previous connection failure"));

            stateService.markSent(notification.getId(), "user@acme.com");

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(SENT);
            assertThat(updated.getRecipientEmail()).isEqualTo("user@acme.com");
            assertThat(updated.getSentAt()).isNotNull();
            assertThat(updated.getLastError()).isNull();
        }

        @Test
        void markSent_WhenResolvedEmailIsNull_ShouldNotOverwriteExistingEmail() {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-1")
                  .setRecipientEmail("existing@acme.com")
                  .setStatus(SENDING));

            stateService.markSent(notification.getId(), null);

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(SENT);
            assertThat(updated.getRecipientEmail()).isEqualTo("existing@acme.com");
        }
    }

    @Nested
    class MarkFailed {

        @Test
        void markFailed_WhenAttemptsLessThanMax_ShouldResetStatusToPendingForRetry() {
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-1")
                  .setStatus(SENDING)
                  .setAttempts(1));

            stateService.markFailed(notification.getId(), "Connection refused", "user@acme.com");

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(PENDING);
            assertThat(updated.getLastError()).isEqualTo("Connection refused");
            assertThat(updated.getRecipientEmail()).isEqualTo("user@acme.com");
        }

        @Test
        void markFailed_WhenAttemptsExceedMax_ShouldUpdateStatusToFailed() {
            int maxAttempts = notificationProperties.retry().maxAttempts();
            Notification notification = mongoTemplate.save(new Notification()
                  .setRecipientId("user-1")
                  .setStatus(SENDING)
                  .setAttempts(maxAttempts));

            stateService.markFailed(notification.getId(), "Max retries reached", "user@acme.com");

            Notification updated = mongoTemplate.findById(notification.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(FAILED);
            assertThat(updated.getLastError()).isEqualTo("Max retries reached");
        }
    }

    @Nested
    class BulkOperations {

        @Test
        void bulkMarkStatus_WhenStatusIsSending_ShouldIncrementAttempts() {
            Notification n1 = mongoTemplate.save(new Notification().setStatus(PENDING).setAttempts(0));
            Notification n2 = mongoTemplate.save(new Notification().setStatus(PENDING).setAttempts(1));

            stateService.bulkMarkStatus(List.of(n1.getId(), n2.getId()), SENDING, null);

            List<Notification> updated = mongoTemplate.findAll(Notification.class);
            assertThat(updated)
                  .extracting(Notification::getStatus)
                  .containsExactlyInAnyOrder(SENDING, SENDING);
            assertThat(updated)
                  .extracting(Notification::getAttempts)
                  .containsExactlyInAnyOrder(1, 2);
        }

        @Test
        void bulkMarkStatus_WhenErrorProvided_ShouldSetLastError() {
            Notification n1 = mongoTemplate.save(new Notification().setStatus(SENDING).setAttempts(1));

            stateService.bulkMarkStatus(List.of(n1.getId()), PENDING, "SMTP Timeout");

            Notification updated = mongoTemplate.findById(n1.getId(), Notification.class);
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isEqualTo(PENDING);
            assertThat(updated.getLastError()).isEqualTo("SMTP Timeout");
        }

        @Test
        void bulkUpdateSuccess_ShouldExecuteBulkWrite() {
            Notification n1 = mongoTemplate.save(new Notification().setStatus(SENDING).setAttempts(1));
            Notification n2 = mongoTemplate.save(new Notification().setStatus(SENDING).setAttempts(1));

            n1.setRecipientEmail("user1@acme.com");
            n2.setRecipientEmail("user2@acme.com");

            stateService.bulkUpdateSuccess(List.of(n1, n2));

            List<Notification> updated = mongoTemplate.findAll(Notification.class);
            assertThat(updated)
                  .extracting(Notification::getStatus)
                  .containsExactlyInAnyOrder(SENT, SENT);
            assertThat(updated)
                  .extracting(Notification::getRecipientEmail)
                  .containsExactlyInAnyOrder("user1@acme.com", "user2@acme.com");
            assertThat(updated)
                  .allSatisfy(n -> assertThat(n.getSentAt()).isNotNull());
        }

        @Test
        void handleBatchFailure_ShouldPartitionByMaxAttemptsAndBulkUpdate() {
            int maxAttempts = notificationProperties.retry().maxAttempts();

            Notification nRetry = mongoTemplate.save(new Notification().setStatus(SENDING).setAttempts(1));
            Notification nFailed = mongoTemplate.save(new Notification().setStatus(SENDING).setAttempts(maxAttempts));

            stateService.handleBatchFailure(List.of(nRetry, nFailed), "gRPC resolution failed");

            Notification updatedRetry = mongoTemplate.findById(nRetry.getId(), Notification.class);
            Notification updatedFailed = mongoTemplate.findById(nFailed.getId(), Notification.class);

            assertThat(updatedRetry).isNotNull();
            assertThat(updatedRetry.getStatus()).isEqualTo(PENDING);
            assertThat(updatedRetry.getLastError()).isEqualTo("gRPC resolution failed");

            assertThat(updatedFailed).isNotNull();
            assertThat(updatedFailed.getStatus()).isEqualTo(FAILED);
            assertThat(updatedFailed.getLastError()).isEqualTo("gRPC resolution failed");
        }
    }
}