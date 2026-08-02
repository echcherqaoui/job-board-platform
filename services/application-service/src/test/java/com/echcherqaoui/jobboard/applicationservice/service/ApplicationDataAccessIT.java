package com.echcherqaoui.jobboard.applicationservice.service;

import com.echcherqaoui.jobboard.applicationservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.ApplicationRequest;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.repository.ApplicationRepository;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.CANCELED;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.PENDING;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.REVIEWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest
class ApplicationDataAccessIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationDataAccess applicationDataAccess;

    @Autowired
    private ApplicationRepository applicationRepository;

    @MockitoBean
    private ApplicationOutboxService applicationOutboxService;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
    }

    @Nested
    class SaveApplication {

        @Test
        void shouldPersistApplicationAndPublishOutboxEvent() {
            UUID applicantId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            ApplicationRequest request = new ApplicationRequest(jobId, "https://storage.com/cv.pdf", "Cover letter");
            JobSummary jobSummary = JobSummary.newBuilder().setJobId(jobId.toString()).setTitle("Software Engineer").build();

            Application saved = applicationDataAccess.saveApplication(applicantId, request, jobSummary);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getStatus()).isEqualTo(PENDING);
            assertThat(saved.getStatusHistory()).hasSize(1);
            assertThat(saved.getStatusHistory().get(0).getNote()).isEqualTo("Application submitted");

            verify(applicationOutboxService).publishApplicationSubmitted(any(Application.class), eq(jobSummary));
        }
    }

    @Nested
    class UpdateApplicationStatus {

        @Test
        void shouldUpdateStatusAppendHistoryAndPublishEvent() {
            UUID callerId = UUID.randomUUID();
            UUID jobId = UUID.randomUUID();

            Application application = applicationRepository.save(new Application()
                  .setApplicantId(UUID.randomUUID())
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv.pdf")
                  .setStatus(PENDING));

            JobSummary jobSummary = JobSummary.newBuilder().setJobId(jobId.toString()).build();

            Application updated = applicationDataAccess.updateApplicationStatus(
                  application, jobSummary, REVIEWED, callerId, "Interview scheduled"
            );

            assertThat(updated.getStatus()).isEqualTo(REVIEWED);
            assertThat(updated.getStatusHistory()).hasSize(1);
            assertThat(updated.getStatusHistory().get(0).getOldStatus()).isEqualTo(PENDING);
            assertThat(updated.getStatusHistory().get(0).getNewStatus()).isEqualTo(REVIEWED);

            verify(applicationOutboxService).publishApplicationStatusUpdated(
                  application, PENDING, REVIEWED, jobSummary, callerId, "Interview scheduled"
            );
        }
    }

    @Nested
    class BulkRejectAndExecute {

        @Test
        void shouldCancelOnlyPendingApplicationsAndPublishOutboxEvent() {
            UUID jobId = UUID.randomUUID();
            UUID applicant1 = UUID.randomUUID();
            UUID applicant2 = UUID.randomUUID();
            UUID applicant3 = UUID.randomUUID();

            Application pendingApp1 = applicationRepository.save(new Application()
                  .setApplicantId(applicant1)
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv1.pdf")
                  .setStatus(PENDING));

            Application pendingApp2 = applicationRepository.save(new Application()
                  .setApplicantId(applicant2)
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv2.pdf")
                  .setStatus(PENDING));

            Application reviewedApp = applicationRepository.save(new Application()
                  .setApplicantId(applicant3)
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv3.pdf")
                  .setStatus(REVIEWED));

            applicationDataAccess.bulkRejectAndExecute(jobId, "Senior Java Developer");

            Application reloadedPending1 = applicationRepository.findWithHistoryById(pendingApp1.getId()).orElseThrow();
            Application reloadedPending2 = applicationRepository.findWithHistoryById(pendingApp2.getId()).orElseThrow();
            Application reloadedReviewed = applicationRepository.findWithHistoryById(reviewedApp.getId()).orElseThrow();

            assertThat(reloadedPending1.getStatus()).isEqualTo(CANCELED);
            assertThat(reloadedPending1.getStatusHistory()).hasSize(1);
            assertThat(reloadedPending1.getStatusHistory().get(0).getNote()).isEqualTo("Job has been closed");

            assertThat(reloadedPending2.getStatus()).isEqualTo(CANCELED);
            assertThat(reloadedReviewed.getStatus()).isEqualTo(REVIEWED);

            verify(applicationOutboxService).publishJobApplicationsCanceled(
                  jobId,
                  "Senior Java Developer",
                  List.of(applicant1.toString(), applicant2.toString())
            );
        }

        @Test
        void shouldDoNothingWhenNoPendingApplicationsExist() {
            UUID jobId = UUID.randomUUID();

            applicationRepository.save(new Application()
                  .setApplicantId(UUID.randomUUID())
                  .setJobId(jobId)
                  .setCvUrl("https://storage.com/cv.pdf")
                  .setStatus(REVIEWED));

            applicationDataAccess.bulkRejectAndExecute(jobId, "Tech Lead");

            verifyNoInteractions(applicationOutboxService);
        }
    }
}