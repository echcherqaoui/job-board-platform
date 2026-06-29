package com.echcherqaoui.jobboard.userservice.service.impl;


import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.userservice.dto.request.EducationRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.ExperienceRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.SkillRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.JobSeekerProfileResponse;
import com.echcherqaoui.jobboard.userservice.exception.domain.CvStorageException;
import com.echcherqaoui.jobboard.userservice.exception.domain.ProfileAlreadyOnboardedException;
import com.echcherqaoui.jobboard.userservice.exception.domain.ProfileNotFoundException;
import com.echcherqaoui.jobboard.userservice.mapper.JobSeekerProfileMapper;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerEducation;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerExperience;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerSkill;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import com.echcherqaoui.jobboard.userservice.repository.JobSeekerProfileRepository;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import com.echcherqaoui.jobboard.userservice.storage.CvStorageClient;
import com.echcherqaoui.jobboard.userservice.storage.CvUploadResult;
import com.echcherqaoui.jobboard.userservice.util.CollectionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.CV_EMPTY;
import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.CV_INVALID_TYPE;
import static com.echcherqaoui.jobboard.userservice.exception.enums.UserErrorCode.CV_NOT_FOUND;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobSeekerProfileServiceImpl implements JobSeekerProfileService {

    private final JobSeekerProfileRepository profileRepository;
    private final CvStorageClient cvStorageClient;
    private final JobSeekerProfileMapper mapper;
    private final JwtContextHolder jwtContextHolder;

    @NonNull
    private JobSeekerProfileResponse getUserProfileById(UUID userId) {
        return profileRepository.findById(userId)
              .map(mapper::toResponse)
              .orElseThrow(() -> new ProfileNotFoundException(userId));
    }

    private void syncCollections(JobSeekerProfile profile, JobSeekerProfileRequest request) {
        // Skills
        CollectionUtils.synchronize(
              profile.getSkills(),
              request.skills(),
              JobSeekerSkill::getId,
              SkillRequest::id,
              req -> mapper.toSkillEntity(req).setProfile(profile),
              mapper::updateJobSeekerSkill
        );

        // Experiences
        CollectionUtils.synchronize(
              profile.getExperiences(),
              request.experiences(),
              JobSeekerExperience::getId,
              ExperienceRequest::id,
              req -> mapper.toExperienceEntity(req).setProfile(profile),
              mapper::updateJobSeekerExperience
        );

        // Educations
        CollectionUtils.synchronize(
              profile.getEducations(),
              request.educations(),
              JobSeekerEducation::getId,
              EducationRequest::id,
              req -> mapper.toEducationEntity(req).setProfile(profile),
              mapper::updateJobSeekerEducation
        );
    }

    private void validateCvFile(@NonNull MultipartFile file) {
        if (file.isEmpty())
            throw new CvStorageException(CV_EMPTY);

        String contentType = file.getContentType();
        if (!"application/pdf".equals(contentType))
            throw new CvStorageException(CV_INVALID_TYPE, contentType);
    }

    @Transactional
    @Override
    public void initializeProfile(UUID userId,
                                  String email,
                                  String firstName,
                                  String lastName) {
        if (profileRepository.existsById(userId)) {
            log.warn("Profile already initialized for user {}", userId);
            return;
        }

        JobSeekerProfile profile = new JobSeekerProfile()
              .setId(userId)
              .setEmail(email)
              .setFirstName(firstName)
              .setLastName(lastName);

        profileRepository.save(profile);
        log.info("Initialized empty profile for user {}", userId);
    }

    @Transactional
    @Override
    public void onboard(JobSeekerProfileRequest req) {
        UUID userId = jwtContextHolder.getUserId();

        JobSeekerProfile profile = profileRepository.findById(userId)
              .orElseThrow(() -> new ProfileNotFoundException(userId));

        if (profile.isOnboardingCompleted())
            throw new ProfileAlreadyOnboardedException();

        mapper.updateProfileFromRequest(req, profile);
        profile.setOnboardingCompleted(true);

        if (req.skills() != null)
            req.skills().forEach(s -> {
                JobSeekerSkill skill = mapper.toSkillEntity(s).setProfile(profile);
                profile.getSkills().add(skill);
            });

        if (req.experiences() != null)
            req.experiences().forEach(e -> {
                JobSeekerExperience exp = mapper.toExperienceEntity(e).setProfile(profile);
                profile.getExperiences().add(exp);
            });

        if (req.educations() != null)
            req.educations().forEach(e -> {
                JobSeekerEducation education = mapper.toEducationEntity(e).setProfile(profile);
                profile.getEducations().add(education);
            });

        profileRepository.save(profile);
        log.info("Successfully completed onboarding for user {}", userId);
    }

    @Transactional
    @Override
    public JobSeekerProfileResponse updateProfile(JobSeekerProfileRequest request) {
        UUID userId = jwtContextHolder.getUserId();

        JobSeekerProfile profile = profileRepository.findById(userId)
              .orElseThrow(() -> new ProfileNotFoundException(userId));

        mapper.updateProfileFromRequest(request, profile);

        syncCollections(profile, request);

        JobSeekerProfile saved = profileRepository.save(profile);

        log.info("Updated job seeker profile {} ", userId);

        return mapper.toResponse(saved);
    }

    @Transactional
    @Override
    public String uploadCv(MultipartFile file) {
        validateCvFile(file);

        UUID userId = jwtContextHolder.getUserId();

        JobSeekerProfile profile = profileRepository.findById(userId)
              .orElseThrow(() -> new ProfileNotFoundException(userId));

        CvUploadResult result = cvStorageClient.uploadCv(file, userId);

        profile.setCvUrl(result.url());
        profile.setCvPublicId(result.publicId());

        profileRepository.save(profile);

        return result.url();
    }

    @Transactional(readOnly = true)
    @Override
    public JobSeekerProfile findProfileById(UUID userId) {
        JobSeekerProfile jobSeekerProfile = profileRepository.findById(userId)
              .orElseThrow(() -> new ProfileNotFoundException(userId));

        Hibernate.initialize(jobSeekerProfile.getSkills());

        return jobSeekerProfile;
    }

    @Transactional(readOnly = true)
    @Override
    public List<JobSeekerSummaryProjection> findAllByUserIdIn(List<UUID> userId) {
        return profileRepository.findByIdIn(userId);
    }

    @Transactional(readOnly = true)
    @Override
    public JobSeekerProfileResponse getMyProfile() {
        return getUserProfileById(jwtContextHolder.getUserId());
    }

    @Transactional(readOnly = true)
    @Override
    public JobSeekerProfileResponse getProfileById(UUID userId) {
        return getUserProfileById(userId);
    }

    @Transactional
    @Override
    public void deleteCv() {
        UUID userId = jwtContextHolder.getUserId();

        JobSeekerProfile profile = profileRepository.findById(userId)
              .orElseThrow(() -> new ProfileNotFoundException(userId));

        if (profile.getCvPublicId() == null)
            throw new CvStorageException(CV_NOT_FOUND, userId);

        cvStorageClient.deleteCv(profile.getCvPublicId());

        profile.setCvUrl(null);
        profile.setCvPublicId(null);
        profileRepository.save(profile);
    }

    @Transactional
    @Override
    public void deleteProfile() {
        UUID userId = jwtContextHolder.getUserId();

        JobSeekerProfile profile = profileRepository.findById(userId)
              .orElseThrow(() -> new ProfileNotFoundException(userId));

        profileRepository.delete(profile);
        log.info("Deleted job seeker profile for user {}", userId);
    }
}
