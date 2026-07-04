package com.echcherqaoui.jobboard.applicationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.applicationservice.kafka.handler.JobEventHandler;
import com.echcherqaoui.jobboard.applicationservice.service.ApplicationDataAccess;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class JobExpiredHandler implements JobEventHandler {

    private final ApplicationDataAccess applicationDataAccess;
    private final SignatureService signatureService;


    @Override
    public String getDescriptorFullName() {
        return JobStatusChangedEvent.getDescriptor().getFullName();
    }

    @Override
    public void handle(Message payload) {
        JobStatusChangedEvent event = (JobStatusChangedEvent) payload;

        if (!"CLOSED".equals(event.getJobStatus())) return;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getJobId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid)
            throw new EventSecurityException(event.getEventId());

        applicationDataAccess.bulkRejectAndExecute(
              UUID.fromString(event.getJobId())
        );

        log.info("Job {} closed — bulk-rejecting pending applications", event.getJobId());
    }
}