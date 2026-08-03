package com.echcherqaoui.jobboard.searchservice.service.impl;

import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.searchservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.echcherqaoui.jobboard.searchservice.exception.domain.JobDocumentNotFoundException;
import com.echcherqaoui.jobboard.searchservice.repository.JobDocumentRepository;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class JobIndexServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private JobIndexServiceImpl jobIndexService;

    @Autowired
    private JobDocumentRepository repository;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @BeforeEach
    void setUpIndex() {
        IndexOperations indexOps = elasticsearchTemplate.indexOps(JobDocument.class);
        if (indexOps.exists())
            indexOps.delete();

        indexOps.create();
        indexOps.putMapping();
    }

    private JobUpsertedEvent buildJobUpsertedEvent(String jobId, String status) {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.newBuilder()
              .setSeconds(now.getEpochSecond())
              .setNanos(now.getNano())
              .build();

        return JobUpsertedEvent.newBuilder()
              .setEventId("evt-1")
              .setJobId(jobId)
              .setRecruiterId("recruiter-1")
              .setCompanyName("Tech Corp")
              .setCompanyLogo("https://cdn.example.com/logo.png")
              .setTitle("Senior Java Developer")
              .setDescription("Backend dev position.")
              .setRequirements("Spring Boot expertise.")
              .setLocation("Remote")
              .setWorkModality("REMOTE")
              .setJobType("FULL_TIME")
              .setExperienceLevel("SENIOR")
              .setSalaryMinCents(100000)
              .setSalaryMaxCents(150000)
              .setCurrency("USD")
              .setStatus(status)
              .addAllSkills(List.of("Java", "Spring Boot"))
              .setCreatedAt(timestamp)
              .setExpiresAt(timestamp)
              .setOccurredAt(timestamp)
              .setSignature("sig-123")
              .build();
    }

    @Nested
    class UpsertJob {

        @Test
        void upsertJob_WhenNewEvent_ShouldSaveDocumentInElasticsearch() {
            JobUpsertedEvent event = buildJobUpsertedEvent("job-100", "ACTIVE");

            jobIndexService.upsertJob(event);

            Optional<JobDocument> found = repository.findById("job-100");
            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo("job-100");
            assertThat(found.get().getTitle()).isEqualTo("Senior Java Developer");
            assertThat(found.get().getSalaryMin()).isEqualTo(1000.0);
            assertThat(found.get().getSalaryMax()).isEqualTo(1500.0);
            assertThat(found.get().getStatus()).isEqualTo("ACTIVE");
            assertThat(found.get().getSkills()).containsExactly("Java", "Spring Boot");
        }
    }

    @Nested
    class DeleteJob {

        @Test
        void deleteJob_WhenDocumentExists_ShouldRemoveFromIndex() {
            JobUpsertedEvent event = buildJobUpsertedEvent("job-200", "ACTIVE");
            jobIndexService.upsertJob(event);
            assertThat(repository.existsById("job-200")).isTrue();

            jobIndexService.deleteJob("job-200");

            assertThat(repository.existsById("job-200")).isFalse();
        }
    }

    @Nested
    class UpdateJobStatus {

        @Test
        void updateJobStatus_WhenDocumentExists_ShouldUpdateStatusFieldOnly() {
            JobUpsertedEvent event = buildJobUpsertedEvent("job-300", "ACTIVE");
            jobIndexService.upsertJob(event);

            jobIndexService.updateJobStatus("job-300", "CLOSED");

            JobDocument updated = repository.findById("job-300").orElseThrow();
            assertThat(updated.getStatus()).isEqualTo("CLOSED");
            assertThat(updated.getTitle()).isEqualTo("Senior Java Developer");
        }

        @Test
        void updateJobStatus_WhenDocumentDoesNotExist_ShouldThrowJobDocumentNotFoundException() {
            assertThatThrownBy(() -> jobIndexService.updateJobStatus("non-existing-job", "EXPIRED"))
                  .isInstanceOf(JobDocumentNotFoundException.class);
        }
    }
}