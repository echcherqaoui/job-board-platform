package com.echcherqaoui.jobboard.userservice.service;

import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.JobSeekerProfileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface JobSeekerProfileService {

    void initializeProfile(UUID userId,
                           String email,
                           String firstName,
                           String lastName);

    void onboard(JobSeekerProfileRequest req);

    JobSeekerProfileResponse updateProfile(JobSeekerProfileRequest request);

    String uploadCv(MultipartFile file);

    JobSeekerProfileResponse getMyProfile();

    JobSeekerProfileResponse getProfileById(UUID userId);

    void deleteCv();

    void deleteProfile();
}
