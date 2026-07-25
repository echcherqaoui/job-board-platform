package com.echcherqaoui.jobboard.notificationservice.grpc;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.notificationservice.exception.domain.EmailNotFoundException;
import com.echcherqaoui.jobboard.user.grpc.CompanyProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.GetRecruiterEmailRequest;
import com.echcherqaoui.jobboard.user.grpc.GetRecruiterEmailResponse;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;

import static com.echcherqaoui.jobboard.notificationservice.exception.enums.UserErrorCode.EMAIL_NOT_FOUND;
import static io.grpc.Status.Code.NOT_FOUND;

@Component
@Slf4j
public class CompanyProfileClient {

    @GrpcClient("user-service")
    private CompanyProfileServiceGrpc.CompanyProfileServiceBlockingStub companyStub;

    @NonNull
    private DownstreamDependencyException handleInfraError(@NonNull StatusRuntimeException ex) {
        return new DownstreamDependencyException(
              "user-service",
              ex.getStatus().getCode(),
              ex.getStatus().getDescription()
        );
    }

    public String getRecruiterEmail(@NonNull String recruiterId) {
        log.info("Sending gRPC request to user-service to fetch recruiter email for ID: {}", recruiterId);

        try {
            GetRecruiterEmailResponse response = companyStub.withDeadlineAfter(Duration.ofMillis(3000))
                  .getRecruiterEmail(
                        GetRecruiterEmailRequest.newBuilder()
                              .setProfileId(recruiterId)
                              .build()
                  );

            log.info("Successfully fetched recruiter email for ID: {}", recruiterId);
            return response.getEmail();

        } catch (StatusRuntimeException ex) {
            if (ex.getStatus().getCode() == NOT_FOUND) {
                log.warn("Recruiter not found in user-service for ID: {}", recruiterId);
                throw new EmailNotFoundException(EMAIL_NOT_FOUND, recruiterId);
            }

            log.warn(
                  "Could not fetch recruiter email {}: {} - {}",
                  recruiterId,
                  ex.getStatus().getCode(),
                  ex.getStatus().getDescription()
            );

            throw handleInfraError(ex);
        }
    }
}
