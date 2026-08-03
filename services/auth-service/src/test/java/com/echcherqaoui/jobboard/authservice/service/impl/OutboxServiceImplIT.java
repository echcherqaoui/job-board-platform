package com.echcherqaoui.jobboard.authservice.service.impl;

import com.echcherqaoui.jobboard.auth.event.JobSeekerRegisteredEvent;
import com.echcherqaoui.jobboard.auth.event.RecruiterRegisteredEvent;
import com.echcherqaoui.jobboard.authservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.authservice.enums.UserRole;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class OutboxServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxServiceImpl outboxService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @MockitoBean
    private SignatureService signatureService;

    @MockitoBean
    private JWKSource<SecurityContext> jwkSource;

    private AppUser sampleUser;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();

        sampleUser = new AppUser()
              .setEmail("test@jobboard.com")
              .setUsername("test_user")
              .setFirstName("Ahmed")
              .setLastName("EDER")
              .setPassword("password123")
              .setRole(UserRole.CANDIDATE)
              .setEnabled(true);
        sampleUser.setId(UUID.randomUUID());

        when(signatureService.sign(anyString(), anyString(), anyString()))
              .thenReturn("valid-hmac-signature");

        when(serializer.serialize(eq("outbox-serialization-context"), any(Message.class)))
              .thenAnswer(invocation -> ((Message)invocation.getArgument(1)).toByteArray());
    }

    @Test
    void publishJobSeekerCreated_ShouldPersistOutboxEventAndGenerateSignature() throws InvalidProtocolBufferException {
        outboxService.publishJobSeekerCreated(sampleUser);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getId()).isNotNull();
        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getAggregateType()).isEqualTo("auth");
        assertThat(event.getAggregateId()).isEqualTo(sampleUser.getId().toString());
        assertThat(event.getEventType()).isEqualTo("job-seeker-registered");
        assertThat(event.getPayload()).isNotEmpty();

        JobSeekerRegisteredEvent payload = JobSeekerRegisteredEvent.parseFrom(event.getPayload());
        assertThat(payload.getUserId()).isEqualTo(sampleUser.getId().toString());
        assertThat(payload.getFirstName()).isEqualTo("Ahmed");
        assertThat(payload.getLastName()).isEqualTo("EDER");
        assertThat(payload.getEmail()).isEqualTo("test@jobboard.com");
        assertThat(payload.getSignature()).isEqualTo("valid-hmac-signature");
        assertThat(payload.getEventId()).isNotBlank();
        assertThat(payload.hasOccurredAt()).isTrue();

        ArgumentCaptor<String> eventIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> timestampCaptor = ArgumentCaptor.forClass(String.class);

        verify(signatureService).sign(eventIdCaptor.capture(), userIdCaptor.capture(), timestampCaptor.capture());
        assertThat(userIdCaptor.getValue()).isEqualTo(sampleUser.getId().toString());
        assertThat(eventIdCaptor.getValue()).isEqualTo(payload.getEventId());
    }

    @Test
    void publishRecruiterCreated_ShouldPersistOutboxEventAndGenerateSignature() throws InvalidProtocolBufferException {
        sampleUser.setRole(UserRole.RECRUITER);

        outboxService.publishRecruiterCreated(sampleUser);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getAggregateType()).isEqualTo("auth");
        assertThat(event.getAggregateId()).isEqualTo(sampleUser.getId().toString());
        assertThat(event.getEventType()).isEqualTo("recruiter-registered");

        RecruiterRegisteredEvent payload = RecruiterRegisteredEvent.parseFrom(event.getPayload());
        assertThat(payload.getUserId()).isEqualTo(sampleUser.getId().toString());
        assertThat(payload.getFirstName()).isEqualTo("Ahmed");
        assertThat(payload.getLastName()).isEqualTo("EDER");
        assertThat(payload.getEmail()).isEqualTo("test@jobboard.com");
        assertThat(payload.getSignature()).isEqualTo("valid-hmac-signature");
        assertThat(payload.getEventId()).isNotBlank();
        assertThat(payload.hasOccurredAt()).isTrue();
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void publishJobSeekerCreated_NullUser_ShouldThrowException() {
        assertThatThrownBy(() -> outboxService.publishJobSeekerCreated(null))
              .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("DataFlowIssue")
    void publishRecruiterCreated_NullUser_ShouldThrowException() {
        assertThatThrownBy(() -> outboxService.publishRecruiterCreated(null))
              .isInstanceOf(NullPointerException.class);
    }
}