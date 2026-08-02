package com.echcherqaoui.jobboard.notificationservice.repository;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.echcherqaoui.jobboard.notificationservice.projection.NotificationIdOnly;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;

import java.time.OffsetDateTime;
import java.util.List;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.FAILED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATIONS_CANCELED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATION_RECEIVED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATION_STATUS_UPDATED;
import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
class NotificationRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    private Notification createNotification(NotificationStatus status,
                                            NotificationType type,
                                            OffsetDateTime updatedAt) {
        Notification notification = new Notification()
              .setRecipientId("user-123")
              .setRecipientEmail("user@example.com")
              .setType(type)
              .setStatus(status)
              .setSubject("Test Subject")
              .setMessage("Test Body")
              .setUpdatedAt(updatedAt);

        return notificationRepository.save(notification);
    }

    @Nested
    class FindByStatusAndUpdatedAtBeforeAndTypeNot {

        @Test
        void findByStatusAndUpdatedAtBeforeAndTypeNot_WhenMatchingCriteria_ShouldReturnProjections() {
            OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);
            OffsetDateTime pastTime = cutoff.minusMinutes(5);

            Notification validNotification = createNotification(PENDING, APPLICATION_RECEIVED, pastTime);
            // Ignored because status is SENT
            createNotification(SENT, APPLICATION_RECEIVED, pastTime);
            // Ignored because updatedAt is after cutoff
            createNotification(PENDING, APPLICATION_RECEIVED, OffsetDateTime.now());
            // Ignored because type matches excluded type
            createNotification(PENDING, APPLICATIONS_CANCELED, pastTime);

            List<NotificationIdOnly> results = notificationRepository
                  .findByStatusAndUpdatedAtBeforeAndTypeNot(PENDING, cutoff, APPLICATIONS_CANCELED);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(validNotification.getId());
        }

        @Test
        void findByStatusAndUpdatedAtBeforeAndTypeNot_WhenNoMatches_ShouldReturnEmptyList() {
            OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);

            createNotification(SENT, APPLICATION_RECEIVED, cutoff.minusMinutes(5));

            List<NotificationIdOnly> results = notificationRepository
                  .findByStatusAndUpdatedAtBeforeAndTypeNot(PENDING, cutoff, APPLICATIONS_CANCELED);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    class FindByStatusAndUpdatedAtBeforeAndType {

        @Test
        void findByStatusAndUpdatedAtBeforeAndType_WhenMatchingCriteria_ShouldReturnFullDocuments() {
            OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);
            OffsetDateTime pastTime = cutoff.minusMinutes(5);

            Notification target = createNotification(FAILED, APPLICATIONS_CANCELED, pastTime);
            // Ignored because type differs
            createNotification(FAILED, APPLICATION_STATUS_UPDATED, pastTime);
            // Ignored because status differs
            createNotification(PENDING, APPLICATIONS_CANCELED, pastTime);
            // Ignored because updatedAt is after cutoff
            createNotification(FAILED, APPLICATIONS_CANCELED, OffsetDateTime.now());

            List<Notification> results = notificationRepository
                  .findByStatusAndUpdatedAtBeforeAndType(FAILED, cutoff, APPLICATIONS_CANCELED);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(target.getId());
            assertThat(results.get(0).getType()).isEqualTo(APPLICATIONS_CANCELED);
            assertThat(results.get(0).getStatus()).isEqualTo(FAILED);
        }

        @Test
        void findByStatusAndUpdatedAtBeforeAndType_WhenNoMatches_ShouldReturnEmptyList() {
            OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);

            createNotification(PENDING, APPLICATIONS_CANCELED, cutoff.minusMinutes(5));

            List<Notification> results = notificationRepository
                  .findByStatusAndUpdatedAtBeforeAndType(FAILED, cutoff, APPLICATIONS_CANCELED);

            assertThat(results).isEmpty();
        }
    }
}