package com.echcherqaoui.jobboard.applicationservice.service;

import com.echcherqaoui.jobboard.applicationservice.dto.request.ApplicationRequest;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatusHistory;
import com.echcherqaoui.jobboard.applicationservice.repository.ApplicationRepository;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.CANCELED;
import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.PENDING;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationDataAccess {

    private final ApplicationRepository applicationRepository;
    private final ApplicationOutboxService applicationOutboxService;

    @Transactional
    public Application saveApplication(UUID applicantId,
                                       @NonNull ApplicationRequest request,
                                       @NonNull JobSummary jobSummary) {
        Application application = new Application()
              .setJobId(request.jobId())
              .setApplicantId(applicantId)
              .setCvUrl(request.cvUrl())
              .setCoverLetter(request.coverLetter());

        ApplicationStatusHistory initialHistory = new ApplicationStatusHistory()
              .setApplication(application)
              .setOldStatus(null)
              .setNewStatus(PENDING)
              .setChangedBy(applicantId)
              .setNote("Application submitted");

        application.getStatusHistory().add(initialHistory);

        Application savedApplication = applicationRepository.save(application);

        applicationOutboxService.publishApplicationSubmitted(
              savedApplication,
              jobSummary
        );

        return savedApplication;
    }

    @Transactional
    public Application updateApplicationStatus(@NonNull Application application,
                                               @NonNull JobSummary jobSummary,
                                               ApplicationStatus newStatus,
                                               UUID callerId,
                                               String note) {
        ApplicationStatus oldStatus = application.getStatus();
        application.setStatus(newStatus);

        ApplicationStatusHistory history = new ApplicationStatusHistory()
              .setApplication(application)
              .setOldStatus(oldStatus)
              .setNewStatus(newStatus)
              .setChangedBy(callerId)
              .setNote(note);

        application.getStatusHistory().add(history);

        applicationOutboxService.publishApplicationStatusUpdated(
              application,
              oldStatus,
              newStatus,
              jobSummary,
              callerId,
              note
        );

        return applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyApplied(UUID jobId, UUID applicantId) {
        return applicationRepository.existsByJobIdAndApplicantId(jobId, applicantId);
    }

    @Transactional(readOnly = true)
    public Optional<Application> findWithHistoryById(UUID applicationId) {
        return applicationRepository.findWithHistoryById(applicationId);
    }

    @Transactional(readOnly = true)
    public Page<Application> findByApplicantId(UUID applicantId, Pageable pageable) {
        return applicationRepository.findByApplicantId(applicantId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Application> findByJobIdAndStatus(UUID jobId, ApplicationStatus status, Pageable pageable) {
        return (status != null)
              ? applicationRepository.findByJobIdAndStatus(jobId, status, pageable)
              : applicationRepository.findByJobId(jobId, pageable);
    }

    @Transactional
    public void bulkRejectAndExecute(UUID jobId, String jobTitle) {
        List<Application> pending = applicationRepository.findByJobIdAndStatus(jobId, PENDING);

        if (pending.isEmpty()) {
            log.info("No PENDING applications to reject for closed job {}", jobId);
            return;
        }

        pending.forEach(app -> {
            ApplicationStatus oldStatus = app.getStatus();
            app.setStatus(CANCELED);

            ApplicationStatusHistory history = new ApplicationStatusHistory()
                  .setApplication(app)
                  .setOldStatus(oldStatus)
                  .setNewStatus(CANCELED)
                  .setChangedBy(null)
                  .setNote("Job has been closed");

            app.getStatusHistory().add(history);
        });

        List<Application> saved = applicationRepository.saveAll(pending);

        List<String> applicantIds = saved.stream()
              .map(application -> application.getApplicantId().toString())
              .toList();
        
        applicationOutboxService.publishJobApplicationsCanceled(
              jobId,
              jobTitle,
              applicantIds
        );

        log.info("Rejected {} PENDING applications for closed job {}", saved.size(), jobId);
    }
}