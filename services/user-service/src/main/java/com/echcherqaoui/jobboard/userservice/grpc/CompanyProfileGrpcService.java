package com.echcherqaoui.jobboard.userservice.grpc;

import com.echcherqaoui.jobboard.user.grpc.CompanyProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileRequest;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileResponse;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.echcherqaoui.jobboard.userservice.service.RecruiterProfileService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class CompanyProfileGrpcService extends CompanyProfileServiceGrpc.CompanyProfileServiceImplBase {

    private final RecruiterProfileService recruiterProfileService;

    @Override
    @PreAuthorize("hasRole('RECRUITER')")
    public void getCompanyProfile(@NonNull GetCompanyProfileRequest request,
                                  @NonNull StreamObserver<GetCompanyProfileResponse> responseObserver) {
        String profileId = request.getProfileId();
        log.info("Received gRPC request to fetch company profile for ID: {}", profileId);

        RecruiterProfile profile = recruiterProfileService
              .getProfileEntityById(UUID.fromString(profileId));

        CompanySummary companySummary = CompanySummary.newBuilder()
              .setCompanyName(profile.getCompanyName() != null ? profile.getCompanyName() : "")
              .setLogoUrl(profile.getCompanyLogoUrl() != null ? profile.getCompanyLogoUrl() : "")
              .build();

        GetCompanyProfileResponse response = GetCompanyProfileResponse.newBuilder()
              .setCompany(companySummary)
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("Successfully processed and returned company profile for ID: {}", profileId);
    }
}