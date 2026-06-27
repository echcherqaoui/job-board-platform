package com.echcherqaoui.jobboard.userservice.service;

import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;

public interface CompanyOutboxService {
    void publishCompanyUpserted(RecruiterProfile profile);

    void publishCompanyDeleted(RecruiterProfile profile);
}
