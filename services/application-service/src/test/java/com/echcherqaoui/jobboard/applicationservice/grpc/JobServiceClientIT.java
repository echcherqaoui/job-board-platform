package com.echcherqaoui.jobboard.applicationservice.grpc;

import com.echcherqaoui.jobboard.applicationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.JobNotFoundException;
import com.echcherqaoui.jobboard.exception.grpc.DownstreamDependencyException;
import com.echcherqaoui.jobboard.job.grpc.BatchGetJobSummariesResponse;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryResponse;
import com.echcherqaoui.jobboard.job.grpc.JobServiceGrpc;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
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
class JobServiceClientIT extends AbstractIntegrationTest {

    @Autowired
    private JobServiceClient jobServiceClient;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private JobServiceGrpc.JobServiceImplBase serviceImplSpy;

    @BeforeEach
    void setUp() throws IOException {
        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });

        serviceImplSpy = spy(new JobServiceGrpc.JobServiceImplBase() {});

        String serverName = InProcessServerBuilder.generateName();

        inProcessServer = InProcessServerBuilder.forName(serverName)
              .directExecutor()
              .addService(serviceImplSpy)
              .build()
              .start();

        inProcessChannel = InProcessChannelBuilder.forName(serverName)
              .directExecutor()
              .build();

        JobServiceGrpc.JobServiceBlockingStub stub =
              JobServiceGrpc.newBlockingStub(inProcessChannel);

        ReflectionTestUtils.setField(jobServiceClient, "jobStub", stub);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (inProcessChannel != null) {
            inProcessChannel.shutdownNow().awaitTermination(5, SECONDS);
        }
        if (inProcessServer != null) {
            inProcessServer.shutdownNow().awaitTermination(5, SECONDS);
        }
    }

    @Nested
    class GetJob {

        @Test
        void getJob_WhenJobExists_ShouldReturnJobSummary() {
            String jobId = UUID.randomUUID().toString();
            JobSummary summary = JobSummary.newBuilder()
                  .setJobId(jobId)
                  .setTitle("Senior Java Engineer")
                  .setCompanyName("Tech Corp")
                  .build();

            GetJobSummaryResponse response = GetJobSummaryResponse.newBuilder()
                  .setJob(summary)
                  .build();

            doAnswer(invocation -> {
                StreamObserver<GetJobSummaryResponse> observer = invocation.getArgument(1);
                observer.onNext(response);
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).getJobSummary(any(), any());

            JobSummary result = jobServiceClient.getJob(jobId);

            assertThat(result).isNotNull();
            assertThat(result.getJobId()).isEqualTo(jobId);
            assertThat(result.getTitle()).isEqualTo("Senior Java Engineer");
            assertThat(result.getCompanyName()).isEqualTo("Tech Corp");
        }

        @Test
        void getJob_WhenResponseHasNoJobEntity_ShouldThrowJobNotFoundException() {
            String jobId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetJobSummaryResponse> observer = invocation.getArgument(1);
                observer.onNext(GetJobSummaryResponse.newBuilder().build());
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).getJobSummary(any(), any());

            Executable action = () -> jobServiceClient.getJob(jobId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(JobNotFoundException.class);
        }

        @Test
        void getJob_WhenGrpcReturnsNotFound_ShouldThrowJobNotFoundException() {
            String jobId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetJobSummaryResponse> observer = invocation.getArgument(1);
                observer.onError(Status.NOT_FOUND.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getJobSummary(any(), any());

            Executable action = () -> jobServiceClient.getJob(jobId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(JobNotFoundException.class);
        }

        @Test
        void getJob_WhenGrpcReturnsUnavailable_ShouldThrowDownstreamDependencyException() {
            String jobId = UUID.randomUUID().toString();

            doAnswer(invocation -> {
                StreamObserver<GetJobSummaryResponse> observer = invocation.getArgument(1);
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
                return null;
            }).when(serviceImplSpy).getJobSummary(any(), any());

            Executable action = () -> jobServiceClient.getJob(jobId);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(DownstreamDependencyException.class);
        }
    }

    @Nested
    class GetJobsByIds {

        @Test
        void getJobsByIds_WhenValidJobIds_ShouldReturnJobSummaries() {
            String id1 = UUID.randomUUID().toString();
            String id2 = UUID.randomUUID().toString();

            JobSummary j1 = JobSummary.newBuilder().setJobId(id1).setTitle("Dev 1").build();
            JobSummary j2 = JobSummary.newBuilder().setJobId(id2).setTitle("Dev 2").build();

            BatchGetJobSummariesResponse response = BatchGetJobSummariesResponse.newBuilder()
                  .addAllJobs(List.of(j1, j2))
                  .build();

            doAnswer(invocation -> {
                StreamObserver<BatchGetJobSummariesResponse> observer = invocation.getArgument(1);
                observer.onNext(response);
                observer.onCompleted();
                return null;
            }).when(serviceImplSpy).batchGetJobSummaries(any(), any());

            List<JobSummary> result = jobServiceClient.getJobsByIds(Set.of(id1, id2));

            assertThat(result).hasSize(2);
            assertThat(result).extracting("jobId").containsExactlyInAnyOrder(id1, id2);
        }

        @Test
        void getJobsByIds_WhenGrpcReturnsInternalError_ShouldThrowDownstreamDependencyException() {
            Set<String> jobIds = Set.of(UUID.randomUUID().toString());

            doAnswer(invocation -> {
                StreamObserver<BatchGetJobSummariesResponse> observer = invocation.getArgument(1);
                observer.onError(Status.INTERNAL.asRuntimeException());
                return null;
            }).when(serviceImplSpy).batchGetJobSummaries(any(), any());

            Executable action = () -> jobServiceClient.getJobsByIds(jobIds);

            assertThatThrownBy(action::execute)
                  .isInstanceOf(DownstreamDependencyException.class);
        }
    }
}