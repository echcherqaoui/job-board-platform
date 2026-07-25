package com.echcherqaoui.jobboard.userservice.grpc;

import com.echcherqaoui.jobboard.user.grpc.BatchGetJobSeekerProfilesRequest;
import com.echcherqaoui.jobboard.user.grpc.BatchGetJobSeekerProfilesResponse;
import com.echcherqaoui.jobboard.user.grpc.GetEmailsByUserIdsRequest;
import com.echcherqaoui.jobboard.user.grpc.GetEmailsByUserIdsResponse;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerEmailRequest;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerEmailResponse;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileRequest;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import com.echcherqaoui.jobboard.userservice.mapper.JobSeekerProfileGrpcMapper;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import com.echcherqaoui.jobboard.userservice.projection.UserEmailProjection;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class JobSeekerProfileGrpcService extends JobSeekerProfileServiceGrpc.JobSeekerProfileServiceImplBase {
    private final JobSeekerProfileService jobSeekerService;
    private final JobSeekerProfileGrpcMapper grpcMapper;

    @NonNull
    private  Set<UUID> getUuids(Stream<String> idsStream) {
        return idsStream
              .map(id -> {
                  try {
                      return UUID.fromString(id);
                  } catch (IllegalArgumentException e) {
                      log.warn("Malformed UUID skipped in batch: {}", id);
                      return null;
                  }
              }).filter(Objects::nonNull)
              .collect(Collectors.toSet());
    }

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

        Set<UUID> userIds = getUuids(request.getUserIdsList().stream());

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

    @Override
    @PreAuthorize("hasAuthority('SCOPE_INTERNAL')")
    public void getJobSeekerEmail(@NonNull GetJobSeekerEmailRequest request,
                                  @NonNull StreamObserver<GetJobSeekerEmailResponse> responseObserver) {
        String profileId = request.getProfileId();

        log.info("Received gRPC request to fetch jobseeker email for ID: {}", profileId);

        String email = jobSeekerService.getProfileEmailById(UUID.fromString(profileId));

        GetJobSeekerEmailResponse response = GetJobSeekerEmailResponse.newBuilder()
              .setEmail(email)
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("Successfully processed and returned jobseeker email for ID: {}", profileId);
    }

    @Override
    @PreAuthorize("hasAuthority('SCOPE_INTERNAL')")
    public void getEmailsByUserIds(@NonNull GetEmailsByUserIdsRequest request,
                                   @NonNull StreamObserver<GetEmailsByUserIdsResponse> responseObserver) {
        log.info("Received gRPC request to fetch emails for {} user IDs", request.getUserIdsList().size());

        if (request.getUserIdsList().isEmpty()) {
            log.debug("Empty userIds list, returning default response");
            responseObserver.onNext(GetEmailsByUserIdsResponse.getDefaultInstance());
            responseObserver.onCompleted();
            return;
        }

        Set<UUID> userIds = getUuids(request.getUserIdsList().stream());

        List<UserEmailProjection> profiles = jobSeekerService.getEmailAndIdByUserIds(userIds);

        // Build the target map safely
        Map<String, String> idToEmailMap = profiles.stream()
              .filter(p -> p.getEmail() != null && !p.getEmail().isBlank())
              .collect(Collectors.toMap(p -> p.getId().toString(), UserEmailProjection::getEmail));

        if (idToEmailMap.size() < userIds.size())
            log.warn(
                  "Resolved {} emails out of {} requested user IDs — {} missing or blank",
                  idToEmailMap.size(),
                  userIds.size(),
                  userIds.size() - idToEmailMap.size()
            );

        GetEmailsByUserIdsResponse response = GetEmailsByUserIdsResponse.newBuilder()
              .putAllUserIdToEmail(idToEmailMap)
              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("Successfully returned {} emails for gRPC request", idToEmailMap.size());
    }
}
