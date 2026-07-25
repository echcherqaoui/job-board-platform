package com.echcherqaoui.jobboard.userservice.projection;

import java.util.UUID;

/**
 * Projection for {@link com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile}
 */
public interface UserEmailProjection {
    UUID getId();

    String getEmail();
}