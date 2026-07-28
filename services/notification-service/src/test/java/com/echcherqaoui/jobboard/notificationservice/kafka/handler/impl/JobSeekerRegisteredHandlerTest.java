package com.echcherqaoui.jobboard.notificationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.auth.event.JobSeekerRegisteredEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.notificationservice.service.NotificationService;
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

class JobSeekerRegisteredHandlerTest {

    private NotificationService notificationService;
    private SignatureService signatureService;
    private JobSeekerRegisteredHandler handler;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        signatureService = mock(SignatureService.class);
        handler = new JobSeekerRegisteredHandler(notificationService, signatureService);
    }

    private JobSeekerRegisteredEvent buildEvent() {
        return JobSeekerRegisteredEvent.newBuilder()
              .setEventId("evt-1")
              .setUserId("user-1")
              .setEmail("jane@example.com")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig")
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptorName() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(JobSeekerRegisteredEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_sendsWelcome_withJobseekerRole_whenSignatureValid() {
        JobSeekerRegisteredEvent event = buildEvent();
        when(signatureService.verify("evt-1", "user-1", String.valueOf(1_700_000_000L), "sig")).thenReturn(true);

        handler.handle(event);

        verify(notificationService).sendWelcome("user-1", "jane@example.com", "JOBSEEKER");
    }

    @Test
    void handle_throwsEventSecurityException_whenSignatureInvalid() {
        JobSeekerRegisteredEvent event = buildEvent();
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(EventSecurityException.class);

        verify(notificationService, never()).sendWelcome(anyString(), anyString(), anyString());
    }
}