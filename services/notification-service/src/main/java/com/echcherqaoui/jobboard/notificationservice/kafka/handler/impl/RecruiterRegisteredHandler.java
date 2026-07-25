package com.echcherqaoui.jobboard.notificationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.auth.event.RecruiterRegisteredEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.notificationservice.kafka.handler.AuthHandler;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecruiterRegisteredHandler implements AuthHandler {

    private final NotificationService notificationService;
    private final SignatureService signatureService;

    @Override
    public String getDescriptorFullName() {
        return RecruiterRegisteredEvent.getDescriptor().getFullName();
    }

    /**
     * Send welcome message to the new recruiter.
     */
    @Override
    public void handle(Message payload) {
        RecruiterRegisteredEvent event = (RecruiterRegisteredEvent) payload;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getUserId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid) {
            log.warn(
                  "Signature verification failed for eventId: {}, userId: {}",
                  event.getEventId(),
                  event.getUserId()
            );
            throw new EventSecurityException(event.getEventId());
        }

        notificationService.sendWelcome(
              event.getUserId(),
              event.getEmail(),
              "RECRUITER"
        );

        log.info("Successfully processed JobSeekerRegisteredEvent for userId: {}", event.getUserId());
    }
}