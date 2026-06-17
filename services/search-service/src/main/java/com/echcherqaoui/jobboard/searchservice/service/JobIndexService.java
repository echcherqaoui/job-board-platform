package com.echcherqaoui.jobboard.searchservice.service;

import com.echcherqaoui.jobboard.event.JobEvent;

public interface JobIndexService {
    void upsertJob(JobEvent event);

    void deleteJob(String jobId);

    void updateJobStatus(String jobId, String status);
}
