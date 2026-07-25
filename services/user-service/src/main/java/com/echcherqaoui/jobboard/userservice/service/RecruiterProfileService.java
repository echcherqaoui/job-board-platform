package com.echcherqaoui.jobboard.userservice.service;

import com.echcherqaoui.jobboard.userservice.dto.request.RecruiterProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.RecruiterProfileResponse;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;

import java.util.UUID;

public interface RecruiterProfileService {
    void initializeRecruiter(UUID userId,
                             String email,
                             String firstName,
                             String lastName);

    void onboard(RecruiterProfileRequest request);

    RecruiterProfileResponse getMe();

    RecruiterProfile getProfileEntityById(UUID id);

    String getProfileEmailById(UUID id);

    RecruiterProfileResponse getRecruiterById(UUID id);

    RecruiterProfileResponse update(RecruiterProfileRequest request);
}
