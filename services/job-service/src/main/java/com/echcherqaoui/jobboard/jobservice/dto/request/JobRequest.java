package com.echcherqaoui.jobboard.jobservice.dto.request;

import com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel;
import com.echcherqaoui.jobboard.jobservice.model.JobType;
import com.echcherqaoui.jobboard.jobservice.model.WorkModality;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record JobRequest(

      @NotBlank(message = "Title is required")
      @Size(max = 300)
      String title,

      @NotBlank(message = "Description is required")
      String description,

      String requirements,

      String responsibilities,

      @Size(max = 200)
      String location,

      WorkModality workModality,

      @NotNull(message = "Job type is required")
      JobType jobType,

      @NotNull(message = "Experience level is required")
      ExperienceLevel experienceLevel,

      @DecimalMin(value = "0.0", inclusive = false)
      BigDecimal salaryMin,

      @DecimalMin(value = "0.0", inclusive = false)
      BigDecimal salaryMax,

      @Size(max = 10)
      String currency,

      OffsetDateTime expiresAt,

      List<@NotBlank String> skills) {
}
