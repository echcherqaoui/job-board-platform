package com.echcherqaoui.jobboard.notificationservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.application.event.ApplicationStatusChangedEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.notificationservice.dto.ApplicationNotificationContext;
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

class ApplicationStatusChangedHandlerTest {

    private NotificationService notificationService;
    private SignatureService signatureService;
    private ApplicationStatusChangedHandler handler;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        signatureService = mock(SignatureService.class);
        handler = new ApplicationStatusChangedHandler(notificationService, signatureService);
    }

    private ApplicationStatusChangedEvent buildEvent() {
        return ApplicationStatusChangedEvent.newBuilder()
              .setEventId("evt-1")
              .setApplicationId("app-1")
              .setApplicantId("applicant-1")
              .setJobId("job-1")
              .setJobTitle("Backend Engineer")
              .setCompanyName("Acme")
              .setNewStatus("ACCEPTED")
              .setNote("Great fit")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig")
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptorName() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(ApplicationStatusChangedEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_buildsContext_andDelegatesToNotificationService_whenSignatureValid() {
        ApplicationStatusChangedEvent event = buildEvent();
        when(signatureService.verify("evt-1", "app-1", String.valueOf(1_700_000_000L), "sig")).thenReturn(true);

        handler.handle(event);

        org.mockito.ArgumentCaptor<ApplicationNotificationContext> captor =
              org.mockito.ArgumentCaptor.forClass(ApplicationNotificationContext.class);
        verify(notificationService).sendApplicationStatusUpdated(captor.capture());

        ApplicationNotificationContext ctx = captor.getValue();
        assertThat(ctx.eventId()).isEqualTo("evt-1");
        assertThat(ctx.applicantId()).isEqualTo("applicant-1");
        assertThat(ctx.jobId()).isEqualTo("job-1");
        assertThat(ctx.jobTitle()).isEqualTo("Backend Engineer");
        assertThat(ctx.companyName()).isEqualTo("Acme");
        assertThat(ctx.newStatus()).isEqualTo("ACCEPTED");
        assertThat(ctx.note()).isEqualTo("Great fit");
        assertThat(ctx.applicationId()).isEqualTo("app-1");
    }

    @Test
    void handle_throwsEventSecurityException_whenSignatureInvalid() {
        ApplicationStatusChangedEvent event = buildEvent();
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(EventSecurityException.class);

        verify(notificationService, never()).sendApplicationStatusUpdated(org.mockito.ArgumentMatchers.any());
    }
}