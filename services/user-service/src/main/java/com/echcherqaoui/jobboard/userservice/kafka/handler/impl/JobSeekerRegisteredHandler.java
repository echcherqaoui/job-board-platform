package com.echcherqaoui.jobboard.userservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.auth.event.JobSeekerRegisteredEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.userservice.kafka.handler.AuthHandler;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JobSeekerRegisteredHandler implements AuthHandler {

    private final SignatureService signatureService;
    private final JobSeekerProfileService jobSeekerService;

    @Override
    public String getDescriptorFullName() {
        return JobSeekerRegisteredEvent.getDescriptor().getFullName();
    }

    @Override
    public void handle(Message payload) {
        JobSeekerRegisteredEvent event = (JobSeekerRegisteredEvent) payload;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getUserId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid)
            throw new EventSecurityException(event.getEventId());

        jobSeekerService.initializeProfile(
              UUID.fromString(event.getUserId()),
              event.getEmail(),
              event.getFirstName(),
              event.getLastName()
        );
    }
}