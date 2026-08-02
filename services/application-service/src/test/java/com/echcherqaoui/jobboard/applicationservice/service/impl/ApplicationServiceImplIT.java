package com.echcherqaoui.jobboard.applicationservice.service.impl;

import com.echcherqaoui.jobboard.applicationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.ApplicationRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.StatusUpdateRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicantApplicationDetailResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationCreationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationSummaryResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.JobApplicationPreview;
import com.echcherqaoui.jobboard.applicationservice.dto.response.StatusUpdateResponse;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.DuplicateApplicationException;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.InvalidStatusTransitionException;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.JobNotOpenException;
import com.echcherqaoui.jobboard.applicationservice.exception.domain.UnauthorizedAccessException;
import com.echcherqaoui.jobboard.applicationservice.grpc.JobServiceClient;
import com.echcherqaoui.jobboard.applicationservice.grpc.ResilienceJobServiceClient;
import com.echcherqaoui.jobboard.applicationservice.grpc.ResilientJobSeekerProfileClient;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.applicationservice.repository.ApplicationRepository;
import com.echcherqaoui.jobboard.applicationservice.service.ApplicationService;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileDetail;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class ApplicationServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private ApplicationRepository applicationRepository;

    @MockitoBean
    private JobServiceClient jobServiceClient;

    @MockitoBean
    private ResilientJobSeekerProfileClient resilientJobSeekerProfileClient;

    @MockitoBean
    private ResilienceJobServiceClient resilienceJobServiceClient;

    @MockitoBean
    private JwtContextHolder jwtContextHolder;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });
    }

    @Nested
    class SubmitApplication {

        @Test
        void shouldSubmitApplicationSuccessfully() {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            when(jwtContextHolder.getUserId()).thenReturn(applicantId);
            when(jwtContextHolder.getFullName()).thenReturn("John Doe");
            when(jwtContextHolder.getEmail()).thenReturn("john.doe@example.com");

            when(jobServiceClient.getJob(jobId.toString())).thenReturn(
                  JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setJobStatus("OPEN")
                        .setRecruiterId(UUID.randomUUID().toString())
                        .build()
            );

            ApplicationRequest request = new ApplicationRequest(jobId, "https://storage.com/cv.pdf", "Cover letter content");
            ApplicationCreationResponse response = applicationService.submitApplication(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);

            Application saved = applicationRepository.findById(response.id()).orElseThrow();
            assertThat(saved.getApplicantId()).isEqualTo(applicantId);
            assertThat(saved.getJobId()).isEqualTo(jobId);
        }

        @Test
        void shouldThrowDuplicateApplicationException_WhenAlreadyApplied() {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            when(jwtContextHolder.getUserId()).thenReturn(applicantId);

            applicationRepository.save(new Application()
                  .setApplicantId(applicantId)
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv.pdf")
                  .setCoverLetter("Letter"));

            ApplicationRequest request = new ApplicationRequest(jobId, "https://storage.com/cv2.pdf", "Letter 2");

            assertThatThrownBy(() -> applicationService.submitApplication(request))
                  .isInstanceOf(DuplicateApplicationException.class);
        }

        @Test
        void shouldThrowJobNotOpenException_WhenJobClosed() {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            when(jwtContextHolder.getUserId()).thenReturn(applicantId);
            when(jobServiceClient.getJob(jobId.toString())).thenReturn(
                  JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setJobStatus("CLOSED")
                        .build()
            );

            ApplicationRequest request = new ApplicationRequest(jobId, "https://storage.com/cv.pdf", "Letter");

            assertThatThrownBy(() -> applicationService.submitApplication(request))
                  .isInstanceOf(JobNotOpenException.class);
        }
    }

    @Nested
    class GetMyApplications {

        @Test
        void shouldReturnPaginatedSummaries() {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            when(jwtContextHolder.getUserId()).thenReturn(applicantId);

            applicationRepository.save(new Application()
                  .setApplicantId(applicantId)
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv.pdf"));

            when(jobServiceClient.getJobsByIds(Set.of(jobId.toString()))).thenReturn(
                  List.of(JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setTitle("Java Developer")
                        .setCompanyName("Tech Corp")
                        .build())
            );

            PaginatedResponse<ApplicationSummaryResponse> result = applicationService.getMyApplications(PageRequest.of(0, 10));

            assertThat(result.content()).hasSize(1);
            assertThat(result.totalElements()).isEqualTo(1);
        }
    }

    @Nested
    class GetApplicationsForJob {

        @Test
        void shouldReturnPaginatedPreviews() {
            UUID jobId = UUID.randomUUID();
            UUID applicantId = UUID.randomUUID();

            applicationRepository.save(new Application()
                  .setApplicantId(applicantId)
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv.pdf"));

            when(resilientJobSeekerProfileClient.fetchProfilesTolerantly(Set.of(applicantId.toString()))).thenReturn(
                  List.of(JobSeekerProfileSummary.newBuilder()
                        .setUserId(applicantId.toString())
                        .setFirstName("John")
                        .setLastName("Doe")
                        .setHeadline("Software Engineer")
                        .setCvUrl("https://storage.com/cv.pdf")
                        .setEmail("john.doe@example.com")
                        .build())
            );

            PaginatedResponse<JobApplicationPreview> result = applicationService.getApplicationsForJob(
                  jobId, null, PageRequest.of(0, 10)
            );

            assertThat(result.content()).hasSize(1);
        }
    }

    @Nested
    class GetApplicationById {

        @Test
        void shouldReturnApplicationSuccessfully() {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            when(jwtContextHolder.getUserId()).thenReturn(applicantId);

            Application saved = applicationRepository.save(new Application()
                  .setApplicantId(applicantId)
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv.pdf"));

            when(resilienceJobServiceClient.fetchJobTolerantly(jobId)).thenReturn(
                  Optional.of(JobSummary.newBuilder().setJobId(jobId.toString()).setTitle("Backend Engineer").build())
            );

            ApplicationResponse response = applicationService.getApplicationById(saved.getId());

            assertThat(response).isNotNull();
        }

        @Test
        void shouldThrowUnauthorizedAccessException_WhenApplicantMismatch() {
            UUID ownerId = UUID.randomUUID();
            UUID callerId = UUID.randomUUID();

            when(jwtContextHolder.getUserId()).thenReturn(callerId);

            Application saved = applicationRepository.save(new Application()
                  .setApplicantId(ownerId)
                  .setJobId(UUID.randomUUID())
                  .setCvUrl("https://storage.com/cv.pdf"));

            assertThatThrownBy(() -> applicationService.getApplicationById(saved.getId()))
                  .isInstanceOf(UnauthorizedAccessException.class);
        }
    }

    @Nested
    class GetApplicationForRecruiter {

        @Test
        void shouldReturnApplicationDetailsSuccessfully() {
            UUID recruiterId = UUID.randomUUID();
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            when(jwtContextHolder.getUserId()).thenReturn(recruiterId);

            Application saved = applicationRepository.save(new Application()
                  .setApplicantId(applicantId)
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv.pdf"));

            when(jobServiceClient.getJob(jobId.toString())).thenReturn(
                  JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setRecruiterId(recruiterId.toString())
                        .build()
            );

            when(resilientJobSeekerProfileClient.fetchProfileTolerantly(applicantId.toString())).thenReturn(
                  Optional.of(JobSeekerProfileDetail.newBuilder()
                        .setUserId(applicantId.toString())
                        .setFirstName("Jane")
                        .setLastName("Doe")
                        .setEmail("jane.doe@example.com")
                        .build())
            );

            ApplicantApplicationDetailResponse response = applicationService.getApplicationForRecruiter(saved.getId());

            assertThat(response).isNotNull();
        }
    }

    @Nested
    class UpdateStatus {

        @Test
        void shouldUpdateStatusSuccessfully() {
            UUID recruiterId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            when(jwtContextHolder.getUserId()).thenReturn(recruiterId);

            Application application = applicationRepository.save(new Application()
                  .setApplicantId(UUID.randomUUID())
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv.pdf")
                  .setStatus(ApplicationStatus.PENDING));

            when(jobServiceClient.getJob(jobId.toString())).thenReturn(
                  JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setRecruiterId(recruiterId.toString())
                        .setJobStatus("OPEN")
                        .build()
            );

            StatusUpdateRequest request = new StatusUpdateRequest(ApplicationStatus.REVIEWED, "Looking good");
            StatusUpdateResponse response = applicationService.updateStatus(application.getId(), request);

            assertThat(response.previousStatus()).isEqualTo(ApplicationStatus.PENDING);
            assertThat(response.newStatus()).isEqualTo(ApplicationStatus.REVIEWED);

            Application updated = applicationRepository.findWithHistoryById(application.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(ApplicationStatus.REVIEWED);
            assertThat(updated.getStatusHistory()).hasSize(1);
        }

        @Test
        void shouldThrowInvalidStatusTransitionException() {
            Application application = applicationRepository.save(new Application()
                  .setApplicantId(UUID.randomUUID())
                  .setJobId(UUID.randomUUID())
                  .setCvUrl("https://storage.com/cv.pdf")
                  .setStatus(ApplicationStatus.REJECTED));

            StatusUpdateRequest request = new StatusUpdateRequest(ApplicationStatus.ACCEPTED, "Reopening application");

            assertThatThrownBy(() -> applicationService.updateStatus(application.getId(), request))
                  .isInstanceOf(InvalidStatusTransitionException.class);
        }
    }
}