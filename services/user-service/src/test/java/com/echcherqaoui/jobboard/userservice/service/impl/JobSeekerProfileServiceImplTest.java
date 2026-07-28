package com.echcherqaoui.jobboard.userservice.service.impl;

import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.SkillRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.JobSeekerProfileResponse;
import com.echcherqaoui.jobboard.userservice.exception.domain.CvStorageException;
import com.echcherqaoui.jobboard.userservice.exception.domain.ProfileAlreadyOnboardedException;
import com.echcherqaoui.jobboard.userservice.exception.domain.ProfileNotFoundException;
import com.echcherqaoui.jobboard.userservice.mapper.JobSeekerProfileMapper;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerSkill;
import com.echcherqaoui.jobboard.userservice.repository.JobSeekerProfileRepository;
import com.echcherqaoui.jobboard.userservice.storage.CvStorageClient;
import com.echcherqaoui.jobboard.userservice.storage.CvUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobSeekerProfileServiceImplTest {

    private JobSeekerProfileRepository profileRepository;
    private CvStorageClient cvStorageClient;
    private JobSeekerProfileMapper mapper;
    private JwtContextHolder jwtContextHolder;
    private JobSeekerProfileServiceImpl service;

    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        profileRepository = mock(JobSeekerProfileRepository.class);
        cvStorageClient = mock(CvStorageClient.class);
        mapper = mock(JobSeekerProfileMapper.class);
        jwtContextHolder = mock(JwtContextHolder.class);
        service = new JobSeekerProfileServiceImpl(profileRepository, cvStorageClient, mapper, jwtContextHolder);
    }

    private JobSeekerProfileRequest buildRequest(List<SkillRequest> skills) {
        return new JobSeekerProfileRequest(
              "+1234567890", "Casablanca", "Backend Dev", "Bio text",
              null, null, null, null, 3, skills, List.of(), List.of()
        );
    }

    @Test
    void initializeProfile_createsProfile_whenNotExisting() {
        when(profileRepository.existsById(USER_ID)).thenReturn(false);

        service.initializeProfile(USER_ID, "user@example.com", "John", "Doe");

        org.mockito.ArgumentCaptor<JobSeekerProfile> captor = org.mockito.ArgumentCaptor.forClass(JobSeekerProfile.class);
        verify(profileRepository).save(captor.capture());

        JobSeekerProfile saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(USER_ID);
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
    }

    @Test
    void initializeProfile_isIdempotent_skipsSave_whenAlreadyExists() {
        when(profileRepository.existsById(USER_ID)).thenReturn(true);

        service.initializeProfile(USER_ID, "user@example.com", "John", "Doe");

        verify(profileRepository, never()).save(any(JobSeekerProfile.class));
    }

    @Test
    void onboard_throwsProfileNotFound_whenProfileMissing() {
        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.onboard(buildRequest(List.of())))
              .isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void onboard_throwsProfileAlreadyOnboarded_whenAlreadyCompleted() {
        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID).setOnboardingCompleted(true);
        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.onboard(buildRequest(List.of())))
              .isInstanceOf(ProfileAlreadyOnboardedException.class);

        verify(profileRepository, never()).save(any(JobSeekerProfile.class));
    }

    @Test
    void onboard_setsOnboardingCompletedTrue_andSaves_onHappyPath() {
        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID).setOnboardingCompleted(false);
        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        service.onboard(buildRequest(List.of()));

        assertThat(profile.isOnboardingCompleted()).isTrue();
        verify(mapper).updateProfileFromRequest(any(JobSeekerProfileRequest.class), eq(profile));
        verify(profileRepository).save(profile);
    }

    @Test
    void onboard_appendsSkills_directlyWithoutSync_whenSkillsPresent() {
        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID).setOnboardingCompleted(false);
        SkillRequest skillRequest = new SkillRequest(null, "Java", null);
        JobSeekerSkill mappedSkill = new JobSeekerSkill().setSkillName("Java");

        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(mapper.toSkillEntity(skillRequest)).thenReturn(mappedSkill);

        service.onboard(buildRequest(List.of(skillRequest)));

        assertThat(profile.getSkills()).containsExactly(mappedSkill);
        assertThat(mappedSkill.getProfile()).isEqualTo(profile);
    }

    @Test
    void onboard_doesNotTouchExistingSkills_whenRequestSkillsNull() {
        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID).setOnboardingCompleted(false);
        JobSeekerSkill preExisting = new JobSeekerSkill().setSkillName("Python").setProfile(profile);
        profile.getSkills().add(preExisting);

        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        service.onboard(buildRequest(null));

        assertThat(profile.getSkills()).containsExactly(preExisting);
    }

    @Test
    void uploadCv_throwsCvStorageException_whenFileEmpty() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> service.uploadCv(file)).isInstanceOf(CvStorageException.class);

        verify(profileRepository, never()).findById(any());
    }

    @Test
    void uploadCv_throwsCvStorageException_whenContentTypeNotPdf() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("image/png");

        assertThatThrownBy(() -> service.uploadCv(file)).isInstanceOf(CvStorageException.class);
    }

    @Test
    void uploadCv_setsCvUrlAndPublicId_onHappyPath() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");

        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID);
        CvUploadResult result = new CvUploadResult("https://cdn.test/cv.pdf", "public-id-1");

        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(cvStorageClient.uploadCv(file, USER_ID)).thenReturn(result);

        String url = service.uploadCv(file);

        assertThat(url).isEqualTo("https://cdn.test/cv.pdf");
        assertThat(profile.getCvUrl()).isEqualTo("https://cdn.test/cv.pdf");
        assertThat(profile.getCvPublicId()).isEqualTo("public-id-1");
        verify(profileRepository).save(profile);
    }

    @Test
    void uploadCv_throwsProfileNotFound_whenProfileMissing() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getContentType()).thenReturn("application/pdf");
        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadCv(file)).isInstanceOf(ProfileNotFoundException.class);

        verify(cvStorageClient, never()).uploadCv(any(), any());
    }

    @Test
    void deleteCv_throwsCvStorageException_whenNoCvPublicId() {
        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID);
        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.deleteCv()).isInstanceOf(CvStorageException.class);

        verify(cvStorageClient, never()).deleteCv(any());
    }

    @Test
    void deleteCv_clearsCvFields_onHappyPath() {
        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID).setCvUrl("url").setCvPublicId("pub-1");
        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        service.deleteCv();

        verify(cvStorageClient).deleteCv("pub-1");
        assertThat(profile.getCvUrl()).isNull();
        assertThat(profile.getCvPublicId()).isNull();
        verify(profileRepository).save(profile);
    }

    @Test
    void deleteProfile_deletesProfile_onHappyPath() {
        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID);
        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));

        service.deleteProfile();

        verify(profileRepository).delete(profile);
    }

    @Test
    void deleteProfile_throwsProfileNotFound_whenMissing() {
        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteProfile()).isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void getMyProfile_returnsMappedResponse() {
        JobSeekerProfile profile = new JobSeekerProfile().setId(USER_ID);
        JobSeekerProfileResponse response = mock(JobSeekerProfileResponse.class);

        when(jwtContextHolder.getUserId()).thenReturn(USER_ID);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(mapper.toResponse(profile)).thenReturn(response);

        assertThat(service.getMyProfile()).isEqualTo(response);
    }

    @Test
    void getProfileById_throwsProfileNotFound_whenMissing() {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfileById(USER_ID)).isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void getProfileEmailById_returnsEmail_whenFound() {
        when(profileRepository.findEmailById(USER_ID)).thenReturn(Optional.of("user@example.com"));

        assertThat(service.getProfileEmailById(USER_ID)).isEqualTo("user@example.com");
    }

    @Test
    void getProfileEmailById_throwsProfileNotFound_usingRecruiterNotExistsErrorCode_whenMissing() {
        when(profileRepository.findEmailById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfileEmailById(USER_ID))
              .isInstanceOf(ProfileNotFoundException.class);
    }


    @Test
    void findProfileById_throwsProfileNotFound_whenMissing() {
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findProfileById(USER_ID)).isInstanceOf(ProfileNotFoundException.class);
    }

    @Test
    void findAllByUserIdIn_delegatesToRepository() {
        Set<UUID> ids = Set.of(USER_ID);
        service.findAllByUserIdIn(ids);
        verify(profileRepository).findByIdIn(ids);
    }

    @Test
    void getEmailAndIdByUserIds_delegatesToRepository() {
        Set<UUID> ids = Set.of(USER_ID);
        service.getEmailAndIdByUserIds(ids);
        verify(profileRepository).findEmailsByUserIds(ids);
    }
}