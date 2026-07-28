package com.echcherqaoui.jobboard.notificationservice.service.impl;

import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import com.echcherqaoui.jobboard.notificationservice.service.EmailTemplateRenderer;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailServiceImplTest {

    private JavaMailSender mailSender;
    private EmailTemplateRenderer templateRenderer;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        NotificationProperties props = mock(NotificationProperties.class);
        NotificationProperties.Mail mailConfig = mock(NotificationProperties.Mail.class);
        templateRenderer = mock(EmailTemplateRenderer.class);

        when(props.mail()).thenReturn(mailConfig);
        when(mailConfig.from()).thenReturn("noreply@jobboard.test");
        when(mailConfig.fromName()).thenReturn("JobBoard");

        Session session = Session.getInstance(new Properties());
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage(session));

        emailService = new EmailServiceImpl(mailSender, props, templateRenderer);
    }

    private Notification buildNotification(String recipientEmail, String subject) {
        return new Notification()
              .setId("n1")
              .setRecipientEmail(recipientEmail)
              .setSubject(subject)
              .setTemplateName("welcome");
    }

    @Test
    void sendHtml_rendersTemplate_andSendsViaMailSender() throws MessagingException, UnsupportedEncodingException {
        Notification notification = buildNotification("user@example.com", "Welcome!");
        when(templateRenderer.render(notification)).thenReturn("<html>hi</html>");

        emailService.sendHtml(notification);

        verify(templateRenderer).render(notification);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendHtml_setsRecipientFromNotification() throws Exception {
        Notification notification = buildNotification("user@example.com", "Welcome!");
        when(templateRenderer.render(notification)).thenReturn("<html>hi</html>");

        emailService.sendHtml(notification);

        org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        MimeMessage sent = captor.getValue();
        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0].toString()).contains("user@example.com");
    }

    @Test
    void sendHtml_setsSubject() throws Exception {
        Notification notification = buildNotification("user@example.com", "Welcome!");
        when(templateRenderer.render(notification)).thenReturn("<html>hi</html>");

        emailService.sendHtml(notification);

        org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getSubject()).isEqualTo("Welcome!");
    }

    @Test
    void sendHtml_setsFromAddress_withPersonalName() throws Exception {
        Notification notification = buildNotification("user@example.com", "Welcome!");
        when(templateRenderer.render(notification)).thenReturn("<html>hi</html>");

        emailService.sendHtml(notification);

        org.mockito.ArgumentCaptor<MimeMessage> captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());

        assertThat(captor.getValue().getFrom()[0].toString()).contains("noreply@jobboard.test");
    }

    @Test
    void sendHtml_propagatesMessagingException_fromMailSenderSend() {
        Notification notification = buildNotification("user@example.com", "Welcome!");
        when(templateRenderer.render(notification)).thenReturn("<html>hi</html>");
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("smtp failure"))
              .when(mailSender).send(any(MimeMessage.class));

        org.junit.jupiter.api.Assertions.assertThrows(
              org.springframework.mail.MailSendException.class,
              () -> emailService.sendHtml(notification)
        );
    }

    @Test
    void sendHtml_throwsNullPointerException_whenNotificationNull() {
        org.junit.jupiter.api.Assertions.assertThrows(
              Exception.class,
              () -> emailService.sendHtml(null)
        );
    }
}