package com.echcherqaoui.jobboard.applicationservice.grpc;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileDetail;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ResilientJobSeekerProfileClient {
    private final JobSeekerProfileClient jobSeekerProfileClient;

    public Optional<JobSeekerProfileDetail> fetchProfileTolerantly(String userId) {
        try {
            return Optional.of(jobSeekerProfileClient.getProfile(userId));
        } catch (DownstreamDependencyException ex) {
            Status.Code code = ex.getGrpcCode();

            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                // Service is down/slow — degrade gracefully, don't block applying.
                log.warn("user-service unavailable for userId={}, degrading: {}", userId, code);
                return Optional.empty();
            }
            throw ex;
        }
    }

    public List<JobSeekerProfileSummary> fetchProfilesTolerantly(Set<String> userIds) {
        try {
            return jobSeekerProfileClient.getProfilesByIds(userIds);
        } catch (DownstreamDependencyException ex) {
            Status.Code code = ex.getGrpcCode();

            if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
                // user-service down — degrade gracefully, list still shows without profile details.
                log.warn("user-service unavailable for batch userIds, degrading: {}", code);
                return List.of();
            }

            throw ex;
        }
    }
}
