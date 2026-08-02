package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.document.NotificationType;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.thymeleaf.exceptions.TemplateInputException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class EmailServiceImplIT extends AbstractIntegrationTest {

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
    private EmailServiceImpl emailService;

    @Autowired
    private NotificationProperties notificationProperties;

    @AfterEach
    void purgeMailbox() throws Exception {
        if (greenMail != null && greenMail.isRunning()) {
            greenMail.purgeEmailFromAllMailboxes();
        }
    }

    @Nested
    class SendHtml {

        @Test
        void sendHtml_WhenValidNotification_ShouldDeliverEmailOverSmtp() throws Exception {
            Notification notification = new Notification()
                  .setRecipientId("user-100")
                  .setRecipientEmail("jobseeker@acme.com")
                  .setSubject("Welcome to Job Board")
                  .setType(NotificationType.WELCOME)
                  .setTemplateName("welcome")
                  .setTemplateVars(Map.of("role", "JOBSEEKER"));

            emailService.sendHtml(notification);

            assertThat(greenMail.waitForIncomingEmail(5000, 1)).isTrue();

            MimeMessage[] receivedMessages = greenMail.getReceivedMessages();
            assertThat(receivedMessages).hasSize(1);

            MimeMessage message = receivedMessages[0];
            assertThat(message.getSubject()).isEqualTo("Welcome to Job Board");
            assertThat(message.getAllRecipients()[0].toString()).hasToString("jobseeker@acme.com");
            assertThat(message.getFrom()[0].toString())
                  .contains(notificationProperties.mail().from());
        }

        @Test
        void sendHtml_WhenTemplateDoesNotExist_ShouldThrowTemplateExceptionAndNotSendEmail() {
            Notification notification = new Notification()
                  .setRecipientId("user-100")
                  .setRecipientEmail("jobseeker@acme.com")
                  .setSubject("Invalid Template Test")
                  .setType(NotificationType.WELCOME)
                  .setTemplateName("non_existent_template");

            assertThatThrownBy(() -> emailService.sendHtml(notification))
                  .isInstanceOf(TemplateInputException.class);

            assertThat(greenMail.getReceivedMessages()).isEmpty();
        }

        @Test
        void sendHtml_WhenSmtpServerIsDown_ShouldThrowMailSendException() {
            greenMail.stop();

            Notification notification = new Notification()
                  .setRecipientId("user-100")
                  .setRecipientEmail("jobseeker@acme.com")
                  .setSubject("Server Down Test")
                  .setType(NotificationType.WELCOME)
                  .setTemplateName("welcome");

            try {
                assertThatThrownBy(() -> emailService.sendHtml(notification))
                      .isInstanceOf(MailSendException.class);
            } finally {
                greenMail.start();
            }
        }
    }
}