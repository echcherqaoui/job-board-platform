package com.echcherqaoui.jobboard.notificationservice.document;

import com.mongodb.lang.Nullable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.PENDING;

@Document(collection = "notifications")
@Getter
@Setter
@Accessors(chain = true)
@CompoundIndex(name = "status_updatedAt_idx", def = "{'status': 1, 'updatedAt': 1}")
public class Notification {

    @Id
    private String id;

    @Indexed(name = "recipientId_idx")
    private String recipientId;

    private String recipientEmail;

    private NotificationType type;

    private String subject;

    private String message;

    @Nullable
    private String jobId;

    private NotificationStatus status = PENDING;

    private int attempts = 0;

    private String lastError;

    private OffsetDateTime sentAt;

    @CreatedDate
    private OffsetDateTime createdAt;

    @LastModifiedDate
    private OffsetDateTime updatedAt;

    private String templateName;

    private Map<String, Object> templateVars = new HashMap<>();
}
