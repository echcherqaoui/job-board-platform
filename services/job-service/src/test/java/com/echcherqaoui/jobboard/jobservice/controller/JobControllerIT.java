package com.echcherqaoui.jobboard.jobservice.controller;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobStatusUpdateRequest;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import com.echcherqaoui.jobboard.jobservice.repository.CompanyProfileRepository;
import com.echcherqaoui.jobboard.jobservice.repository.JobRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel.MID;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.CLOSED;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.DRAFT;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.OPEN;
import static com.echcherqaoui.jobboard.jobservice.model.JobType.FULL_TIME;
import static com.echcherqaoui.jobboard.jobservice.model.WorkModality.REMOTE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @MockitoBean
    private SignatureService signatureService;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyProfileRepository.deleteAllInBatch();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> ((Message) invocation.getArgument(1)).toByteArray());

        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("mock-signature");
    }

    /**
     * Helper to simulate a JWT token with ROLE_RECRUITER and subject claim.
     */
    private RequestPostProcessor recruiterJwt(UUID recruiterId) {
        return jwt()
              .authorities(new SimpleGrantedAuthority("ROLE_RECRUITER"))
              .jwt(builder -> builder.claim("sub", recruiterId.toString()));
    }

    private RequestPostProcessor userJwt(UUID userId) {
        return jwt().jwt(builder -> builder.claim("sub", userId.toString()));
    }

    private JobRequest buildValidJobRequest(String title) {
        return new JobRequest(
              title,
              "Description for " + title,
              "5 years of experience in backend development",
              "Architect microservices and maintain APIs",
              "Casablanca, Morocco",
              REMOTE,
              FULL_TIME,
              MID,
              new BigDecimal("45000.00"),
              new BigDecimal("75000.00"),
              "MAD",
              OffsetDateTime.now(UTC).plusDays(30),
              List.of("Java", "Spring Boot", "PostgreSQL")
        );
    }

    private void seedCompanyProfile(UUID recruiterId) {
        CompanyProfile profile = new CompanyProfile()
              .setRecruiterId(recruiterId)
              .setCompanyName("AgileCorp")
              .setCompanyLogo("https://example.com/logo.png")
              .setLastEventId("evt-seed-1")
              .setUpdatedAt(OffsetDateTime.now(UTC));

        companyProfileRepository.save(profile);
    }

    private Job seedJob(String title, JobStatus status, UUID recruiterId) {
        Job job = new Job();
        job.setRecruiterId(recruiterId);
        job.setTitle(title);
        job.setDescription("Description for " + title);
        job.setLocation("Casablanca");
        job.setWorkModality(REMOTE);
        job.setJobType(FULL_TIME);
        job.setExperienceLevel(MID);
        job.setStatus(status);
        job.setCreatedAt(OffsetDateTime.now(UTC));
        job.setUpdatedAt(OffsetDateTime.now(UTC));
        return jobRepository.save(job);
    }

    @Nested
    class PostJob {

        @Test
        void postJob_WhenValidRequest_ShouldCreateJobAndOutboxEvent() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            seedCompanyProfile(recruiterId);

            JobRequest request = buildValidJobRequest("Senior Java Developer");

            mockMvc.perform(post("/api/v1/jobs")
                        .with(recruiterJwt(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  ).andExpect(status().isCreated())
                  .andExpect(jsonPath("$.id").exists())
                  .andExpect(jsonPath("$.title").value("Senior Java Developer"))
                  .andExpect(jsonPath("$.companyName").value("AgileCorp"))
                  .andExpect(jsonPath("$.status").value("DRAFT"));

            List<OutboxEvent> events = outboxEventRepository.findAll();
            assertThat(events).hasSize(1);

            OutboxEvent event = events.get(0);
            assertThat(event.getAggregateType()).isEqualTo("job");
            assertThat(event.getEventType()).isEqualTo("job-upserted");
            assertThat(event.getPayload()).isNotNull();
        }

        @Test
        void postJob_WhenValidationFails_ShouldReturn400() throws Exception {
            UUID recruiterId = UUID.randomUUID();

            JobRequest invalidRequest = new JobRequest(
                  "",
                  "Desc",
                  "Req",
                  "Resp",
                  "Casablanca",
                  REMOTE,
                  FULL_TIME,
                  MID,
                  new BigDecimal("45000.00"),
                  new BigDecimal("75000.00"),
                  "MAD",
                  null,
                  List.of()
            );

            mockMvc.perform(post("/api/v1/jobs")
                        .with(recruiterJwt(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                  )
                  .andExpect(status().isBadRequest());

            assertThat(jobRepository.count()).isZero();
        }

        @Test
        void postJob_WhenNoPriorCompanyProfile_ShouldCreateJob() throws Exception {
            UUID recruiterWithoutCompany = UUID.randomUUID();
            JobRequest request = buildValidJobRequest("Senior Java Developer");

            mockMvc.perform(post("/api/v1/jobs")
                        .with(recruiterJwt(recruiterWithoutCompany))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  )
                  .andExpect(status().isCreated())
                  .andExpect(jsonPath("$.id").exists());

            assertThat(jobRepository.count()).isEqualTo(1);
        }

        @Test
        void postJob_WhenMinSalaryGreaterThanMaxSalary_ShouldReturn400() throws Exception {
            UUID recruiterId = UUID.randomUUID();

            JobRequest invalidSalaryRequest = new JobRequest(
                  "Java Engineer",
                  "Desc",
                  "Req",
                  "Resp",
                  "Casablanca",
                  REMOTE,
                  FULL_TIME,
                  MID,
                  new BigDecimal("80000.00"), // min > max
                  new BigDecimal("40000.00"),
                  "MAD",
                  OffsetDateTime.now(UTC).plusDays(10),
                  List.of("Java")
            );

            mockMvc.perform(post("/api/v1/jobs")
                  .with(recruiterJwt(recruiterId))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidSalaryRequest))
            ).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class SearchJobs {

        @Test
        void searchJobs_WhenMatchesCriteria_ShouldReturnPaginatedResults() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID recruiterId = UUID.randomUUID();
            seedJob("Java Engineer", OPEN, recruiterId);
            seedJob("Python Engineer", OPEN, recruiterId);

            mockMvc.perform(get("/api/v1/jobs").with(userJwt(userId))
                        .param("keyword", "Java")
                        .param("page", "0")
                        .param("size", "10")
                  ).andExpect(status().isOk())
                  .andExpect(jsonPath("$.content", hasSize(1)))
                  .andExpect(jsonPath("$.content[0].title").value("Java Engineer"));
        }
    }

    @Nested
    class GetMyJobs {

        @Test
        void getMyJobs_WhenJobsExistForUser_ShouldReturnOnlyRecruiterJobs() throws Exception {
            UUID myRecruiterId = UUID.randomUUID();
            UUID otherRecruiterId = UUID.randomUUID();

            seedJob("My Job 1", OPEN, myRecruiterId);
            seedJob("My Job 2", DRAFT, myRecruiterId);
            seedJob("Other Recruiter Job", OPEN, otherRecruiterId);

            mockMvc.perform(get("/api/v1/jobs/my")
                        .with(recruiterJwt(myRecruiterId))
                  ).andExpect(status().isOk())
                  .andExpect(jsonPath("$.content", hasSize(2)));
        }
    }

    @Nested
    class GetJobById {

        @Test
        void getJobById_WhenExists_ShouldReturnJob() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID recruiterId = UUID.randomUUID();
            Job job = seedJob("Architect", OPEN, recruiterId);

            mockMvc.perform(get("/api/v1/jobs/{id}", job.getId()).with(userJwt(userId)))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.id").value(job.getId().toString()))
                  .andExpect(jsonPath("$.title").value("Architect"));
        }

        @Test
        void getJobById_WhenNotFound_ShouldReturn404() throws Exception {
            UUID userId = UUID.randomUUID();
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/jobs/{id}", nonExistentId).with(userJwt(userId)))

                  .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateJob {

        @Test
        void updateJob_WhenOwner_ShouldUpdateDatabaseEntity() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            seedCompanyProfile(recruiterId);
            Job existingJob = seedJob("Old Title", DRAFT, recruiterId);

            JobRequest updateRequest = buildValidJobRequest("Updated Lead Engineer");

            mockMvc.perform(put("/api/v1/jobs/{id}", existingJob.getId())
                        .with(recruiterJwt(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.title").value("Updated Lead Engineer"));

            Job updated = jobRepository.findById(existingJob.getId()).orElseThrow();
            assertThat(updated.getTitle()).isEqualTo("Updated Lead Engineer");
        }

        @Test
        void updateJob_WhenNotOwner_ShouldReturn403Forbidden() throws Exception {
            UUID ownerId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();

            Job job = seedJob("Owner Job", DRAFT, ownerId);
            JobRequest updateRequest = buildValidJobRequest("Hacked Title");

            mockMvc.perform(put("/api/v1/jobs/{id}", job.getId())
                        .with(recruiterJwt(otherUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                  )
                  .andExpect(status().isForbidden());
        }
    }

    @Nested
    class UpdateJobStatus {

        @Test
        void updateJobStatus_WhenValidTransition_ShouldUpdateStatus() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            seedCompanyProfile(recruiterId);
            Job job = seedJob("Draft Job", DRAFT, recruiterId);

            JobStatusUpdateRequest request = new JobStatusUpdateRequest(OPEN);

            mockMvc.perform(patch("/api/v1/jobs/{id}/status", job.getId())
                        .with(recruiterJwt(recruiterId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.status").value("OPEN"));

            Job updated = jobRepository.findById(job.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(OPEN);
        }

        @Test
        void updateJobStatus_WhenNotOwner_ShouldReturn403Forbidden() throws Exception {
            UUID ownerId = UUID.randomUUID();
            UUID strangerId = UUID.randomUUID();

            Job job = seedJob("Protected Job", DRAFT, ownerId);
            JobStatusUpdateRequest request = new JobStatusUpdateRequest(CLOSED);

            mockMvc.perform(patch("/api/v1/jobs/{id}/status", job.getId())
                        .with(recruiterJwt(strangerId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  )
                  .andExpect(status().isForbidden());
        }
    }

    @Nested
    class DeleteJob {

        @Test
        void deleteJob_WhenOwner_ShouldRemoveFromDatabaseAndWriteOutboxEvent() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            Job job = seedJob("Job to Delete", OPEN, recruiterId);

            mockMvc.perform(delete("/api/v1/jobs/{id}", job.getId())
                        .with(recruiterJwt(recruiterId))
                  )
                  .andExpect(status().isNoContent());

            assertThat(jobRepository.findById(job.getId())).isEmpty();
            assertThat(outboxEventRepository.findAll())
                  .anyMatch(e -> e.getEventType().equals("job-deleted"));
        }

        @Test
        void deleteJob_WhenNotOwner_ShouldReturn403Forbidden() throws Exception {
            UUID ownerId = UUID.randomUUID();
            UUID unauthorizedUser = UUID.randomUUID();
            Job job = seedJob("Job to Protect", OPEN, ownerId);

            mockMvc.perform(delete("/api/v1/jobs/{id}", job.getId())
                        .with(recruiterJwt(unauthorizedUser))
                  )
                  .andExpect(status().isForbidden());

            assertThat(jobRepository.findById(job.getId())).isPresent();
        }
    }
}