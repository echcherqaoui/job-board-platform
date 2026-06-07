package com.echcherqaoui.jobboard.jobservice.grpc;

import com.echcherqaoui.jobboard.user.grpc.CompanyProfileGrpc;
import com.echcherqaoui.jobboard.user.grpc.GetProfileByIdRequest;
import com.echcherqaoui.jobboard.user.grpc.UserProfileServiceGrpc;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Slf4j
public class UserServiceGrpcClient {

    @GrpcClient("user-service")
    private UserProfileServiceGrpc.UserProfileServiceBlockingStub stub;

    /**
     * Fetches a company profile by its profile ID.
     * Returns empty if not found or if User Service is unreachable.
     */
    public Optional<CompanyProfileGrpc> getCompanyProfileById(@NonNull String profileId) {
        try {
            CompanyProfileGrpc grpcProfile = stub.withDeadlineAfter(Duration.ofMillis(3000))
                  .getCompanyProfileById(
                    GetProfileByIdRequest.newBuilder()
                            .setProfileId(profileId)
                            .build()
            );
            return Optional.of(grpcProfile);
        } catch (StatusRuntimeException ex) {
            log.warn(
                  "Could not fetch company profile {}: {} - {}",
                  profileId,
                  ex.getStatus().getCode(),
                  ex.getStatus().getDescription()
            );
            return Optional.empty();
        }
    }
}
