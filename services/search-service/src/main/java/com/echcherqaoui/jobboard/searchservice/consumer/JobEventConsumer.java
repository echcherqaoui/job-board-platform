package com.echcherqaoui.jobboard.searchservice.consumer;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.job.event.JobDeletedEvent;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.searchservice.service.JobIndexService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobEventConsumer {

    private final JobIndexService jobIndexService;
    private final SignatureService signatureService;

    private void validateEvent(String eventId,
                               String jobId,
                               String occurredAt,
                               String signature) {
        boolean valid = signatureService.verify(
              eventId,
              jobId,
              occurredAt,
              signature
        );

        if (!valid) {
            log.warn(
                  "Invalid signature for JobEvent [eventId={}, jobId={}] — skipping",
                  eventId,
                  jobId
            );

            throw new EventSecurityException(eventId);
        }
    }

    @KafkaListener(
          topics = { "${kafka.topics.job.job-upserted}"},
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "jobUpsertListenerContainerFactory"
    )
    public void onJobSnapshot(@NonNull JobUpsertedEvent event, Acknowledgment ack) {

        validateEvent(
              event.getEventId(),
              event.getJobId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        jobIndexService.upsertJob(event);

        ack.acknowledge();
    }

    @KafkaListener(
          topics = { "${kafka.topics.job.job-status-changed}", "${kafka.topics.job.job-expired}" },
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "jobStatusChangedListenerContainerFactory"
    )
    public void onJobStatusChanged(@NonNull JobStatusChangedEvent event, Acknowledgment ack) {
        validateEvent(
              event.getEventId(),
              event.getJobId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        jobIndexService.updateJobStatus(event.getJobId(), event.getJobStatus());

        ack.acknowledge();
    }

    @KafkaListener(
          topics = "${kafka.topics.job.job-deleted}",
          groupId = "${spring.kafka.consumer.group-id}",
          containerFactory = "jobDeletedListenerContainerFactory"
    )
    public void onJobDeleted(@NonNull JobDeletedEvent event, Acknowledgment ack) {
        validateEvent(
              event.getEventId(),
              event.getJobId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        jobIndexService.deleteJob(event.getJobId());

        ack.acknowledge();
    }
}
