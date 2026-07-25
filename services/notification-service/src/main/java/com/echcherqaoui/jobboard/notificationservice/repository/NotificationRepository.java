package com.echcherqaoui.jobboard.notificationservice.repository;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.echcherqaoui.jobboard.notificationservice.projection.NotificationIdOnly;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<NotificationIdOnly> findByStatusAndUpdatedAtBeforeAndTypeNot(NotificationStatus status,
                                                                      OffsetDateTime updatedAt,
                                                                      NotificationType type);

    List<Notification> findByStatusAndUpdatedAtBeforeAndType(NotificationStatus status,
                                                             OffsetDateTime updatedAt,
                                                             NotificationType type);
}
