package com.echcherqaoui.jobboard.userservice.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record ExperienceResponse(
      UUID id,
      String companyName,
      String jobTitle,
      String location,
      LocalDate startDate,
      LocalDate endDate,
      boolean current,
      String description) {
}