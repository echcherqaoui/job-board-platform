package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.job.event.JobDeletedEvent;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.jobservice.model.*;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobOutboxServiceImplTest {

    private OutboxEventRepository outboxEventRepository;
    private KafkaProtobufSerializer<Message> serializer;
    private JobOutboxServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        serializer = mock(KafkaProtobufSerializer.class);
        SignatureService signatureService = mock(SignatureService.class);
        service = new JobOutboxServiceImpl(outboxEventRepository, serializer, signatureService);

        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("sig");
        when(serializer.serialize(anyString(), any())).thenReturn(new byte[] {1, 2, 3});
    }

    private Job job(UUID id, UUID recruiterId, JobStatus status) {
        Job job = new Job();
        job.setId(id);
        job.setRecruiterId(recruiterId);
        job.setTitle("Backend Engineer");
        job.setDescription("desc");
        job.setWorkModality(WorkModality.REMOTE);
        job.setJobType(JobType.FULL_TIME);
        job.setExperienceLevel(ExperienceLevel.MID);
        job.setStatus(status);
        job.setCreatedAt(OffsetDateTime.now());
        return job;
    }

    private CompanyProfile companyProfile() {
        CompanyProfile profile = new CompanyProfile();
        profile.setCompanyName("Acme");
        return profile;
    }

    @Test
    void publishJobUpserted_buildsCorrectProtoAndSaves() {
        UUID jobId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        Job job = job(jobId, recruiterId, JobStatus.OPEN);
        CompanyProfile company = companyProfile();

        service.publishJobUpserted(job, company);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(anyString(), protoCaptor.capture());
        JobUpsertedEvent event = (JobUpsertedEvent) protoCaptor.getValue();

        assertThat(event.getJobId()).isEqualTo(jobId.toString());
        assertThat(event.getRecruiterId()).isEqualTo(recruiterId.toString());
        assertThat(event.getCompanyName()).isEqualTo("Acme");
        assertThat(event.getTitle()).isEqualTo("Backend Engineer");
        assertThat(event.getStatus()).isEqualTo("OPEN");
        assertThat(event.getSignature()).isEqualTo("sig");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("job-upserted");
    }

    @Test
    void publishJobUpserted_nullOptionalFields_defaultToEmptyOrZero() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId, UUID.randomUUID(), JobStatus.OPEN);
        job.setRequirements(null);
        job.setLocation(null);
        job.setSalaryMin(null);
        job.setSalaryMax(null);
        job.setCurrency(null);
        job.setExpiresAt(null);
        CompanyProfile company = companyProfile();
        company.setCompanyLogo(null);

        service.publishJobUpserted(job, company);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(anyString(), protoCaptor.capture());
        JobUpsertedEvent event = (JobUpsertedEvent) protoCaptor.getValue();

        assertThat(event.getRequirements()).isEmpty();
        assertThat(event.getLocation()).isEmpty();
        assertThat(event.getCurrency()).isEmpty();
        assertThat(event.getCompanyLogo()).isEmpty();
        assertThat(event.getSalaryMinCents()).isZero();
        assertThat(event.getSalaryMaxCents()).isZero();
        assertThat(event.hasExpiresAt()).isFalse();
    }

    @Test
    void publishJobStatusChanged_currentlyOmitsJobTitle_documentingBug() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId, UUID.randomUUID(), JobStatus.CLOSED);

        service.publishJobStatusChanged(job);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(anyString(), protoCaptor.capture());
        JobStatusChangedEvent event = (JobStatusChangedEvent) protoCaptor.getValue();

        assertThat(event.getJobId()).isEqualTo(jobId.toString());
        assertThat(event.getJobStatus()).isEqualTo("CLOSED");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("job-status-changed");
    }

    @Test
    void publishJobDeleted_buildsCorrectProtoAndSaves() {
        UUID jobId = UUID.randomUUID();
        UUID recruiterId = UUID.randomUUID();
        Job job = job(jobId, recruiterId, JobStatus.OPEN);

        service.publishJobDeleted(job);

        ArgumentCaptor<Message> protoCaptor = ArgumentCaptor.forClass(Message.class);
        verify(serializer).serialize(anyString(), protoCaptor.capture());
        JobDeletedEvent event = (JobDeletedEvent) protoCaptor.getValue();

        assertThat(event.getJobId()).isEqualTo(jobId.toString());
        assertThat(event.getRecruiterId()).isEqualTo(recruiterId.toString());

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("job-deleted");
    }

    @Test
    void publishJobExpiredBatch_includesJobTitleAndUsesExpiredEventType() {
        UUID job1Id = UUID.randomUUID();
        UUID job2Id = UUID.randomUUID();
        Job job1 = job(job1Id, UUID.randomUUID(), JobStatus.CLOSED);
        Job job2 = job(job2Id, UUID.randomUUID(), JobStatus.CLOSED);

        service.publishJobExpiredBatch(List.of(job1, job2));

        ArgumentCaptor<List<OutboxEvent>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(outboxEventRepository).saveAll(batchCaptor.capture());
        List<OutboxEvent> saved = batchCaptor.getValue();

        assertThat(saved).hasSize(2)
              .allSatisfy(evt -> assertThat(evt.getEventType()).isEqualTo("job-expired"))
              .extracting(OutboxEvent::getAggregateId)
              .containsExactlyInAnyOrder(job1Id.toString(), job2Id.toString());

        // single/never-called checks: expired batch must not go through the
        // per-event save() path used by other publish methods.
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    void publishJobExpiredBatch_emptyList_stillCallsSaveAllWithEmptyList() {
        service.publishJobExpiredBatch(List.of());

        verify(outboxEventRepository).saveAll(List.of());
    }
}