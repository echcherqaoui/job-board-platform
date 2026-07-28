package com.echcherqaoui.jobboard.searchservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.job.event.JobDeletedEvent;
import com.echcherqaoui.jobboard.searchservice.service.JobIndexService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobDeletedHandlerTest {

    private JobIndexService jobIndexService;
    private SignatureService signatureService;
    private JobDeletedHandler handler;

    @BeforeEach
    void setUp() {
        jobIndexService = mock(JobIndexService.class);
        signatureService = mock(SignatureService.class);
        handler = new JobDeletedHandler(jobIndexService, signatureService);
    }

    private JobDeletedEvent buildEvent() {
        return JobDeletedEvent.newBuilder()
              .setEventId("evt-1")
              .setJobId("job-1")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig")
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptorName() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(JobDeletedEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_deletesJob_whenSignatureValid() {
        JobDeletedEvent event = buildEvent();
        when(signatureService.verify("evt-1", "job-1", String.valueOf(1_700_000_000L), "sig")).thenReturn(true);

        handler.handle(event);

        verify(jobIndexService).deleteJob("job-1");
    }

    @Test
    void handle_throwsEventSecurityException_whenSignatureInvalid() {
        JobDeletedEvent event = buildEvent();
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(EventSecurityException.class);

        verify(jobIndexService, never()).deleteJob(anyString());
    }
}