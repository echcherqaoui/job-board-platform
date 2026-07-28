package com.echcherqaoui.jobboard.userservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.auth.event.JobSeekerRegisteredEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSeekerRegisteredHandlerTest {

    private SignatureService signatureService;
    private JobSeekerProfileService jobSeekerService;
    private JobSeekerRegisteredHandler handler;

    @BeforeEach
    void setUp() {
        signatureService = mock(SignatureService.class);
        jobSeekerService = mock(JobSeekerProfileService.class);
        handler = new JobSeekerRegisteredHandler(signatureService, jobSeekerService);
    }

    private JobSeekerRegisteredEvent buildEvent(UUID userId) {
        return JobSeekerRegisteredEvent.newBuilder()
              .setEventId("evt-1")
              .setUserId(userId.toString())
              .setEmail("seeker@x.com")
              .setFirstName("Jane")
              .setLastName("Doe")
              .setSignature("sig")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1000L).build())
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptor() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(JobSeekerRegisteredEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_invalidSignature_throwsAndSkipsInitialization() {
        UUID userId = UUID.randomUUID();
        JobSeekerRegisteredEvent event = buildEvent(userId);
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event))
              .isInstanceOf(EventSecurityException.class);

        verify(jobSeekerService, never()).initializeProfile(
              org.mockito.ArgumentMatchers.any(UUID.class),
              anyString(), anyString(), anyString());
    }

    @Test
    void handle_validSignature_initializesProfileWithCorrectArgs() {
        UUID userId = UUID.randomUUID();
        JobSeekerRegisteredEvent event = buildEvent(userId);
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        handler.handle(event);

        verify(jobSeekerService).initializeProfile(userId, "seeker@x.com", "Jane", "Doe");
    }

    @Test
    void handle_verifiesSignatureWithCorrectArgs() {
        UUID userId = UUID.randomUUID();
        JobSeekerRegisteredEvent event = buildEvent(userId);
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        handler.handle(event);

        verify(signatureService).verify("evt-1", userId.toString(), "1000", "sig");
    }

    private static UUID any() {
        return org.mockito.ArgumentMatchers.any(UUID.class);
    }

    private static String any(Class<String> c) {
        return org.mockito.ArgumentMatchers.any(c);
    }
}