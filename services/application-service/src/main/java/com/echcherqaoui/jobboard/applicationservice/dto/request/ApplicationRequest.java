package com.echcherqaoui.jobboard.applicationservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApplicationRequest(
      @NotNull(message = "Job ID is required")
      UUID jobId,

      @NotBlank(message = "CV URL is required")
      String cvUrl,

      String coverLetter) {
}
