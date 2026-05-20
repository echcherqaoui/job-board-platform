package com.echcherqaoui.jobboard.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record EducationRequest(
      UUID id,
      @NotBlank(message = "Institution is required")
      @Size(max = 200, message = "Institution name cannot exceed 200 characters")
      String institution,

      @NotBlank(message = "Degree is required")
      @Size(max = 200, message = "Degree cannot exceed 200 characters")
      String degree,

      @Size(max = 200, message = "Field of study cannot exceed 200 characters")
      String field,

      @NotNull(message = "Start date is required")
      @PastOrPresent(message = "Start date cannot be in the future")
      LocalDate startDate,

      LocalDate endDate,

      boolean current,

      @Size(max = 1000, message = "Description cannot exceed 1000 characters")
      String description) {
}