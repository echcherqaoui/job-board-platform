package com.echcherqaoui.jobboard.searchservice.repository;

import com.echcherqaoui.jobboard.searchservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
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

@SpringBootTest
class JobDocumentRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private JobDocumentRepository repository;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @BeforeEach
    void setUp() {
        IndexOperations indexOps = elasticsearchTemplate.indexOps(JobDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping();
    }

    private JobDocument createSampleJob(String id, String title, String companyName) {
        return new JobDocument()
              .setId(id)
              .setRecruiterId("recruiter-100")
              .setCompanyName(companyName)
              .setCompanyLogo("https://cdn.example.com/logo.png")
              .setTitle(title)
              .setDescription("Designing and implementing microservices architecture.")
              .setRequirements("3+ years of professional backend engineering experience.")
              .setLocation("Remote, US")
              .setWorkModality("REMOTE")
              .setJobType("FULL_TIME")
              .setExperienceLevel("SENIOR")
              .setStatus("ACTIVE")
              .setSalaryMin(100000.0)
              .setSalaryMax(140000.0)
              .setCurrency("USD")
              .setSkills(List.of("Java", "Spring Boot", "Elasticsearch"))
              .setCreatedAt(Instant.now())
              .setExpiresAt(Instant.now().plusSeconds(86400 * 30));
    }

    @Nested
    class CrudOperations {

        @Test
        void save_ShouldIndexDocumentSuccessfully() {
            JobDocument job = createSampleJob("job-1", "Senior Java Engineer", "TechCorp");

            JobDocument saved = repository.save(job);

            assertThat(saved.getId()).isEqualTo("job-1");

            Optional<JobDocument> retrieved = repository.findById("job-1");
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().getTitle()).isEqualTo("Senior Java Engineer");
            assertThat(retrieved.get().getSkills()).containsExactly("Java", "Spring Boot", "Elasticsearch");
        }

        @Test
        void deleteById_ShouldRemoveDocumentFromIndex() {
            JobDocument job = createSampleJob("job-2", "Frontend Developer", "WebStudio");
            repository.save(job);

            assertThat(repository.existsById("job-2")).isTrue();

            repository.deleteById("job-2");

            assertThat(repository.existsById("job-2")).isFalse();
        }

        @Test
        void saveAll_ShouldIndexMultipleDocuments() {
            List<JobDocument> jobs = List.of(
                  createSampleJob("job-3", "DevOps Engineer", "CloudInc"),
                  createSampleJob("job-4", "QA Automation Engineer", "QualityFirst")
            );

            repository.saveAll(jobs);

            assertThat(repository.findAll()).hasSize(2);
        }
    }
}