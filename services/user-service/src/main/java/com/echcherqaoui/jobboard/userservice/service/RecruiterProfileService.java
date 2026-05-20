package com.echcherqaoui.jobboard.userservice.service;

import com.echcherqaoui.jobboard.userservice.dto.request.RecruiterProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.RecruiterProfileResponse;

import java.util.UUID;

public interface RecruiterProfileService {
    void initializeRecruiter(UUID userId,
                             String email,
                             String firstName,
                             String lastName);

    void onboard(RecruiterProfileRequest request);

    RecruiterProfileResponse getMe();

    RecruiterProfileResponse getRecruiterById(UUID id);

    RecruiterProfileResponse update(RecruiterProfileRequest request);
}
