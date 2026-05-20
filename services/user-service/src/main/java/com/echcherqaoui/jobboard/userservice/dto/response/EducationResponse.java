package com.echcherqaoui.jobboard.userservice.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record EducationResponse(
      UUID id,
      String institution,
      String degree,
      String field,
      LocalDate startDate,
      LocalDate endDate,
      boolean current,
      String description) {
}