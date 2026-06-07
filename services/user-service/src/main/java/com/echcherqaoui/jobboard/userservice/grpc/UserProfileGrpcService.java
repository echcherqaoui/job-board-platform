package com.echcherqaoui.jobboard.userservice.grpc;

import com.echcherqaoui.jobboard.user.grpc.CompanyProfileGrpc;
import com.echcherqaoui.jobboard.user.grpc.GetProfileByIdRequest;
import com.echcherqaoui.jobboard.user.grpc.UserProfileServiceGrpc;
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
public class UserProfileGrpcService extends UserProfileServiceGrpc.UserProfileServiceImplBase {

    private final RecruiterProfileService recruiterProfileService;

    @Override
    @PreAuthorize("hasAuthority('RECRUITER')")
    public void getCompanyProfileById(@NonNull GetProfileByIdRequest request,
                                      @NonNull StreamObserver<CompanyProfileGrpc> responseObserver) {
        RecruiterProfile profile = recruiterProfileService
              .getProfileEntityById(UUID.fromString(request.getProfileId()));

        CompanyProfileGrpc response = CompanyProfileGrpc.newBuilder()
              .setCompanyName(profile.getCompanyName() != null ? profile.getCompanyName() : "")
              .setLogoUrl(profile.getCompanyLogoUrl() != null ? profile.getCompanyLogoUrl() : "")
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}