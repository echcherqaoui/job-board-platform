package com.echcherqaoui.jobboard.jobservice.consumer;

import com.echcherqaoui.jobboard.event.CompanyCreatedEvent;
import com.echcherqaoui.jobboard.event.CompanyDeletedEvent;
import com.echcherqaoui.jobboard.event.CompanyUpdatedEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.util.InstantConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static java.time.ZoneOffset.UTC;

@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyProfileEventConsumer {

    private final CompanyProfileService companyProfileService;
    private final SignatureService signatureService;

    private void validateEvent(String eventId,
                               String recruiterId,
                               String occurredAt,
                               String signature) {
        boolean valid = signatureService.verify(
              eventId,
              recruiterId,
              occurredAt,
              signature
        );

        if (!valid)
            throw new EventSecurityException(eventId);
    }

    @KafkaListener(
          topics = "${kafka.topics.company.company-created}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "companyCreatedListenerContainerFactory"
    )
    public void onCompanyCreated(@NonNull CompanyCreatedEvent event, Acknowledgment ack) {
        validateEvent(
              event.getEventId(),
              event.getRecruiterId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        log.info("Received CompanyCreatedEvent for recruiter {}", event.getRecruiterId());
        companyProfileService.upsert(
              UUID.fromString(event.getRecruiterId()),
              event.getCompanyName(),
              event.getCompanyLogo(),
              event.getEventId(),
              InstantConverter.toInstant(event.getOccurredAt()).atOffset(UTC)
        );

        ack.acknowledge();
    }

    @KafkaListener(
          topics = "${kafka.topics.company.company-updated}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "companyUpdatedListenerContainerFactory"
    )
    public void onCompanyUpdated(@NonNull CompanyUpdatedEvent event, Acknowledgment ack) {
        validateEvent(
              event.getEventId(),
              event.getRecruiterId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        log.info("Received CompanyUpdatedEvent for recruiter {}", event.getRecruiterId());
        companyProfileService.upsert(
              UUID.fromString(event.getRecruiterId()),
              event.getCompanyName(),
              event.getCompanyLogo(),
              event.getEventId(),
              InstantConverter.toInstant(event.getOccurredAt()).atOffset(UTC)
        );

        ack.acknowledge();
    }

    @KafkaListener(
          topics = "${kafka.topics.company.company-deleted}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "companyDeletedListenerContainerFactory"
    )
    public void onCompanyDeleted(@NonNull CompanyDeletedEvent event, Acknowledgment ack) {
        validateEvent(
              event.getEventId(),
              event.getRecruiterId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        log.info("Received CompanyDeletedEvent for recruiter {}", event.getRecruiterId());

        companyProfileService.delete(UUID.fromString(event.getRecruiterId()));

        ack.acknowledge();
    }
}