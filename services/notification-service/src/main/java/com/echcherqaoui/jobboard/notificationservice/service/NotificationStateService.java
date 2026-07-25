package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus;
import org.springframework.lang.NonNull;

import java.util.List;


public interface NotificationStateService {
    Notification claim(String notificationId);

    void markSent(String notificationId, String resolvedEmail);

    void markFailed(String notificationId, String errorMessage, String resolvedEmail);

    void bulkMarkStatus(List<String> ids, NotificationStatus status, String error);

    void bulkUpdateSuccess(@NonNull List<Notification> notifications);

    void handleBatchFailure(@NonNull List<Notification> notifications, String errorMessage);
}