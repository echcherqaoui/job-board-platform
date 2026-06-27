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
import com.echcherqaoui.jobboard.userservice.service.RecruiterProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.RECRUITER_NOT_EXISTS;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecruiterProfileServiceImpl implements RecruiterProfileService {

    private final RecruiterProfileRepository recruiterProfileRepository;
    private final CompanyOutboxService companyOutboxService;
    private final RecruiterProfileMapper mapper;
    private final JwtContextHolder jwtContextHolder;

    @NonNull
    private RecruiterProfileResponse getById(UUID id) {
        return recruiterProfileRepository.findById(id)
              .map(mapper::toResponse)
              .orElseThrow(() -> new ProfileNotFoundException(RECRUITER_NOT_EXISTS, id));
    }

    @Transactional
    @Override
    public void initializeRecruiter(UUID userId,
                                    String email,
                                    String firstName,
                                    String lastName) {
        if (recruiterProfileRepository.existsById(userId)) {
            log.warn("Profile already initialized for user {}", userId);
            return;
        }

        RecruiterProfile profile = new RecruiterProfile()
              .setId(userId)
              .setEmail(email)
              .setFirstName(firstName)
              .setLastName(lastName);

        recruiterProfileRepository.save(profile);
        log.info("Initialized empty profile for user {}", userId);
    }

    @Transactional
    @Override
    public void onboard(RecruiterProfileRequest request) {
        UUID userId = jwtContextHolder.getUserId();

        RecruiterProfile profile = recruiterProfileRepository.findById(userId)
              .orElseThrow(() -> new ProfileNotFoundException(RECRUITER_NOT_EXISTS, userId));

        if (profile.isOnboardingCompleted())
            throw new ProfileAlreadyOnboardedException();

        mapper.updateEntity(request, profile);
        profile.setOnboardingCompleted(true);

        RecruiterProfile saved = recruiterProfileRepository.save(profile);

        companyOutboxService.publishCompanyUpserted(saved);

        log.info("Successfully completed onboarding for recruiter user {}", userId);
    }

    @Transactional(readOnly = true)
    @Override
    public RecruiterProfileResponse getMe() {
        return getById(jwtContextHolder.getUserId());
    }

    @Transactional
    @Override
    public RecruiterProfileResponse update(@NonNull RecruiterProfileRequest request) {
        UUID userId = jwtContextHolder.getUserId();

        RecruiterProfile profile = recruiterProfileRepository.findById(userId)
              .orElseThrow(() -> new ProfileNotFoundException(RECRUITER_NOT_EXISTS, userId));

        boolean companyChanged = !Objects.equals(profile.getCompanyName(), request.companyName())
              || !Objects.equals(profile.getCompanyLogoUrl(), request.companyLogoUrl());

        mapper.updateEntity(request, profile);

        RecruiterProfile saved = recruiterProfileRepository.save(profile);

        if (companyChanged)
            companyOutboxService.publishCompanyUpserted(saved);

        log.info("Updated recruiter profile {}", userId);

        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    @Override
    public RecruiterProfile getProfileEntityById(UUID id) {
        return recruiterProfileRepository.findById(id)
              .orElseThrow(() -> new ProfileNotFoundException(RECRUITER_NOT_EXISTS, id));
    }

    @Transactional(readOnly = true)
    @Override
    public RecruiterProfileResponse getRecruiterById(UUID id) {
        return getById(id);
    }

}