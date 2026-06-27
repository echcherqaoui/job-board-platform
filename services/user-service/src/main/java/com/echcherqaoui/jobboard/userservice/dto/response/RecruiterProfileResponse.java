package com.echcherqaoui.jobboard.userservice.dto.response;

import com.echcherqaoui.jobboard.userservice.enums.CompanySize;

import java.util.UUID;

public record RecruiterProfileResponse(
      UUID id,
      String firstName,
      String lastName,
      String email,
      String companyName,
      String companyDescription,
      String companyLogoUrl,
      String companyWebsite,
      String companyLocation,
      CompanySize companySize) {
}