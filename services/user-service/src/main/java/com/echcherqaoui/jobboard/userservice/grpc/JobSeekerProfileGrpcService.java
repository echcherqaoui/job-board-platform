package com.echcherqaoui.jobboard.userservice.grpc;

import com.echcherqaoui.jobboard.user.grpc.BatchGetJobSeekerProfilesRequest;
import com.echcherqaoui.jobboard.user.grpc.BatchGetJobSeekerProfilesResponse;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileRequest;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import com.echcherqaoui.jobboard.userservice.mapper.JobSeekerProfileGrpcMapper;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class JobSeekerProfileGrpcService extends JobSeekerProfileServiceGrpc.JobSeekerProfileServiceImplBase {
    private final JobSeekerProfileService jobSeekerService;
    private final JobSeekerProfileGrpcMapper grpcMapper;

    @Override
    @PreAuthorize("hasAnyRole('RECRUITER', 'CANDIDATE')")
    public void getJobSeekerProfile(@NonNull GetJobSeekerProfileRequest request,
                                    @NonNull StreamObserver<GetJobSeekerProfileResponse> responseObserver) {
        JobSeekerProfile jobSeekerProfile = jobSeekerService.findProfileById(UUID.fromString(request.getUserId()));

        GetJobSeekerProfileResponse response = GetJobSeekerProfileResponse.newBuilder()
              .setProfile(grpcMapper.toGrpcDetail(jobSeekerProfile))
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    @PreAuthorize("hasRole('RECRUITER')")
    public void batchGetJobSeekerProfiles(@NonNull BatchGetJobSeekerProfilesRequest request,
                                          @NonNull StreamObserver<BatchGetJobSeekerProfilesResponse> responseObserver) {
        List<UUID> userIds = request.getUserIdsList().stream()
              .map(id -> {
                  try {
                      return UUID.fromString(id);
                  } catch (IllegalArgumentException e) {
                      log.warn("Malformed UUID skipped in batch: {}", id);
                      return null;
                  }
              }).filter(Objects::nonNull)
              .toList();

        List<JobSeekerSummaryProjection> profiles = jobSeekerService.findAllByUserIdIn(userIds);

        List<JobSeekerProfileSummary> summaries = profiles.stream()
              .map(grpcMapper::toGrpcSummary)
              .toList();

        BatchGetJobSeekerProfilesResponse response = BatchGetJobSeekerProfilesResponse.newBuilder()
              .addAllProfiles(summaries)
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
