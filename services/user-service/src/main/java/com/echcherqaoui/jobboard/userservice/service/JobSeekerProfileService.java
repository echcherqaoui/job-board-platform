package com.echcherqaoui.jobboard.userservice.service;

import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.JobSeekerProfileResponse;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import com.echcherqaoui.jobboard.userservice.projection.UserEmailProjection;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface JobSeekerProfileService {

    void initializeProfile(UUID userId,
                           String email,
                           String firstName,
                           String lastName);

    void onboard(JobSeekerProfileRequest req);

    JobSeekerProfileResponse updateProfile(JobSeekerProfileRequest request);

    String uploadCv(MultipartFile file);

    JobSeekerProfile findProfileById(UUID userId);

    List<JobSeekerSummaryProjection> findAllByUserIdIn(Set<UUID> userId);

    JobSeekerProfileResponse getMyProfile();

    JobSeekerProfileResponse getProfileById(UUID userId);

    String getProfileEmailById(UUID uuid);

    List<UserEmailProjection> getEmailAndIdByUserIds(Set<UUID> ids);

    void deleteCv();

    void deleteProfile();

}
