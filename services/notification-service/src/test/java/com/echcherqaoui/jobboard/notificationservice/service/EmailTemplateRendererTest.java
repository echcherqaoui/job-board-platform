package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailTemplateRendererTest {

    private TemplateEngine templateEngine;
    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        templateEngine = mock(TemplateEngine.class);
        NotificationProperties props = mock(NotificationProperties.class);
        NotificationProperties.Mail mailConfig = mock(NotificationProperties.Mail.class);

        when(props.frontendUrl()).thenReturn("https://jobboard.test");
        when(props.mail()).thenReturn(mailConfig);
        when(mailConfig.fromName()).thenReturn("JobBoard");

        renderer = new EmailTemplateRenderer(templateEngine, props);
    }

    private Notification buildNotification(String templateName, Map<String, Object> templateVars) {
        Notification n = new Notification()
              .setId("n1")
              .setTemplateName(templateName);
        n.setTemplateVars(templateVars);
        return n;
    }

    @Test
    void render_buildsTemplatePath_withEmailsPrefix() {
        Notification notification = buildNotification("welcome", Map.of());
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html></html>");

        renderer.render(notification);

        verify(templateEngine).process(eq("emails/welcome"), any(Context.class));
    }

    @Test
    void render_setsBaseVariables_frontendUrlAndAppName() {
        Notification notification = buildNotification("welcome", Map.of());

        renderer.render(notification);

        verify(templateEngine).process(anyString(), org.mockito.ArgumentMatchers.argThat(ctx ->
              "https://jobboard.test".equals(ctx.getVariable("frontendUrl"))
                    && "JobBoard".equals(ctx.getVariable("appName"))
        ));
    }

    @Test
    void render_mergesTemplateVars_intoContext() {
        Notification notification = buildNotification(
              "application-status-updated",
              Map.of("jobTitle", "Backend Engineer", "status", "ACCEPTED")
        );

        renderer.render(notification);

        verify(templateEngine).process(anyString(), org.mockito.ArgumentMatchers.argThat(ctx ->
              "Backend Engineer".equals(ctx.getVariable("jobTitle"))
                    && "ACCEPTED".equals(ctx.getVariable("status"))
        ));
    }

    @Test
    void render_doesNotThrow_whenTemplateVarsNull() {
        Notification notification = new Notification()
              .setId("n1")
              .setTemplateName("welcome");
        notification.setTemplateVars(null);

        renderer.render(notification);

        verify(templateEngine).process(eq("emails/welcome"), any(Context.class));
    }

    @Test
    void render_returnsTemplateEngineOutput() {
        Notification notification = buildNotification("welcome", Map.of());
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>rendered</html>");

        String result = renderer.render(notification);

        assertThat(result).isEqualTo("<html>rendered</html>");
    }
}