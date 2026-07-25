package com.echcherqaoui.jobboard.notificationservice.grpc;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.notificationservice.exception.domain.EmailNotFoundException;
import com.echcherqaoui.jobboard.user.grpc.GetEmailsByUserIdsRequest;
import com.echcherqaoui.jobboard.user.grpc.GetEmailsByUserIdsResponse;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerEmailRequest;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerEmailResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileServiceGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.echcherqaoui.jobboard.notificationservice.exception.enums.UserErrorCode.EMAIL_NOT_FOUND;

@Component
@Slf4j
public class JobSeekerProfileClient {

    @GrpcClient("user-service")
    private JobSeekerProfileServiceGrpc.JobSeekerProfileServiceBlockingStub jobSeekerStub;

    @NonNull
    private DownstreamDependencyException handleInfraError(@NonNull StatusRuntimeException ex) {
        return new DownstreamDependencyException(
              "user-service",
              ex.getStatus().getCode(),
              ex.getStatus().getDescription()
        );
    }

    public String getJobSeekerEmail(@NonNull String recruiterId) {
        log.info("Sending gRPC request to user-service to fetch jobseeker email for ID: {}", recruiterId);

        try {
            GetJobSeekerEmailResponse response = jobSeekerStub.withDeadlineAfter(Duration.ofMillis(3000))
                  .getJobSeekerEmail(
                        GetJobSeekerEmailRequest.newBuilder()
                              .setProfileId(recruiterId)
                              .build()
                  );

            log.info("Successfully fetched jobSeeker email for ID: {}", recruiterId);
            return response.getEmail();

        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND) {
                log.warn("JobSeeker email not found in user-service for ID: {}", recruiterId);
                throw new EmailNotFoundException(EMAIL_NOT_FOUND, recruiterId);
            }

            log.warn(
                  "Could not fetch jobSeeker email {}: {} - {}",
                  recruiterId,
                  ex.getStatus().getCode(),
                  ex.getStatus().getDescription()
            );

            throw handleInfraError(ex);
        }
    }


    public Map<String, String> getEmailsByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty())
            return Collections.emptyMap();

        log.info("Sending gRPC request to user-service to fetch emails for {} users", userIds.size());

        try {
            GetEmailsByUserIdsRequest request = GetEmailsByUserIdsRequest.newBuilder()
                  .addAllUserIds(userIds)
                  .build();

            GetEmailsByUserIdsResponse response = jobSeekerStub
                  .withDeadlineAfter(Duration.ofMillis(3000))
                  .getEmailsByUserIds(request);

            log.info("Successfully fetched emails for {} users", response.getUserIdToEmailCount());
            return response.getUserIdToEmailMap();
        } catch (StatusRuntimeException ex) {
            log.warn(
                  "Could not fetch emails for batch size {}: {} - {}",
                  userIds.size(),
                  ex.getStatus().getCode(),
                  ex.getStatus().getDescription()
            );

            throw handleInfraError(ex);
        }
    }
}
