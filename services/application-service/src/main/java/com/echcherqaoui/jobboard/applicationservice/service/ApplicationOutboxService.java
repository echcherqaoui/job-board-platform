package com.echcherqaoui.jobboard.applicationservice.service;

import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;

import java.util.UUID;

public interface ApplicationOutboxService {
    void publishApplicationSubmitted(Application application,
                                     JobSummary job);

    void publishApplicationStatusUpdated(Application application,
                                         ApplicationStatus oldStatus,
                                         ApplicationStatus newStatus,
                                         JobSummary job,
                                         UUID callerId,
                                         String note);

    void publishJobApplicationsCanceled(UUID jobId, int affectedCount);
}
