package com.echcherqaoui.jobboard.applicationservice.service.impl;

import com.echcherqaoui.jobboard.applicationservice.dto.request.ApplicationRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.StatusUpdateRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicantApplicationDetailResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationCreationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationSummaryResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.JobApplicationPreview;
import com.echcherqaoui.jobboard.applicationservice.dto.response.StatusUpdateResponse;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.ApplicationNotFoundException;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.DuplicateApplicationException;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.InvalidStatusTransitionException;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.JobNotOpenException;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.UnauthorizedAccessException;
import com.echcherqaoui.jobboard.applicationservice.grpc.JobServiceClient;
import com.echcherqaoui.jobboard.applicationservice.grpc.ResilienceJobServiceClient;
import com.echcherqaoui.jobboard.applicationservice.grpc.ResilientJobSeekerProfileClient;
import com.echcherqaoui.jobboard.applicationservice.mapper.ApplicationMapper;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.applicationservice.service.ApplicationDataAccess;
import com.echcherqaoui.jobboard.applicationservice.service.ApplicationService;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileDetail;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.echcherqaoui.jobboard.applicationservice.exception.enums.ApplicationErrorCode.JOB_NOT_ACCEPTING_APPLICATIONS;
import static com.echcherqaoui.jobboard.applicationservice.exception.enums.ApplicationErrorCode.NOT_AUTHORIZED_TO_VIEW;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationServiceImpl implements ApplicationService {
    private final ApplicationDataAccess applicationDataAccess;
    private final ApplicationMapper applicationMapper;
    private final JobServiceClient jobServiceClient;
    private final ResilientJobSeekerProfileClient resilientJobSeekerProfileClient;
    private final ResilienceJobServiceClient resilienceJobServiceClient;
    private final JwtContextHolder jwtContextHolder;

    @Override
    public ApplicationCreationResponse submitApplication(@NonNull ApplicationRequest request) {
        UUID applicantId = jwtContextHolder.getUserId();

        if (applicationDataAccess.isAlreadyApplied(request.jobId(), applicantId))
            throw new DuplicateApplicationException(request.jobId());

        // Job Service call - Strict failure path. If this fails, the whole method fails.
        JobSummary jobSummary = jobServiceClient.getJob(request.jobId().toString());

        if (!jobSummary.getJobStatus().equals("OPEN"))
            throw new JobNotOpenException(JOB_NOT_ACCEPTING_APPLICATIONS, request.jobId());

        Application saved = applicationDataAccess.saveApplication(
              applicantId,
              request,
              jobSummary
        );

        return new ApplicationCreationResponse(
              saved.getId(),
              saved.getStatus(),
              saved.getSubmittedAt()
        );
    }

    @Override
    public PaginatedResponse<ApplicationSummaryResponse> getMyApplications(Pageable pageable) {
        UUID applicantId = jwtContextHolder.getUserId();

        Page<Application> applications = applicationDataAccess.findByApplicantId(applicantId, pageable);

        Set<String> jobIds = applications.stream()
              .map(app -> app.getJobId().toString())
              .collect(Collectors.toSet());

        Map<String, JobSummary> jobsById = jobServiceClient.getJobsByIds(jobIds).stream()
              .collect(Collectors.toMap(JobSummary::getJobId, Function.identity()));

        Function<Application, ApplicationSummaryResponse> toSummaryResponse =
              application -> applicationMapper.toSummaryResponse(
                    application,
                    jobsById.get(application.getJobId().toString())
              );

        return PaginatedResponse.of(applications, toSummaryResponse);
    }

    @Transactional(readOnly = true)
    @Override
    public PaginatedResponse<JobApplicationPreview> getApplicationsForJob(UUID jobId,
                                                                          ApplicationStatus status,
                                                                          Pageable pageable) {
        Page<Application> applicationPage = applicationDataAccess.findByJobIdAndStatus(jobId, status, pageable);

        Set<String> applicantIds = applicationPage.stream()
              .map(app -> app.getApplicantId().toString())
              .collect(Collectors.toSet());

        Map<UUID, JobSeekerProfileSummary> profilesById = resilientJobSeekerProfileClient.fetchProfilesTolerantly(applicantIds).stream()
              .collect(Collectors.toMap(
                    profile -> UUID.fromString(profile.getUserId()),
                    Function.identity()
              ));

        Function<Application, JobApplicationPreview> toJobApplicationPreview = application ->
              applicationMapper.toApplicationPreview(
                    application,
                    profilesById.get(application.getApplicantId())
              );


        return PaginatedResponse.of(applicationPage, toJobApplicationPreview);
    }

    @Transactional(readOnly = true)
    @Override
    public ApplicationResponse getApplicationById(UUID applicationId) {
        Application application = applicationDataAccess.findWithHistoryById(applicationId)
              .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (!application.getApplicantId().equals(jwtContextHolder.getUserId()))
            throw new UnauthorizedAccessException(NOT_AUTHORIZED_TO_VIEW);

        JobSummary job = resilienceJobServiceClient.fetchJobTolerantly(application.getJobId()).orElse(null);

        return applicationMapper.toApplicationResponse(application, job);
    }

    @Transactional(readOnly = true)
    @Override
    public ApplicantApplicationDetailResponse getApplicationForRecruiter(UUID applicationId) {
        Application application = applicationDataAccess.findWithHistoryById(applicationId)
              .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        //This one can't tolerate job-service being down
        // RecruiterId is only known via job-service, and authorization depends on it.
        JobSummary job = jobServiceClient.getJob(application.getJobId().toString());

        if (!job.getRecruiterId().equals(jwtContextHolder.getUserId().toString()))
            throw new UnauthorizedAccessException(NOT_AUTHORIZED_TO_VIEW);

        JobSeekerProfileDetail profile = resilientJobSeekerProfileClient.fetchProfileTolerantly(
              application.getApplicantId().toString()
        ).orElse(null);

        return applicationMapper.toRecruiterDetailResponse(application, job, profile);
    }

    @Override
    public StatusUpdateResponse updateStatus(UUID applicationId,
                                             @NonNull StatusUpdateRequest request) {

        Application application = applicationDataAccess.findWithHistoryById(applicationId)
              .orElseThrow(() -> new ApplicationNotFoundException(applicationId));

        if (!application.getStatus().canTransitionTo(request.status()))
            throw new InvalidStatusTransitionException(application.getStatus(), request.status());

        String jobId = application.getJobId().toString();

        JobSummary jobSummary = jobServiceClient.getJob(jobId);

        UUID recruiterId = jwtContextHolder.getUserId();

        if (!jobSummary.getRecruiterId().equals(recruiterId.toString()))
            throw new UnauthorizedAccessException(NOT_AUTHORIZED_TO_VIEW);

        if (!jobSummary.getJobStatus().equals("OPEN"))
            throw new JobNotOpenException(JOB_NOT_ACCEPTING_APPLICATIONS, jobId);

        ApplicationStatus oldStatus = application.getStatus();

        Application saved = applicationDataAccess.updateApplicationStatus(
              application,
              jobSummary,
              request.status(),
              recruiterId,
              request.note()
        );

        return new StatusUpdateResponse(
              saved.getId(),
              oldStatus,
              saved.getStatus(),
              saved.getUpdatedAt(),
              recruiterId.toString()
        );
    }
}
