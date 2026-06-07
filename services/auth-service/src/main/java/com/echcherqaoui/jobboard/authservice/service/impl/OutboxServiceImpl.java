package com.echcherqaoui.jobboard.authservice.service.impl;

import com.echcherqaoui.jobboard.authservice.model.AppUser;
import com.echcherqaoui.jobboard.authservice.service.OutboxService;
import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.event.UserCreatedEvent;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.util.InstantConverter;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProtobufSerializer<Message> serializer;
    private final SignatureService signatureService;

    private static final String AGGREGATE_TYPE = "auth";
    private static final String EVENT_TYPE_JOB_SEEKER_REGISTERED = "job-seeker-registered";
    private static final String EVENT_TYPE_RECRUITER_REGISTERED = "recruiter-registered";

    // Schema Registry subject is determined by RecordNameStrategy (fully qualified Protobuf type name),
    // not by the topic passed to serialize(). This constant is a placeholder to satisfy the API contract
    // while making the intent explicit — Debezium handles routing via event_type, not this value.
    private static final String SERIALIZATION_CONTEXT = "outbox-serialization-context";

    /**
     * Serializes the user signup data into a Protobuf payload and pushes it to the outbox table.
     * Must be executed within the same database transaction as the user registration save.
     */
    private void publishUserCreated(@NonNull AppUser user,
                                    String eventType) {
        Instant now = Instant.now();

        String eventId = UUID.randomUUID().toString();
        String userId = user.getId().toString();

        // Sign the event using eventId + userId + timestamp to prevent fake event injection
        String signature = signatureService.sign(
              eventId,
              userId,
              String.valueOf(now.getEpochSecond())
        );

        UserCreatedEvent proto = UserCreatedEvent.newBuilder()
              .setEventId(eventId)
              .setUserId(userId)
              .setFirstName(user.getFirstName())
              .setLastName(user.getLastName())
              .setEmail(user.getEmail())
              .setSignature(signature)
              .setOccurredAt(InstantConverter.toTimestamp(now))
              .build();

        // Serialize to Schema Registry wire format: [0x00][schema_id][protobuf bytes]
        // Must serialize here so Debezium passes bytes as-is to Kafka via ByteArrayConverter
        byte[] serializedPayload = serializer.serialize(SERIALIZATION_CONTEXT, proto);

        OutboxEvent outboxEvent = new OutboxEvent()
              .setId(UUID.randomUUID())
              .setCreatedAt(OffsetDateTime.now())
              .setAggregateType(AGGREGATE_TYPE)
              .setAggregateId(userId)
              .setEventType(eventType)
              .setPayload(serializedPayload);

        outboxEventRepository.save(outboxEvent);
    }

    @Override
    public void publishJobSeekerCreated(AppUser user) {
        publishUserCreated(
              user,
              EVENT_TYPE_JOB_SEEKER_REGISTERED
        );
    }

    @Override
    public void publishRecruiterCreated(AppUser user) {
        publishUserCreated(
              user,
              EVENT_TYPE_RECRUITER_REGISTERED
        );
    }
}