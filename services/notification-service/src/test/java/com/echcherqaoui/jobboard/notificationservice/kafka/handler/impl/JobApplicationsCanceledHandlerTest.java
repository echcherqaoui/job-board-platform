package com.echcherqaoui.jobboard.notificationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.application.event.JobApplicationsCanceledEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobApplicationsCanceledHandlerTest {

    private NotificationService notificationService;
    private SignatureService signatureService;
    private JobApplicationsCanceledHandler handler;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        signatureService = mock(SignatureService.class);
        handler = new JobApplicationsCanceledHandler(notificationService, signatureService);
    }

    private JobApplicationsCanceledEvent buildEvent(String eventId, String jobId, List<String> applicantIds) {
        return JobApplicationsCanceledEvent.newBuilder()
              .setEventId(eventId)
              .setJobId(jobId)
              .setJobTitle("Senior Backend Engineer")
              .addAllApplicantIds(applicantIds)
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig-value")
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptorName() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(JobApplicationsCanceledEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_delegatesToNotificationService_whenSignatureValid() {
        JobApplicationsCanceledEvent event = buildEvent("evt-1", "job-1", List.of("u1", "u2"));

        when(signatureService.verify(
              "evt-1",
              "job-1",
              String.valueOf(1_700_000_000L),
              "sig-value"
        )).thenReturn(true);

        handler.handle(event);

        verify(notificationService).sendApplicationsCanceled(
              "evt-1", "job-1", "Senior Backend Engineer", List.of("u1", "u2")
        );
    }

    @Test
    void handle_throwsEventSecurityException_andSkipsNotification_whenSignatureInvalid() {
        JobApplicationsCanceledEvent event = buildEvent("evt-2", "job-2", List.of("u1"));

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString()))
              .thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event))
              .isInstanceOf(EventSecurityException.class);

        verify(notificationService, never()).sendApplicationsCanceled(
              anyString(), anyString(), anyString(), eq(List.of("u1"))
        );
    }

    @Test
    void handle_passesEmptyApplicantList_throughUnchanged() {
        JobApplicationsCanceledEvent event = buildEvent("evt-3", "job-3", List.of());

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString()))
              .thenReturn(true);

        handler.handle(event);

        verify(notificationService).sendApplicationsCanceled(
              "evt-3", "job-3", "Senior Backend Engineer", List.of()
        );
    }
}