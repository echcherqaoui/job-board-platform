package com.echcherqaoui.jobboard.userservice.projection;

import java.util.UUID;

public interface JobSeekerSummaryProjection {
    UUID getId();
    String getFirstName();
    String getLastName();
    String getHeadline();
    String getCvUrl();
    String getEmail();
}