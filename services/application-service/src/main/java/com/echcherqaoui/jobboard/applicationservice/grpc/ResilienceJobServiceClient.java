package com.echcherqaoui.jobboard.applicationservice.grpc;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResilienceJobServiceClient {

    private final JobServiceClient jobServiceClient;

    /** For read paths that can render without job details if job-service is down. */
    public Optional<JobSummary> fetchJobTolerantly(@NonNull UUID jobId) {
        try {
            return Optional.of(jobServiceClient.getJob(jobId.toString()));
        } catch (DownstreamDependencyException ex) {
            // Only degrade on network/timeout/availability issues
            if (ex.getGrpcCode() == Status.Code.UNAVAILABLE || ex.getGrpcCode() == Status.Code.DEADLINE_EXCEEDED) {
                log.warn("job-service unreachable or timing out. Degrading gracefully for jobId={}", jobId);
                return Optional.empty();
            }
            // Re-throw contract/security violations
            throw ex;
        }
    }

    public List<JobSummary> fetchJobsTolerantly(Set<String> jobIds) {
        try {
            return jobServiceClient.getJobsByIds(jobIds);
        } catch (DownstreamDependencyException ex) {
            Status.Code code = ex.getGrpcCode();

            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                // job-service down — degrade gracefully, list still shows without job details.
                log.warn("job-service unavailable for batch jobIds, degrading: {}", ex.getMessage());
                return List.of();
            }

            throw ex;
        }
    }
}