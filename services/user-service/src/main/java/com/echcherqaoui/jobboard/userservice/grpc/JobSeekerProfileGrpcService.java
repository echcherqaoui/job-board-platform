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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
        String userId = request.getUserId();

        log.info("Received gRPC request to fetch job seeker profile for user ID: {}", userId);

        JobSeekerProfile jobSeekerProfile = jobSeekerService.findProfileById(UUID.fromString(userId));

        GetJobSeekerProfileResponse response = GetJobSeekerProfileResponse.newBuilder()
              .setProfile(grpcMapper.toGrpcDetail(jobSeekerProfile))
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("Successfully returned job seeker profile for user ID: {}", userId);
    }

    @Override
    @PreAuthorize("hasRole('RECRUITER')")
    public void batchGetJobSeekerProfiles(@NonNull BatchGetJobSeekerProfilesRequest request,
                                          @NonNull StreamObserver<BatchGetJobSeekerProfilesResponse> responseObserver) {
        log.info("Received gRPC batch request to fetch job seeker profiles. Incoming count: {}", request.getUserIdsCount());

        Set<UUID> userIds = request.getUserIdsList().stream()
              .map(id -> {
                  try {
                      return UUID.fromString(id);
                  } catch (IllegalArgumentException e) {
                      log.warn("Malformed UUID skipped in batch: {}", id);
                      return null;
                  }
              }).filter(Objects::nonNull)
              .collect(Collectors.toSet());

        log.debug("Fetching summaries for {} valid user IDs from service layer", userIds.size());

        List<JobSeekerSummaryProjection> profiles = jobSeekerService.findAllByUserIdIn(userIds);

        List<JobSeekerProfileSummary> summaries = profiles.stream()
              .map(grpcMapper::toGrpcSummary)
              .toList();

        BatchGetJobSeekerProfilesResponse response = BatchGetJobSeekerProfilesResponse.newBuilder()
              .addAllProfiles(summaries)
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("Successfully completed batch request. Returned {} profile summaries", summaries.size());
    }
}
