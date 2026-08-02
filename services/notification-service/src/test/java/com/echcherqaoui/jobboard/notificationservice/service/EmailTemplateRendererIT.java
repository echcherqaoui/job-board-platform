package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.thymeleaf.exceptions.TemplateInputException;

import java.util.Map;

import static com.echcherqaoui.jobboard.notificationservice.document.NotificationType.WELCOME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
class EmailTemplateRendererIT extends AbstractIntegrationTest {

    @Autowired
    private EmailTemplateRenderer emailTemplateRenderer;

    @Autowired
    private NotificationProperties notificationProperties;

    @Test
    void render_WhenValidNotificationAndTemplateVars_ShouldRenderHtmlWithVariables() {
        Notification notification = new Notification()
              .setRecipientId("user-123")
              .setRecipientEmail("applicant@acme.com")
              .setType(WELCOME)
              .setTemplateName("welcome")
              .setTemplateVars(Map.of("role", "JOBSEEKER"));

        String renderedContent = emailTemplateRenderer.render(notification);

        assertThat(renderedContent)
              .isNotNull()
              .isNotBlank()
              .contains(notificationProperties.frontendUrl())
              .contains(notificationProperties.mail().fromName())
              .contains("JOBSEEKER");
    }

    @Test
    void render_WhenTemplateVarsIsNull_ShouldRenderHtmlWithoutNullPointerException() {
        Notification notification = new Notification()
              .setRecipientId("user-456")
              .setRecipientEmail("recruiter@acme.com")
              .setType(WELCOME)
              .setTemplateName("welcome")
              .setTemplateVars(null);

        String renderedContent = emailTemplateRenderer.render(notification);

        assertThat(renderedContent)
              .isNotNull()
              .isNotBlank()
              .contains(notificationProperties.frontendUrl())
              .contains(notificationProperties.mail().fromName());
    }

    @Test
    void render_WhenTemplateNameDoesNotExist_ShouldThrow() {
        Notification notification = new Notification()
              .setRecipientId("user-789")
              .setRecipientEmail("ghost@acme.com")
              .setType(WELCOME)
              .setTemplateName("does-not-exist");

        assertThatThrownBy(() -> emailTemplateRenderer.render(notification))
              .isInstanceOf(TemplateInputException.class);
    }
}