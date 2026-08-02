package com.echcherqaoui.jobboard.jobservice.repository;

import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobSearchCriteria;
import com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import com.echcherqaoui.jobboard.jobservice.model.JobType;
import com.echcherqaoui.jobboard.jobservice.model.WorkModality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel.JUNIOR;
import static com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel.LEAD;
import static com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel.SENIOR;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.CLOSED;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.DRAFT;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.OPEN;
import static com.echcherqaoui.jobboard.jobservice.model.JobType.CONTRACT;
import static com.echcherqaoui.jobboard.jobservice.model.JobType.FULL_TIME;
import static com.echcherqaoui.jobboard.jobservice.model.WorkModality.HYBRID;
import static com.echcherqaoui.jobboard.jobservice.model.WorkModality.ON_SITE;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobSpecificationIT  extends AbstractIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    private UUID recruiterId;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        recruiterId = UUID.randomUUID();

        // Senior Java Remote Open Job
        saveJob(
              "Senior Java Developer",
              "Spring Boot microservices architecture",
              "Rabat, Morocco",
              WorkModality.REMOTE,
              FULL_TIME,
              SENIOR,
              new BigDecimal("60000.00"),
              OPEN
        );

        // Junior Angular Onsite Open Job
        saveJob(
              "Junior Frontend Developer",
              "Angular signals and RXJS",
              "Casablanca, Morocco",
              ON_SITE,
              FULL_TIME,
              JUNIOR,
              new BigDecimal("30000.00"),
              OPEN
        );

        // Senior Java Hybrid Closed Job
        saveJob(
              "Senior Java Engineer",
              "Legacy monolith migration",
              "Rabat, Morocco",
              HYBRID,
              CONTRACT,
              SENIOR,
              new BigDecimal("70000.00"),
              CLOSED
        );

        // Lead DevOps Hybrid Draft Job
        saveJob(
              "DevOps Lead",
              "Kubernetes and CI/CD pipelines",
              "Casablanca, Morocco",
              HYBRID,
              FULL_TIME,
              LEAD,
              new BigDecimal("80000.00"),
              DRAFT
        );
    }

    private void saveJob(String title,
                         String description,
                         String location,
                         WorkModality modality,
                         JobType jobType,
                         ExperienceLevel level,
                         BigDecimal salaryMin,
                         JobStatus status) {

        OffsetDateTime now = OffsetDateTime.now();
        Job job = new Job()
              .setRecruiterId(recruiterId)
              .setTitle(title)
              .setDescription(description)
              .setLocation(location)
              .setWorkModality(modality)
              .setJobType(jobType)
              .setExperienceLevel(level)
              .setSalaryMin(salaryMin)
              .setStatus(status)
              .setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30))
              .setCreatedAt(now)
              .setUpdatedAt(now);

        jobRepository.save(job);
    }

    @Test
    void withFilters_WhenNullStatusPassed_ShouldDefaultToOpenStatus() {
        JobSearchCriteria criteria = new JobSearchCriteria(null, null, null, null, null, null, null);
        Specification<Job> spec = JobSpecification.withFilters(criteria);

        Page<Job> result = jobRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).allMatch(j -> j.getStatus() == OPEN);
    }

    @Test
    void withFilters_WhenExplicitStatusPassed_ShouldFilterByStatus() {
        JobSearchCriteria criteria = new JobSearchCriteria(null, null, null, null, null, null, DRAFT);
        Specification<Job> spec = JobSpecification.withFilters(criteria);

        Page<Job> result = jobRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("DevOps Lead");
    }

    @Test
    void withFilters_WhenMatchingTitleInKeyword_ShouldReturnMatch() {
        JobSearchCriteria criteria = new JobSearchCriteria("angular", null, null, null, null, null, null);
        Specification<Job> spec = JobSpecification.withFilters(criteria);

        Page<Job> result = jobRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Junior Frontend Developer");
    }

    @Test
    void withFilters_WhenMatchingDescriptionInKeyword_ShouldReturnMatch() {
        JobSearchCriteria criteria = new JobSearchCriteria("microservices", null, null, null, null, null, null);
        Specification<Job> spec = JobSpecification.withFilters(criteria);

        Page<Job> result = jobRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Senior Java Developer");
    }

    @Test
    void withFilters_WhenFilteringByLocationCaseInsensitive_ShouldReturnMatchingLocation() {
        JobSearchCriteria criteria = new JobSearchCriteria(null, "rabat", null, null, null, null, null);
        Specification<Job> spec = JobSpecification.withFilters(criteria);

        Page<Job> result = jobRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getLocation()).contains("Rabat");
    }

    @Test
    void withFilters_WhenFilteringByWorkModalityJobTypeExperienceLevelAndSalaryMin_ShouldReturnExactMatch() {
        JobSearchCriteria criteria = new JobSearchCriteria(
              null,
              null,
              WorkModality.REMOTE,
              FULL_TIME,
              SENIOR,
              new BigDecimal("50000.00"),
              OPEN
        );

        Specification<Job> spec = JobSpecification.withFilters(criteria);

        Page<Job> result = jobRepository.findAll(spec, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        Job matched = result.getContent().get(0);
        assertThat(matched.getTitle()).isEqualTo("Senior Java Developer");
        assertThat(matched.getWorkModality()).isEqualTo(WorkModality.REMOTE);
        assertThat(matched.getJobType()).isEqualTo(FULL_TIME);
        assertThat(matched.getExperienceLevel()).isEqualTo(SENIOR);
        assertThat(matched.getSalaryMin()).isGreaterThanOrEqualTo(new BigDecimal("50000.00"));
    }
}