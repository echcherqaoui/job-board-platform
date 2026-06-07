package com.echcherqaoui.jobboard.userservice.service.impl;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.event.CompanyCreatedEvent;
import com.echcherqaoui.jobboard.event.CompanyDeletedEvent;
import com.echcherqaoui.jobboard.event.CompanyUpdatedEvent;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.echcherqaoui.jobboard.userservice.service.CompanyOutboxService;
import com.echcherqaoui.jobboard.util.InstantConverter;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyOutboxServiceImpl implements CompanyOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProtobufSerializer<Message> serializer;
    private final SignatureService signatureService;

    private static final String AGGREGATE_TYPE = "company";
    private static final String EVENT_TYPE_COMPANY_CREATED = "company-created";
    private static final String EVENT_TYPE_COMPANY_UPDATED = "company-updated";
    private static final String EVENT_TYPE_COMPANY_DELETED = "company-deleted";

    // Placeholder topic — RecordNameStrategy resolves Schema Registry subject
    // from the fully-qualified Protobuf type name, not this value.
    private static final String SERIALIZATION_CONTEXT = "outbox-serialization-context";

    private Message buildProto(@NonNull String eventType,
                               String eventId,
                               String recruiterId,
                               String signature,
                               Instant now,
                               @NonNull RecruiterProfile profile) {
        Timestamp occurredAt = InstantConverter.toTimestamp(now);
        String companyLogoUrl = profile.getCompanyLogoUrl() != null ? profile.getCompanyLogoUrl() : "";

        return switch (eventType) {
            case EVENT_TYPE_COMPANY_CREATED -> CompanyCreatedEvent.newBuilder()
                  .setEventId(eventId)
                  .setRecruiterId(recruiterId)
                  .setCompanyName(profile.getCompanyName())
                  .setCompanyLogo(companyLogoUrl)
                  .setSignature(signature)
                  .setOccurredAt(occurredAt)
                  .build();

            case EVENT_TYPE_COMPANY_UPDATED -> CompanyUpdatedEvent.newBuilder()
                  .setEventId(eventId)
                  .setRecruiterId(recruiterId)
                  .setCompanyName(profile.getCompanyName())
                  .setCompanyLogo(companyLogoUrl)
                  .setSignature(signature)
                  .setOccurredAt(occurredAt)
                  .build();

            case EVENT_TYPE_COMPANY_DELETED -> CompanyDeletedEvent.newBuilder()
                  .setEventId(eventId)
                  .setRecruiterId(recruiterId)
                  .setSignature(signature)
                  .setOccurredAt(occurredAt)
                  .build();

            default -> throw new IllegalArgumentException("Unknown company event type: " + eventType);
        };
    }

    private void publishCompanyEvent(@NonNull RecruiterProfile profile,
                                     String eventType) {
        Instant now = Instant.now();

        String eventId = UUID.randomUUID().toString();
        String recruiterId = profile.getId().toString();

        String signature = signatureService.sign(
              eventId,
              recruiterId,
              String.valueOf(now.getEpochSecond())
        );

        Message proto = buildProto(
              eventType,
              eventId,
              recruiterId,
              signature,
              now,
              profile
        );

        byte[] serializedPayload = serializer.serialize(SERIALIZATION_CONTEXT, proto);

        OutboxEvent outboxEvent = new OutboxEvent()
              .setId(UUID.randomUUID())
              .setCreatedAt(OffsetDateTime.now())
              .setAggregateType(AGGREGATE_TYPE)
              .setAggregateId(recruiterId)
              .setEventType(eventType)
              .setPayload(serializedPayload);

        outboxEventRepository.save(outboxEvent);
    }

    @Override
    public void publishCompanyCreated(RecruiterProfile profile) {
        publishCompanyEvent(profile, EVENT_TYPE_COMPANY_CREATED);
    }

    @Override
    public void publishCompanyUpdated(RecruiterProfile profile) {
        publishCompanyEvent(profile, EVENT_TYPE_COMPANY_UPDATED);
    }

    @Override
    public void publishCompanyDeleted(RecruiterProfile profile) {
        publishCompanyEvent(profile, EVENT_TYPE_COMPANY_DELETED);
    }
}
