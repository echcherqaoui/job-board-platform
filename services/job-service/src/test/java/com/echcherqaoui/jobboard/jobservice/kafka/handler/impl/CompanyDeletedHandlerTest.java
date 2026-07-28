package com.echcherqaoui.jobboard.jobservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.event.CompanyDeletedEvent;
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

class CompanyDeletedHandlerTest {

    private CompanyProfileService companyProfileService;
    private SignatureService signatureService;
    private CompanyDeletedHandler handler;

    @BeforeEach
    void setUp() {
        companyProfileService = mock(CompanyProfileService.class);
        signatureService = mock(SignatureService.class);
        handler = new CompanyDeletedHandler(companyProfileService, signatureService);
    }

    private CompanyDeletedEvent buildEvent(String recruiterId) {
        return CompanyDeletedEvent.newBuilder()
              .setEventId("evt-1")
              .setRecruiterId(recruiterId)
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig")
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptorName() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(CompanyDeletedEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_deletesProfile_whenSignatureValid() {
        UUID recruiterId = UUID.randomUUID();
        CompanyDeletedEvent event = buildEvent(recruiterId.toString());

        when(signatureService.verify("evt-1", recruiterId.toString(), String.valueOf(1_700_000_000L), "sig"))
              .thenReturn(true);

        handler.handle(event);

        verify(companyProfileService).delete(recruiterId);
    }

    @Test
    void handle_throwsEventSecurityException_whenSignatureInvalid() {
        CompanyDeletedEvent event = buildEvent(UUID.randomUUID().toString());

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(EventSecurityException.class);

        verify(companyProfileService, never()).delete(any());
    }

    @Test
    void handle_throwsIllegalArgumentException_whenRecruiterIdIsMalformedUuid() {
        CompanyDeletedEvent event = buildEvent("not-a-uuid");

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(IllegalArgumentException.class);
    }
}