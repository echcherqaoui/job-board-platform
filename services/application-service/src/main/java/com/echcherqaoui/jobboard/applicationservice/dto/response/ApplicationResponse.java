package com.echcherqaoui.jobboard.applicationservice.dto.response;

import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApplicationResponse(UUID id,
                                  UUID jobId,
                                  String jobTitle,
                                  String companyName,
                                  UUID applicantId,
                                  ApplicationStatus status,
                                  String cvUrl,
                                  String coverLetter,
                                  List<StatusHistoryResponse> statusHistory,
                                  OffsetDateTime submittedAt) {
}