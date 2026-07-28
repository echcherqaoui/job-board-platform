package com.echcherqaoui.jobboard.searchservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.searchservice.service.JobIndexService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobUpsertedHandlerTest {

    private JobIndexService jobIndexService;
    private SignatureService signatureService;
    private JobUpsertedHandler handler;

    @BeforeEach
    void setUp() {
        jobIndexService = mock(JobIndexService.class);
        signatureService = mock(SignatureService.class);
        handler = new JobUpsertedHandler(jobIndexService, signatureService);
    }

    private JobUpsertedEvent buildEvent() {
        return JobUpsertedEvent.newBuilder()
              .setEventId("evt-1")
              .setJobId("job-1")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig")
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptorName() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(JobUpsertedEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_upsertsJob_whenSignatureValid() {
        JobUpsertedEvent event = buildEvent();
        when(signatureService.verify("evt-1", "job-1", String.valueOf(1_700_000_000L), "sig")).thenReturn(true);

        handler.handle(event);

        verify(jobIndexService).upsertJob(event);
    }

    @Test
    void handle_throwsEventSecurityException_whenSignatureInvalid() {
        JobUpsertedEvent event = buildEvent();
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(EventSecurityException.class);

        verify(jobIndexService, never()).upsertJob(any());
    }
}