package com.echcherqaoui.jobboard.userservice.grpc;

import com.echcherqaoui.jobboard.user.grpc.BatchGetJobSeekerProfilesRequest;
import com.echcherqaoui.jobboard.user.grpc.BatchGetJobSeekerProfilesResponse;
import com.echcherqaoui.jobboard.user.grpc.GetEmailsByUserIdsRequest;
import com.echcherqaoui.jobboard.user.grpc.GetEmailsByUserIdsResponse;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerEmailRequest;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerEmailResponse;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileRequest;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileDetail;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import com.echcherqaoui.jobboard.userservice.mapper.JobSeekerProfileGrpcMapper;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import com.echcherqaoui.jobboard.userservice.projection.UserEmailProjection;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class JobSeekerProfileGrpcServiceTest {

    private JobSeekerProfileService jobSeekerService;
    private JobSeekerProfileGrpcMapper grpcMapper;
    private JobSeekerProfileGrpcService grpcService;

    @BeforeEach
    void setUp() {
        jobSeekerService = mock(JobSeekerProfileService.class);
        grpcMapper = mock(JobSeekerProfileGrpcMapper.class);
        grpcService = new JobSeekerProfileGrpcService(jobSeekerService, grpcMapper);
    }

    @Test
    void getJobSeekerProfile_happyPath_returnsResponseAndCompletes() {
        UUID userId = UUID.randomUUID();
        JobSeekerProfile profile = mock(JobSeekerProfile.class);
        JobSeekerProfileDetail detail = mock(JobSeekerProfileDetail.class);

        when(jobSeekerService.findProfileById(userId)).thenReturn(profile);
        when(grpcMapper.toGrpcDetail(profile)).thenReturn(detail);

        GetJobSeekerProfileRequest request = GetJobSeekerProfileRequest.newBuilder()
              .setUserId(userId.toString())
              .build();
        StreamObserver<GetJobSeekerProfileResponse> observer = mock(StreamObserver.class);

        grpcService.getJobSeekerProfile(request, observer);

        var captor = org.mockito.ArgumentCaptor.forClass(GetJobSeekerProfileResponse.class);
        verify(observer).onNext(captor.capture());
        verify(observer).onCompleted();
        assertThat(captor.getValue().getProfile()).isEqualTo(detail);
    }

    @Test
    void batchGetJobSeekerProfiles_filtersOutMalformedUuids() {
        UUID validId = UUID.randomUUID();
        BatchGetJobSeekerProfilesRequest request = BatchGetJobSeekerProfilesRequest.newBuilder()
              .addUserIds(validId.toString())
              .addUserIds("not-a-uuid")
              .build();

        JobSeekerSummaryProjection projection = mock(JobSeekerSummaryProjection.class);
        JobSeekerProfileSummary summary = mock(JobSeekerProfileSummary.class);

        when(jobSeekerService.findAllByUserIdIn(anySet())).thenReturn(List.of(projection));
        when(grpcMapper.toGrpcSummary(projection)).thenReturn(summary);

        StreamObserver<BatchGetJobSeekerProfilesResponse> observer = mock(StreamObserver.class);

        grpcService.batchGetJobSeekerProfiles(request, observer);

        var idsCaptor = org.mockito.ArgumentCaptor.forClass(Set.class);
        verify(jobSeekerService).findAllByUserIdIn(idsCaptor.capture());

        // Only the valid UUID should have been passed through; the malformed one is dropped.
        assertThat(idsCaptor.getValue()).containsExactly(validId);

        verify(observer).onCompleted();
    }

    @Test
    void batchGetJobSeekerProfiles_emptyResult_returnsEmptyResponse() {
        BatchGetJobSeekerProfilesRequest request = BatchGetJobSeekerProfilesRequest.newBuilder().build();
        when(jobSeekerService.findAllByUserIdIn(anySet())).thenReturn(List.of());

        StreamObserver<BatchGetJobSeekerProfilesResponse> observer = mock(StreamObserver.class);

        grpcService.batchGetJobSeekerProfiles(request, observer);

        var captor = org.mockito.ArgumentCaptor.forClass(BatchGetJobSeekerProfilesResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getProfilesList()).isEmpty();
        verify(observer).onCompleted();
    }

    @Test
    void getJobSeekerEmail_happyPath_returnsEmail() {
        UUID profileId = UUID.randomUUID();
        when(jobSeekerService.getProfileEmailById(profileId)).thenReturn("seeker@x.com");

        GetJobSeekerEmailRequest request = GetJobSeekerEmailRequest.newBuilder()
              .setProfileId(profileId.toString())
              .build();
        StreamObserver<GetJobSeekerEmailResponse> observer = mock(StreamObserver.class);

        grpcService.getJobSeekerEmail(request, observer);

        var captor = org.mockito.ArgumentCaptor.forClass(GetJobSeekerEmailResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("seeker@x.com");
        verify(observer).onCompleted();
    }

    @Test
    void getEmailsByUserIds_emptyRequestList_returnsDefaultInstanceWithoutCallingService() {
        GetEmailsByUserIdsRequest request = GetEmailsByUserIdsRequest.newBuilder().build();
        StreamObserver<GetEmailsByUserIdsResponse> observer = mock(StreamObserver.class);

        grpcService.getEmailsByUserIds(request, observer);

        verify(jobSeekerService, never()).getEmailAndIdByUserIds(anySet());
        var captor = org.mockito.ArgumentCaptor.forClass(GetEmailsByUserIdsResponse.class);
        verify(observer).onNext(captor.capture());
        assertThat(captor.getValue()).isEqualTo(GetEmailsByUserIdsResponse.getDefaultInstance());
        verify(observer).onCompleted();
    }

    @Test
    void getEmailsByUserIds_filtersNullAndBlankEmails() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        GetEmailsByUserIdsRequest request = GetEmailsByUserIdsRequest.newBuilder()
              .addUserIds(id1.toString())
              .addUserIds(id2.toString())
              .build();

        UserEmailProjection proj1 = mock(UserEmailProjection.class);
        when(proj1.getId()).thenReturn(id1);
        when(proj1.getEmail()).thenReturn("valid@x.com");

        UserEmailProjection proj2 = mock(UserEmailProjection.class);
        when(proj2.getId()).thenReturn(id2);
        when(proj2.getEmail()).thenReturn("  "); // blank, should be filtered

        when(jobSeekerService.getEmailAndIdByUserIds(anySet())).thenReturn(List.of(proj1, proj2));

        StreamObserver<GetEmailsByUserIdsResponse> observer = mock(StreamObserver.class);

        grpcService.getEmailsByUserIds(request, observer);

        var captor = org.mockito.ArgumentCaptor.forClass(GetEmailsByUserIdsResponse.class);
        verify(observer).onNext(captor.capture());

        var map = captor.getValue().getUserIdToEmailMap();
        assertThat(map).hasSize(1);
        assertThat(map.get(id1.toString())).isEqualTo("valid@x.com");
        assertThat(map.containsKey(id2.toString())).isFalse();

        verify(observer).onCompleted();
    }
}