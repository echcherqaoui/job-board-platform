package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.FAILED;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENDING;
import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static org.springframework.data.mongodb.core.BulkOperations.BulkMode.UNORDERED;

@Component
@RequiredArgsConstructor
public class NotificationStateServiceImpl implements NotificationStateService {

    private final MongoTemplate mongoTemplate;
    private final NotificationProperties notificationProperties;

    @Override
    public Notification claim(String notificationId) {
        Update update = new Update()
              .set("status", SENDING)
              .inc("attempts", 1)
              .set("updatedAt", OffsetDateTime.now());

        Query query = Query.query(
              Criteria.where("id").is(notificationId)
                    .and("status").is(PENDING)
        );

        return mongoTemplate.findAndModify(
              query,
              update,
              FindAndModifyOptions.options().returnNew(true),
              Notification.class
        );
    }

    @Override
    public void markSent(String notificationId, String resolvedEmail) {
        OffsetDateTime now = OffsetDateTime.now();

        Update update = new Update()
              .set("status", SENT)
              .set("sentAt", now)
              .unset("lastError")
              .set("updatedAt", now);

        if (resolvedEmail != null)
            update.set("recipientEmail", resolvedEmail);

        mongoTemplate.update(Notification.class)
              .matching(Criteria.where("id").is(notificationId))
              .apply(update)
              .first();
    }

    @Override
    public void markFailed(String notificationId, String errorMessage, String resolvedEmail) {
        Notification notification = mongoTemplate.findById(notificationId, Notification.class);

        NotificationStatus nextStatus = (notification != null && notification.getAttempts() >= notificationProperties.retry().maxAttempts())
              ? FAILED
              : PENDING; // eligible for retry job again

        Update update = new Update()
              .set("status", nextStatus)
              .set("lastError", errorMessage)
              .set("updatedAt", OffsetDateTime.now());

        if (resolvedEmail != null)
            update.set("recipientEmail", resolvedEmail);

        mongoTemplate.update(Notification.class)
              .matching(Criteria.where("id").is(notificationId))
              .apply(update)
              .first();
    }

    @Override
    public void bulkMarkStatus(List<String> ids, NotificationStatus status, String error) {
        Update update = new Update()
              .set("status", status)
              .set("updatedAt", OffsetDateTime.now());

        if (status == SENDING)
            update.inc("attempts", 1);

        if (error != null)
            update.set("lastError", error);
        else
            update.unset("lastError");

        mongoTemplate.updateMulti(
              Query.query(Criteria.where("id").in(ids)),
              update,
              Notification.class
        );
    }

    @Override
    public void bulkUpdateSuccess(@NonNull List<Notification> notifications) {
        BulkOperations bulkOps = mongoTemplate.bulkOps(UNORDERED, Notification.class);

        for (Notification n : notifications) {
            Query query = Query.query(Criteria.where("id").is(n.getId()));
            Update update = new Update()
                  .set("status", SENT)
                  .set("attempts", n.getAttempts())
                  .set("recipientEmail", n.getRecipientEmail())
                  .set("sentAt", OffsetDateTime.now())
                  .set("updatedAt", OffsetDateTime.now())
                  .unset("lastError");

            bulkOps.updateOne(query, update);
        }

        bulkOps.execute();
    }

    @Override
    public void handleBatchFailure(@NonNull List<Notification> notifications, String errorMessage) {
        if (notifications.isEmpty()) return;

        List<String> failedIds = new ArrayList<>();
        List<String> retryIds = new ArrayList<>();

        int maxAttempts = notificationProperties.retry().maxAttempts();

        for (Notification n : notifications)
            if (n.getAttempts() >= maxAttempts)
                failedIds.add(n.getId());
            else
                retryIds.add(n.getId());

        if (!failedIds.isEmpty())
            bulkMarkStatus(failedIds, FAILED, errorMessage);

        if (!retryIds.isEmpty())
            bulkMarkStatus(retryIds, PENDING, errorMessage);
    }
}