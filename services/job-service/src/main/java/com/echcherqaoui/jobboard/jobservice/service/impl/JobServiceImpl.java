package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobSearchCriteria;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobStatusUpdateRequest;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobResponse;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobSummaryResponse;
import com.echcherqaoui.jobboard.jobservice.exception.domain.JobExpiredException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.JobNotFoundException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.UnauthorizedJobAccessException;
import com.echcherqaoui.jobboard.jobservice.mapper.JobMapper;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import com.echcherqaoui.jobboard.jobservice.repository.JobRepository;
import com.echcherqaoui.jobboard.jobservice.repository.JobSpecification;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.jobservice.service.JobOutboxService;
import com.echcherqaoui.jobboard.jobservice.service.JobService;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.echcherqaoui.jobboard.jobservice.exception.enums.JobErrorCode.COMPANY_DOES_NOT_OWN_JOB;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.CLOSED;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final CompanyProfileService companyProfileService;
    private final JobOutboxService jobOutboxService;
    private final JobMapper jobMapper;
    private final JwtContextHolder jwtContextHolder;

    private void populateSkills(Job job, List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) return;

        job.addSkills(skillNames);
    }

    private JobResponse enrichWithCompany(Job job, UUID companyId) {
        Optional<CompanyProfile> profile = companyProfileService.findByRecruiterId(companyId);
        if (profile.isEmpty()) return jobMapper.toResponse(job);

        return jobMapper.toResponse(job, profile.get());
    }

    private @NonNull Job findAndVerifyOwnership(UUID jobId, UUID companyId) {
        Job job = jobRepository.findWithSkillsById(jobId)
              .orElseThrow(() -> new JobNotFoundException(jobId));

        if (!job.getRecruiterId().equals(companyId))
            throw new UnauthorizedJobAccessException(COMPANY_DOES_NOT_OWN_JOB, companyId, jobId);

        return job;
    }

    @Transactional
    @Override
    public JobResponse postJob(JobRequest request) {
        UUID currentUserId = jwtContextHolder.getUserId();

        Job job = jobMapper.toJobEntity(request);
        job.setRecruiterId(currentUserId);

        populateSkills(job, request.skills());

        Job saved = jobRepository.save(job);
        log.info("Created job {} for company {}", saved.getId(), currentUserId);

        CompanyProfile companyProfile = companyProfileService.getByRecruiterId(currentUserId);

        jobOutboxService.publishJobUpserted(saved, companyProfile);

        return jobMapper.toResponse(saved, companyProfile);
    }

    @Transactional(readOnly = true)
    @Override
    public PaginatedResponse<JobSummaryResponse> searchJobs(JobSearchCriteria criteria, Pageable pageable) {
        Specification<Job> jobSpecification = JobSpecification.withFilters(criteria);

        Page<Job> jobPage = jobRepository.findAll(jobSpecification, pageable);

        // Extract all unique recruiter IDs from the page
        Set<UUID> recruiterIds = jobPage.stream()
              .map(Job::getRecruiterId)
              .collect(Collectors.toSet());

        // Single query — fetch all needed profiles at once
        Map<UUID, CompanyProfile> profileMap = companyProfileService.getProfilesByRecruiterId(recruiterIds);

        Function<Job, JobSummaryResponse> responseFunction = job -> {
            CompanyProfile profile = profileMap.get(job.getRecruiterId());
            return profile != null
                  ? jobMapper.toSummaryResponse(job, profile)
                  : jobMapper.toSummaryResponse(job);
        };

        return PaginatedResponse.of(jobPage, responseFunction);
    }


    @Transactional(readOnly = true)
    @Override
    public JobResponse getJobById(UUID jobId) {
        Job job = jobRepository.findWithSkillsById(jobId)
              .orElseThrow(() -> new JobNotFoundException(jobId));

        return enrichWithCompany(job, job.getRecruiterId());
    }

    @Transactional(readOnly = true)
    @Override
    public JobSummaryProjection findJobProjectionById(UUID jobId) {
        return jobRepository.findJobProjectionById(jobId)
              .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    @Transactional(readOnly = true)
    @Override
    public List<JobSummaryProjection> getJobsSummaries(Set<UUID> jobIds) {
        return jobRepository.findByIdIn(jobIds);
    }

    @Transactional(readOnly = true)
    @Override
    public PaginatedResponse<JobSummaryResponse> getMyJobs(Pageable pageable) {
        UUID currentUserId = jwtContextHolder.getUserId();

        Page<Job> jobs = jobRepository.findByRecruiterId(currentUserId, pageable);
        CompanyProfile profile = companyProfileService.findByRecruiterId(currentUserId).orElse(null);

        Function<Job, JobSummaryResponse> responseFunction = job -> profile != null
              ? jobMapper.toSummaryResponse(job, profile)
              : jobMapper.toSummaryResponse(job);

        return PaginatedResponse.of(jobs, responseFunction);
    }

    @Transactional
    @Override
    public JobResponse updateJob(UUID jobId, JobRequest request) {
        UUID currentUserId = jwtContextHolder.getUserId();

        Job job = findAndVerifyOwnership(jobId, currentUserId);

        jobMapper.updateEntity(request, job);

        if (request.skills() != null) {
            job.getSkills().clear();
            populateSkills(job, request.skills());
        }

        Job saved = jobRepository.save(job);
        log.info("Updated job {} for company {}", saved.getId(), currentUserId);

        CompanyProfile companyProfile = companyProfileService.getByRecruiterId(currentUserId);

        jobOutboxService.publishJobUpserted(saved, companyProfile);

        return jobMapper.toResponse(saved, companyProfile);
    }

    @Transactional
    @Override
    public JobResponse updateJobStatus(UUID jobId,
                                       @NonNull JobStatusUpdateRequest request) {
        UUID currentUserId = jwtContextHolder.getUserId();

        Job job = findAndVerifyOwnership(jobId, currentUserId);

        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(OffsetDateTime.now()))
            throw new JobExpiredException(jobId);

        job.setStatus(request.status());
        Job saved = jobRepository.save(job);

        log.info(
              "Job {} status changed to {} by company {}",
              jobId,
              request.status(),
              currentUserId
        );

        jobOutboxService.publishJobStatusChanged(saved);

        return enrichWithCompany(job, currentUserId);
    }

    @Transactional
    @Override
    public void expireJobs() {
        List<Job> expired = jobRepository.findExpiredJobs(OffsetDateTime.now());
        if (expired.isEmpty()) return;

        expired.forEach(job -> job.setStatus(CLOSED));
        jobRepository.saveAll(expired);
        jobOutboxService.publishJobExpiredBatch(expired);
    }

    @Transactional
    @Override
    public void deleteJob(UUID jobId) {
        UUID currentUserId = jwtContextHolder.getUserId();

        Job job = findAndVerifyOwnership(jobId, currentUserId);
        jobRepository.delete(job);
        jobOutboxService.publishJobDeleted(job);

        log.info("Deleted job {} for company {}", jobId, currentUserId);
    }

}
