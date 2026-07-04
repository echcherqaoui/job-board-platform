package com.echcherqaoui.jobboard.applicationservice.grpc;

import com.echcherqaoui.jobboard.applicationservice.exception.domain.JobNotFoundException;
import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.job.grpc.BatchGetJobSummariesRequest;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryRequest;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryResponse;
import com.echcherqaoui.jobboard.job.grpc.JobServiceGrpc;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
public class JobServiceClient {

    @GrpcClient("job-service")
    private JobServiceGrpc.JobServiceBlockingStub jobStub;

    @NonNull
    private DownstreamDependencyException handleInfraError(@NonNull StatusRuntimeException ex) {
        return new DownstreamDependencyException(
              "job-service",
              ex.getStatus().getCode(),
              ex.getStatus().getDescription()
        );
    }

    // Fetches a single job summary by ID from the job service.
    public JobSummary getJob(String jobId) {
        log.info("Sending gRPC request to job-service to fetch job summary for ID: {}", jobId);
        try {
            GetJobSummaryResponse response = jobStub.withDeadlineAfter(Duration.ofMillis(3000))
                  .getJobSummary(GetJobSummaryRequest.newBuilder().setJobId(jobId).build());

            if (!response.hasJob()) {
                log.warn("gRPC response missing job entity for ID: {}", jobId);
                throw new JobNotFoundException(jobId);
            }

            log.info("Successfully fetched job summary for ID: {}", jobId);
            return response.getJob();
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.warn("Job not found in job-service for ID: {}", jobId);
                throw new JobNotFoundException(jobId);
            }
            log.warn(
                  "gRPC getJob failed for jobId={}: {}",
                  jobId,
                  ex.getStatus()
            );

            throw handleInfraError(ex);
        }
    }

    // Fetches multiple job summaries by their IDs from the job service in a single call.
    public List<JobSummary> getJobsByIds(@NonNull Set<String> jobIds) {
        log.info("Sending gRPC batch request to job-service to fetch job summaries. Request count: {}", jobIds.size());
        try {
            return jobStub.withDeadlineAfter(Duration.ofMillis(3000))
                  .batchGetJobSummaries(BatchGetJobSummariesRequest.newBuilder().addAllJobIds(jobIds).build())
                  .getJobsList();
        } catch (StatusRuntimeException ex) {
            log.warn("gRPC getJobsByIds failed: {}", ex.getStatus());
            throw handleInfraError(ex);
        }
    }
}
