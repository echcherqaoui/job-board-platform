package com.echcherqaoui.jobboard.userservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record ExperienceRequest(
      UUID id,

      @NotBlank(message = "Company name is required")
      @Size(max = 200, message = "Company name cannot exceed 200 characters")
      String companyName,

      @NotBlank(message = "Job title is required")
      @Size(max = 200, message = "Job title cannot exceed 200 characters")
      String jobTitle,

      @Size(max = 200, message = "Location cannot exceed 200 characters")
      String location,

      @NotNull(message = "Start date is required")
      @PastOrPresent(message = "Start date cannot be in the future")
      LocalDate startDate,

      LocalDate endDate,

      boolean current,

      @Size(max = 3000, message = "Description cannot exceed 3000 characters")
      String description) {
}