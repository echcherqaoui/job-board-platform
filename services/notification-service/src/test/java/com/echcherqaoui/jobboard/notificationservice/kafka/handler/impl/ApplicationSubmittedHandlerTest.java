package com.echcherqaoui.jobboard.notificationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.application.event.ApplicationSubmittedEvent;
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

class ApplicationSubmittedHandlerTest {

    private NotificationService notificationService;
    private SignatureService signatureService;
    private ApplicationSubmittedHandler handler;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        signatureService = mock(SignatureService.class);
        handler = new ApplicationSubmittedHandler(notificationService, signatureService);
    }

    private ApplicationSubmittedEvent buildEvent() {
        return ApplicationSubmittedEvent.newBuilder()
              .setEventId("evt-1")
              .setApplicationId("app-1")
              .setRecruiterId("recruiter-1")
              .setApplicantName("Jane Doe")
              .setJobTitle("Backend Engineer")
              .setJobId("job-1")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig")
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptorName() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(ApplicationSubmittedEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_delegatesToNotificationService_whenSignatureValid() {
        ApplicationSubmittedEvent event = buildEvent();
        when(signatureService.verify("evt-1", "app-1", String.valueOf(1_700_000_000L), "sig")).thenReturn(true);

        handler.handle(event);

        verify(notificationService).sendApplicationReceived(
              "evt-1", "recruiter-1", "Jane Doe", "Backend Engineer", "app-1", "job-1"
        );
    }

    @Test
    void handle_throwsEventSecurityException_whenSignatureInvalid() {
        ApplicationSubmittedEvent event = buildEvent();
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(EventSecurityException.class);

        verify(notificationService, never()).sendApplicationReceived(
              anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );
    }
}