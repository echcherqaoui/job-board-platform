package com.echcherqaoui.jobboard.userservice.service.impl;

import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.userservice.dto.request.RecruiterProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.RecruiterProfileResponse;
import com.echcherqaoui.jobboard.userservice.exception.domain.ProfileAlreadyOnboardedException;
import com.echcherqaoui.jobboard.userservice.exception.domain.ProfileNotFoundException;
import com.echcherqaoui.jobboard.userservice.mapper.RecruiterProfileMapper;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.echcherqaoui.jobboard.userservice.repository.RecruiterProfileRepository;
import com.echcherqaoui.jobboard.userservice.service.CompanyOutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecruiterProfileServiceImplTest {

    private RecruiterProfileRepository recruiterProfileRepository;
    private CompanyOutboxService companyOutboxService;
    private RecruiterProfileMapper mapper;
    private RecruiterProfileServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        recruiterProfileRepository = mock(RecruiterProfileRepository.class);
        companyOutboxService = mock(CompanyOutboxService.class);
        mapper = mock(RecruiterProfileMapper.class);
        JwtContextHolder jwtContextHolder = mock(JwtContextHolder.class);

        service = new RecruiterProfileServiceImpl(
              recruiterProfileRepository, companyOutboxService, mapper, jwtContextHolder);

        when(jwtContextHolder.getUserId()).thenReturn(userId);
    }

    private RecruiterProfile buildProfile(String companyName, String logoUrl, boolean onboarded) {
        return new RecruiterProfile()
              .setId(userId)
              .setCompanyName(companyName)
              .setCompanyLogoUrl(logoUrl)
              .setOnboardingCompleted(onboarded);
    }

    @Test
    void initializeRecruiter_alreadyExists_doesNotSave() {
        when(recruiterProfileRepository.existsById(userId)).thenReturn(true);

        service.initializeRecruiter(userId, "e@x.com", "John", "Doe");

        verify(recruiterProfileRepository, never()).save(any());
    }

    @Test
    void initializeRecruiter_new_savesSkeletonProfile() {
        when(recruiterProfileRepository.existsById(userId)).thenReturn(false);

        service.initializeRecruiter(userId, "e@x.com", "John", "Doe");

        var captor = org.mockito.ArgumentCaptor.forClass(RecruiterProfile.class);
        verify(recruiterProfileRepository).save(captor.capture());

        RecruiterProfile saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("e@x.com");
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
    }

    @Test
    void onboard_profileNotFound_throws() {
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.onboard(mock(RecruiterProfileRequest.class)))
              .isInstanceOf(ProfileNotFoundException.class);

        verify(recruiterProfileRepository, never()).save(any());
    }

    @Test
    void onboard_alreadyOnboarded_throws() {
        RecruiterProfile profile = buildProfile("Acme", "logo.png", true);
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.onboard(mock(RecruiterProfileRequest.class)))
              .isInstanceOf(ProfileAlreadyOnboardedException.class);

        verify(recruiterProfileRepository, never()).save(any());
        verify(companyOutboxService, never()).publishCompanyUpserted(any());
    }

    @Test
    void onboard_happyPath_setsFlagSavesAndPublishesOutbox() {
        RecruiterProfile profile = buildProfile(null, null, false);
        RecruiterProfileRequest request = mock(RecruiterProfileRequest.class);
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(recruiterProfileRepository.save(profile)).thenReturn(profile);

        service.onboard(request);

        assertThat(profile.isOnboardingCompleted()).isTrue();
        verify(mapper).updateEntity(request, profile);
        verify(recruiterProfileRepository).save(profile);
        verify(companyOutboxService).publishCompanyUpserted(profile);
    }

    @Test
    void getMe_returnsMappedResponse() {
        RecruiterProfile profile = buildProfile("Acme", "logo.png", true);
        RecruiterProfileResponse response = mock(RecruiterProfileResponse.class);
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(mapper.toResponse(profile)).thenReturn(response);

        RecruiterProfileResponse result = service.getMe();

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getMe_notFound_throws() {
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMe())
              .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void update_companyNameChanged_publishesOutbox() {
        RecruiterProfile profile = buildProfile("OldName", "logo.png", true);
        RecruiterProfileRequest request = mock(RecruiterProfileRequest.class);
        when(request.companyName()).thenReturn("NewName");
        when(request.companyLogoUrl()).thenReturn("logo.png");
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(recruiterProfileRepository.save(profile)).thenReturn(profile);

        service.update(request);

        verify(companyOutboxService, times(1)).publishCompanyUpserted(profile);
    }

    @Test
    void update_companyLogoChanged_publishesOutbox() {
        RecruiterProfile profile = buildProfile("Acme", "old-logo.png", true);
        RecruiterProfileRequest request = mock(RecruiterProfileRequest.class);
        when(request.companyName()).thenReturn("Acme");
        when(request.companyLogoUrl()).thenReturn("new-logo.png");
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(recruiterProfileRepository.save(profile)).thenReturn(profile);

        service.update(request);

        verify(companyOutboxService, times(1)).publishCompanyUpserted(profile);
    }

    @Test
    void update_noCompanyFieldsChanged_doesNotPublishOutbox() {
        RecruiterProfile profile = buildProfile("Acme", "logo.png", true);
        RecruiterProfileRequest request = mock(RecruiterProfileRequest.class);
        when(request.companyName()).thenReturn("Acme");
        when(request.companyLogoUrl()).thenReturn("logo.png");
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(recruiterProfileRepository.save(profile)).thenReturn(profile);

        service.update(request);

        verify(companyOutboxService, never()).publishCompanyUpserted(any());
    }

    @Test
    void update_notFound_throws() {
        when(recruiterProfileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(mock(RecruiterProfileRequest.class)))
              .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void getProfileEntityById_found_returnsEntity() {
        RecruiterProfile profile = buildProfile("Acme", "logo.png", true);
        UUID otherId = UUID.randomUUID();
        when(recruiterProfileRepository.findById(otherId)).thenReturn(Optional.of(profile));

        assertThat(service.getProfileEntityById(otherId)).isEqualTo(profile);
    }

    @Test
    void getProfileEntityById_notFound_throws() {
        UUID otherId = UUID.randomUUID();
        when(recruiterProfileRepository.findById(otherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfileEntityById(otherId))
              .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void getProfileEmailById_found_returnsEmail() {
        UUID otherId = UUID.randomUUID();
        when(recruiterProfileRepository.findEmailById(otherId)).thenReturn(Optional.of("e@x.com"));

        assertThat(service.getProfileEmailById(otherId)).isEqualTo("e@x.com");
    }

    @Test
    void getProfileEmailById_notFound_throws() {
        UUID otherId = UUID.randomUUID();
        when(recruiterProfileRepository.findEmailById(otherId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfileEmailById(otherId))
              .isInstanceOf(ProfileNotFoundException.class);
    }
}