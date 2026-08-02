package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.projection.NotificationIdOnly;
import com.echcherqaoui.jobboard.notificationservice.repository.NotificationRepository;
import com.mongodb.MongoBulkWriteException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.BulkOperationException;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.APPLICATIONS_CANCELED;
import static org.springframework.data.mongodb.core.BulkOperations.BulkMode.UNORDERED;


@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationOrchestrator {
    private final NotificationRepository notificationRepository;
    private final MongoTemplate mongoTemplate;
    private final SingleNotificationSender singleSender;
    private final BatchNotificationSender batchSender;

    public void submitBatch(List<Notification> notifications, String jobId) {
        if (notifications == null || notifications.isEmpty()) return;

        BulkOperations bulkOps = mongoTemplate.bulkOps(UNORDERED, Notification.class);
        bulkOps.insert(notifications);

        try {
            bulkOps.execute();
        } catch (BulkOperationException | MongoBulkWriteException | DuplicateKeyException ex) {
            log.info("Bulk insert finished with some duplicates ignored for Job: {}", jobId);
        } catch (Exception ex) {
            log.error("Bulk insert failed unexpectedly for Job: {}", jobId, ex);
            throw ex;
        }

        //  Query the DB for the IDs we just tried to insert that are actually PENDING.
        List<String> targetIds = notifications.stream().map(Notification::getId).toList();

        List<Notification> insertedNotifications = mongoTemplate.find(
              Query.query(
                    Criteria.where("id").in(targetIds)
                          .and("status").is(PENDING)
                          .and("attempts").is(0)
              ),
              Notification.class
        );

        if (!insertedNotifications.isEmpty())
            batchSender.executeBatchSend(insertedNotifications, jobId);
    }

    public void submit(Notification notification) {
        try {
            String notificationId = notificationRepository.insert(notification).getId(); // Fails on duplicate _id
            singleSender.executeSend(notificationId);
        } catch (DuplicateKeyException ex) {
            log.info("Notification {} already exists, skipping duplicate dispatch", notification.getId());
        }
    }

    public void retrySingles() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);

        List<NotificationIdOnly> idsOnly = notificationRepository.findByStatusAndUpdatedAtBeforeAndTypeNot(
              PENDING,
              cutoff,
              APPLICATIONS_CANCELED
        );

        if (idsOnly.isEmpty()) {
            log.debug("No pending single notifications to retry before cutoff: {}", cutoff);

            return;
        }

        log.info("Retrying {} pending single notifications", idsOnly.size());

        for (NotificationIdOnly idOnly : idsOnly)
            singleSender.attemptSend(idOnly.getId());
    }

    public void retryBatches() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(10);

        List<Notification> stale = notificationRepository.findByStatusAndUpdatedAtBeforeAndType(
              PENDING,
              cutoff,
              APPLICATIONS_CANCELED
        );

        if (stale.isEmpty()) {
            log.debug("No stale APPLICATIONS_CANCELED notifications found before cutoff: {}", cutoff);

            return;
        }

        Map<String, List<Notification>> notificationsByJob = stale.stream()
              .filter(n -> n.getJobId() != null)
              .collect(Collectors.groupingBy(Notification::getJobId));

        log.debug("Grouped {} stale notifications into {} jobs", stale.size(), notificationsByJob.size());

        notificationsByJob.forEach((jobId, notifications) -> batchSender.attemptBatchSend(notifications, jobId));
    }
}