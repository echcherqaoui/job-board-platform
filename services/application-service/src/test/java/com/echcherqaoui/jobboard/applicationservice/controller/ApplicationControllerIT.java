package com.echcherqaoui.jobboard.applicationservice.controller;

import com.echcherqaoui.jobboard.applicationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.ApplicationRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.StatusUpdateRequest;
import com.echcherqaoui.jobboard.applicationservice.grpc.JobServiceClient;
import com.echcherqaoui.jobboard.applicationservice.grpc.ResilienceJobServiceClient;
import com.echcherqaoui.jobboard.applicationservice.grpc.ResilientJobSeekerProfileClient;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.applicationservice.repository.ApplicationRepository;
import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.PENDING;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.REVIEWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApplicationControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private JobServiceClient jobServiceClient;

    @MockitoBean
    private ResilienceJobServiceClient resilienceJobServiceClient;

    @MockitoBean
    private ResilientJobSeekerProfileClient resilientJobSeekerProfileClient;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @MockitoBean
    private SignatureService signatureService;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        applicationRepository.deleteAllInBatch();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> ((Message) invocation.getArgument(1)).toByteArray());

        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("mock-signature");
    }

    private RequestPostProcessor candidateJwt(UUID userId) {
        return jwt()
              .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"))
              .jwt(builder -> builder
                    .claim("sub", userId.toString())
                    .claim("full_name", "John Doe")
                    .claim("email", "john.doe@example.com"));
    }

    private RequestPostProcessor recruiterJwt(UUID recruiterId) {
        return jwt()
              .authorities(new SimpleGrantedAuthority("ROLE_RECRUITER"))
              .jwt(builder -> builder.claim("sub", recruiterId.toString()));
    }

    private Application seedApplication(UUID applicantId, UUID jobId, ApplicationStatus status) {
        Application application = new Application()
              .setApplicantId(applicantId)
              .setJobId(jobId)
              .setCvUrl("https://storage.com/cv.pdf")
              .setCoverLetter("Sample cover letter")
              .setStatus(status);
        return applicationRepository.save(application);
    }

    @Nested
    class SubmitApplication {

        @Test
        void submitApplication_WhenValidCandidateRequest_ShouldCreateApplicationAndOutboxEvent() throws Exception {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            when(jobServiceClient.getJob(jobId.toString())).thenReturn(
                  JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setJobStatus("OPEN")
                        .setRecruiterId(UUID.randomUUID().toString())
                        .setTitle("Java Software Engineer")
                        .build()
            );

            ApplicationRequest request = new ApplicationRequest(jobId, "https://storage.com/cv.pdf", "Cover letter content");

            mockMvc.perform(post("/api/v1/applications")
                        .with(candidateJwt(applicantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  )
                  .andExpect(status().isCreated())
                  .andExpect(jsonPath("$.id").exists())
                  .andExpect(jsonPath("$.status").value("PENDING"));

            List<Application> apps = applicationRepository.findAll();
            assertThat(apps).hasSize(1);
            assertThat(apps.get(0).getApplicantId()).isEqualTo(applicantId);

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getAggregateType()).isEqualTo("application");
            assertThat(events.get(0).getEventType()).isEqualTo("application-submitted");
        }

        @Test
        void submitApplication_WhenRecruiter_ShouldReturn403Forbidden() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            ApplicationRequest request = new ApplicationRequest(UUID.randomUUID(), "https://storage.com/cv.pdf", "Letter");

            mockMvc.perform(post("/api/v1/applications")
                        .with(recruiterJwt(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  )
                  .andExpect(status().isForbidden());
        }

        @Test
        void submitApplication_WhenValidationFails_ShouldReturn400() throws Exception {
            UUID applicantId = UUID.randomUUID();
            ApplicationRequest invalidRequest = new ApplicationRequest(null, "", "");

            mockMvc.perform(post("/api/v1/applications")
                        .with(candidateJwt(applicantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                  )
                  .andExpect(status().isBadRequest());

            assertThat(applicationRepository.count()).isZero();
        }

        @Test
        void submitApplication_WhenAlreadyApplied_ShouldReturn409Conflict() throws Exception {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            seedApplication(applicantId, jobId, PENDING);

            ApplicationRequest duplicateRequest = new ApplicationRequest(jobId, "https://storage.com/cv2.pdf", "Letter");

            mockMvc.perform(post("/api/v1/applications")
                        .with(candidateJwt(applicantId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest))
                  )
                  .andExpect(status().isConflict());
        }
    }

    @Nested
    class GetMyApplications {

        @Test
        void getMyApplications_WhenCandidate_ShouldReturnPaginatedSummaries() throws Exception {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            seedApplication(applicantId, jobId, PENDING);

            when(jobServiceClient.getJobsByIds(Set.of(jobId.toString()))).thenReturn(
                  List.of(JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setTitle("Backend Developer")
                        .setCompanyName("AgileCorp")
                        .build())
            );

            mockMvc.perform(get("/api/v1/applications/my")
                        .with(candidateJwt(applicantId))
                        .param("page", "0")
                        .param("size", "20")
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.content", hasSize(1)))
                  .andExpect(jsonPath("$.content[0].jobTitle").value("Backend Developer"))
                  .andExpect(jsonPath("$.content[0].companyName").value("AgileCorp"));
        }

        @Test
        void getMyApplications_WhenRecruiter_ShouldReturn403Forbidden() throws Exception {
            UUID recruiterId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/applications/my")
                        .with(recruiterJwt(recruiterId))
                  )
                  .andExpect(status().isForbidden());
        }
    }

    @Nested
    class GetApplicationsForJob {

        @Test
        void getApplicationsForJob_WhenRecruiter_ShouldReturnPreviews() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            UUID applicantId = UUID.randomUUID();

            seedApplication(applicantId, jobId, PENDING);

            when(resilientJobSeekerProfileClient.fetchProfilesTolerantly(Set.of(applicantId.toString()))).thenReturn(
                  List.of(JobSeekerProfileSummary.newBuilder()
                        .setUserId(applicantId.toString())
                        .setFirstName("Jane")
                        .setLastName("Doe")
                        .setHeadline("Java Developer")
                        .setCvUrl("https://storage.com/cv.pdf")
                        .setEmail("jane.doe@example.com")
                        .build())
            );

            mockMvc.perform(get("/api/v1/applications/job/{jobId}", jobId)
                        .with(recruiterJwt(recruiterId))
                        .param("page", "0")
                        .param("size", "20")
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.content", hasSize(1)))
                  .andExpect(jsonPath("$.content[0].applicantName").value("Jane Doe"));
        }

        @Test
        void getApplicationsForJob_WhenCandidate_ShouldReturn403Forbidden() throws Exception {
            UUID candidateId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/applications/job/{jobId}", jobId)
                        .with(candidateJwt(candidateId))
                  )
                  .andExpect(status().isForbidden());
        }
    }

    @Nested
    class GetApplicationById {

        @Test
        void getApplicationById_WhenCandidateOwner_ShouldReturnApplicationDetails() throws Exception {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            Application application = seedApplication(applicantId, jobId, PENDING);

            when(resilienceJobServiceClient.fetchJobTolerantly(jobId)).thenReturn(
                  Optional.of(JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setTitle("Full Stack Engineer")
                        .build())
            );

            mockMvc.perform(get("/api/v1/applications/{id}", application.getId())
                        .with(candidateJwt(applicantId))
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.id").value(application.getId().toString()))
                  .andExpect(jsonPath("$.jobTitle").value("Full Stack Engineer"));
        }

        @Test
        void getApplicationById_WhenNotOwnerCandidate_ShouldReturn403Forbidden() throws Exception {
            UUID ownerId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            Application application = seedApplication(ownerId, jobId, PENDING);

            mockMvc.perform(get("/api/v1/applications/{id}", application.getId())
                        .with(candidateJwt(otherUserId))
                  )
                  .andExpect(status().isForbidden());
        }

        @Test
        void getApplicationById_WhenNotFound_ShouldReturn404() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/applications/{id}", nonExistentId)
                        .with(candidateJwt(userId))
                  )
                  .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateStatus {

        @Test
        void updateStatus_WhenRecruiterOwner_ShouldUpdateStatusAndCreateOutboxEvent() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();
            UUID applicantId = UUID.randomUUID();

            Application application = seedApplication(applicantId, jobId, PENDING);

            when(jobServiceClient.getJob(jobId.toString())).thenReturn(
                  JobSummary.newBuilder()
                        .setJobId(jobId.toString())
                        .setRecruiterId(recruiterId.toString())
                        .setJobStatus("OPEN")
                        .build()
            );

            StatusUpdateRequest request = new StatusUpdateRequest(REVIEWED, "Moving to initial interview");

            mockMvc.perform(patch("/api/v1/applications/{id}/status", application.getId())
                        .with(recruiterJwt(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.previousStatus").value("PENDING"))
                  .andExpect(jsonPath("$.newStatus").value("REVIEWED"));

            Application updated = applicationRepository.findById(application.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(REVIEWED);

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getEventType()).isEqualTo("application-status-changed");
        }

        @Test
        void updateStatus_WhenCandidate_ShouldReturn403Forbidden() throws Exception {
            UUID candidateId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            Application application = seedApplication(UUID.randomUUID(), jobId, PENDING);
            StatusUpdateRequest request = new StatusUpdateRequest(REVIEWED, "Comment");

            mockMvc.perform(patch("/api/v1/applications/{id}/status", application.getId())
                        .with(candidateJwt(candidateId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  )
                  .andExpect(status().isForbidden());
        }

        @Test
        void updateStatus_WhenValidationFails_ShouldReturn400() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            Application application = seedApplication(UUID.randomUUID(), jobId, PENDING);
            StatusUpdateRequest invalidRequest = new StatusUpdateRequest(null, "");

            mockMvc.perform(patch("/api/v1/applications/{id}/status", application.getId())
                  .with(recruiterJwt(recruiterId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest))
            ).andExpect(status().isBadRequest());
        }
    }
}