package com.echcherqaoui.jobboard.notificationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.application.event.ApplicationStatusChangedEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.notificationservice.dto.ApplicationNotificationContext;
import com.echcherqaoui.jobboard.notificationservice.kafka.handler.ApplicationHandler;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationStatusChangedHandler implements ApplicationHandler {

    private final NotificationService notificationService;
    private final SignatureService signatureService;

    @Override
    public String getDescriptorFullName() {
        return ApplicationStatusChangedEvent.getDescriptor().getFullName();
    }

    /**
     * Notify the APPLICANT that their application status changed.
     */
    @Override
    public void handle(Message payload) {
        ApplicationStatusChangedEvent event = (ApplicationStatusChangedEvent) payload;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getApplicationId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid) {
            log.warn(
                  "Signature verification failed for eventId: {}, applicationId: {}",
                  event.getEventId(),
                  event.getApplicationId()
            );
            throw new EventSecurityException(event.getEventId());
        }

        ApplicationNotificationContext context = new ApplicationNotificationContext(
              event.getEventId(),
              event.getApplicantId(),
              event.getJobId(),
              event.getJobTitle(),
              event.getCompanyName(),
              event.getNewStatus(),
              event.getNote(),
              event.getApplicationId()
        );

        notificationService.sendApplicationStatusUpdated(context);

        log.info(
              "Successfully processed ApplicationStatusChangedEvent for applicationId: {}, newStatus: {}",
              event.getApplicationId(),
              event.getNewStatus()
        );
    }
}