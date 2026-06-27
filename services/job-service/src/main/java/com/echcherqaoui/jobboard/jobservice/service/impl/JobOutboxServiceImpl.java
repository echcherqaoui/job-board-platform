package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.job.event.JobDeletedEvent;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.model.JobSkill;
import com.echcherqaoui.jobboard.jobservice.service.JobOutboxService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.util.InstantConverter;
import com.echcherqaoui.jobboard.util.MoneyConverter;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobOutboxServiceImpl implements JobOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProtobufSerializer<Message> serializer;
    private final SignatureService signatureService;

    private static final String AGGREGATE_TYPE = "job";
    private static final String EVENT_TYPE_JOB_UPSERTED = "job-upserted";
    private static final String EVENT_TYPE_JOB_DELETED = "job-deleted";
    private static final String EVENT_TYPE_JOB_STATUS_CHANGED = "job-status-changed";
    private static final String EVENT_TYPE_JOB_EXPIRED = "job-expired";
    private static final String SERIALIZATION_CONTEXT = "outbox-serialization-context";


    private void persist(@NonNull Message proto,
                         @NonNull String aggregateId,
                         @NonNull String eventType) {
        byte[] payload = serializer.serialize(SERIALIZATION_CONTEXT, proto);

        OutboxEvent outboxEvent = new OutboxEvent()
              .setId(UUID.randomUUID())
              .setCreatedAt(OffsetDateTime.now())
              .setAggregateType(AGGREGATE_TYPE)
              .setAggregateId(aggregateId)
              .setEventType(eventType)
              .setPayload(payload);

        outboxEventRepository.save(outboxEvent);
    }

    private void publishJobUpsertedEvent(@NonNull Job job,
                                         @NonNull CompanyProfile companyProfile) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String jobId = job.getId().toString();

        String signature = signatureService.sign(
              eventId,
              jobId,
              String.valueOf(now.getEpochSecond())
        );

        String requirements = job.getRequirements() != null ? job.getRequirements() : "";
        String location = job.getLocation() != null ? job.getLocation() : "";
        long salaryMinCents = job.getSalaryMin() != null ? MoneyConverter.toCents(job.getSalaryMin(), 2) : 0L;
        long salaryMaxCents = job.getSalaryMax() != null ? MoneyConverter.toCents(job.getSalaryMax(), 2) : 0L;
        String currency = job.getCurrency() != null ? job.getCurrency() : "";
        String companyLogo = companyProfile.getCompanyLogo() != null ? companyProfile.getCompanyLogo() : "";

        List<String> skillsList = job.getSkills()
              .stream().map(JobSkill::getSkill)
              .toList();

        JobUpsertedEvent.Builder builder = JobUpsertedEvent.newBuilder()
              .setEventId(eventId)
              .setJobId(jobId)
              .setRecruiterId(job.getRecruiterId().toString())
              .setCompanyName(companyProfile.getCompanyName())
              .setCompanyLogo(companyLogo)
              .setTitle(job.getTitle())
              .setDescription(job.getDescription())
              .setRequirements(requirements)
              .setLocation(location)
              .setWorkModality(job.getWorkModality().name())
              .setJobType(job.getJobType().name())
              .setExperienceLevel(job.getExperienceLevel().name())
              .setSalaryMinCents(salaryMinCents)
              .setSalaryMaxCents(salaryMaxCents)
              .setCurrency(currency)
              .setStatus(job.getStatus().name())
              .addAllSkills(skillsList)
              .setCreatedAt(InstantConverter.toTimestamp(job.getCreatedAt().toInstant()))
              .setOccurredAt(InstantConverter.toTimestamp(now))
              .setSignature(signature);

        if (job.getExpiresAt() != null)
            builder.setExpiresAt(InstantConverter.toTimestamp(job.getExpiresAt().toInstant()));

        persist(
              builder.build(),
              jobId,
              EVENT_TYPE_JOB_UPSERTED
        );
    }

    private void publishJobStatusChangedEvent(@NonNull Job job) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String jobId = job.getId().toString();

        String signature = signatureService.sign(
              eventId,
              jobId,
              String.valueOf(now.getEpochSecond())
        );

        JobStatusChangedEvent proto = JobStatusChangedEvent.newBuilder()
              .setEventId(eventId)
              .setJobId(jobId)
              .setRecruiterId(job.getRecruiterId().toString())
              .setJobStatus(job.getStatus().name())
              .setOccurredAt(InstantConverter.toTimestamp(now))
              .setSignature(signature)
              .build();

        persist(proto, jobId, EVENT_TYPE_JOB_STATUS_CHANGED);
    }

    private OutboxEvent buildStatusChangedOutboxEvent(@NonNull Job job) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String jobId = job.getId().toString();

        String signature = signatureService.sign(
              eventId,
              jobId,
              String.valueOf(now.getEpochSecond())
        );

        JobStatusChangedEvent proto = JobStatusChangedEvent.newBuilder()
              .setEventId(eventId)
              .setJobId(jobId)
              .setRecruiterId(job.getRecruiterId().toString())
              .setJobStatus(job.getStatus().name())
              .setOccurredAt(InstantConverter.toTimestamp(now))
              .setSignature(signature)
              .build();

        byte[] payload = serializer.serialize(SERIALIZATION_CONTEXT, proto);

        return new OutboxEvent()
              .setId(UUID.randomUUID())
              .setCreatedAt(OffsetDateTime.now())
              .setAggregateType(AGGREGATE_TYPE)
              .setAggregateId(jobId)
              .setEventType(EVENT_TYPE_JOB_EXPIRED)
              .setPayload(payload);
    }

    private void publishJobDeletedEvent(@NonNull Job job) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String jobId = job.getId().toString();

        String signature = signatureService.sign(
              eventId,
              jobId,
              String.valueOf(now.getEpochSecond())
        );

        JobDeletedEvent proto = JobDeletedEvent.newBuilder()
              .setEventId(eventId)
              .setJobId(jobId)
              .setRecruiterId(job.getRecruiterId().toString())
              .setOccurredAt(InstantConverter.toTimestamp(now))
              .setSignature(signature)
              .build();

        persist(proto, jobId, EVENT_TYPE_JOB_DELETED);
    }

    @Override
    public void publishJobUpserted(Job job, CompanyProfile companyProfile) {
        publishJobUpsertedEvent(job, companyProfile);
    }

    @Override
    public void publishJobStatusChanged(Job job) {
        publishJobStatusChangedEvent(job);
    }

    @Override
    public void publishJobStatusChangedBatch(List<Job> jobs) {
        List<OutboxEvent> events = jobs.stream()
              .map(this::buildStatusChangedOutboxEvent)
              .toList();

        outboxEventRepository.saveAll(events);
    }

    @Override
    public void publishJobDeleted(Job job) {
        publishJobDeletedEvent(job);
    }
}