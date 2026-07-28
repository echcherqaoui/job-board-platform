package com.echcherqaoui.jobboard.userservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.auth.event.RecruiterRegisteredEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.userservice.kafka.handler.AuthHandler;
import com.echcherqaoui.jobboard.userservice.service.RecruiterProfileService;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RecruiterRegisteredHandler implements AuthHandler {

    private final RecruiterProfileService recruiterService;
    private final SignatureService signatureService;

    @Override
    public String getDescriptorFullName() {
        return RecruiterRegisteredEvent.getDescriptor().getFullName();
    }

    @Override
    public void handle(Message payload) {
        RecruiterRegisteredEvent event = (RecruiterRegisteredEvent) payload;

        

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getUserId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid)
            throw new EventSecurityException(event.getEventId());

        recruiterService.initializeRecruiter(
              UUID.fromString(event.getUserId()),
              event.getEmail(),
              event.getFirstName(),
              event.getLastName()
        );
    }
}