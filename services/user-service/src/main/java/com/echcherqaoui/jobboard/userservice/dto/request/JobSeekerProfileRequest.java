package com.echcherqaoui.jobboard.userservice.dto.request;

import com.echcherqaoui.jobboard.userservice.validation.OnboardingGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record JobSeekerProfileRequest(
      @NotBlank(groups = OnboardingGroup.class, message = "Phone number is required during onboarding")
      @Size(max = 30, message = "Phone number cannot exceed 30 characters") // Universal constraint
      String phone,

      @NotBlank(groups = OnboardingGroup.class, message = "Location is required during onboarding")
      @Size(max = 200, message = "Location cannot exceed 200 characters")
      String location,

      @Size(max = 300, message = "Headline cannot exceed 300 characters")
      String headline,

      @Size(max = 2000, message = "Bio cannot exceed 2000 characters")
      String bio,

      @URL(message = "Profile picture must be a valid URL")
      String profilePicture,

      @URL(message = "LinkedIn link must be a valid URL")
      String linkedinUrl,

      @URL(message = "GitHub link must be a valid URL")
      String githubUrl,

      @URL(message = "Portfolio link must be a valid URL")
      String portfolioUrl,

      @Min(value = 0, message = "Years of experience cannot be negative")
      @Max(value = 50, message = "Years of experience cannot exceed 50")
      Integer yearsExperience,

      List<@Valid SkillRequest> skills,

      List<@Valid ExperienceRequest> experiences,

      List<@Valid EducationRequest> educations) {
}