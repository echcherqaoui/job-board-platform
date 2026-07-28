package com.echcherqaoui.jobboard.jobservice.kafka.handler.impl;

import com.echcherqaoui.jobboard.exception.core.EventSecurityException;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.event.CompanyUpsertedEvent;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyUpsertedHandlerTest {

    private CompanyProfileService companyProfileService;
    private SignatureService signatureService;
    private CompanyUpsertedHandler handler;

    @BeforeEach
    void setUp() {
        companyProfileService = mock(CompanyProfileService.class);
        signatureService = mock(SignatureService.class);
        handler = new CompanyUpsertedHandler(companyProfileService, signatureService);
    }

    private CompanyUpsertedEvent buildEvent(String recruiterId) {
        return CompanyUpsertedEvent.newBuilder()
              .setEventId("evt-1")
              .setRecruiterId(recruiterId)
              .setCompanyName("Acme")
              .setCompanyLogo("logo.png")
              .setOccurredAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build())
              .setSignature("sig")
              .build();
    }

    @Test
    void getDescriptorFullName_returnsEventDescriptorName() {
        assertThat(handler.getDescriptorFullName())
              .isEqualTo(CompanyUpsertedEvent.getDescriptor().getFullName());
    }

    @Test
    void handle_upsertsProfile_withConvertedTimestamp_whenSignatureValid() {
        UUID recruiterId = UUID.randomUUID();
        CompanyUpsertedEvent event = buildEvent(recruiterId.toString());

        when(signatureService.verify("evt-1", recruiterId.toString(), String.valueOf(1_700_000_000L), "sig"))
              .thenReturn(true);

        handler.handle(event);

        OffsetDateTime expected = Instant.ofEpochSecond(1_700_000_000L).atOffset(ZoneOffset.UTC);
        verify(companyProfileService).upsert(recruiterId, "Acme", "logo.png", "evt-1", expected);
    }

    @Test
    void handle_throwsEventSecurityException_whenSignatureInvalid() {
        CompanyUpsertedEvent event = buildEvent(UUID.randomUUID().toString());

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(EventSecurityException.class);

        verify(companyProfileService, never()).upsert(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void handle_throwsIllegalArgumentException_whenRecruiterIdIsMalformedUuid() {
        CompanyUpsertedEvent event = buildEvent("not-a-uuid");

        when(signatureService.verify(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> handler.handle(event)).isInstanceOf(IllegalArgumentException.class);
    }
}