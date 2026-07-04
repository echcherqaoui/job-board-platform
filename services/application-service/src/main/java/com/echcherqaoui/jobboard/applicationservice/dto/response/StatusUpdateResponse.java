package com.echcherqaoui.jobboard.applicationservice.dto.response;

import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StatusUpdateResponse(UUID applicationId,
                                   ApplicationStatus previousStatus,
                                   ApplicationStatus newStatus,
                                   OffsetDateTime changedAt,
                                   String changedBy) {
}