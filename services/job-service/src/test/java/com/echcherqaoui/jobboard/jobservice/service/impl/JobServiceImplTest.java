package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobSearchCriteria;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobStatusUpdateRequest;
import com.echcherqaoui.jobboard.jobservice.exception.domain.JobExpiredException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.JobNotFoundException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.UnauthorizedJobAccessException;
import com.echcherqaoui.jobboard.jobservice.mapper.JobMapper;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import com.echcherqaoui.jobboard.jobservice.repository.JobRepository;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.jobservice.service.JobOutboxService;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.CLOSED;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.OPEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobServiceImplTest {

    private JobRepository jobRepository;
    private CompanyProfileService companyProfileService;
    private JobOutboxService jobOutboxService;
    private JobMapper jobMapper;
    private JobServiceImpl service;

    private final UUID recruiterId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jobRepository = mock(JobRepository.class);
        companyProfileService = mock(CompanyProfileService.class);
        jobOutboxService = mock(JobOutboxService.class);
        jobMapper = mock(JobMapper.class);
        JwtContextHolder jwtContextHolder = mock(JwtContextHolder.class);
        service = new JobServiceImpl(jobRepository, companyProfileService, jobOutboxService, jobMapper, jwtContextHolder);
        when(jwtContextHolder.getUserId()).thenReturn(recruiterId);
    }

    private Job job(UUID owner, JobStatus status, OffsetDateTime expiresAt) {
        Job job = new Job();
        job.setId(jobId);
        job.setRecruiterId(owner);
        job.setStatus(status);
        job.setExpiresAt(expiresAt);
        return job;
    }

    @Test
    void updateJobStatus_notOwner_throwsUnauthorized() {
        Job job = job(UUID.randomUUID(), OPEN, null); // different owner
        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.updateJobStatus(jobId, new JobStatusUpdateRequest(CLOSED)))
              .isInstanceOf(UnauthorizedJobAccessException.class);

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(jobOutboxService);
    }

    @Test
    void updateJobStatus_jobExpired_throwsJobExpiredException() {
        Job job = job(recruiterId, OPEN, OffsetDateTime.now().minusDays(1));
        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.updateJobStatus(jobId, new JobStatusUpdateRequest(CLOSED)))
              .isInstanceOf(JobExpiredException.class);

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(jobOutboxService);
    }

    @Test
    void updateJobStatus_noTransitionGuard_allowsAnyStatusChange_documentingGap() {
        Job job = job(recruiterId, CLOSED, null);
        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenReturn(job);
        when(companyProfileService.findByRecruiterId(recruiterId)).thenReturn(Optional.empty());

        service.updateJobStatus(jobId, new JobStatusUpdateRequest(OPEN));

        assertThat(job.getStatus()).isEqualTo(OPEN);
        verify(jobOutboxService).publishJobStatusChanged(job);
    }

    @Test
    void updateJobStatus_happyPath_savesAndPublishesEvent() {
        Job job = job(recruiterId, OPEN, null);
        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenReturn(job);
        when(companyProfileService.findByRecruiterId(recruiterId)).thenReturn(Optional.empty());

        service.updateJobStatus(jobId, new JobStatusUpdateRequest(CLOSED));

        assertThat(job.getStatus()).isEqualTo(CLOSED);
        verify(jobRepository).save(job);
        verify(jobOutboxService).publishJobStatusChanged(job);
    }

    @Test
    void expireJobs_noExpiredJobs_doesNothing() {
        when(jobRepository.findExpiredJobs(any())).thenReturn(List.of());

        service.expireJobs();

        verify(jobRepository, never()).saveAll(any());
        verifyNoInteractions(jobOutboxService);
    }

    @Test
    void expireJobs_withExpiredJobs_closesAllAndPublishesBatch() {
        Job job1 = job(recruiterId, OPEN, OffsetDateTime.now().minusDays(1));
        Job job2 = job(recruiterId, OPEN, OffsetDateTime.now().minusDays(2));
        List<Job> expired = List.of(job1, job2);
        when(jobRepository.findExpiredJobs(any())).thenReturn(expired);
        when(jobRepository.saveAll(expired)).thenReturn(expired);

        service.expireJobs();

        assertThat(job1.getStatus()).isEqualTo(CLOSED);
        assertThat(job2.getStatus()).isEqualTo(CLOSED);
        verify(jobRepository).saveAll(expired);
        verify(jobOutboxService).publishJobExpiredBatch(expired);
        verify(jobOutboxService, never()).publishJobStatusChanged(any());
    }

    @Test
    void deleteJob_notOwner_throwsUnauthorized_neverDeletes() {
        Job job = job(UUID.randomUUID(), OPEN, null);
        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.deleteJob(jobId))
              .isInstanceOf(UnauthorizedJobAccessException.class);

        verify(jobRepository, never()).delete(any(Job.class));
        verifyNoInteractions(jobOutboxService);
    }

    @Test
    void deleteJob_owner_deletesAndPublishesEvent() {
        Job job = job(recruiterId, OPEN, null);
        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));

        service.deleteJob(jobId);

        verify(jobRepository).delete(job);
        verify(jobOutboxService).publishJobDeleted(job);
    }

    @Test
    void postJob_ifCompanyLookupThrowsAfterSave_jobIsAlreadyPersisted_documentingRisk() {
        JobRequest request = mock(JobRequest.class);
        Job mappedJob = new Job();
        when(jobMapper.toJobEntity(request)).thenReturn(mappedJob);
        when(jobRepository.save(mappedJob)).thenReturn(mappedJob);
        when(companyProfileService.getByRecruiterId(recruiterId))
              .thenThrow(new RuntimeException("company-service down"));

        assertThatThrownBy(() -> service.postJob(request))
              .isInstanceOf(RuntimeException.class);

        verify(jobRepository).save(mappedJob);
        verifyNoInteractions(jobOutboxService);
    }

    @Test
    void searchJobs_dedupsRecruiterIds_andBatchFetchesProfiles() {
        UUID recruiterA = UUID.randomUUID();
        UUID recruiterB = UUID.randomUUID();
        Job jobFromA1 = job(recruiterA, OPEN, null);
        Job jobFromA2 = job(recruiterA, OPEN, null);
        Job jobFromB = job(recruiterB, OPEN, null);

        JobSearchCriteria criteria = mock(JobSearchCriteria.class);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.Pageable.unpaged();
        Page<Job> page = new org.springframework.data.domain.PageImpl<>(List.of(jobFromA1, jobFromA2, jobFromB));

        when(jobRepository.findAll(ArgumentMatchers.<Specification<Job>>any(), eq(pageable)))
              .thenReturn(page);
        when(companyProfileService.getProfilesByRecruiterId(any())).thenReturn(Map.of());

        service.searchJobs(criteria, pageable);

        ArgumentCaptor<Set<UUID>> captor = ArgumentCaptor.forClass(Set.class);
        verify(companyProfileService).getProfilesByRecruiterId(captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder(recruiterA, recruiterB);
    }

    @Test
    void searchJobs_usesEnrichedMapper_whenProfileFound_andPlainMapper_whenNot() {
        Job jobWithProfile = job(recruiterId, OPEN, null);
        UUID orphanRecruiter = UUID.randomUUID();
        Job jobWithoutProfile = job(orphanRecruiter, OPEN, null);

        CompanyProfile profile = mock(CompanyProfile.class);
        JobSearchCriteria criteria = mock(JobSearchCriteria.class);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.Pageable.unpaged();
        Page<Job> page = new org.springframework.data.domain.PageImpl<>(List.of(jobWithProfile, jobWithoutProfile));

        when(jobRepository.findAll(ArgumentMatchers.<Specification<Job>>any(), eq(pageable)))
              .thenReturn(page);
        when(companyProfileService.getProfilesByRecruiterId(any()))
              .thenReturn(Map.of(recruiterId, profile));

        service.searchJobs(criteria, pageable);

        verify(jobMapper).toSummaryResponse(jobWithProfile, profile);
        verify(jobMapper).toSummaryResponse(jobWithoutProfile);
    }

    @Test
    void getMyJobs_usesEnrichedMapper_whenProfileExists() {
        Job job = job(recruiterId, OPEN, null);
        CompanyProfile profile = mock(CompanyProfile.class);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.Pageable.unpaged();
        Page<Job> page = new org.springframework.data.domain.PageImpl<>(List.of(job));

        when(jobRepository.findByRecruiterId(recruiterId, pageable)).thenReturn(page);
        when(companyProfileService.findByRecruiterId(recruiterId)).thenReturn(Optional.of(profile));

        service.getMyJobs(pageable);

        verify(jobMapper).toSummaryResponse(job, profile);
    }

    @Test
    void getMyJobs_usesPlainMapper_whenNoProfileExists() {
        Job job = job(recruiterId, OPEN, null);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.Pageable.unpaged();
        Page<Job> page = new org.springframework.data.domain.PageImpl<>(List.of(job));

        when(jobRepository.findByRecruiterId(recruiterId, pageable)).thenReturn(page);
        when(companyProfileService.findByRecruiterId(recruiterId)).thenReturn(Optional.empty());

        service.getMyJobs(pageable);

        verify(jobMapper).toSummaryResponse(job);
    }

    @Test
    void getJobById_throwsJobNotFound_whenMissing() {
        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJobById(jobId)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void getJobById_enrichesWithCompany_usingJobsOwnRecruiterId() {
        Job job = job(recruiterId, OPEN, null);
        CompanyProfile profile = mock(CompanyProfile.class);

        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));
        when(companyProfileService.findByRecruiterId(recruiterId)).thenReturn(Optional.of(profile));

        service.getJobById(jobId);

        verify(jobMapper).toResponse(job, profile);
    }

    @Test
    void getJobById_fallsBackToPlainMapper_whenNoProfileFound() {
        Job job = job(recruiterId, OPEN, null);

        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));
        when(companyProfileService.findByRecruiterId(recruiterId)).thenReturn(Optional.empty());

        service.getJobById(jobId);

        verify(jobMapper).toResponse(job);
    }

    @Test
    void findJobProjectionById_throwsJobNotFound_whenMissing() {
        when(jobRepository.findJobProjectionById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findJobProjectionById(jobId)).isInstanceOf(JobNotFoundException.class);
    }

    @Test
    void findJobProjectionById_returnsProjection_whenFound() {
        JobSummaryProjection projection = mock(JobSummaryProjection.class);
        when(jobRepository.findJobProjectionById(jobId)).thenReturn(Optional.of(projection));

        assertThat(service.findJobProjectionById(jobId)).isEqualTo(projection);
    }

    @Test
    void getJobsSummaries_delegatesToRepository() {
        Set<UUID> ids = Set.of(jobId);
        service.getJobsSummaries(ids);
        verify(jobRepository).findByIdIn(ids);
    }

    @Test
    void updateJob_throwsUnauthorized_whenNotOwner() {
        Job job = job(UUID.randomUUID(), OPEN, null);
        JobRequest request = mock(JobRequest.class);
        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.updateJob(jobId, request))
              .isInstanceOf(UnauthorizedJobAccessException.class);

        verify(jobRepository, never()).save(any());
        verifyNoInteractions(jobOutboxService);
    }

    @Test
    void updateJob_leavesSkillsUntouched_whenRequestSkillsNull() {
        Job job = job(recruiterId, OPEN, null);
        job.addSkills(List.of("Java"));
        JobRequest request = mock(JobRequest.class);
        when(request.skills()).thenReturn(null);

        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);
        when(companyProfileService.getByRecruiterId(recruiterId)).thenReturn(mock(CompanyProfile.class));

        service.updateJob(jobId, request);

        assertThat(job.getSkills()).hasSize(1);
    }

    @Test
    void updateJob_clearsSkills_whenRequestSkillsIsEmptyList() {
        Job job = job(recruiterId, OPEN, null);
        job.addSkills(List.of("Java"));
        JobRequest request = mock(JobRequest.class);
        when(request.skills()).thenReturn(List.of());

        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);
        when(companyProfileService.getByRecruiterId(recruiterId)).thenReturn(mock(CompanyProfile.class));

        service.updateJob(jobId, request);

        assertThat(job.getSkills()).isEmpty();
    }

    @Test
    void updateJob_replacesSkills_whenRequestSkillsNonEmpty() {
        Job job = job(recruiterId, OPEN, null);
        job.addSkills(List.of("Python"));
        JobRequest request = mock(JobRequest.class);
        when(request.skills()).thenReturn(List.of("Java", "Spring"));

        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);
        when(companyProfileService.getByRecruiterId(recruiterId)).thenReturn(mock(CompanyProfile.class));

        service.updateJob(jobId, request);

        assertThat(job.getSkills()).hasSize(2);
    }

    @Test
    void updateJob_savesJobBeforeCompanyProfileLookup_documentingSameRiskAsPostJob() {
        Job job = job(recruiterId, OPEN, null);
        JobRequest request = mock(JobRequest.class);
        when(request.skills()).thenReturn(null);

        when(jobRepository.findWithSkillsById(jobId)).thenReturn(Optional.of(job));
        when(jobRepository.save(job)).thenReturn(job);
        when(companyProfileService.getByRecruiterId(recruiterId))
              .thenThrow(new RuntimeException("company-service down"));

        assertThatThrownBy(() -> service.updateJob(jobId, request))
              .isInstanceOf(RuntimeException.class);

        verify(jobRepository).save(job);
        verifyNoInteractions(jobOutboxService);
    }
}