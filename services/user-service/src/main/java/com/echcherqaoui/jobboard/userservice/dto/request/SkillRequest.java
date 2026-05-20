package com.echcherqaoui.jobboard.userservice.dto.request;

import com.echcherqaoui.jobboard.userservice.enums.SkillLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SkillRequest(
      UUID id,

      @NotBlank(message = "Skill name is required")
      @Size(max = 100, message = "Skill name cannot exceed 100 characters")
      String skillName,

      @NotNull(message = "Skill level is required")
      SkillLevel level) {
}