package com.echcherqaoui.jobboard.jobservice.grpc.server;

import com.echcherqaoui.jobboard.job.grpc.BatchGetJobSummariesRequest;
import com.echcherqaoui.jobboard.job.grpc.BatchGetJobSummariesResponse;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryRequest;
import com.echcherqaoui.jobboard.job.grpc.GetJobSummaryResponse;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.grpc.client.CompanyProfileClient;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.repository.CompanyProfileRepository;
import com.echcherqaoui.jobboard.jobservice.repository.JobRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel.MID;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.OPEN;
import static com.echcherqaoui.jobboard.jobservice.model.JobType.FULL_TIME;
import static com.echcherqaoui.jobboard.jobservice.model.WorkModality.REMOTE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class JobGrpcServiceIT extends AbstractIntegrationTest {

    @Autowired
    private JobGrpcService jobGrpcService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @MockitoBean
    private CompanyProfileClient companyProfileClient;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @MockitoBean
    private SignatureService signatureService;

    private final UUID recruiterId1 = UUID.randomUUID();
    private final UUID recruiterId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jobRepository.deleteAll();
        companyProfileRepository.deleteAll();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });

        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("mock-signature");

        // Seed Company Profiles
        companyProfileRepository.save(new CompanyProfile()
              .setRecruiterId(recruiterId1)
              .setCompanyName("AgileCorp")
              .setCompanyLogo("agile_logo.png")
              .setLastEventId("seed-1")
              .setUpdatedAt(OffsetDateTime.now(UTC)));

        companyProfileRepository.save(new CompanyProfile()
              .setRecruiterId(recruiterId2)
              .setCompanyName("TechGlobal")
              .setCompanyLogo("tech_logo.png")
              .setLastEventId("seed-2")
              .setUpdatedAt(OffsetDateTime.now(UTC)));
    }

    private Jwt buildJwt(UUID subjectId) {
        return Jwt.withTokenValue("mock-token")
              .header("alg", "none")
              .subject(subjectId.toString())
              .claim("scope", "none")
              .issuedAt(Instant.now())
              .expiresAt(Instant.now().plusSeconds(3600))
              .build();
    }

    private void authenticateAs(UUID userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Stream.of(roles)
              .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
              .toList();

        JwtAuthenticationToken token = new JwtAuthenticationToken(buildJwt(userId), authorities, userId.toString());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @SuppressWarnings("unchecked")
    private <T> StreamObserver<T> mockObserver() {
        return mock(StreamObserver.class);
    }

    private Job seedJob(UUID recruiterId, String title) {
        Job job = new Job()
              .setRecruiterId(recruiterId)
              .setTitle(title)
              .setDescription("Description for " + title)
              .setLocation("Casablanca")
              .setWorkModality(REMOTE)
              .setJobType(FULL_TIME)
              .setExperienceLevel(MID)
              .setStatus(OPEN)
              .setCreatedAt(OffsetDateTime.now(UTC))
              .setUpdatedAt(OffsetDateTime.now(UTC));

        return jobRepository.save(job);
    }

    @Nested
    class GetJobSummary {

        @Test
        void happyPath_shouldReturnSummary() {
            authenticateAs(recruiterId1, "CANDIDATE");
            Job seededJob = seedJob(recruiterId1, "Cloud Architect");

            GetJobSummaryRequest request = GetJobSummaryRequest.newBuilder()
                  .setJobId(seededJob.getId().toString())
                  .build();

            StreamObserver<GetJobSummaryResponse> observer = mockObserver();
            jobGrpcService.getJobSummary(request, observer);

            ArgumentCaptor<GetJobSummaryResponse> responseCaptor = ArgumentCaptor.forClass(GetJobSummaryResponse.class);
            verify(observer).onNext(responseCaptor.capture());
            verify(observer).onCompleted();

            GetJobSummaryResponse response = responseCaptor.getValue();
            assertThat(response.getJob().getJobId()).isEqualTo(seededJob.getId().toString());
            assertThat(response.getJob().getTitle()).isEqualTo("Cloud Architect");
            assertThat(response.getJob().getCompanyName()).isEqualTo("AgileCorp");
        }

        @Test
        void throwsAccessDenied_whenUnauthorized() {
            authenticateAs(UUID.randomUUID(), "USER");

            Job seededJob = seedJob(recruiterId1, "DevOps Engineer");
            String jobId = seededJob.getId().toString();

            GetJobSummaryRequest request = GetJobSummaryRequest.newBuilder()
                  .setJobId(jobId)
                  .build();

            StreamObserver<GetJobSummaryResponse> observer = mockObserver();

            assertThatThrownBy(() -> jobGrpcService.getJobSummary(request, observer))
                  .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void throwsAuthenticationCredentialsNotFoundException_whenUnauthenticated() {
            Job seededJob = seedJob(recruiterId1, "DevOps Engineer");
            String jobId = seededJob.getId().toString();

            GetJobSummaryRequest request = GetJobSummaryRequest.newBuilder()
                  .setJobId(jobId)
                  .build();
            StreamObserver<GetJobSummaryResponse> observer = mockObserver();

            assertThatThrownBy(() -> jobGrpcService.getJobSummary(request, observer))
                  .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        }
    }

    @Nested
    class BatchGetJobSummaries {

        @Test
        void happyPath_shouldReturnAllMatchingSummaries() {
            authenticateAs(UUID.randomUUID(), "CANDIDATE");

            Job job1 = seedJob(recruiterId1, "Backend Engineer");
            Job job2 = seedJob(recruiterId2, "Frontend Developer");
            UUID nonExistentJobId = UUID.randomUUID();

            BatchGetJobSummariesRequest request = BatchGetJobSummariesRequest.newBuilder()
                  .addAllJobIds(List.of(
                        job1.getId().toString(),
                        job2.getId().toString(),
                        nonExistentJobId.toString(),
                        "invalid-uuid-string"
                  ))
                  .build();

            StreamObserver<BatchGetJobSummariesResponse> observer = mockObserver();
            jobGrpcService.batchGetJobSummaries(request, observer);

            ArgumentCaptor<BatchGetJobSummariesResponse> captor = ArgumentCaptor.forClass(BatchGetJobSummariesResponse.class);
            verify(observer).onNext(captor.capture());
            verify(observer).onCompleted();

            BatchGetJobSummariesResponse response = captor.getValue();
            List<JobSummary> summaries = response.getJobsList();

            assertThat(summaries).hasSize(2);
            assertThat(summaries)
                  .extracting(JobSummary::getJobId)
                  .containsExactlyInAnyOrder(job1.getId().toString(), job2.getId().toString());

            JobSummary summary1 = summaries.stream()
                  .filter(s -> s.getJobId().equals(job1.getId().toString()))
                  .findFirst()
                  .orElseThrow();
            assertThat(summary1.getCompanyName()).isEqualTo("AgileCorp");
            assertThat(summary1.getTitle()).isEqualTo("Backend Engineer");

            JobSummary summary2 = summaries.stream()
                  .filter(s -> s.getJobId().equals(job2.getId().toString()))
                  .findFirst()
                  .orElseThrow();
            assertThat(summary2.getCompanyName()).isEqualTo("TechGlobal");
            assertThat(summary2.getTitle()).isEqualTo("Frontend Developer");
        }

        @Test
        void throwsAccessDenied_whenUnauthorized() {
            authenticateAs(UUID.randomUUID(), "USER");

            BatchGetJobSummariesRequest request = BatchGetJobSummariesRequest.newBuilder()
                  .addJobIds(UUID.randomUUID().toString())
                  .build();

            StreamObserver<BatchGetJobSummariesResponse> observer = mockObserver();

            assertThatThrownBy(() -> jobGrpcService.batchGetJobSummaries(request, observer))
                  .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        void throwsAuthenticationCredentialsNotFoundException_whenUnauthenticated() {
            BatchGetJobSummariesRequest request = BatchGetJobSummariesRequest.newBuilder()
                  .addJobIds(UUID.randomUUID().toString())
                  .build();

            StreamObserver<BatchGetJobSummariesResponse> observer = mockObserver();

            assertThatThrownBy(() -> jobGrpcService.batchGetJobSummaries(request, observer))
                  .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        }
    }
}