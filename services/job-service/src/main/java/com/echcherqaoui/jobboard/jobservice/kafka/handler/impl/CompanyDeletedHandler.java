package com.echcherqaoui.jobboard.jobservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.jobservice.kafka.handler.UserHandler;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.event.CompanyDeletedEvent;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyDeletedHandler implements UserHandler {

    private final CompanyProfileService companyProfileService;
    private final SignatureService signatureService;

    @Override
    public String getDescriptorFullName() {
        return CompanyDeletedEvent.getDescriptor().getFullName();
    }

    @Override
    public void handle(Message payload) {
        CompanyDeletedEvent event = (CompanyDeletedEvent) payload;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getRecruiterId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid)
            throw new EventSecurityException(event.getEventId());

        companyProfileService.delete(UUID.fromString(event.getRecruiterId()));
    }
}