package com.echcherqaoui.jobboard.applicationservice.dto.request;

import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(
      @NotNull(message = "Status is required")
      ApplicationStatus status,

      String note) {
}
