package com.echcherqaoui.jobboard.userservice.service.impl;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.event.CompanyDeletedEvent;
import com.echcherqaoui.jobboard.user.event.CompanyUpsertedEvent;
import com.echcherqaoui.jobboard.userservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class CompanyOutboxServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyOutboxServiceImpl companyOutboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private SignatureService signatureService;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });
    }

    @Nested
    class PublishCompanyUpserted {

        @Test
        void publishCompanyUpserted_ShouldPersistOutboxEventWithValidProtobufAndSignature() throws InvalidProtocolBufferException {
            UUID recruiterId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();

            RecruiterProfile profile = new RecruiterProfile()
                  .setId(recruiterId)
                  .setEmail("recruiter@acme.com")
                  .setCompanyName("Acme Corp")
                  .setCompanyLogoUrl("https://acme.com/logo.png")
                  .setCreatedAt(now)
                  .setUpdatedAt(now);

            companyOutboxService.publishCompanyUpserted(profile);

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent event = events.get(0);
            assertThat(event.getAggregateType()).isEqualTo("company");
            assertThat(event.getAggregateId()).isEqualTo(recruiterId.toString());
            assertThat(event.getEventType()).isEqualTo("company-upserted");
            assertThat(event.getCreatedAt()).isNotNull();

            CompanyUpsertedEvent proto = CompanyUpsertedEvent.parseFrom(event.getPayload());
            assertThat(proto.getRecruiterId()).isEqualTo(recruiterId.toString());
            assertThat(proto.getCompanyName()).isEqualTo("Acme Corp");
            assertThat(proto.getCompanyLogo()).isEqualTo("https://acme.com/logo.png");

            boolean isValidSignature = signatureService.verify(
                  proto.getEventId(),
                  proto.getRecruiterId(),
                  String.valueOf(proto.getOccurredAt().getSeconds()),
                  proto.getSignature()
            );
            assertThat(isValidSignature).isTrue();
        }

        @Test
        void publishCompanyUpserted_WhenLogoUrlIsNull_ShouldDefaultToEmptyStringInProto() throws InvalidProtocolBufferException {
            UUID recruiterId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();

            RecruiterProfile profile = new RecruiterProfile()
                  .setId(recruiterId)
                  .setEmail("recruiter@acme.com")
                  .setCompanyName("Acme Corp")
                  .setCompanyLogoUrl(null)
                  .setCreatedAt(now)
                  .setUpdatedAt(now);

            companyOutboxService.publishCompanyUpserted(profile);

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            CompanyUpsertedEvent proto = CompanyUpsertedEvent.parseFrom(events.get(0).getPayload());
            assertThat(proto.getCompanyLogo()).isEmpty();
        }

        @Test
        void publishCompanyUpserted_WhenProfileIsNull_ShouldThrowNullPointerException() {
            assertThatThrownBy(() -> companyOutboxService.publishCompanyUpserted(null))
                  .isInstanceOf(NullPointerException.class);

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class PublishCompanyDeleted {

        @Test
        void publishCompanyDeleted_ShouldPersistDeletedOutboxEventWithValidSignature() throws InvalidProtocolBufferException {
            UUID recruiterId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();

            RecruiterProfile profile = new RecruiterProfile()
                  .setId(recruiterId)
                  .setEmail("recruiter@acme.com")
                  .setCompanyName("Acme Corp")
                  .setCreatedAt(now)
                  .setUpdatedAt(now);

            companyOutboxService.publishCompanyDeleted(profile);

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent event = events.get(0);
            assertThat(event.getAggregateType()).isEqualTo("company");
            assertThat(event.getAggregateId()).isEqualTo(recruiterId.toString());
            assertThat(event.getEventType()).isEqualTo("company-deleted");

            CompanyDeletedEvent proto = CompanyDeletedEvent.parseFrom(event.getPayload());
            assertThat(proto.getRecruiterId()).isEqualTo(recruiterId.toString());

            boolean isValidSignature = signatureService.verify(
                  proto.getEventId(),
                  proto.getRecruiterId(),
                  String.valueOf(proto.getOccurredAt().getSeconds()),
                  proto.getSignature()
            );
            assertThat(isValidSignature).isTrue();
        }

        @Test
        void publishCompanyDeleted_WhenProfileIsNull_ShouldThrowNullPointerException() {
            assertThatThrownBy(() -> companyOutboxService.publishCompanyDeleted(null))
                  .isInstanceOf(NullPointerException.class);

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }
    }
}