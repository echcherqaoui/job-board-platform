package com.echcherqaoui.jobboard.jobservice.service;

import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobSearchCriteria;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobStatusUpdateRequest;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobResponse;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobSummaryResponse;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface JobService {
    JobResponse postJob(JobRequest request);

    PaginatedResponse<JobSummaryResponse> searchJobs(JobSearchCriteria criteria, Pageable pageable);

    JobSummaryProjection findJobProjectionById(UUID jobId);

    JobResponse getJobById(UUID jobId);

    List<JobSummaryProjection> getJobsSummaries(Set<UUID> jobIds);

    PaginatedResponse<JobSummaryResponse> getMyJobs(Pageable pageable);

    JobResponse updateJob(UUID jobId, JobRequest request);

    JobResponse updateJobStatus(UUID jobId,
                                JobStatusUpdateRequest request);

    void expireJobs();

    void deleteJob(UUID jobId);
}
