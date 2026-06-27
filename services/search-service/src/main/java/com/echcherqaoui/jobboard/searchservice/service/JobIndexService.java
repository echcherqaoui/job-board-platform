package com.echcherqaoui.jobboard.searchservice.service;

import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;

public interface JobIndexService {
    void upsertJob(JobUpsertedEvent event);

    void deleteJob(String jobId);

    void updateJobStatus(String jobId, String status);
}
