package com.echcherqaoui.jobboard.notificationservice.service;

import com.echcherqaoui.jobboard.notificationservice.config.NotificationProperties;
import com.echcherqaoui.jobboard.notificationservice.document.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
@RequiredArgsConstructor
public class EmailTemplateRenderer {
    private final TemplateEngine templateEngine;
    private final NotificationProperties props;

    public String render(Notification notification) {
        Context context = new Context();
        context.setVariable("frontendUrl", props.frontendUrl());
        context.setVariable("appName", props.mail().fromName());

        if (notification.getTemplateVars() != null) notification.getTemplateVars().forEach(context::setVariable);

        return templateEngine.process("emails/" + notification.getTemplateName(), context);
    }
}