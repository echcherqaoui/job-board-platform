package com.echcherqaoui.jobboard.applicationservice.service.impl;

import com.echcherqaoui.jobboard.application.event.ApplicationStatusChangedEvent;
import com.echcherqaoui.jobboard.application.event.ApplicationSubmittedEvent;
import com.echcherqaoui.jobboard.application.event.JobApplicationsCanceledEvent;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.PENDING;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.REVIEWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationOutboxServiceImplTest {

    private OutboxEventRepository outboxEventRepository;
    private KafkaProtobufSerializer<Message> serializer;
    private JwtContextHolder jwtContextHolder;
    private ApplicationOutboxServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        serializer = mock(KafkaProtobufSerializer.class);
        SignatureService signatureService = mock(SignatureService.class);
        jwtContextHolder = mock(JwtContextHolder.class);
        service = new ApplicationOutboxServiceImpl(outboxEventRepository, serializer, signatureService, jwtContextHolder);

        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("sig");
        when(serializer.serialize(anyString(), any())).thenReturn(new byte[] {1, 2, 3});
    }

    private Application application(UUID applicationId, UUID jobId, UUID applicantId) {
        Application app = new Application();
        app.setId(applicationId);
        app.setJobId(jobId);
        app.setApplicantId(applicantId);
        app.setStatus(PENDING);
        app.setSubmittedAt(OffsetDateTime.now());
        return app;
    }

    private JobSummary jobSummary(String jobId, String recruiterId) {
        return JobSummary.newBuilder()
              .setJobId(jobId)
              .setTitle("Backend Engineer")
              .setCompanyName("Acme")
              .setRecruiterId(recruiterId)
              .build();
    }

    @Test
    void publishApplicationSubmitted_buildsCorrectProtoAndSavesOutboxEvent() {
        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        Application app = application(applicationId, jobId, applicantId);
        JobSummary job = jobSummary(jobId.toString(), "recruiter-1");
        when(jwtContextHolder.getFullName()).thenReturn("Ahmed EDER");

        service.publishApplicationSubmitted(app, job);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(eq("outbox-serialization-context"), protoCaptor.capture());
        ApplicationSubmittedEvent event = (ApplicationSubmittedEvent) protoCaptor.getValue();

        assertThat(event.getApplicationId()).isEqualTo(applicationId.toString());
        assertThat(event.getJobId()).isEqualTo(jobId.toString());
        assertThat(event.getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(event.getRecruiterId()).isEqualTo("recruiter-1");
        assertThat(event.getApplicantId()).isEqualTo(applicantId.toString());
        assertThat(event.getApplicantName()).isEqualTo("Ahmed EDER");
        assertThat(event.getSignature()).isEqualTo("sig");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("application");
        assertThat(saved.getAggregateId()).isEqualTo(applicationId.toString());
        assertThat(saved.getEventType()).isEqualTo("application-submitted");
    }

    @Test
    void publishApplicationStatusUpdated_buildsCorrectProtoAndSavesOutboxEvent() {
        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Application app = application(applicationId, jobId, applicantId);
        JobSummary job = jobSummary(jobId.toString(), callerId.toString());

        service.publishApplicationStatusUpdated(app, PENDING, REVIEWED, job, callerId, "looks good");

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(eq("outbox-serialization-context"), protoCaptor.capture());
        ApplicationStatusChangedEvent event = (ApplicationStatusChangedEvent) protoCaptor.getValue();

        assertThat(event.getApplicationId()).isEqualTo(applicationId.toString());
        assertThat(event.getApplicantId()).isEqualTo(applicantId.toString());
        assertThat(event.getOldStatus()).isEqualTo("PENDING");
        assertThat(event.getNewStatus()).isEqualTo("REVIEWED");
        assertThat(event.getNote()).isEqualTo("looks good");
        assertThat(event.getChangedBy()).isEqualTo(callerId.toString());
        assertThat(event.getJobId()).isEqualTo(jobId.toString());
        assertThat(event.getCompanyName()).isEqualTo("Acme");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("application-status-changed");
    }

    @Test
    void publishApplicationStatusUpdated_nullNote_setsEmptyStringNotNull() {
        UUID applicationId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID applicantId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Application app = application(applicationId, jobId, applicantId);
        JobSummary job = jobSummary(jobId.toString(), callerId.toString());

        service.publishApplicationStatusUpdated(app, PENDING, REVIEWED, job, callerId, null);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(anyString(), protoCaptor.capture());
        ApplicationStatusChangedEvent event = (ApplicationStatusChangedEvent) protoCaptor.getValue();

        assertThat(event.getNote()).isEmpty();
    }

    @Test
    void publishJobApplicationsCanceled_buildsCorrectProtoAndSavesOutboxEvent() {
        UUID jobId = UUID.randomUUID();
        List<String> applicantIds = List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString());

        service.publishJobApplicationsCanceled(jobId, "Backend Engineer", applicantIds);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(eq("outbox-serialization-context"), protoCaptor.capture());
        JobApplicationsCanceledEvent event = (JobApplicationsCanceledEvent) protoCaptor.getValue();

        assertThat(event.getJobId()).isEqualTo(jobId.toString());
        assertThat(event.getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(event.getApplicantIdsList()).containsExactlyElementsOf(applicantIds);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent saved = outboxCaptor.getValue();

        assertThat(saved.getAggregateId()).isEqualTo(jobId.toString());
        assertThat(saved.getEventType()).isEqualTo("application-canceled");
    }

    @Test
    void publishJobApplicationsCanceled_emptyApplicantList_stillPublishes() {
        UUID jobId = UUID.randomUUID();

        service.publishJobApplicationsCanceled(jobId, "Backend Engineer", List.of());

        verify(outboxEventRepository).save(any());
    }
}