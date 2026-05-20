package com.echcherqaoui.jobboard.userservice.service;

import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.JobSeekerProfileResponse;

import java.util.UUID;

public interface JobSeekerProfileService {

    void initializeProfile(UUID userId,
                           String email,
                           String firstName,
                           String lastName);

    void onboard(JobSeekerProfileRequest req);

    JobSeekerProfileResponse updateProfile(JobSeekerProfileRequest request);

    JobSeekerProfileResponse getMyProfile();

    JobSeekerProfileResponse getProfileById(UUID userId);

    void deleteProfile();
}
