package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import jakarta.mail.MessagingException;

import java.util.List;
import java.util.Map;

public interface BulkEmailService {
    List<Notification> sendBulkCancellationEmails(List<Notification> notifications,
                                                  Map<String, String> idToEmailMap) throws MessagingException;
}
