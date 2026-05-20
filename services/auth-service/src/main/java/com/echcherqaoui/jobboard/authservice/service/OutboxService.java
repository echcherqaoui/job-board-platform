package com.echcherqaoui.jobboard.authservice.service;

import com.echcherqaoui.jobboard.authservice.model.AppUser;

public interface OutboxService {
    void publishJobSeekerCreated(AppUser user);

    void publishRecruiterCreated(AppUser user);
}
