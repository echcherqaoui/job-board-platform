package com.echcherqaoui.jobboard.userservice.grpc;

import com.echcherqaoui.jobboard.user.grpc.CompanyProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileRequest;
import com.echcherqaoui.jobboard.user.grpc.GetCompanyProfileResponse;
import com.echcherqaoui.jobboard.user.grpc.GetRecruiterEmailRequest;
import com.echcherqaoui.jobboard.user.grpc.GetRecruiterEmailResponse;
import com.echcherqaoui.jobboard.userservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.echcherqaoui.jobboard.userservice.repository.RecruiterProfileRepository;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class CompanyProfileGrpcServiceIT extends AbstractIntegrationTest {

    @Autowired
    private RecruiterProfileRepository repository;

    @Autowired
    private CompanyProfileGrpcService grpcService;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private CompanyProfileServiceGrpc.CompanyProfileServiceBlockingStub stub;

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

        stub = CompanyProfileServiceGrpc.newBlockingStub(inProcessChannel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (inProcessChannel != null)
            inProcessChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);

        if (inProcessServer != null)
            inProcessServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Nested
    class GetCompanyProfile {

        @Test
        @WithMockUser(roles = "RECRUITER")
        void getCompanyProfile_WhenExists_ShouldReturnCompanySummary() {
            UUID profileId = UUID.randomUUID();
            RecruiterProfile profile = new RecruiterProfile()
                  .setId(profileId)
                  .setEmail("recruiter@acme.com")
                  .setCompanyName("Acme Corp")
                  .setCompanyLogoUrl("https://acme.com/logo.png");
            repository.save(profile);

            GetCompanyProfileRequest request = GetCompanyProfileRequest.newBuilder()
                  .setProfileId(profileId.toString())
                  .build();

            GetCompanyProfileResponse response = stub.getCompanyProfile(request);

            CompanySummary summary = response.getCompany();
            assertThat(summary.getCompanyName()).isEqualTo("Acme Corp");
            assertThat(summary.getLogoUrl()).isEqualTo("https://acme.com/logo.png");
        }

        @Test
        @WithMockUser(roles = "RECRUITER")
        void getCompanyProfile_WhenNullableFields_ShouldReturnEmptyStringsInProto() {
            UUID profileId = UUID.randomUUID();
            RecruiterProfile profile = new RecruiterProfile()
                  .setId(profileId)
                  .setEmail("recruiter@acme.com")
                  .setCompanyName(null)
                  .setCompanyLogoUrl(null);
            repository.save(profile);

            GetCompanyProfileRequest request = GetCompanyProfileRequest.newBuilder()
                  .setProfileId(profileId.toString())
                  .build();

            GetCompanyProfileResponse response = stub.getCompanyProfile(request);

            CompanySummary summary = response.getCompany();
            assertThat(summary.getCompanyName()).isEmpty();
            assertThat(summary.getLogoUrl()).isEmpty();
        }

        @Test
        @WithMockUser(roles = "CANDIDATE")
        void getCompanyProfile_WhenUnauthorizedRole_ShouldThrowAccessDenied() {
            GetCompanyProfileRequest request = GetCompanyProfileRequest.newBuilder()
                  .setProfileId(UUID.randomUUID().toString())
                  .build();

            assertThatThrownBy(() -> stub.getCompanyProfile(request))
                  .isInstanceOf(StatusRuntimeException.class)
                  .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                  .isEqualTo(Status.UNKNOWN.getCode());
        }
    }

    @Nested
    class GetRecruiterEmail {

        @Test
        @WithMockUser(authorities = "SCOPE_INTERNAL")
        void getRecruiterEmail_WhenExists_ShouldReturnEmail() {
            UUID profileId = UUID.randomUUID();
            RecruiterProfile profile = new RecruiterProfile()
                  .setId(profileId)
                  .setEmail("internal.recruiter@acme.com");
            repository.save(profile);

            GetRecruiterEmailRequest request = GetRecruiterEmailRequest.newBuilder()
                  .setProfileId(profileId.toString())
                  .build();

            GetRecruiterEmailResponse response = stub.getRecruiterEmail(request);

            assertThat(response.getEmail()).isEqualTo("internal.recruiter@acme.com");
        }

        @Test
        @WithMockUser(roles = "RECRUITER")
        void getRecruiterEmail_WhenMissingScopeInternal_ShouldThrowAccessDenied() {
            GetRecruiterEmailRequest request = GetRecruiterEmailRequest.newBuilder()
                  .setProfileId(UUID.randomUUID().toString())
                  .build();

            assertThatThrownBy(() -> stub.getRecruiterEmail(request))
                  .isInstanceOf(StatusRuntimeException.class)
                  .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                  .isEqualTo(Status.UNKNOWN.getCode());
        }
    }
}