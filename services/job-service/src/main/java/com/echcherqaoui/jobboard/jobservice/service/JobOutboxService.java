package com.echcherqaoui.jobboard.jobservice.service;

import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;

public interface JobOutboxService {
    void publishJobCreated(Job job, CompanyProfile companyProfile);

    void publishJobUpdated(Job job, CompanyProfile companyProfile);

    void publishJobStatusChanged(Job job);

    void publishJobDeleted(Job job);
}
