package com.echcherqaoui.jobboard.searchservice.controller;

import com.echcherqaoui.jobboard.searchservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerIT extends AbstractIntegrationTest {

    @Value("${api.base-path}")
    private String basePath;

    @Autowired
    private MockMvc mockMvc;

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

    private RequestPostProcessor userJwt() {
        return jwt()
              .authorities(new SimpleGrantedAuthority("ROLE_CANDIDATE"))
              .jwt(builder -> builder
                    .claim("sub", UUID.randomUUID().toString())
                    .claim("email", "user@example.com"));
    }

    private JobDocument seedJob(String id,
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
        JobDocument doc = new JobDocument()
              .setId(id)
              .setRecruiterId("recruiter-1")
              .setTitle(title)
              .setCompanyName(companyName)
              .setCompanyLogo("https://storage.com/logo.png")
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

        elasticsearchTemplate.save(doc);
        return doc;
    }

    private void refreshIndex() {
        elasticsearchTemplate.indexOps(JobDocument.class).refresh();
    }

    @Nested
    class SearchJobs {

        @Test
        void searchJobs_WhenKeywordProvided_ShouldReturnMatchingHitsAndHighlights() throws Exception {
            Instant now = Instant.now();
            seedJob(
                  "job-1",
                  "Senior Java Engineer",
                  "Acme Corp",
                  "OPEN",
                  "REMOTE",
                  "FULL_TIME",
                  "SENIOR",
                  120000.0,
                  150000.0,
                  List.of("Java", "Spring Boot"),
                  now,
                  now.plus(30, DAYS)
            );

            seedJob(
                  "job-2",
                  "Frontend Developer",
                  "Beta LLC",
                  "OPEN",
                  "HYBRID",
                  "FULL_TIME",
                  "MID",
                  90000.0,
                  110000.0,
                  List.of("Angular", "TypeScript"),
                  now,
                  now.plus(30, DAYS)
            );

            refreshIndex();

            mockMvc.perform(get(basePath + "/search/jobs")
                        .with(userJwt())
                        .param("keyword", "Java")
                        .param("status", "OPEN")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sortBy", "relevance")
                        .param("sortDirection", "desc")
                  ).andExpect(status().isOk())
                  .andExpect(jsonPath("$.totalHits").value(1))
                  .andExpect(jsonPath("$.hits", hasSize(1)))
                  .andExpect(jsonPath("$.hits[0].id").value("job-1"))
                  .andExpect(jsonPath("$.hits[0].title").value("Senior Java Engineer"))
                  .andExpect(jsonPath("$.hits[0].highlights.title[0]").exists());
        }

        @Test
        void searchJobs_WhenFilteredByCompanyAndSalary_ShouldReturnFilteredResults() throws Exception {
            Instant now = Instant.now();
            seedJob(
                  "job-10",
                  "Backend Engineer",
                  "TechCorp",
                  "OPEN",
                  "REMOTE",
                  "FULL_TIME",
                  "SENIOR",
                  130000.0,
                  160000.0,
                  List.of("Java"),
                  now,
                  now.plus(10, DAYS)
            );

            seedJob(
                  "job-11",
                  "DevOps Engineer",
                  "TechCorp",
                  "OPEN",
                  "ON_SITE",
                  "CONTRACT",
                  "SENIOR",
                  80000.0,
                  100000.0,
                  List.of("Docker"),
                  now,
                  now.plus(10, DAYS)
            );

            refreshIndex();

            mockMvc.perform(get(basePath + "/search/jobs")
                        .with(userJwt())
                        .param("companyName", "TechCorp")
                        .param("workModalities", "REMOTE")
                        .param("salaryMin", "100000")
                        .param("salaryMax", "170000")
                  ).andExpect(status().isOk())
                  .andExpect(jsonPath("$.totalHits").value(1))
                  .andExpect(jsonPath("$.hits[0].id").value("job-10"));
        }

        @Test
        void searchJobs_WhenUnauthenticated_ShouldReturn401Unauthorized() throws Exception {
            mockMvc.perform(get(basePath + "/search/jobs"))
                  .andExpect(status().isUnauthorized());
        }

        @Test
        void searchJobs_WhenValidationFails_ShouldReturn400() throws Exception {
            mockMvc.perform(get(basePath + "/search/jobs")
                  .with(userJwt())
                  .param("page", "-1")
                  .param("size", "150")
                  .param("salaryMin", "-500")
            ).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class GetJobById {

        @Test
        void getJobById_WhenJobExists_ShouldReturnJobDTO() throws Exception {
            Instant now = Instant.now();
            JobDocument job = seedJob(
                  "job-target",
                  "Security Engineer",
                  "SecureInc",
                  "OPEN",
                  "HYBRID",
                  "FULL_TIME",
                  "MID",
                  100000.0,
                  130000.0,
                  List.of("Security"),
                  now,
                  now.plus(10, DAYS)
            );

            refreshIndex();

            mockMvc.perform(get(basePath + "/search/jobs/{id}", job.getId())
                        .with(userJwt())
                  ).andExpect(status().isOk())
                  .andExpect(jsonPath("$.id").value("job-target"))
                  .andExpect(jsonPath("$.title").value("Security Engineer"))
                  .andExpect(jsonPath("$.companyName").value("SecureInc"));
        }

        @Test
        void getJobById_WhenUnauthenticated_ShouldReturn401Unauthorized() throws Exception {
            mockMvc.perform(get(basePath + "/search/jobs/{id}", "job-target"))
                  .andExpect(status().isUnauthorized());
        }

        @Test
        void getJobById_WhenNotFound_ShouldReturn404() throws Exception {
            mockMvc.perform(get(basePath + "/search/jobs/{id}", "non-existent-id")
                  .with(userJwt())
            ).andExpect(status().isNotFound());
        }
    }

    @Nested
    class Autocomplete {
        @Test
        void autocomplete_WhenValidPrefix_ShouldReturnSuggestions() throws Exception {
            Instant now = Instant.now();
            seedJob(
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

            seedJob(
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

            refreshIndex();

            mockMvc.perform(get(basePath + "/search/jobs/autocomplete")
                        .with(userJwt())
                        .param("prefix", "Soft")
                        .param("limit", "10")
                  ).andExpect(status().isOk())
                  .andExpect(jsonPath("$.suggestions", hasSize(2)))
                  .andExpect(jsonPath("$.suggestions[0]").exists());
        }

        @Test
        void autocomplete_WhenUnauthenticated_ShouldReturn401Unauthorized() throws Exception {
            mockMvc.perform(get(basePath + "/search/jobs/autocomplete")
                  .param("prefix", "Soft")
            ).andExpect(status().isUnauthorized());
        }

        @Test
        void autocomplete_WhenPrefixTooShort_ShouldReturn400() throws Exception {
            mockMvc.perform(get(basePath + "/search/jobs/autocomplete")
                  .with(userJwt())
                  .param("prefix", "a")
            ).andExpect(status().isBadRequest());
        }

        @Test
        void autocomplete_WhenLimitExceedsMax_ShouldReturn400() throws Exception {
            mockMvc.perform(get(basePath + "/search/jobs/autocomplete")
                  .with(userJwt())
                  .param("prefix", "Software")
                  .param("limit", "50")
            ).andExpect(status().isBadRequest());
        }
    }
}