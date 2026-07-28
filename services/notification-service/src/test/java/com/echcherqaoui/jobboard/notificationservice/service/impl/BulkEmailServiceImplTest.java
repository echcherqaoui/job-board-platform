package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.service.EmailTemplateRenderer;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.mail.MailProperties;

import java.util.List;
import java.util.Map;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENDING;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BulkEmailServiceImplTest {

    private BulkEmailServiceImpl service;

    @BeforeEach
    void setUp() {
        MailProperties mailProperties = mock(MailProperties.class);
        NotificationProperties notificationProperties = mock(NotificationProperties.class);
        NotificationProperties.Mail mailConfig = mock(NotificationProperties.Mail.class);
        EmailTemplateRenderer templateRenderer = mock(EmailTemplateRenderer.class);

        when(mailProperties.getHost()).thenReturn("localhost");
        when(mailProperties.getPort()).thenReturn(25);
        when(notificationProperties.mail()).thenReturn(mailConfig);
        when(mailConfig.from()).thenReturn("noreply@jobboard.test");
        when(mailConfig.fromName()).thenReturn("JobBoard");
        when(templateRenderer.render(org.mockito.ArgumentMatchers.any(Notification.class)))
              .thenReturn("<html>body</html>");

        service = new BulkEmailServiceImpl(mailProperties, notificationProperties, templateRenderer);
    }

    private Notification buildNotification(String id, String recipientId) {
        return new Notification()
              .setId(id)
              .setRecipientId(recipientId)
              .setStatus(SENDING)
              .setAttempts(1);
    }

    @Test
    void sendBulkCancellationEmails_throwsMessagingException_whenTransportUnreachable() {
        List<Notification> notifications = List.of(buildNotification("n1", "u1"));

        org.junit.jupiter.api.Assertions.assertThrows(MessagingException.class, () ->
              service.sendBulkCancellationEmails(notifications, Map.of("u1", "u1@example.com"))
        );
    }
}