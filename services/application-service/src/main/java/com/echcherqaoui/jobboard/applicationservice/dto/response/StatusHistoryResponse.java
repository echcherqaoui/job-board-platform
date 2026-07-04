package com.echcherqaoui.jobboard.applicationservice.dto.response;

import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StatusHistoryResponse(UUID id,
                                    ApplicationStatus oldStatus,
                                    ApplicationStatus newStatus,
                                    UUID changedBy,
                                    String note,
                                    OffsetDateTime changedAt) {
}
