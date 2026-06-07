package com.echcherqaoui.jobboard.userservice.service;

import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;

public interface CompanyOutboxService {
    void publishCompanyCreated(RecruiterProfile profile);

    void publishCompanyUpdated(RecruiterProfile profile);

    void publishCompanyDeleted(RecruiterProfile profile);
}
