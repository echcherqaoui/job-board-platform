package com.echcherqaoui.jobboard.applicationservice.repository;

import com.echcherqaoui.jobboard.applicationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatusHistory;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApplicationRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private UUID sampleJobId1;
    private UUID sampleJobId2;
    private UUID sampleApplicantId1;
    private UUID sampleApplicantId2;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();

        sampleJobId1 = UUID.randomUUID();
        sampleJobId2 = UUID.randomUUID();
        sampleApplicantId1 = UUID.randomUUID();
        sampleApplicantId2 = UUID.randomUUID();
    }

    private Application createApplication(UUID jobId, UUID applicantId, ApplicationStatus status) {
        return new Application()
              .setJobId(jobId)
              .setApplicantId(applicantId)
              .setCvUrl("https://storage.jobboard.com/cvs/sample.pdf")
              .setCoverLetter("Sample cover letter text...")
              .setStatus(status)
              .setSubmittedAt(OffsetDateTime.now())
              .setUpdatedAt(OffsetDateTime.now());
    }

    @Nested
    class ExistsByJobIdAndApplicantIdTests {

        @Test
        @DisplayName("Should return true when record exists for given jobId and applicantId")
        void existsByJobIdAndApplicantId_ReturnsTrue_WhenExists() {
            Application application = createApplication(sampleJobId1, sampleApplicantId1, ApplicationStatus.PENDING);
            entityManager.persistAndFlush(application);

            boolean exists = applicationRepository.existsByJobIdAndApplicantId(sampleJobId1, sampleApplicantId1);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should return false when no match for jobId and applicantId combination")
        void existsByJobIdAndApplicantId_ReturnsFalse_WhenNoMatch() {
            Application application = createApplication(sampleJobId1, sampleApplicantId1, ApplicationStatus.PENDING);
            entityManager.persistAndFlush(application);

            boolean wrongJob = applicationRepository.existsByJobIdAndApplicantId(sampleJobId2, sampleApplicantId1);
            boolean wrongApplicant = applicationRepository.existsByJobIdAndApplicantId(sampleJobId1, sampleApplicantId2);

            assertThat(wrongJob).isFalse();
            assertThat(wrongApplicant).isFalse();
        }
    }

    @Nested
    class FindByApplicantIdTests {

        @Test
        @DisplayName("Should return paginated applications matching applicantId")
        void findByApplicantId_ReturnsPaginatedResults() {
            Application app1 = createApplication(sampleJobId1, sampleApplicantId1, ApplicationStatus.PENDING);
            Application app2 = createApplication(sampleJobId2, sampleApplicantId1, ApplicationStatus.ACCEPTED);
            Application otherApp = createApplication(sampleJobId1, sampleApplicantId2, ApplicationStatus.PENDING);

            entityManager.persist(app1);
            entityManager.persist(app2);
            entityManager.persist(otherApp);
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 10, Sort.by("submittedAt").descending());
            Page<Application> result = applicationRepository.findByApplicantId(sampleApplicantId1, pageable);

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                  .extracting(Application::getApplicantId)
                  .containsOnly(sampleApplicantId1);
        }

        @Test
        @DisplayName("Should return empty page when applicant has no applications")
        void findByApplicantId_ReturnsEmptyPage_WhenNoneFound() {
            Pageable pageable = PageRequest.of(0, 5);
            Page<Application> result = applicationRepository.findByApplicantId(UUID.randomUUID(), pageable);

            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    class FindWithHistoryByIdTests {

        @Test
        @DisplayName("Should fetch application along with eagerly initialized statusHistory")
        void findWithHistoryById_EagerlyFetchesStatusHistory() {
            Application application = createApplication(sampleJobId1, sampleApplicantId1, ApplicationStatus.REVIEWED);

            ApplicationStatusHistory history1 = new ApplicationStatusHistory()
                  .setApplication(application)
                  .setOldStatus(null)
                  .setNewStatus(ApplicationStatus.PENDING)
                  .setChangedBy(sampleApplicantId1)
                  .setChangedAt(OffsetDateTime.now().minusMinutes(10))
                  .setNote("Application submitted");

            ApplicationStatusHistory history2 = new ApplicationStatusHistory()
                  .setApplication(application)
                  .setOldStatus(ApplicationStatus.PENDING)
                  .setNewStatus(ApplicationStatus.REVIEWED)
                  .setChangedBy(UUID.randomUUID())
                  .setChangedAt(OffsetDateTime.now())
                  .setNote("Under HR review");

            application.getStatusHistory().add(history1);
            application.getStatusHistory().add(history2);

            Application savedApp = entityManager.persistAndFlush(application);

            // Clear L1 Persistence Context to enforce an actual DB round-trip
            entityManager.clear();

            Optional<Application> fetchedAppOptional = applicationRepository.findWithHistoryById(savedApp.getId());

            assertThat(fetchedAppOptional).isPresent();
            Application fetchedApp = fetchedAppOptional.get();

            // Verify @EntityGraph loaded statusHistory eagerly without LazyInitializationException
            assertThat(Hibernate.isInitialized(fetchedApp.getStatusHistory())).isTrue();
            assertThat(fetchedApp.getStatusHistory()).hasSize(2);
            assertThat(fetchedApp.getStatusHistory())
                  .extracting(ApplicationStatusHistory::getNewStatus)
                  .containsExactlyInAnyOrder(ApplicationStatus.PENDING, ApplicationStatus.REVIEWED);
        }

        @Test
        @DisplayName("Should return empty Optional when application ID does not exist")
        void findWithHistoryById_ReturnsEmpty_WhenNotFound() {
            Optional<Application> result = applicationRepository.findWithHistoryById(UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }


    @Nested
    class FindByJobIdTests {

        @Test
        @DisplayName("Should return paginated applications for specific jobId")
        void findByJobId_ReturnsPaginatedResults() {
            Application app1 = createApplication(sampleJobId1, sampleApplicantId1, ApplicationStatus.PENDING);
            Application app2 = createApplication(sampleJobId1, sampleApplicantId2, ApplicationStatus.REJECTED);
            Application otherJobApp = createApplication(sampleJobId2, sampleApplicantId1, ApplicationStatus.PENDING);

            entityManager.persist(app1);
            entityManager.persist(app2);
            entityManager.persist(otherJobApp);
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 10);
            Page<Application> result = applicationRepository.findByJobId(sampleJobId1, pageable);

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent())
                  .extracting(Application::getJobId)
                  .containsOnly(sampleJobId1);
        }
    }

    @Nested
    class FindByJobIdAndStatusPageableTests {

        @Test
        @DisplayName("Should return paged applications filtered by jobId and status")
        void findByJobIdAndStatus_Pageable_FiltersCorrectly() {
            Application pending1 = createApplication(sampleJobId1, sampleApplicantId1, ApplicationStatus.PENDING);
            Application pending2 = createApplication(sampleJobId1, sampleApplicantId2, ApplicationStatus.PENDING);
            Application accepted = createApplication(sampleJobId1, UUID.randomUUID(), ApplicationStatus.ACCEPTED);

            entityManager.persist(pending1);
            entityManager.persist(pending2);
            entityManager.persist(accepted);
            entityManager.flush();

            Pageable pageable = PageRequest.of(0, 1);
            Page<Application> result = applicationRepository.findByJobIdAndStatus(sampleJobId1, ApplicationStatus.PENDING, pageable);

            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getStatus()).isEqualTo(ApplicationStatus.PENDING);
        }
    }
    @Nested
    class FindByJobIdAndStatusListTests {

        @Test
        @DisplayName("Should return list of applications matching jobId and status")
        void findByJobIdAndStatus_List_FiltersCorrectly() {
            Application matching1 = createApplication(sampleJobId1, sampleApplicantId1, ApplicationStatus.ACCEPTED);
            Application matching2 = createApplication(sampleJobId1, sampleApplicantId2, ApplicationStatus.ACCEPTED);
            Application nonMatchingStatus = createApplication(sampleJobId1, UUID.randomUUID(), ApplicationStatus.REJECTED);
            Application nonMatchingJob = createApplication(sampleJobId2, sampleApplicantId1, ApplicationStatus.ACCEPTED);

            entityManager.persist(matching1);
            entityManager.persist(matching2);
            entityManager.persist(nonMatchingStatus);
            entityManager.persist(nonMatchingJob);
            entityManager.flush();

            List<Application> result = applicationRepository.findByJobIdAndStatus(sampleJobId1, ApplicationStatus.ACCEPTED);

            assertThat(result).hasSize(2);
            assertThat(result)
                  .extracting(Application::getApplicantId)
                  .containsExactlyInAnyOrder(sampleApplicantId1, sampleApplicantId2);
        }

        @Test
        @DisplayName("Should return empty list when no match is found")
        void findByJobIdAndStatus_List_ReturnsEmptyWhenNoMatch() {
            List<Application> result = applicationRepository.findByJobIdAndStatus(sampleJobId1, ApplicationStatus.ACCEPTED);

            assertThat(result).isEmpty();
        }
    }

}