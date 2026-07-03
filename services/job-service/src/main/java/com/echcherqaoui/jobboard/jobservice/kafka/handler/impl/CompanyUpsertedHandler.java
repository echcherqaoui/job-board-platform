package com.echcherqaoui.jobboard.jobservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.jobservice.kafka.handler.CompanyEventHandler;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.event.CompanyUpsertedEvent;
import com.echcherqaoui.jobboard.util.InstantConverter;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static java.time.ZoneOffset.UTC;

@Component
@RequiredArgsConstructor
public class CompanyUpsertedHandler implements CompanyEventHandler {

    private final CompanyProfileService companyProfileService;
    private final SignatureService signatureService;

    @Override
    public String getDescriptorFullName() {
        return CompanyUpsertedEvent.getDescriptor().getFullName();
    }

    @Override
    public void handle(Message payload) {
        CompanyUpsertedEvent event = (CompanyUpsertedEvent) payload;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getRecruiterId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid)
            throw new EventSecurityException(event.getEventId());

        companyProfileService.upsert(
              UUID.fromString(event.getRecruiterId()),
              event.getCompanyName(),
              event.getCompanyLogo(),
              event.getEventId(),
              InstantConverter.toInstant(event.getOccurredAt()).atOffset(UTC)
        );
    }
}