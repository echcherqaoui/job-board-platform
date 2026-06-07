package com.echcherqaoui.jobboard.jobservice.dto.request;

import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import jakarta.validation.constraints.NotNull;

public record JobStatusUpdateRequest(
        @NotNull(message = "Status is required")
        JobStatus status
) {}
