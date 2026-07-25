package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.service.BulkEmailService;
import com.echcherqaoui.jobboard.notificationservice.service.EmailTemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static jakarta.mail.Message.RecipientType.TO;

@Service
@Slf4j
@RequiredArgsConstructor
public class BulkEmailServiceImpl implements BulkEmailService {

    private final MailProperties mailProperties;
    private final NotificationProperties notificationProperties;
    private final EmailTemplateRenderer templateRenderer;

    private boolean sendEmail(@NonNull Notification notification,
                              Session session,
                              InternetAddress fromAddress,
                              String email,
                              @NonNull Transport transport,
                              String htmlBody) {
        try {
            MimeMessage message = new MimeMessage(session);

            message.setFrom(fromAddress);
            message.setRecipient(TO, new InternetAddress(email));
            message.setSubject(notification.getSubject());
            message.setContent(htmlBody, "text/html; charset=UTF-8");

            transport.sendMessage(message, message.getAllRecipients());

            notification.setStatus(SENT);
            notification.setRecipientEmail(email);
        } catch (MessagingException ex) {
            log.error("Failed to transport individual message for record: {}", notification.getId(), ex);
            notification.setLastError(ex.getMessage());

            // If the socket pipeline itself is dead, break early.
            if (!transport.isConnected())
                return false;
        }
        return true;
    }

    @Override
    public List<Notification> sendBulkCancellationEmails(@NonNull List<Notification> notifications,
                                                         Map<String, String> idToEmailMap) throws MessagingException {

        Properties props = new Properties();
        props.put("mail.smtp.host", mailProperties.getHost());
        props.put("mail.smtp.port", String.valueOf(mailProperties.getPort()));
        props.put("mail.smtp.auth", "false");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");

        Session session = Session.getInstance(props);

        try (Transport transport = session.getTransport("smtp")) {
            transport.connect();

            String personalName = notificationProperties.mail().fromName();
            String fromEmail = notificationProperties.mail().from();
            InternetAddress fromAddress = new InternetAddress(fromEmail, personalName != null ? personalName : "");

            // Render once — content is identical for every recipient in this batch
            String htmlBody = templateRenderer.render(notifications.get(0));

            for (Notification notification : notifications) {
                String email = idToEmailMap.get(notification.getRecipientId());

                if (email == null || email.isBlank())
                    notification.setLastError("No email resolved for recipient");
                else if (!sendEmail(notification, session, fromAddress, email, transport, htmlBody))
                    break;
            }

            log.info("Bulk broadcast complete for {} emails.", notifications.size());
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to set personal name format on From address string", e);
            throw new MessagingException("Invalid sender configuration format", e);
        }

        List<Notification> failedNotifications = new ArrayList<>();
        for (Notification notification : notifications)
            if (notification.getStatus() != SENT) {
                if (notification.getLastError() == null)
                    notification.setLastError("Skipped due to upstream SMTP transport connection drop");

                failedNotifications.add(notification);
            }

        return failedNotifications;
    }
}