package com.echcherqaoui.jobboard.applicationservice.dto.response;

import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ApplicationSummaryResponse(UUID id,
                                         UUID jobId,
                                         String jobTitle,
                                         String companyName,
                                         UUID applicantId,
                                         ApplicationStatus status,
                                         OffsetDateTime submittedAt) {
}
