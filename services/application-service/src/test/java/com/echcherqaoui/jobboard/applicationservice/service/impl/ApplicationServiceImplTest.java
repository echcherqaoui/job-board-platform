package com.echcherqaoui.jobboard.applicationservice.service.impl;

import com.echcherqaoui.jobboard.applicationservice.dto.request.ApplicationRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.StatusUpdateRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationCreationResponse;
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
import com.echcherqaoui.jobboard.applicationservice.service.ApplicationDataAccess;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.ACCEPTED;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.PENDING;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.REJECTED;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.REVIEWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplicationServiceImplTest {

    private ApplicationDataAccess applicationDataAccess;
    private JobServiceClient jobServiceClient;
    private JwtContextHolder jwtContextHolder;
    private ApplicationServiceImpl service;

    private final UUID applicantId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applicationDataAccess = mock(ApplicationDataAccess.class);
        ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
        jobServiceClient = mock(JobServiceClient.class);
        ResilientJobSeekerProfileClient resilientJobSeekerProfileClient = mock(ResilientJobSeekerProfileClient.class);
        ResilienceJobServiceClient resilienceJobServiceClient = mock(ResilienceJobServiceClient.class);
        jwtContextHolder = mock(JwtContextHolder.class);
        service = new ApplicationServiceImpl(
              applicationDataAccess, applicationMapper, jobServiceClient,
              resilientJobSeekerProfileClient, resilienceJobServiceClient, jwtContextHolder
        );
        when(jwtContextHolder.getUserId()).thenReturn(applicantId);
    }

    private ApplicationRequest applicationRequest() {
        return new ApplicationRequest(jobId, "http://cv.url", "cover letter");
    }

    private JobSummary jobSummary(String status, String recruiterId) {
        return JobSummary.newBuilder()
              .setJobId(jobId.toString())
              .setJobStatus(status)
              .setRecruiterId(recruiterId)
              .setTitle("Backend Engineer")
              .setCompanyName("Acme")
              .build();
    }

    // ---- submitApplication ----

    @Test
    void submitApplication_alreadyApplied_throwsDuplicateException_neverCallsJobService() {
        when(applicationDataAccess.isAlreadyApplied(jobId, applicantId)).thenReturn(true);

        assertThatThrownBy(() -> service.submitApplication(applicationRequest()))
              .isInstanceOf(DuplicateApplicationException.class);

        verifyNoInteractions(jobServiceClient);
        verify(applicationDataAccess, never()).saveApplication(any(), any(), any());
    }

    @Test
    void submitApplication_jobNotOpen_throwsJobNotOpenException() {
        when(applicationDataAccess.isAlreadyApplied(jobId, applicantId)).thenReturn(false);
        when(jobServiceClient.getJob(jobId.toString())).thenReturn(jobSummary("CLOSED", "recruiter-1"));

        assertThatThrownBy(() -> service.submitApplication(applicationRequest()))
              .isInstanceOf(JobNotOpenException.class);

        verify(applicationDataAccess, never()).saveApplication(any(), any(), any());
    }

    @Test
    void submitApplication_happyPath_returnsCorrectResponse() {
        when(applicationDataAccess.isAlreadyApplied(jobId, applicantId)).thenReturn(false);
        JobSummary job = jobSummary("OPEN", "recruiter-1");
        when(jobServiceClient.getJob(jobId.toString())).thenReturn(job);

        Application saved = new Application()
              .setJobId(jobId)
              .setApplicantId(applicantId)
              .setStatus(PENDING);
        saved.setSubmittedAt(OffsetDateTime.now());
        when(applicationDataAccess.saveApplication(applicantId, applicationRequest(), job)).thenReturn(saved);

        ApplicationCreationResponse response = service.submitApplication(applicationRequest());

        assertThat(response.status()).isEqualTo(PENDING);
        assertThat(response.id()).isEqualTo(saved.getId()); // was applicationId()
    }

    @Test
    void updateStatus_applicationNotFound_throws() {
        when(applicationDataAccess.findWithHistoryById(applicationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(applicationId, new StatusUpdateRequest(REVIEWED, "note")))
              .isInstanceOf(ApplicationNotFoundException.class);

        verifyNoInteractions(jobServiceClient);
    }

    @Test
    void updateStatus_invalidTransition_throwsBeforeCallingJobService() {
        Application app = new Application().setJobId(jobId).setApplicantId(applicantId).setStatus(REJECTED);
        when(applicationDataAccess.findWithHistoryById(applicationId)).thenReturn(Optional.of(app));

        assertThatThrownBy(() -> service.updateStatus(applicationId, new StatusUpdateRequest(ACCEPTED, "note")))
              .isInstanceOf(InvalidStatusTransitionException.class);

        // Local check must short-circuit before any gRPC call.
        verifyNoInteractions(jobServiceClient);
    }

    @Test
    void updateStatus_callerIsNotJobRecruiter_throwsUnauthorized() {
        Application app = new Application().setJobId(jobId).setApplicantId(applicantId).setStatus(PENDING);
        when(applicationDataAccess.findWithHistoryById(applicationId)).thenReturn(Optional.of(app));
        when(jobServiceClient.getJob(jobId.toString())).thenReturn(jobSummary("OPEN", "someone-else"));
        when(jwtContextHolder.getUserId()).thenReturn(applicantId); // not the recruiter

        assertThatThrownBy(() -> service.updateStatus(applicationId, new StatusUpdateRequest(REVIEWED, "note")))
              .isInstanceOf(UnauthorizedAccessException.class);

        verify(applicationDataAccess, never()).updateApplicationStatus(any(), any(), any(), any(), any());
    }

    @Test
    void updateStatus_jobNotOpen_throwsJobNotOpenException() {
        UUID recruiterId = UUID.randomUUID();
        Application app = new Application().setJobId(jobId).setApplicantId(applicantId).setStatus(PENDING);
        when(applicationDataAccess.findWithHistoryById(applicationId)).thenReturn(Optional.of(app));
        when(jobServiceClient.getJob(jobId.toString())).thenReturn(jobSummary("CLOSED", recruiterId.toString()));
        when(jwtContextHolder.getUserId()).thenReturn(recruiterId);

        assertThatThrownBy(() -> service.updateStatus(applicationId, new StatusUpdateRequest(REVIEWED, "note")))
              .isInstanceOf(JobNotOpenException.class);

        verify(applicationDataAccess, never()).updateApplicationStatus(any(), any(), any(), any(), any());
    }

    @Test
    void updateStatus_happyPath_returnsCorrectResponseWithOldAndNewStatus() {
        UUID recruiterId = UUID.randomUUID();
        Application app = new Application().setJobId(jobId).setApplicantId(applicantId).setStatus(PENDING);
        JobSummary job = jobSummary("OPEN", recruiterId.toString());
        when(applicationDataAccess.findWithHistoryById(applicationId)).thenReturn(Optional.of(app));
        when(jobServiceClient.getJob(jobId.toString())).thenReturn(job);
        when(jwtContextHolder.getUserId()).thenReturn(recruiterId);

        Application saved = new Application().setJobId(jobId).setApplicantId(applicantId).setStatus(REVIEWED);
        saved.setUpdatedAt(OffsetDateTime.now());
        when(applicationDataAccess.updateApplicationStatus(app, job, REVIEWED, recruiterId, "looks good"))
              .thenReturn(saved);

        StatusUpdateResponse response = service.updateStatus(applicationId, new StatusUpdateRequest(REVIEWED, "looks good"));

        assertThat(response.previousStatus()).isEqualTo(PENDING); // was oldStatus()
        assertThat(response.newStatus()).isEqualTo(REVIEWED);
        assertThat(response.changedBy()).isEqualTo(recruiterId.toString()); // was updatedBy()
    }
}