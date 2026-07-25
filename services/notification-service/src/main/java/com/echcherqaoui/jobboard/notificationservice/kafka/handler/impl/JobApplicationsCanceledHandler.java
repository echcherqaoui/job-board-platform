package com.echcherqaoui.jobboard.notificationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.application.event.JobApplicationsCanceledEvent;
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
public class JobApplicationsCanceledHandler implements ApplicationHandler {

    private final NotificationService notificationService;
    private final SignatureService signatureService;

    @Override
    public String getDescriptorFullName() {
        return JobApplicationsCanceledEvent.getDescriptor().getFullName();
    }

    @Override
    public void handle(Message payload) {
        JobApplicationsCanceledEvent event = (JobApplicationsCanceledEvent) payload;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getJobId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid) {
            log.warn(
                  "Signature verification failed for eventId: {}, jobId: {}",
                  event.getEventId(),
                  event.getJobId()
            );
            throw new EventSecurityException(event.getEventId());
        }

        notificationService.sendApplicationsCanceled(
              event.getEventId(),
              event.getJobId(),
              event.getJobTitle(),
              event.getApplicantIdsList()
        );

        log.info(
              "Successfully processed JobApplicationsCanceledEvent for jobId: {}, total applicants: {}",
              event.getJobId(),
              event.getApplicantIdsCount()
        );
    }
}