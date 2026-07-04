package com.echcherqaoui.jobboard.applicationservice.dto.response;

import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobApplicationPreview(UUID id,
                                    UUID applicantId,
                                    String applicantName,
                                    String applicantHeadline,
                                    String applicantCvUrl,
                                    ApplicationStatus status,
                                    OffsetDateTime submittedAt) {
}