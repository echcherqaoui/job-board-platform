package com.echcherqaoui.jobboard.applicationservice.service.impl;

import com.echcherqaoui.jobboard.application.event.ApplicationStatusChangedEvent;
import com.echcherqaoui.jobboard.application.event.ApplicationSubmittedEvent;
import com.echcherqaoui.jobboard.application.event.JobApplicationsCanceledEvent;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.applicationservice.service.ApplicationOutboxService;
import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.util.InstantConverter;
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
public class ApplicationOutboxServiceImpl implements ApplicationOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaProtobufSerializer<Message> serializer;
    private final SignatureService signatureService;
    private final JwtContextHolder jwtContextHolder;

    private static final String AGGREGATE_TYPE = "application";
    private static final String EVENT_TYPE_APPLICATION_SUBMITTED = "application-submitted";
    private static final String EVENT_TYPE_APPLICATION_STATUS_CHANGED = "application-status-changed";
    private static final String EVENT_TYPE_APPLICATION_CANCELED = "application-canceled";

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

    @Override
    public void publishApplicationSubmitted(@NonNull Application application,
                                            @NonNull JobSummary job) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String applicationId = application.getId().toString();

        String signature = signatureService.sign(
              eventId,
              applicationId,
              String.valueOf(now.getEpochSecond())
        );

        Instant submittedAt = application.getSubmittedAt().toInstant();

        ApplicationSubmittedEvent event = ApplicationSubmittedEvent.newBuilder()
              .setEventId(eventId)
              .setApplicationId(applicationId)
              .setJobId(application.getJobId().toString())
              .setJobTitle(job.getTitle())
              .setRecruiterId(job.getRecruiterId())
              .setApplicantName(jwtContextHolder.getFullName())
              .setApplicantId(application.getApplicantId().toString())
              .setSubmittedAt(InstantConverter.toTimestamp(submittedAt))
              .setOccurredAt(InstantConverter.toTimestamp(now))
              .setSignature(signature)
              .build();

        persist(
              event,
              applicationId,
              EVENT_TYPE_APPLICATION_SUBMITTED
        );
    }

    @Override
    public void publishApplicationStatusUpdated(@NonNull Application application,
                                                @NonNull ApplicationStatus oldStatus,
                                                @NonNull ApplicationStatus newStatus,
                                                @NonNull JobSummary jobSummary,
                                                @NonNull UUID callerId,
                                                String note) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String applicationId = application.getId().toString();

        String signature = signatureService.sign(
              eventId,
              applicationId,
              String.valueOf(now.getEpochSecond())
        );

        // Assumes your Protobuf contract matches these standard field types
        ApplicationStatusChangedEvent event = ApplicationStatusChangedEvent.newBuilder()
              .setEventId(eventId)
              .setApplicationId(applicationId)
              .setApplicantId(application.getApplicantId().toString())
              .setOldStatus(oldStatus.name())
              .setNewStatus(newStatus.name())
              .setJobTitle(jobSummary.getTitle())
              .setNote(note != null ? note : "")
              .setJobId(jobSummary.getJobId())
              .setChangedBy(callerId.toString())
              .setCompanyName(jobSummary.getCompanyName())
              .setOccurredAt(InstantConverter.toTimestamp(now))
              .setSignature(signature)
              .build();

        persist(
              event,
              applicationId,
              EVENT_TYPE_APPLICATION_STATUS_CHANGED
        );
    }

    @Override
    public void publishJobApplicationsCanceled(@NonNull UUID jobId,
                                               String jobTitle,
                                               List<String> applicantIds) {
        Instant now = Instant.now();
        String eventId = UUID.randomUUID().toString();
        String jobIdStr = jobId.toString();

        String signature = signatureService.sign(
              eventId,
              jobIdStr,
              String.valueOf(now.getEpochSecond())
        );

        JobApplicationsCanceledEvent event = JobApplicationsCanceledEvent.newBuilder()
              .setEventId(eventId)
              .setJobId(jobIdStr)
              .setJobTitle(jobTitle)
              .addAllApplicantIds(applicantIds)
              .setOccurredAt(InstantConverter.toTimestamp(now))
              .setSignature(signature)
              .build();

        // Persist using jobId as the partitioning/routing key for the outbox
        persist(
              event,
              jobIdStr,
              EVENT_TYPE_APPLICATION_CANCELED
        );
    }
}
