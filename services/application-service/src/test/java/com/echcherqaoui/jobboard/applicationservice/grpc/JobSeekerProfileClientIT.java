package com.echcherqaoui.jobboard.applicationservice.grpc;

import com.echcherqaoui.jobboard.applicationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.ApplicantProfileNotFoundException;
import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.user.grpc.BatchGetJobSeekerProfilesResponse;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerProfileResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileDetail;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@SpringBootTest
class JobSeekerProfileClientIT extends AbstractIntegrationTest {

    @Autowired
    private JobSeekerProfileClient jobSeekerProfileClient;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private JobSeekerProfileServiceGrpc.JobSeekerProfileServiceImplBase serviceImplSpy;

    @BeforeEach
    void setUp() throws IOException {
        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });

        // Instantiate standard base class and create a spy to mock behavior without custom classes
        serviceImplSpy = spy(new JobSeekerProfileServiceGrpc.JobSeekerProfileServiceImplBase() {});

        String serverName = InProcessServerBuilder.generateName();

        inProcessServer = InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(serviceImplSpy)
              .build()
              .start();

        inProcessChannel = InProcessChannelBuilder.forName(serverName)
              .directExecutor()
              .build();

        JobSeekerProfileServiceGrpc.JobSeekerProfileServiceBlockingStub stub =
              JobSeekerProfileServiceGrpc.newBlockingStub(inProcessChannel);

        ReflectionTestUtils.setField(jobSeekerProfileClient, "userStub", stub);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (inProcessChannel != null)
            inProcessChannel.shutdownNow().awaitTermination(5, SECONDS);

        if (inProcessServer != null)
            inProcessServer.shutdownNow().awaitTermination(5, SECONDS);
    }

    @Nested
    class GetProfile {

        @Test
        void getProfile_WhenProfileExists_ShouldReturnProfileDetail() {
            String userId = UUID.randomUUID().toString();
            JobSeekerProfileDetail detail = JobSeekerProfileDetail.newBuilder()
                  .setUserId(userId)
                  .setFirstName("John")
                  .setLastName("Doe")
                  .setEmail("john@test.com")
                  .build();

            GetJobSeekerProfileResponse response = GetJobSeekerProfileResponse.newBuilder()
                  .setProfile(detail)
                  .build();

            doAnswer(invocation -> {
                StreamObserver<GetJobSeekerProfileResponse> observer = invocation.getArgument(1);
                observer.onNext(response);
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).getJobSeekerProfile(any(), any());

            JobSeekerProfileDetail result = jobSeekerProfileClient.getProfile(userId);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getFirstName()).isEqualTo("John");
        }

        @Test
        void getProfile_WhenResponseHasNoProfile_ShouldThrowApplicantProfileNotFoundException() {
            String userId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetJobSeekerProfileResponse> observer = invocation.getArgument(1);
                observer.onNext(GetJobSeekerProfileResponse.newBuilder().build());
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).getJobSeekerProfile(any(), any());

            Executable action = () -> jobSeekerProfileClient.getProfile(userId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(ApplicantProfileNotFoundException.class);
        }

        @Test
        void getProfile_WhenGrpcReturnsNotFound_ShouldThrowApplicantProfileNotFoundException() {
            String userId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetJobSeekerProfileResponse> observer = invocation.getArgument(1);
                observer.onError(Status.NOT_FOUND.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getJobSeekerProfile(any(), any());

            Executable action = () -> jobSeekerProfileClient.getProfile(userId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(ApplicantProfileNotFoundException.class);
        }

        @Test
        void getProfile_WhenGrpcReturnsUnavailable_ShouldThrowDownstreamDependencyException() {
            String userId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetJobSeekerProfileResponse> observer = invocation.getArgument(1);
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getJobSeekerProfile(any(), any());

            Executable action = () -> jobSeekerProfileClient.getProfile(userId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(DownstreamDependencyException.class);
        }
    }

    @Nested
    class GetProfilesByIds {

        @Test
        void getProfilesByIds_WhenValidUserIds_ShouldReturnProfileSummaries() {
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();

            JobSeekerProfileSummary s1 = JobSeekerProfileSummary.newBuilder().setUserId(id1).build();
            JobSeekerProfileSummary s2 = JobSeekerProfileSummary.newBuilder().setUserId(id2).build();

            BatchGetJobSeekerProfilesResponse response = BatchGetJobSeekerProfilesResponse.newBuilder()
                  .addAllProfiles(List.of(s1, s2))
                  .build();

            doAnswer(invocation -> {
                StreamObserver<BatchGetJobSeekerProfilesResponse> observer = invocation.getArgument(1);
                observer.onNext(response);
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).batchGetJobSeekerProfiles(any(), any());

            List<JobSeekerProfileSummary> result = jobSeekerProfileClient.getProfilesByIds(Set.of(id1, id2));

            assertThat(result).hasSize(2);
            assertThat(result).extracting("userId").containsExactlyInAnyOrder(id1, id2);
        }

        @Test
        void getProfilesByIds_WhenGrpcReturnsInternalError_ShouldThrowDownstreamDependencyException() {
            Set<String> userIds = Set.of(UUID.randomUUID().toString());

            doAnswer(invocation -> {
                StreamObserver<BatchGetJobSeekerProfilesResponse> observer = invocation.getArgument(1);
                observer.onError(Status.INTERNAL.asRuntimeException());
                return null;
            }).when(serviceImplSpy).batchGetJobSeekerProfiles(any(), any());

            Executable action = () -> jobSeekerProfileClient.getProfilesByIds(userIds);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(DownstreamDependencyException.class);
        }
    }
}