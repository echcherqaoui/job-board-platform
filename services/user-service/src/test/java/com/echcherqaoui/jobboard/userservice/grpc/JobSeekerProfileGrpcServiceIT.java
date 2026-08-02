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
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileServiceGrpc;
import com.echcherqaoui.jobboard.userservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.repository.JobSeekerProfileRepository;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class JobSeekerProfileGrpcServiceIT extends AbstractIntegrationTest {

    @Autowired
    private JobSeekerProfileRepository repository;

    @Autowired
    private JobSeekerProfileGrpcService grpcService;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private JobSeekerProfileServiceGrpc.JobSeekerProfileServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws IOException {
        repository.deleteAll();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });

        String serverName = InProcessServerBuilder.generateName();

        inProcessServer = InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(grpcService)
              .build()
              .start();

        inProcessChannel = InProcessChannelBuilder.forName(serverName)
              .directExecutor()
              .build();

        stub = JobSeekerProfileServiceGrpc.newBlockingStub(inProcessChannel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (inProcessChannel != null)
            inProcessChannel.shutdownNow().awaitTermination(5, SECONDS);

        if (inProcessServer != null)
            inProcessServer.shutdownNow().awaitTermination(5, SECONDS);
    }

    @Nested
    class GetJobSeekerProfile {

        @Test
        @WithMockUser(roles = "RECRUITER")
        void getJobSeekerProfile_WhenProfileExists_ShouldReturnDetail() {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("candidate@test.com")
                  .setFirstName("John")
                  .setLastName("Doe")
                  .setHeadline("Software Engineer")
                  .setYearsExperience(3);
            repository.save(profile);

            GetJobSeekerProfileRequest request = GetJobSeekerProfileRequest.newBuilder()
                  .setUserId(userId.toString())
                  .build();

            GetJobSeekerProfileResponse response = stub.getJobSeekerProfile(request);

            JobSeekerProfileDetail detail = response.getProfile();
            assertThat(detail.getUserId()).isEqualTo(userId.toString());
            assertThat(detail.getFirstName()).isEqualTo("John");
            assertThat(detail.getLastName()).isEqualTo("Doe");
            assertThat(detail.getEmail()).isEqualTo("candidate@test.com");
            assertThat(detail.getHeadline()).isEqualTo("Software Engineer");
            assertThat(detail.getYearsExperience()).isEqualTo(3);
        }

        @Test
        @WithMockUser(roles = "ANONYMOUS")
        void getJobSeekerProfile_WhenUnauthorizedRole_ShouldThrowAccessDenied() {
            GetJobSeekerProfileRequest request = GetJobSeekerProfileRequest.newBuilder()
                  .setUserId(UUID.randomUUID().toString())
                  .build();

            assertThatThrownBy(() -> stub.getJobSeekerProfile(request))
                  .isInstanceOf(StatusRuntimeException.class)
                  .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                  .isEqualTo(Status.UNKNOWN.getCode());
        }
    }

    @Nested
    class BatchGetJobSeekerProfiles {

        @Test
        @WithMockUser(roles = "RECRUITER")
        void batchGetJobSeekerProfiles_ShouldReturnSummariesAndIgnoreMalformedUuids() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            JobSeekerProfile p1 = new JobSeekerProfile()
                  .setId(id1)
                  .setEmail("dev1@test.com")
                  .setFirstName("Alice")
                  .setLastName("Smith");

            JobSeekerProfile p2 = new JobSeekerProfile()
                  .setId(id2)
                  .setEmail("dev2@test.com")
                  .setFirstName("Bob")
                  .setLastName("Martin");

            repository.saveAll(List.of(p1, p2));

            BatchGetJobSeekerProfilesRequest request = BatchGetJobSeekerProfilesRequest.newBuilder()
                  .addUserIds(id1.toString())
                  .addUserIds(id2.toString())
                  .addUserIds("invalid-uuid-string")
                  .build();

            BatchGetJobSeekerProfilesResponse response = stub.batchGetJobSeekerProfiles(request);

            assertThat(response.getProfilesList()).hasSize(2);
            assertThat(response.getProfilesList())
                  .extracting("userId")
                  .containsExactlyInAnyOrder(id1.toString(), id2.toString());
        }
    }

    @Nested
    class GetJobSeekerEmail {

        @Test
        @WithMockUser(authorities = "SCOPE_INTERNAL")
        void getJobSeekerEmail_WhenExists_ShouldReturnEmail() {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("internal.fetch@test.com");
            repository.save(profile);

            GetJobSeekerEmailRequest request = GetJobSeekerEmailRequest.newBuilder()
                  .setProfileId(userId.toString())
                  .build();

            GetJobSeekerEmailResponse response = stub.getJobSeekerEmail(request);

            assertThat(response.getEmail()).isEqualTo("internal.fetch@test.com");
        }
    }

    @Nested
    class GetEmailsByUserIds {

        @Test
        @WithMockUser(authorities = "SCOPE_INTERNAL")
        void getEmailsByUserIds_WhenValidIds_ShouldReturnEmailMap() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            JobSeekerProfile p1 = new JobSeekerProfile()
                  .setId(id1)
                  .setEmail("user1@test.com");

            JobSeekerProfile p2 = new JobSeekerProfile()
                  .setId(id2)
                  .setEmail("user2@test.com");

            repository.saveAll(List.of(p1, p2));

            GetEmailsByUserIdsRequest request = GetEmailsByUserIdsRequest.newBuilder()
                  .addUserIds(id1.toString())
                  .addUserIds(id2.toString())
                  .build();

            GetEmailsByUserIdsResponse response = stub.getEmailsByUserIds(request);

            assertThat(response.getUserIdToEmailMap())
                  .hasSize(2)
                  .containsEntry(id1.toString(), "user1@test.com")
                  .containsEntry(id2.toString(), "user2@test.com");
        }

        @Test
        @WithMockUser(authorities = "SCOPE_INTERNAL")
        void getEmailsByUserIds_WhenEmptyList_ShouldReturnDefaultInstance() {
            GetEmailsByUserIdsRequest request = GetEmailsByUserIdsRequest.newBuilder().build();

            GetEmailsByUserIdsResponse response = stub.getEmailsByUserIds(request);

            assertThat(response.getUserIdToEmailMap()).isEmpty();
        }
    }
}