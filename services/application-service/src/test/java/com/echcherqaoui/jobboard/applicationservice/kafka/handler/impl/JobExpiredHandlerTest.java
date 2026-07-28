package com.echcherqaoui.jobboard.applicationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.applicationservice.service.ApplicationDataAccess;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobExpiredHandlerTest {

    private ApplicationDataAccess applicationDataAccess;
    private SignatureService signatureService;
    private JobExpiredHandler handler;

    @BeforeEach
    void setUp() {
        applicationDataAccess = mock(ApplicationDataAccess.class);
        signatureService = mock(SignatureService.class);
        handler = new JobExpiredHandler(applicationDataAccess, signatureService);
    }

    private JobStatusChangedEvent event(String status, String jobId, String eventId, long epochSeconds, String signature) {
        return JobStatusChangedEvent.newBuilder()
              .setJobStatus(status)
              .setJobId(jobId)
              .setEventId(eventId)
              .setJobTitle("Senior Backend Engineer")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(epochSeconds).build())
              .setSignature(signature)
              .build();
    }

    @Test
    void nonClosedStatus_returnsEarly_noSideEffects() {
        JobStatusChangedEvent evt = event("OPEN", UUID.randomUUID().toString(), "evt-1", 1000L, "sig");

        handler.handle(evt);

        verifyNoInteractions(signatureService, applicationDataAccess);
    }

    @Test
    void closedStatus_invalidSignature_throwsAndSkipsBulkReject() {
        String jobId = UUID.randomUUID().toString();
        JobStatusChangedEvent evt = event("CLOSED", jobId, "evt-1", 1000L, "bad-sig");
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(evt))
              .isInstanceOf(EventSecurityException.class);

        verify(applicationDataAccess, never()).bulkRejectAndExecute(any(), any());
    }

    @Test
    void closedStatus_validSignature_callsBulkRejectWithCorrectArgs() {
        UUID jobId = UUID.randomUUID();
        JobStatusChangedEvent evt = event("CLOSED", jobId.toString(), "evt-1", 1000L, "good-sig");
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        handler.handle(evt);

        verify(applicationDataAccess).bulkRejectAndExecute(jobId, "Senior Backend Engineer");
    }

    @Test
    void closedStatus_signatureVerifiedWithExactEventFields() {
        UUID jobId = UUID.randomUUID();
        JobStatusChangedEvent evt = event("CLOSED", jobId.toString(), "evt-42", 5000L, "sig-xyz");
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        handler.handle(evt);

        verify(signatureService).verify("evt-42", jobId.toString(), "5000", "sig-xyz");
    }
}