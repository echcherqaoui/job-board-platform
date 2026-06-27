package com.echcherqaoui.jobboard.jobservice.consumer;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.event.CompanyDeletedEvent;
import com.echcherqaoui.jobboard.user.event.CompanyUpsertedEvent;
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
          topics = "${kafka.topics.company.company-upserted}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "companyUpsertedListenerContainerFactory"
    )
    public void onCompanyUpserted(@NonNull CompanyUpsertedEvent event, @NonNull Acknowledgment ack) {
        validateEvent(
              event.getEventId(),
              event.getRecruiterId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        log.info("Received CompanyUpsertedEvent for recruiter {}", event.getRecruiterId());
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
    public void onCompanyDeleted(@NonNull CompanyDeletedEvent event, @NonNull Acknowledgment ack) {
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