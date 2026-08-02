package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.job.event.JobDeletedEvent;
import com.echcherqaoui.jobboard.job.event.JobStatusChangedEvent;
import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobSearchCriteria;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobStatusUpdateRequest;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobResponse;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobSummaryResponse;
import com.echcherqaoui.jobboard.jobservice.exception.domain.JobExpiredException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.UnauthorizedJobAccessException;
import com.echcherqaoui.jobboard.jobservice.grpc.client.CompanyProfileClient;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import com.echcherqaoui.jobboard.jobservice.repository.CompanyProfileRepository;
import com.echcherqaoui.jobboard.jobservice.repository.JobRepository;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.jobservice.service.JobService;
import com.echcherqaoui.jobboard.security.jwt.JwtContextHolder;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel.MID;
import static com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel.SENIOR;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.CLOSED;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.DRAFT;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.OPEN;
import static com.echcherqaoui.jobboard.jobservice.model.JobType.FULL_TIME;
import static com.echcherqaoui.jobboard.jobservice.model.JobType.PART_TIME;
import static com.echcherqaoui.jobboard.jobservice.model.WorkModality.HYBRID;
import static com.echcherqaoui.jobboard.jobservice.model.WorkModality.REMOTE;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class JobServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private CompanyProfileService companyProfileService;

    @MockitoBean
    private CompanyProfileClient companyProfileClient;

    @MockitoBean
    private JwtContextHolder jwtContextHolder;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @MockitoBean
    private SignatureService signatureService;

    private final UUID recruiterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAllInBatch();
        jobRepository.deleteAllInBatch();
        companyProfileRepository.deleteAllInBatch();

        when(jwtContextHolder.getUserId()).thenReturn(recruiterId);
        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });

        when(signatureService.sign(anyString(), anyString(), anyString())).thenReturn("mock-signature");

        CompanyProfile seededProfile = new CompanyProfile()
              .setRecruiterId(recruiterId)
              .setCompanyName("AgileCorp")
              .setCompanyLogo("agile_logo.png")
              .setLastEventId("seed-event")
              .setUpdatedAt(OffsetDateTime.now(UTC));
        companyProfileRepository.save(seededProfile);

        when(companyProfileService.getByRecruiterId(recruiterId)).thenReturn(seededProfile);
    }

    private JobRequest buildJobRequest(String title) {
        return new JobRequest(
              title,
              "Description for " + title,
              "Requirements details",
              "Responsibilities details",
              "Casablanca",
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

    private Job seedJob(String title, JobStatus status, String location, OffsetDateTime expiresAt) {
        Job job = new Job();
        job.setRecruiterId(recruiterId);
        job.setTitle(title);
        job.setDescription("Description for " + title);
        job.setLocation(location);
        job.setWorkModality(REMOTE);
        job.setJobType(FULL_TIME);
        job.setExperienceLevel(MID);
        job.setSalaryMin(new BigDecimal("45000.00"));
        job.setSalaryMax(new BigDecimal("75000.00"));
        job.setCurrency("MAD");
        job.setStatus(status);
        job.setExpiresAt(expiresAt);
        job.addSkills(List.of("Java", "Spring Boot"));
        job.setCreatedAt(OffsetDateTime.now(UTC));
        job.setUpdatedAt(OffsetDateTime.now(UTC));
        return jobRepository.save(job);
    }

    @Nested
    class PostJob {

        @Test
        void happyPath_shouldSaveJobAndOutboxEvent() throws InvalidProtocolBufferException {
            JobRequest request = buildJobRequest("Lead Backend Engineer");

            JobResponse response = jobService.postJob(request);

            assertThat(response).isNotNull();
            assertThat(response.id()).isNotNull();
            assertThat(response.recruiterId()).isEqualTo(recruiterId);
            assertThat(response.companyName()).isEqualTo("AgileCorp");
            assertThat(response.companyLogo()).isEqualTo("agile_logo.png");
            assertThat(response.title()).isEqualTo("Lead Backend Engineer");
            assertThat(response.status()).isEqualTo(DRAFT);
            assertThat(response.skills()).containsExactlyInAnyOrder("Java", "Spring Boot", "PostgreSQL");

            Job dbJob = jobRepository.findWithSkillsById(response.id()).orElse(null);
            assertThat(dbJob).isNotNull();
            assertThat(dbJob.getTitle()).isEqualTo("Lead Backend Engineer");

            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            OutboxEvent event = outboxEvents.get(0);
            assertThat(event.getEventType()).isEqualTo("job-upserted");
            assertThat(event.getAggregateId()).isEqualTo(dbJob.getId().toString());

            JobUpsertedEvent parsedPayload = JobUpsertedEvent.parseFrom(event.getPayload());
            assertThat(parsedPayload.getJobId()).isEqualTo(dbJob.getId().toString());
            assertThat(parsedPayload.getTitle()).isEqualTo("Lead Backend Engineer");
            assertThat(parsedPayload.getCompanyName()).isEqualTo("AgileCorp");
            assertThat(parsedPayload.getSkillsList()).containsExactlyInAnyOrder("Java", "Spring Boot", "PostgreSQL");
        }

        @Test
        void rollsBackJobRow_whenCompanyProfileLookupThrowsAfterSave() {
            UUID testRecruiterId = UUID.randomUUID();
            when(jwtContextHolder.getUserId()).thenReturn(testRecruiterId);
            when(companyProfileService.getByRecruiterId(testRecruiterId))
                  .thenThrow(new RuntimeException("company-service down"));

            JobRequest request = buildJobRequest("Backend Engineer");
            long countBefore = jobRepository.count();

            assertThatThrownBy(() -> jobService.postJob(request))
                  .isInstanceOf(RuntimeException.class);

            long countAfter = jobRepository.count();
            assertThat(countAfter).isEqualTo(countBefore);
        }
    }

    @Nested
    class UpdateJob {

        @Test
        void happyPath_shouldUpdateJobAndOutboxEvent() throws InvalidProtocolBufferException {
            Job seededJob = seedJob("Software Developer", DRAFT, "Rabat", null);

            JobRequest updateRequest = new JobRequest(
                  "Senior Software Developer",
                  "New Description",
                  "New Requirements",
                  "New Responsibilities",
                  "Rabat",
                  HYBRID,
                  PART_TIME,
                  SENIOR,
                  new BigDecimal("60000.00"),
                  new BigDecimal("90000.00"),
                  "MAD",
                  null,
                  List.of("Go", "Kubernetes")
            );

            JobResponse response = jobService.updateJob(seededJob.getId(), updateRequest);

            assertThat(response).isNotNull();
            assertThat(response.title()).isEqualTo("Senior Software Developer");
            assertThat(response.workModality()).isEqualTo(HYBRID);
            assertThat(response.jobType()).isEqualTo(PART_TIME);
            assertThat(response.experienceLevel()).isEqualTo(SENIOR);
            assertThat(response.skills()).containsExactlyInAnyOrder("Go", "Kubernetes");

            Job updatedDbJob = jobRepository.findWithSkillsById(seededJob.getId()).orElse(null);
            assertThat(updatedDbJob).isNotNull();
            assertThat(updatedDbJob.getTitle()).isEqualTo("Senior Software Developer");

            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            OutboxEvent event = outboxEvents.get(0);
            assertThat(event.getEventType()).isEqualTo("job-upserted");

            JobUpsertedEvent parsedPayload = JobUpsertedEvent.parseFrom(event.getPayload());
            assertThat(parsedPayload.getTitle()).isEqualTo("Senior Software Developer");
            assertThat(parsedPayload.getSkillsList()).containsExactlyInAnyOrder("Go", "Kubernetes");
        }

        @Test
        void rollsBackChanges_whenCompanyProfileLookupThrowsAfterSave() {
            UUID testRecruiterId = UUID.randomUUID();
            when(jwtContextHolder.getUserId()).thenReturn(testRecruiterId);

            CompanyProfile realProfile = new CompanyProfile()
                  .setRecruiterId(testRecruiterId)
                  .setCompanyName("Acme")
                  .setCompanyLogo("logo.png")
                  .setLastEventId("evt-seed")
                  .setUpdatedAt(OffsetDateTime.now(UTC));
            when(companyProfileService.getByRecruiterId(testRecruiterId)).thenReturn(realProfile);

            JobRequest createRequest = buildJobRequest("Backend Engineer");
            JobResponse created = jobService.postJob(createRequest);
            UUID jobId = created.id();

            when(companyProfileService.getByRecruiterId(testRecruiterId))
                  .thenThrow(new RuntimeException("company-service down"));

            JobRequest updateRequest = buildJobRequest("Updated Title");

            assertThatThrownBy(() -> jobService.updateJob(jobId, updateRequest))
                  .isInstanceOf(RuntimeException.class);

            Job jobInDb = jobRepository.findById(jobId).orElseThrow();
            assertThat(jobInDb.getTitle()).isEqualTo("Backend Engineer");
        }
    }

    @Nested
    class UpdateJobStatus {

        @Test
        void happyPath_shouldUpdateStatusAndOutboxEvent() throws InvalidProtocolBufferException {
            Job seededJob = seedJob("Data Engineer", DRAFT, "Casablanca", null);

            JobStatusUpdateRequest request = new JobStatusUpdateRequest(OPEN);
            JobResponse response = jobService.updateJobStatus(seededJob.getId(), request);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(OPEN);

            Job dbJob = jobRepository.findById(seededJob.getId()).orElseThrow();
            assertThat(dbJob.getStatus()).isEqualTo(OPEN);

            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            OutboxEvent event = outboxEvents.get(0);
            assertThat(event.getEventType()).isEqualTo("job-status-changed");

            JobStatusChangedEvent parsed = JobStatusChangedEvent.parseFrom(event.getPayload());
            assertThat(parsed.getJobId()).isEqualTo(seededJob.getId().toString());
            assertThat(parsed.getJobStatus()).isEqualTo("OPEN");
        }

        @Test
        void unauthorized_shouldThrowException() {
            Job seededJob = seedJob("Data Engineer", DRAFT, "Casablanca", null);

            when(jwtContextHolder.getUserId()).thenReturn(UUID.randomUUID());

            JobStatusUpdateRequest request = new JobStatusUpdateRequest(OPEN);

            UUID jobId = seededJob.getId();

            assertThatThrownBy(() -> jobService.updateJobStatus(jobId, request))
                  .isInstanceOf(UnauthorizedJobAccessException.class);
        }

        @Test
        void expiredJob_shouldThrowException() {
            Job seededJob = seedJob("Expired Engineer", OPEN, "Casablanca", OffsetDateTime.now(UTC).minusDays(1));

            JobStatusUpdateRequest request = new JobStatusUpdateRequest(CLOSED);

            UUID jobId = seededJob.getId();

            assertThatThrownBy(() -> jobService.updateJobStatus(jobId, request))
                  .isInstanceOf(JobExpiredException.class);
        }
    }

    @Nested
    class DeleteJob {

        @Test
        void happyPath_shouldDeleteJobAndSaveOutboxEvent() throws InvalidProtocolBufferException {
            Job seededJob = seedJob("QA Engineer", OPEN, "Casablanca", null);

            jobService.deleteJob(seededJob.getId());

            assertThat(jobRepository.findById(seededJob.getId())).isEmpty();

            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            OutboxEvent event = outboxEvents.get(0);
            assertThat(event.getEventType()).isEqualTo("job-deleted");

            JobDeletedEvent parsed = JobDeletedEvent.parseFrom(event.getPayload());
            assertThat(parsed.getJobId()).isEqualTo(seededJob.getId().toString());
        }
    }

    @Nested
    class ExpireJobs {

        @Test
        void shouldCloseExpiredJobsAndSaveBatchEvents() throws InvalidProtocolBufferException {
            Job expiredJob = seedJob("Expired Dev", OPEN, "Casablanca", OffsetDateTime.now(UTC).minusDays(1));
            Job activeJob = seedJob("Active Dev", OPEN, "Casablanca", OffsetDateTime.now(UTC).plusDays(2));
            Job draftJob = seedJob("Draft Dev", DRAFT, "Casablanca", OffsetDateTime.now(UTC).minusDays(1));

            jobService.expireJobs();

            assertThat(jobRepository.findById(expiredJob.getId()).orElseThrow().getStatus()).isEqualTo(CLOSED);
            assertThat(jobRepository.findById(activeJob.getId()).orElseThrow().getStatus()).isEqualTo(OPEN);
            assertThat(jobRepository.findById(draftJob.getId()).orElseThrow().getStatus()).isEqualTo(DRAFT);

            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            OutboxEvent event = outboxEvents.get(0);
            assertThat(event.getEventType()).isEqualTo("job-expired");

            JobStatusChangedEvent parsed = JobStatusChangedEvent.parseFrom(event.getPayload());
            assertThat(parsed.getJobId()).isEqualTo(expiredJob.getId().toString());
            assertThat(parsed.getJobStatus()).isEqualTo("CLOSED");
            assertThat(parsed.getJobTitle()).isEqualTo("Expired Dev");
        }
    }

    @Nested
    class GetJobs {

        @Test
        void searchJobs_shouldFilterCorrectly() {
            seedJob("Java Dev Casablanca", OPEN, "Casablanca", null);
            seedJob("Python Dev Casablanca", OPEN, "Casablanca", null);
            seedJob("Java Dev Rabat", OPEN, "Rabat", null);
            seedJob("Java Dev Casablanca Closed", CLOSED, "Casablanca", null);

            JobSearchCriteria keywordCriteria = new JobSearchCriteria("Java", null, null, null, null, null, null);
            PaginatedResponse<JobSummaryResponse> keywordResult = jobService.searchJobs(keywordCriteria, PageRequest.of(0, 10));
            assertThat(keywordResult.content()).hasSize(2);
            assertThat(keywordResult.content()).allMatch(j -> j.title().contains("Java"));

            JobSearchCriteria locationCriteria = new JobSearchCriteria(null, "Rabat", null, null, null, null, null);
            PaginatedResponse<JobSummaryResponse> locationResult = jobService.searchJobs(locationCriteria, PageRequest.of(0, 10));
            assertThat(locationResult.content()).hasSize(1);
            assertThat(locationResult.content().get(0).title()).isEqualTo("Java Dev Rabat");

            JobSearchCriteria combinedCriteria = new JobSearchCriteria("Java", "Casablanca", null, null, null, null, null);
            PaginatedResponse<JobSummaryResponse> combinedResult = jobService.searchJobs(combinedCriteria, PageRequest.of(0, 10));
            assertThat(combinedResult.content()).hasSize(1);
            assertThat(combinedResult.content().get(0).title()).isEqualTo("Java Dev Casablanca");
        }

        @Test
        void getMyJobs_shouldReturnRecruiterJobs() {
            seedJob("Recruiter Job 1", OPEN, "Casablanca", null);
            seedJob("Recruiter Job 2", OPEN, "Casablanca", null);

            UUID otherRecruiter = UUID.randomUUID();
            Job otherJob = new Job();
            otherJob.setRecruiterId(otherRecruiter);
            otherJob.setTitle("Other Job");
            otherJob.setDescription("Desc");
            otherJob.setWorkModality(REMOTE);
            otherJob.setJobType(FULL_TIME);
            otherJob.setExperienceLevel(MID);
            otherJob.setStatus(OPEN);
            otherJob.setCreatedAt(OffsetDateTime.now(UTC));
            otherJob.setUpdatedAt(OffsetDateTime.now(UTC));
            jobRepository.save(otherJob);

            PaginatedResponse<JobSummaryResponse> myJobsResult = jobService.getMyJobs(PageRequest.of(0, 10));
            assertThat(myJobsResult.content()).hasSize(2);
            assertThat(myJobsResult.content()).allMatch(j -> j.recruiterId().equals(recruiterId));
        }

        @Test
        void findJobProjectionById_shouldReturnCorrectProjection() {
            Job seededJob = seedJob("Projection Dev", OPEN, "Casablanca", null);

            JobSummaryProjection projection = jobService.findJobProjectionById(seededJob.getId());

            assertThat(projection).isNotNull();
            assertThat(projection.getId()).isEqualTo(seededJob.getId());
            assertThat(projection.getTitle()).isEqualTo("Projection Dev");
            assertThat(projection.getStatus()).isEqualTo(OPEN);
        }

        @Test
        void getJobsSummaries_shouldReturnListOfProjections() {
            Job job1 = seedJob("Dev 1", OPEN, "Casablanca", null);
            Job job2 = seedJob("Dev 2", OPEN, "Rabat", null);

            List<JobSummaryProjection> summaries = jobService.getJobsSummaries(Set.of(job1.getId(), job2.getId()));

            assertThat(summaries).hasSize(2);
            assertThat(summaries).extracting(JobSummaryProjection::getId)
                  .containsExactlyInAnyOrder(job1.getId(), job2.getId());
        }
    }
}