package com.echcherqaoui.jobboard.userservice.service.impl;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.event.CompanyDeletedEvent;
import com.echcherqaoui.jobboard.user.event.CompanyUpsertedEvent;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class CompanyOutboxServiceImplTest {

    private OutboxEventRepository outboxEventRepository;
    private KafkaProtobufSerializer<Message> serializer;
    private SignatureService signatureService;
    private CompanyOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        serializer = mock(KafkaProtobufSerializer.class);
        signatureService = mock(SignatureService.class);

        service = new CompanyOutboxServiceImpl(outboxEventRepository, serializer, signatureService);

        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("signed-value");
        when(serializer.serialize(anyString(), any(Message.class))).thenReturn(new byte[]{1, 2, 3});
    }

    private RecruiterProfile buildProfile(String companyLogoUrl) {
        return new RecruiterProfile()
              .setId(UUID.randomUUID())
              .setCompanyName("Acme Corp")
              .setCompanyLogoUrl(companyLogoUrl);
    }

    @Test
    void publishCompanyUpserted_buildsCorrectProtoAndOutboxEvent() {
        RecruiterProfile profile = buildProfile("http://logo.url/acme.png");

        service.publishCompanyUpserted(profile);

        verify(signatureService).sign(anyString(), eq(profile.getId().toString()), anyString());

        var protoCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(eq("outbox-serialization-context"), protoCaptor.capture());

        CompanyUpsertedEvent proto = (CompanyUpsertedEvent) protoCaptor.getValue();
        assertThat(proto.getRecruiterId()).isEqualTo(profile.getId().toString());
        assertThat(proto.getCompanyName()).isEqualTo("Acme Corp");
        assertThat(proto.getCompanyLogo()).isEqualTo("http://logo.url/acme.png");
        assertThat(proto.getSignature()).isEqualTo("signed-value");
        assertThat(proto.getEventId()).isNotBlank();

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());

        OutboxEvent saved = eventCaptor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("company");
        assertThat(saved.getAggregateId()).isEqualTo(profile.getId().toString());
        assertThat(saved.getEventType()).isEqualTo("company-upserted");
        assertThat(saved.getPayload()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void publishCompanyUpserted_nullCompanyLogoUrl_defaultsToEmptyString() {
        RecruiterProfile profile = buildProfile(null);

        service.publishCompanyUpserted(profile);

        var protoCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(anyString(), protoCaptor.capture());

        CompanyUpsertedEvent proto = (CompanyUpsertedEvent) protoCaptor.getValue();
        assertThat(proto.getCompanyLogo()).isEmpty();
    }

    @Test
    void publishCompanyDeleted_buildsCorrectProtoAndOutboxEvent() {
        RecruiterProfile profile = buildProfile("http://logo.url/acme.png");

        service.publishCompanyDeleted(profile);

        var protoCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(anyString(), protoCaptor.capture());

        CompanyDeletedEvent proto = (CompanyDeletedEvent) protoCaptor.getValue();
        assertThat(proto.getRecruiterId()).isEqualTo(profile.getId().toString());
        assertThat(proto.getSignature()).isEqualTo("signed-value");
        assertThat(proto.getEventId()).isNotBlank();

        var eventCaptor = org.mockito.ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());

        OutboxEvent saved = eventCaptor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("company");
        assertThat(saved.getAggregateId()).isEqualTo(profile.getId().toString());
        assertThat(saved.getEventType()).isEqualTo("company-deleted");
    }

    @Test
    void publish_generatesUniqueEventIdsAcrossCalls() {
        RecruiterProfile profile = buildProfile("logo.png");

        service.publishCompanyUpserted(profile);
        service.publishCompanyUpserted(profile);

        var protoCaptor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(serializer, times(2)).serialize(anyString(), protoCaptor.capture());

        var events = protoCaptor.getAllValues();
        String id1 = ((CompanyUpsertedEvent) events.get(0)).getEventId();
        String id2 = ((CompanyUpsertedEvent) events.get(1)).getEventId();
        assertThat(id1).isNotEqualTo(id2);
    }
}