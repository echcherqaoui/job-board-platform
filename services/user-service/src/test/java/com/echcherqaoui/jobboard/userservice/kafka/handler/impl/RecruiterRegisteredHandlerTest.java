package com.echcherqaoui.jobboard.userservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.auth.event.RecruiterRegisteredEvent;
import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.userservice.service.RecruiterProfileService;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecruiterRegisteredHandlerTest {

    private RecruiterProfileService recruiterService;
    private SignatureService signatureService;
    private RecruiterRegisteredHandler handler;

    @BeforeEach
    void setUp() {
        recruiterService = mock(RecruiterProfileService.class);
        signatureService = mock(SignatureService.class);
        handler = new RecruiterRegisteredHandler(recruiterService, signatureService);
    }

    private RecruiterRegisteredEvent buildEvent(UUID userId) {
        return RecruiterRegisteredEvent.newBuilder()
              .setEventId("evt-2")
              .setUserId(userId.toString())
              .setEmail("recruiter@x.com")
              .setFirstName("John")
              .setLastName("Smith")
              .setSignature("sig")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(2000L).build())
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptor() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(RecruiterRegisteredEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_invalidSignature_throwsAndSkipsInitialization() {
        UUID userId = UUID.randomUUID();
        RecruiterRegisteredEvent event = buildEvent(userId);
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event))
              .isInstanceOf(EventSecurityException.class);

        verify(recruiterService, never()).initializeRecruiter(
              any(UUID.class), anyString(), anyString(), anyString());
    }

    @Test
    void handle_validSignature_initializesRecruiterWithCorrectArgs() {
        UUID userId = UUID.randomUUID();
        RecruiterRegisteredEvent event = buildEvent(userId);
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        handler.handle(event);

        verify(recruiterService).initializeRecruiter(userId, "recruiter@x.com", "John", "Smith");
    }

    @Test
    void handle_verifiesSignatureWithCorrectArgs() {
        UUID userId = UUID.randomUUID();
        RecruiterRegisteredEvent event = buildEvent(userId);
        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        handler.handle(event);

        verify(signatureService).verify("evt-2", userId.toString(), "2000", "sig");
    }
}