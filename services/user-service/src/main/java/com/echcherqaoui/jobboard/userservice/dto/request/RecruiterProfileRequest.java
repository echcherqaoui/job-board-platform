
package com.echcherqaoui.jobboard.userservice.dto.request;

import com.echcherqaoui.jobboard.userservice.enums.CompanySize;
import com.echcherqaoui.jobboard.userservice.validation.OnboardingGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record RecruiterProfileRequest(
      @NotBlank(groups = OnboardingGroup.class, message = "Company name is required")
      @Size(max = 100, message = "Company name cannot exceed 100 characters")
      String companyName,

      @NotNull(groups = OnboardingGroup.class, message = "Company size category is required")
      CompanySize companySize,

      @Size(max = 2000, message = "Company description cannot exceed 2000 characters")
      String companyDescription,

      @URL(message = "Company logo must be a valid URL")
      String companyLogoUrl,

      @URL(message = "Company website must be a valid URL")
      @Size(max = 255, message = "Website URL cannot exceed 255 characters")
      String companyWebsite,

      @Size(max = 200, message = "Company location cannot exceed 200 characters")
      String companyLocation
) {}