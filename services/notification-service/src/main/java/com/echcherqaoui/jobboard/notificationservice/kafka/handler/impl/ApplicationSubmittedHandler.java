package com.echcherqaoui.jobboard.notificationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.application.event.ApplicationSubmittedEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
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
public class ApplicationSubmittedHandler implements ApplicationHandler {

    private final NotificationService notificationService;
    private final SignatureService signatureService;

    @Override
    public String getDescriptorFullName() {
        return ApplicationSubmittedEvent.getDescriptor().getFullName();
    }

    /**
     * Notify the COMPANY that a new application has arrived.
     */
    @Override
    public void handle(Message payload) {
        ApplicationSubmittedEvent event = (ApplicationSubmittedEvent) payload;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getApplicationId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid) {
            log.warn("Signature verification failed for eventId: {}, applicationId: {}",
                  event.getEventId(), event.getApplicationId());
            throw new EventSecurityException(event.getEventId());
        }

        notificationService.sendApplicationReceived(
              event.getEventId(),
              event.getRecruiterId(),
              event.getApplicantName(),
              event.getJobTitle(),
              event.getApplicationId(),
              event.getJobId()
        );

        log.info("Successfully processed ApplicationSubmittedEvent for applicationId: {}", event.getApplicationId());
    }
}