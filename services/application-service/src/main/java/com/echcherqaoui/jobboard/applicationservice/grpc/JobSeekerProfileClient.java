package com.echcherqaoui.jobboard.applicationservice.grpc;

import com.echcherqaoui.jobboard.applicationservice.exception.domain.ApplicantProfileNotFoundException;
import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.user.grpc.BatchGetJobSeekerProfilesRequest;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileRequest;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileDetail;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
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
public class JobSeekerProfileClient {

    @GrpcClient("user-service")
    private JobSeekerProfileServiceGrpc.JobSeekerProfileServiceBlockingStub userStub;

    @NonNull
    private DownstreamDependencyException handleInfraError(@NonNull StatusRuntimeException ex) {
        return new DownstreamDependencyException(
              "user-service",
              ex.getStatus().getCode(),
              ex.getStatus().getDescription()
        );
    }

    // Fetches a jobseeker profile by user ID from the user service.
    public JobSeekerProfileDetail getProfile(String userId) {
        try {
            GetJobSeekerProfileResponse jobSeekerProfileResponse = userStub.withDeadlineAfter(Duration.ofMillis(3000))
                  .getJobSeekerProfile(
                        GetJobSeekerProfileRequest.newBuilder().setUserId(userId).build()
                  );
            if (!jobSeekerProfileResponse.hasProfile())
                throw new ApplicantProfileNotFoundException(userId);

            return jobSeekerProfileResponse.getProfile();
        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND)
                // No profile exists for this user
                throw new ApplicantProfileNotFoundException(userId);

            // Anything else (INVALID_ARGUMENT, PERMISSION_DENIED, etc.) is unexpected — fail loudly.
            log.error("gRPC getJobSeekerProfile failed for userId={}: {}", userId, ex.getStatus());
            throw handleInfraError(ex);
        }
    }

    /**
     * Bulk fetches profiles by user IDs.
     * Returns an empty list on gRPC failure to degrade gracefully.
     */
    public List<JobSeekerProfileSummary> getProfilesByIds(Set<String> userIds) {
        try {
            return userStub.withDeadlineAfter(Duration.ofMillis(3000))
                  .batchGetJobSeekerProfiles(
                        BatchGetJobSeekerProfilesRequest.newBuilder()
                              .addAllUserIds(userIds)
                              .build()
                  ).getProfilesList();
        } catch (StatusRuntimeException ex) {

            log.error("gRPC getProfilesByIds failed: {}", ex.getStatus());
            throw handleInfraError(ex);
        }
    }
}
