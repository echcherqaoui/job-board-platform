package com.echcherqaoui.jobboard.jobservice.grpc.client;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.CompanyProfileNotFoundException;
import com.echcherqaoui.jobboard.user.grpc.CompanyProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileRequest;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Fetches a company profile by its profile ID.
     * Returns empty if not found or if User Service is unreachable.
     */
    public Optional<CompanySummary> getCompanyProfileById(@NonNull String profileId) {
        try {
            GetCompanyProfileResponse response = companyStub.withDeadlineAfter(Duration.ofMillis(3000))
                  .getCompanyProfile(
                        GetCompanyProfileRequest.newBuilder()
                              .setProfileId(profileId)
                              .build()
                  );

            return response.hasCompany() ? Optional.of(response.getCompany()) : Optional.empty();
        } catch (StatusRuntimeException ex) {
            UUID id = UUID.fromString(profileId);
            if (ex.getStatus().getCode() == Status.Code.NOT_FOUND)
                // No profile exists for this user
                throw new CompanyProfileNotFoundException(id);

            log.warn(
                  "Could not fetch company profile {}: {} - {}",
                  profileId,
                  ex.getStatus().getCode(),
                  ex.getStatus().getDescription()
            );

            throw handleInfraError(ex);
        }
    }
}
