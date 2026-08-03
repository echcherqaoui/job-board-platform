package com.echcherqaoui.jobboard.searchservice.service.impl;

import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.echcherqaoui.jobboard.searchservice.dto.request.JobSearchRequest;
import com.echcherqaoui.jobboard.searchservice.dto.response.AutocompleteResponse;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobDTO;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchHit;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchResponse;
import com.echcherqaoui.jobboard.searchservice.exception.domain.JobDocumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class JobSearchServiceImplIT {

    @Container
    static final ElasticsearchContainer ELASTICSEARCH = new ElasticsearchContainer(
          DockerImageName.parse("elasticsearch:8.18.8"))
          .withEnv("xpack.security.enabled", "false");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", ELASTICSEARCH::getHttpHostAddress);
        registry.add("search.highlight.pre-tag", () -> "<mark>");
        registry.add("search.highlight.post-tag", () -> "</mark>");
    }

    @Autowired
    private JobSearchServiceImpl jobSearchService;

    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @BeforeEach
    void setUpIndex() {
        IndexOperations indexOps = elasticsearchTemplate.indexOps(JobDocument.class);
        if (indexOps.exists()) {
            indexOps.delete();
        }
        indexOps.create();
        indexOps.putMapping();
    }

    private JobDocument buildDoc(String id,
                                 String title,
                                 String companyName,
                                 String status,
                                 String modality,
                                 String jobType,
                                 String expLevel,
                                 Double salaryMin,
                                 Double salaryMax,
                                 List<String> skills,
                                 Instant createdAt,
                                 Instant expiresAt) {
        return new JobDocument()
              .setId(id)
              .setRecruiterId("recruiter-1")
              .setTitle(title)
              .setCompanyName(companyName)
              .setCompanyLogo("https://cdn.example.com/logo.png")
              .setDescription("Description for " + title)
              .setRequirements("Requirements for " + title)
              .setLocation("Remote")
              .setWorkModality(modality)
              .setJobType(jobType)
              .setExperienceLevel(expLevel)
              .setStatus(status)
              .setSalaryMin(salaryMin)
              .setSalaryMax(salaryMax)
              .setCurrency("USD")
              .setSkills(skills)
              .setCreatedAt(createdAt)
              .setExpiresAt(expiresAt);
    }

    @Nested
    class SearchAndFilterTests {

        @Test
        void shouldSearchByKeywordAndHighlight() {
            Instant now = Instant.now();
            JobDocument doc1 = buildDoc(
                  "job-1",
                  "Senior Java Engineer", "Acme Corp", "OPEN",
                  "REMOTE", "FULL_TIME", "SENIOR", 120000.0, 150000.0,
                  List.of("Java", "Spring Boot", "Elasticsearch"), now, now.plus(30, DAYS));

            JobDocument doc2 = buildDoc("job-2", "Frontend Developer", "Beta LLC", "OPEN",
                  "HYBRID", "FULL_TIME", "MID", 90000.0, 110000.0,
                  List.of("Angular", "TypeScript"), now, now.plus(30, DAYS));

            elasticsearchTemplate.save(doc1, doc2);
            elasticsearchTemplate.indexOps(JobDocument.class).refresh();

            JobSearchRequest request = new JobSearchRequest(
                  "Java", null, null, null, null, null, null, null, null, "OPEN", 0, 10, "relevance", "desc"
            );

            JobSearchResponse response = jobSearchService.search(request);

            assertThat(response.totalHits()).isEqualTo(1);
            assertThat(response.hits()).hasSize(1);

            JobSearchHit hit = response.hits().get(0);
            assertThat(hit.id()).isEqualTo("job-1");
            assertThat(hit.title()).isEqualTo("Senior Java Engineer");
            assertThat(hit.highlights()).containsKey("title");
            assertThat(hit.highlights().get("title").get(0)).contains("<mark>Java</mark>");
        }

        @Test
        void shouldFilterByCriteria() {
            Instant now = Instant.now();
            JobDocument doc1 = buildDoc("job-10", "Backend Engineer", "TechCorp", "OPEN",
                  "REMOTE", "FULL_TIME", "SENIOR", 130000.0, 160000.0,
                  List.of("Java", "Kafka"), now, now.plus(10, DAYS));

            JobDocument doc2 = buildDoc("job-11", "DevOps Engineer", "TechCorp", "OPEN",
                  "ON_SITE", "CONTRACT", "SENIOR", 80000.0, 100000.0,
                  List.of("Docker", "Kubernetes"), now, now.plus(10, DAYS));

            JobDocument doc3 = buildDoc("job-12", "Archived Java Lead", "TechCorp", "CLOSED",
                  "REMOTE", "FULL_TIME", "LEAD", 180000.0, 200000.0,
                  List.of("Java"), now, now.plus(10, DAYS));

            elasticsearchTemplate.save(doc1, doc2, doc3);
            elasticsearchTemplate.indexOps(JobDocument.class).refresh();

            JobSearchRequest request = new JobSearchRequest(
                  null, "TechCorp", null, List.of("REMOTE"), null, null, null, 100000.0, 170000.0, "OPEN", 0, 10, "createdAt", "desc"
            );

            JobSearchResponse response = jobSearchService.search(request);

            assertThat(response.totalHits()).isEqualTo(1);
            assertThat(response.hits().get(0).id()).isEqualTo("job-10");
        }

        @Test
        void shouldExcludeExpiredJobs() {
            Instant now = Instant.now();
            JobDocument validJob = buildDoc("job-valid", "Valid Role", "Company A", "OPEN",
                  "REMOTE", "FULL_TIME", "MID", 90000.0, 110000.0,
                  List.of("Java"), now, now.plus(1, DAYS));

            JobDocument expiredJob = buildDoc("job-expired", "Expired Role", "Company A", "OPEN",
                  "REMOTE", "FULL_TIME", "MID", 90000.0, 110000.0,
                  List.of("Java"), now.minus(10, DAYS), now.minus(1, DAYS));

            elasticsearchTemplate.save(validJob, expiredJob);
            elasticsearchTemplate.indexOps(JobDocument.class).refresh();

            JobSearchRequest request = new JobSearchRequest(
                  null, null, null, null, null, null, null, null, null, "OPEN", 0, 10, "relevance", "desc"
            );

            JobSearchResponse response = jobSearchService.search(request);

            assertThat(response.totalHits()).isEqualTo(1);
            assertThat(response.hits().get(0).id()).isEqualTo("job-valid");
        }
    }

    @Nested
    class PaginationAndSortingTests {

        @Test
        void shouldSortBySalaryMinAsc() {
            Instant now = Instant.now();
            JobDocument lowSalary = buildDoc(
                  "job-low",
                  "Junior Dev",
                  "Company B",
                  "OPEN",
                  "REMOTE",
                  "FULL_TIME",
                  "JUNIOR",
                  60000.0,
                  80000.0,
                  List.of("Java"),
                  now,
                  now.plus(5, DAYS)
            );

            JobDocument highSalary = buildDoc(
                  "job-high",
                  "Architect",
                  "Company B",
                  "OPEN",
                  "REMOTE",
                  "FULL_TIME",
                  "LEAD",
                  150000.0,
                  200000.0,
                  List.of("Java"),
                  now,
                  now.plus(5, DAYS)
            );

            elasticsearchTemplate.save(highSalary, lowSalary);
            elasticsearchTemplate.indexOps(JobDocument.class).refresh();

            JobSearchRequest request = new JobSearchRequest(
                  null, null, null, null, null, null, null, null, null, "OPEN", 0, 10, "salaryMin", "asc"
            );

            JobSearchResponse response = jobSearchService.search(request);

            assertThat(response.totalHits()).isEqualTo(2);
            assertThat(response.hits().get(0).id()).isEqualTo("job-low");
            assertThat(response.hits().get(1).id()).isEqualTo("job-high");
        }
    }

    @Nested
    class GetByIdTests {

        @Test
        void shouldGetById() {
            Instant now = Instant.now();
            JobDocument doc = buildDoc(
                  "job-target",
                  "Security Analyst",
                  "SecureInc",
                  "OPEN",
                  "HYBRID",
                  "FULL_TIME",
                  "MID",
                  100000.0,
                  130000.0,
                  List.of("Security", "Spring"),
                  now,
                  now.plus(10, DAYS)
            );

            elasticsearchTemplate.save(doc);
            elasticsearchTemplate.indexOps(JobDocument.class).refresh();

            JobDTO result = jobSearchService.getById("job-target");

            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo("job-target");
            assertThat(result.title()).isEqualTo("Security Analyst");
            assertThat(result.companyName()).isEqualTo("SecureInc");
        }

        @Test
        void shouldThrowNotFoundException() {
            assertThatThrownBy(() -> jobSearchService.getById("non-existent-id"))
                  .isInstanceOf(JobDocumentNotFoundException.class);
        }
    }

    @Nested
    class AutocompleteTests {

        @Test
        void shouldReturnAutocompleteSuggestions() {
            Instant now = Instant.now();
            JobDocument doc1 = buildDoc(
                  "job-ac-1",
                  "Software Engineer",
                  "Corp X",
                  "OPEN",
                  "REMOTE",
                  "FULL_TIME",
                  "MID",
                  90000.0,
                  120000.0,
                  List.of("Java"),
                  now,
                  now.plus(10, DAYS)
            );

            JobDocument doc2 = buildDoc(
                  "job-ac-2",
                  "Software Architect",
                  "Corp Y",
                  "OPEN",
                  "REMOTE",
                  "FULL_TIME",
                  "LEAD",
                  160000.0,
                  190000.0,
                  List.of("Architecture"),
                  now,
                  now.plus(10, DAYS)
            );

            JobDocument doc3 = buildDoc(
                  "job-ac-3",
                  "Software Engineer",
                  "Corp Z",
                  "OPEN",
                  "REMOTE",
                  "FULL_TIME",
                  "SENIOR",
                  130000.0,
                  150000.0,
                  List.of("C++"),
                  now,
                  now.plus(10, DAYS)
            );

            JobDocument closedDoc = buildDoc(
                  "job-ac-4",
                  "Software Tester",
                  "Corp Z",
                  "CLOSED",
                  "REMOTE",
                  "FULL_TIME",
                  "MID",
                  70000.0,
                  90000.0,
                  List.of("QA"),
                  now,
                  now.plus(10, DAYS)
            );

            elasticsearchTemplate.save(doc1, doc2, doc3, closedDoc);
            elasticsearchTemplate.indexOps(JobDocument.class).refresh();

            AutocompleteResponse response = jobSearchService.autocomplete("Soft", 5);

            assertThat(response.suggestions())
                  .containsExactlyInAnyOrder("Software Engineer", "Software Architect")
                  .doesNotContain("Software Tester");
        }

        @Test
        void shouldReturnEmptyForBlankPrefix() {
            AutocompleteResponse response = jobSearchService.autocomplete("  ", 5);
            assertThat(response.suggestions()).isEmpty();
        }
    }
}