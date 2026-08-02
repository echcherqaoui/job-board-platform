package com.echcherqaoui.jobboard.notificationservice.grpc;

import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.notificationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.notificationservice.exception.domain.EmailNotFoundException;
import com.echcherqaoui.jobboard.user.grpc.CompanyProfileServiceGrpc;
import com.echcherqaoui.jobboard.user.grpc.GetRecruiterEmailResponse;
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
class CompanyProfileClientIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyProfileClient companyProfileClient;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private CompanyProfileServiceGrpc.CompanyProfileServiceImplBase serviceImplSpy;

    @BeforeEach
    void setUp() throws IOException {
        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });

        serviceImplSpy = spy(new CompanyProfileServiceGrpc.CompanyProfileServiceImplBase() {});

        String serverName = InProcessServerBuilder.generateName();

        inProcessServer = InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(serviceImplSpy)
              .build()
              .start();

        inProcessChannel = InProcessChannelBuilder.forName(serverName)
              .directExecutor()
              .build();

        CompanyProfileServiceGrpc.CompanyProfileServiceBlockingStub stub =
              CompanyProfileServiceGrpc.newBlockingStub(inProcessChannel);

        ReflectionTestUtils.setField(companyProfileClient, "companyStub", stub);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (inProcessChannel != null)
            inProcessChannel.shutdownNow().awaitTermination(5, SECONDS);

        if (inProcessServer != null)
            inProcessServer.shutdownNow().awaitTermination(5, SECONDS);
    }

    @Nested
    class GetRecruiterEmail {

        @Test
        void getRecruiterEmail_WhenRecruiterExists_ShouldReturnEmail() {
            String recruiterId = UUID.randomUUID().toString();
            String expectedEmail = "recruiter@acme.com";

            GetRecruiterEmailResponse response = GetRecruiterEmailResponse.newBuilder()
                  .setEmail(expectedEmail)
                  .build();

            doAnswer(invocation -> {
                StreamObserver<GetRecruiterEmailResponse> observer = invocation.getArgument(1);
                observer.onNext(response);
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).getRecruiterEmail(any(), any());

            String result = companyProfileClient.getRecruiterEmail(recruiterId);

            assertThat(result)
                  .isNotNull()
                  .isEqualTo(expectedEmail);
        }

        @Test
        void getRecruiterEmail_WhenGrpcReturnsNotFound_ShouldThrowEmailNotFoundException() {
            String recruiterId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetRecruiterEmailResponse> observer = invocation.getArgument(1);
                observer.onError(Status.NOT_FOUND.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getRecruiterEmail(any(), any());

            Executable action = () -> companyProfileClient.getRecruiterEmail(recruiterId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(EmailNotFoundException.class);
        }

        @Test
        void getRecruiterEmail_WhenGrpcReturnsUnavailable_ShouldThrowDownstreamDependencyException() {
            String recruiterId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetRecruiterEmailResponse> observer = invocation.getArgument(1);
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getRecruiterEmail(any(), any());

            Executable action = () -> companyProfileClient.getRecruiterEmail(recruiterId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(DownstreamDependencyException.class);
        }
    }
}