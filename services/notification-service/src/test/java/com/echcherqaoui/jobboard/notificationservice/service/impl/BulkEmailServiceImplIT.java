package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.thymeleaf.exceptions.TemplateInputException;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationStatus.SENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BulkEmailServiceImplIT extends AbstractIntegrationTest {

    static GreenMail greenMail;

    @BeforeAll
    static void startGreenMail() {
        greenMail = new GreenMail(ServerSetupTest.SMTP.dynamicPort());
        greenMail.start();
    }

    @AfterAll
    static void stopGreenMail() {
        if (greenMail != null) {
            greenMail.stop();
        }
    }

    @DynamicPropertySource
    static void configureMailHost(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "localhost");
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
    }

    @Autowired
    private BulkEmailServiceImpl bulkEmailService;

    @AfterEach
    void purgeMailbox() throws Exception {
        if (greenMail != null && greenMail.isRunning()) {
            greenMail.purgeEmailFromAllMailboxes();
        }
    }

    @Nested
    class SendBulkCancellationEmails {

        @Test
        void sendBulkCancellationEmails_WhenAllEmailsResolved_ShouldDeliverBatchAndReturnNoFailures() throws Exception {
            String recipientId1 = "user-1";
            String recipientId2 = "user-2";

            Notification n1 = new Notification()
                  .setRecipientId(recipientId1)
                  .setSubject("Application Canceled")
                  .setType(NotificationType.APPLICATIONS_CANCELED)
                  .setTemplateName("welcome");

            Notification n2 = new Notification()
                  .setRecipientId(recipientId2)
                  .setSubject("Application Canceled")
                  .setType(NotificationType.APPLICATIONS_CANCELED)
                  .setTemplateName("welcome");

            List<Notification> notifications = List.of(n1, n2);

            Map<String, String> idToEmailMap = Map.of(
                  recipientId1, "user1@acme.com",
                  recipientId2, "user2@acme.com"
            );

            List<Notification> failedNotifications = bulkEmailService.sendBulkCancellationEmails(notifications, idToEmailMap);

            assertThat(failedNotifications).isEmpty();

            assertThat(n1.getStatus()).isEqualTo(SENT);
            assertThat(n2.getStatus()).isEqualTo(SENT);

            assertThat(greenMail.waitForIncomingEmail(5000, 2)).isTrue();

            MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
            assertThat(receivedMessages).hasSize(2);
            assertThat(receivedMessages[0].getSubject()).isEqualTo("Application Canceled");
        }

        @Test
        void sendBulkCancellationEmails_WhenRecipientEmailMissing_ShouldMarkAsFailedAndProcessOthers() throws Exception {
            String recipientId1 = "user-1";
            String recipientId2 = "missing-user";

            Notification n1 = new Notification()
                  .setRecipientId(recipientId1)
                  .setSubject("Application Canceled")
                  .setType(NotificationType.APPLICATIONS_CANCELED)
                  .setTemplateName("welcome");

            Notification n2 = new Notification()
                  .setRecipientId(recipientId2)
                  .setSubject("Application Canceled")
                  .setType(NotificationType.APPLICATIONS_CANCELED)
                  .setTemplateName("welcome");

            List<Notification> notifications = List.of(n1, n2);

            Map<String, String> idToEmailMap = Map.of(
                  recipientId1, "user1@acme.com"
            );

            List<Notification> failedNotifications = bulkEmailService.sendBulkCancellationEmails(notifications, idToEmailMap);

            assertThat(failedNotifications)
                  .hasSize(1)
                  .containsExactly(n2);

            assertThat(n2.getLastError()).isEqualTo("No email resolved for recipient");
            assertThat(n1.getStatus()).isEqualTo(SENT);

            assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();
            assertThat(greenMail.getReceivedMessages()).hasSize(1);
        }

        @Test
        void sendBulkCancellationEmails_WhenSmtpConnectionFails_ShouldThrowMessagingException() {
            Properties props = new Properties();
            props.put("mail.smtp.host", "localhost");
            props.put("mail.smtp.port", "65534"); // Unbound port
            props.put("mail.smtp.connectiontimeout", "1000");
            props.put("mail.smtp.timeout", "1000");

            Session session = Session.getInstance(props);

            assertThatThrownBy(() -> {
                try (Transport transport = session.getTransport("smtp")) {
                    transport.connect();
                }
            }).isInstanceOf(MessagingException.class);
        }

        @Test
        void sendBulkCancellationEmails_WhenInvalidTemplate_ShouldThrowTemplateInputException() {
            Notification n1 = new Notification()
                  .setRecipientId("user-1")
                  .setSubject("Application Canceled")
                  .setType(NotificationType.APPLICATIONS_CANCELED)
                  .setTemplateName("invalid_template_name");

            List<Notification> notifications = List.of(n1);
            Map<String, String> idToEmailMap = Map.of("user-1", "user1@acme.com");

            assertThatThrownBy(() -> bulkEmailService.sendBulkCancellationEmails(notifications, idToEmailMap))
                  .isInstanceOf(TemplateInputException.class);

            assertThat(greenMail.getReceivedMessages()).isEmpty();
        }
    }
}