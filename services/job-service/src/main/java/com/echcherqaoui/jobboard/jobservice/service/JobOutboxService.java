package com.echcherqaoui.jobboard.jobservice.service;

import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;

import java.util.List;

public interface JobOutboxService {
    void publishJobUpserted(Job job, CompanyProfile companyProfile);


    void publishJobStatusChanged(Job job);

    void publishJobStatusChangedBatch(List<Job> jobs);

    void publishJobDeleted(Job job);
}
