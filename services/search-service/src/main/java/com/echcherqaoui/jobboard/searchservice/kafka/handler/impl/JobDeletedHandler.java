package com.echcherqaoui.jobboard.searchservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.job.event.JobDeletedEvent;
import com.echcherqaoui.jobboard.searchservice.kafka.handler.JobEventHandler;
import com.echcherqaoui.jobboard.searchservice.service.JobIndexService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobDeletedHandler implements JobEventHandler {

    private final JobIndexService jobIndexService;
    private final SignatureService signatureService;

    @Override
    public String getDescriptorFullName() {
        return JobDeletedEvent.getDescriptor().getFullName();
    }

    @Override
    public void handle(Message payload) {
        JobDeletedEvent event = (JobDeletedEvent) payload;

        boolean valid = signatureService.verify(
              event.getEventId(),
              event.getJobId(),
              String.valueOf(event.getOccurredAt().getSeconds()),
              event.getSignature()
        );

        if (!valid)
            throw new EventSecurityException(event.getEventId());

        jobIndexService.deleteJob(event.getJobId());
    }
}