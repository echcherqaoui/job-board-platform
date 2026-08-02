package com.echcherqaoui.jobboard.jobservice.repository;

import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import com.echcherqaoui.jobboard.jobservice.model.JobType;
import com.echcherqaoui.jobboard.jobservice.model.WorkModality;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.DRAFT;
import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.OPEN;
import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobRepositoryIT  extends AbstractIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
    }

    private Job createBaseJob(UUID recruiterId, String title, JobStatus status, OffsetDateTime expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        Job job = new Job()
              .setRecruiterId(recruiterId)
              .setTitle(title)
              .setDescription("Sample description")
              .setRequirements("Sample requirements")
              .setResponsibilities("Sample responsibilities")
              .setLocation("Casablanca")
              .setWorkModality(WorkModality.REMOTE)
              .setJobType(JobType.FULL_TIME)
              .setExperienceLevel(ExperienceLevel.SENIOR)
              .setSalaryMin(new BigDecimal("50000.00"))
              .setSalaryMax(new BigDecimal("80000.00"))
              .setCurrency("MAD")
              .setStatus(status)
              .setExpiresAt(expiresAt)
              .setCreatedAt(now)
              .setUpdatedAt(now);


        job.addSkills(List.of("Java", "Spring Boot"));
        return job;
    }

    @Test
    void findWithSkillsById_ShouldFetchSkillsGraphInSingleQuery() {
        Job job = createBaseJob(
              UUID.randomUUID(),
              "Senior Java Engineer",
              OPEN,
              OffsetDateTime.now(UTC).plusDays(10)
        );

        Job savedJob = jobRepository.saveAndFlush(job);

        entityManager.clear(); // Clear persistence context to test lazy initialization / graph fetching

        Optional<Job> foundJob = jobRepository.findWithSkillsById(savedJob.getId());

        assertThat(foundJob).isPresent();
        assertThat(Hibernate.isInitialized(foundJob.get().getSkills())).isTrue();
        assertThat(foundJob.get().getSkills()).hasSize(2);
    }

    @Test
    void findByRecruiterId_ShouldReturnPagedJobsForSpecificRecruiter() {
        UUID targetRecruiterId = UUID.randomUUID();
        UUID otherRecruiterId = UUID.randomUUID();

        jobRepository.save(createBaseJob(targetRecruiterId, "Job 1", OPEN, OffsetDateTime.now(UTC).plusDays(10)));
        jobRepository.save(createBaseJob(targetRecruiterId, "Job 2", OPEN, OffsetDateTime.now(UTC).plusDays(10)));
        jobRepository.save(createBaseJob(otherRecruiterId, "Other Job", OPEN, OffsetDateTime.now(UTC).plusDays(10)));

        Pageable pageable = PageRequest.of(0, 10);
        Page<Job> page = jobRepository.findByRecruiterId(targetRecruiterId, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Job::getTitle).containsExactlyInAnyOrder("Job 1", "Job 2");
    }

    @Test
    void findByIdIn_ShouldReturnProjectionsForGivenJobIds() {
        UUID recruiterId = UUID.randomUUID();
        Job job1 = jobRepository.save(createBaseJob(recruiterId, "Job 1", OPEN, OffsetDateTime.now(UTC).plusDays(10)));
        Job job2 = jobRepository.save(createBaseJob(recruiterId, "Job 2", DRAFT, OffsetDateTime.now(UTC).plusDays(10)));

        List<JobSummaryProjection> projections = jobRepository.findByIdIn(Set.of(job1.getId(), job2.getId()));

        assertThat(projections).hasSize(2);
        assertThat(projections).extracting(JobSummaryProjection::getId).containsExactlyInAnyOrder(job1.getId(), job2.getId());
        assertThat(projections).extracting(JobSummaryProjection::getTitle).containsExactlyInAnyOrder("Job 1", "Job 2");
    }

    @Test
    void findExpiredJobs_ShouldReturnOnlyOpenJobsWithExpiresAtBeforeNow() {
        UUID recruiterId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(UTC);

        // Expired & OPEN -> Matches
        Job expiredOpen = jobRepository.save(createBaseJob(recruiterId, "Expired Open", OPEN, now.minusDays(1)));
        // Expired & CLOSED -> Ignored (status != OPEN)
        jobRepository.save(createBaseJob(recruiterId, "Expired Closed", JobStatus.CLOSED, now.minusDays(1)));
        // Not Expired & OPEN -> Ignored (expiresAt > now)
        jobRepository.save(createBaseJob(recruiterId, "Active Open", OPEN, now.plusDays(5)));

        List<Job> expiredJobs = jobRepository.findExpiredJobs(now);

        assertThat(expiredJobs).hasSize(1);
        assertThat(expiredJobs.get(0).getId()).isEqualTo(expiredOpen.getId());
        assertThat(expiredJobs.get(0).getTitle()).isEqualTo("Expired Open");
    }

    @Test
    void findJobProjectionById_ShouldReturnProjectionWhenJobExists() {
        UUID recruiterId = UUID.randomUUID();
        Job job = jobRepository.save(createBaseJob(recruiterId, "Lead Architect", OPEN, OffsetDateTime.now(UTC).plusDays(10)));

        Optional<JobSummaryProjection> projection = jobRepository.findJobProjectionById(job.getId());

        assertThat(projection).isPresent();
        assertThat(projection.get().getId()).isEqualTo(job.getId());
        assertThat(projection.get().getRecruiterId()).isEqualTo(recruiterId);
        assertThat(projection.get().getTitle()).isEqualTo("Lead Architect");
        assertThat(projection.get().getStatus()).isEqualTo(OPEN);
    }
}