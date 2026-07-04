package com.echcherqaoui.jobboard.applicationservice.dto.response;

import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ApplicantApplicationDetailResponse(UUID id,
                                                 UUID jobId,
                                                 String jobTitle,
                                                 String companyName,
                                                 UUID applicantId,
                                                 String applicantName,
                                                 String applicantHeadline,
                                                 String applicantCvUrl,
                                                 ApplicationStatus status,
                                                 String coverLetter,
                                                 List<StatusHistoryResponse> statusHistory,
                                                 OffsetDateTime submittedAt) {}