package com.echcherqaoui.jobboard.notificationservice.grpc;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.exception.domain.EmailNotFoundException;
import com.echcherqaoui.jobboard.user.grpc.GetEmailsByUserIdsResponse;
import com.echcherqaoui.jobboard.user.grpc.GetJobSeekerEmailResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileServiceGrpc;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
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

        ReflectionTestUtils.setField(jobSeekerProfileClient, "jobSeekerStub", stub);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (inProcessChannel != null)
            inProcessChannel.shutdownNow().awaitTermination(5, SECONDS);

        if (inProcessServer != null)
            inProcessServer.shutdownNow().awaitTermination(5, SECONDS);
    }

    @Nested
    class GetJobSeekerEmail {

        @Test
        void getJobSeekerEmail_WhenJobSeekerExists_ShouldReturnEmail() {
            String profileId = UUID.randomUUID().toString();
            String expectedEmail = "jobseeker@acme.com";

            GetJobSeekerEmailResponse response = GetJobSeekerEmailResponse.newBuilder()
                  .setEmail(expectedEmail)
                  .build();

            doAnswer(invocation -> {
                StreamObserver<GetJobSeekerEmailResponse> observer = invocation.getArgument(1);
                observer.onNext(response);
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).getJobSeekerEmail(any(), any());

            String result = jobSeekerProfileClient.getJobSeekerEmail(profileId);

            assertThat(result)
                  .isNotNull()
                  .isEqualTo(expectedEmail);
        }

        @Test
        void getJobSeekerEmail_WhenGrpcReturnsNotFound_ShouldThrowEmailNotFoundException() {
            String profileId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetJobSeekerEmailResponse> observer = invocation.getArgument(1);
                observer.onError(Status.NOT_FOUND.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getJobSeekerEmail(any(), any());

            Executable action = () -> jobSeekerProfileClient.getJobSeekerEmail(profileId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(EmailNotFoundException.class);
        }

        @Test
        void getJobSeekerEmail_WhenGrpcReturnsUnavailable_ShouldThrowDownstreamDependencyException() {
            String profileId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetJobSeekerEmailResponse> observer = invocation.getArgument(1);
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getJobSeekerEmail(any(), any());

            Executable action = () -> jobSeekerProfileClient.getJobSeekerEmail(profileId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(DownstreamDependencyException.class);
        }
    }

    @Nested
    class GetEmailsByUserIds {

        @Test
        void getEmailsByUserIds_WhenUserIdsListIsNull_ShouldReturnEmptyMapWithoutGrpcCall() {
            Map<String, String> result = jobSeekerProfileClient.getEmailsByUserIds(null);

            assertThat(result).isEmpty();
            verify(serviceImplSpy, never()).getEmailsByUserIds(any(), any());
        }

        @Test
        void getEmailsByUserIds_WhenUserIdsListIsEmpty_ShouldReturnEmptyMapWithoutGrpcCall() {
            Map<String, String> result = jobSeekerProfileClient.getEmailsByUserIds(Collections.emptyList());

            assertThat(result).isEmpty();
            verify(serviceImplSpy, never()).getEmailsByUserIds(any(), any());
        }

        @Test
        void getEmailsByUserIds_WhenValidUserIds_ShouldReturnEmailMap() {
            String userId1 = UUID.randomUUID().toString();
            String userId2 = UUID.randomUUID().toString();
            List<String> userIds = List.of(userId1, userId2);

            Map<String, String> expectedMap = Map.of(
                  userId1, "user1@acme.com",
                  userId2, "user2@acme.com"
            );

            GetEmailsByUserIdsResponse response = GetEmailsByUserIdsResponse.newBuilder()
                  .putAllUserIdToEmail(expectedMap)
                  .build();

            doAnswer(invocation -> {
                StreamObserver<GetEmailsByUserIdsResponse> observer = invocation.getArgument(1);
                observer.onNext(response);
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).getEmailsByUserIds(any(), any());

            Map<String, String> actualMap = jobSeekerProfileClient.getEmailsByUserIds(userIds);

            assertThat(actualMap)
                  .isNotNull()
                  .containsExactlyInAnyOrderEntriesOf(expectedMap);
        }

        @Test
        void getEmailsByUserIds_WhenGrpcReturnsInternalError_ShouldThrowDownstreamDependencyException() {
            List<String> userIds = List.of(UUID.randomUUID().toString());

            doAnswer(invocation -> {
                StreamObserver<GetEmailsByUserIdsResponse> observer = invocation.getArgument(1);
                observer.onError(Status.INTERNAL.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getEmailsByUserIds(any(), any());

            Executable action = () -> jobSeekerProfileClient.getEmailsByUserIds(userIds);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(DownstreamDependencyException.class);
        }
    }
}