package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;

import java.util.List;


public interface BatchNotificationSender {
    void executeBatchSend(List<Notification> notifications, String jobId);

    void attemptBatchSend(List<Notification> notifications, String jobId);
}