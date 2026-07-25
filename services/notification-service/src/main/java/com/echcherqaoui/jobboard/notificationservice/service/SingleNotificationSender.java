package com.echcherqaoui.jobboard.notificationservice.service;


public interface SingleNotificationSender {
    void executeSend(String notificationId);

    void attemptSend(String notificationId);
}