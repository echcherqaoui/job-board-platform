package com.echcherqaoui.jobboard.jobservice.service;

import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobSearchCriteria;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobStatusUpdateRequest;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobResponse;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobSummaryResponse;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;

import java.util.UUID;

public interface JobService {
    JobResponse postJob(JobRequest request);

    PaginatedResponse<JobSummaryResponse> searchJobs(JobSearchCriteria criteria, Pageable pageable);

    JobResponse getJobById(UUID jobId);

    PaginatedResponse<JobSummaryResponse> getMyJobs(Pageable pageable);

    JobResponse updateJob(UUID jobId, JobRequest request);

    JobResponse updateJobStatus(UUID jobId,
                                @NonNull JobStatusUpdateRequest request);

    void deleteJob(UUID jobId);
}
