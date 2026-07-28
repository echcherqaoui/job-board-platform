package com.echcherqaoui.jobboard.authservice.service.impl;

import com.echcherqaoui.jobboard.auth.event.JobSeekerRegisteredEvent;
import com.echcherqaoui.jobboard.auth.event.RecruiterRegisteredEvent;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxServiceImplTest {

    private OutboxEventRepository outboxEventRepository;
    private KafkaProtobufSerializer<Message> serializer;
    private SignatureService signatureService;
    private OutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);

        serializer = mock(KafkaProtobufSerializer.class);
        signatureService = mock(SignatureService.class);
        service = new OutboxServiceImpl(outboxEventRepository, serializer, signatureService);
    }

    private AppUser mockUser() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(user.getFirstName()).thenReturn("Ahmed");
        when(user.getLastName()).thenReturn("EDER");
        when(user.getEmail()).thenReturn("ahmed@example.com");
        return user;
    }

    @Test
    void publishJobSeekerCreated_buildsCorrectProtoAndSavesOutboxEvent() {
        AppUser user = mockUser();
        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("sig-abc");
        when(serializer.serialize(anyString(), any())).thenReturn(new byte[] {1, 2, 3});

        service.publishJobSeekerCreated(user);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(eq("outbox-serialization-context"), protoCaptor.capture());
        JobSeekerRegisteredEvent proto = (JobSeekerRegisteredEvent) protoCaptor.getValue();

        assertThat(proto.getUserId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(proto.getFirstName()).isEqualTo("Ahmed");
        assertThat(proto.getLastName()).isEqualTo("EDER");
        assertThat(proto.getEmail()).isEqualTo("ahmed@example.com");
        assertThat(proto.getSignature()).isEqualTo("sig-abc");
        assertThat(proto.getEventId()).isNotBlank();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getAggregateType()).isEqualTo("auth");
        assertThat(savedEvent.getAggregateId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(savedEvent.getEventType()).isEqualTo("job-seeker-registered");
        assertThat(savedEvent.getPayload()).isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    void publishRecruiterCreated_buildsCorrectProtoAndSavesOutboxEvent() {
        AppUser user = mockUser();
        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("sig-xyz");
        when(serializer.serialize(anyString(), any())).thenReturn(new byte[] {4, 5, 6});

        service.publishRecruiterCreated(user);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(eq("outbox-serialization-context"), protoCaptor.capture());
        RecruiterRegisteredEvent proto = (RecruiterRegisteredEvent) protoCaptor.getValue();

        assertThat(proto.getUserId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(proto.getEmail()).isEqualTo("ahmed@example.com");
        assertThat(proto.getSignature()).isEqualTo("sig-xyz");

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();

        assertThat(savedEvent.getEventType()).isEqualTo("recruiter-registered");
        assertThat(savedEvent.getAggregateType()).isEqualTo("auth");
    }

    @Test
    void signatureService_calledWithEventIdUserIdAndEpochSecond() {
        AppUser user = mockUser();
        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("sig");
        when(serializer.serialize(anyString(), any())).thenReturn(new byte[] {1});

        service.publishJobSeekerCreated(user);

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> epochCaptor = ArgumentCaptor.forClass(String.class);
        verify(signatureService).sign(eventIdCaptor.capture(), eq("11111111-1111-1111-1111-111111111111"), epochCaptor.capture());

        assertThat(eventIdCaptor.getValue()).isNotBlank();
        long epochSecond = Long.parseLong(epochCaptor.getValue());
        long nowSecond = java.time.Instant.now().getEpochSecond();
        assertThat(epochSecond).isCloseTo(nowSecond, org.assertj.core.data.Offset.offset(5L));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void nullUser_throwsBeforeCallingDependencies() {
        assertThatThrownBy(() -> service.publishJobSeekerCreated(null))
              .isInstanceOf(Exception.class);

        verifyNoInteractions(signatureService, outboxEventRepository);
    }
}