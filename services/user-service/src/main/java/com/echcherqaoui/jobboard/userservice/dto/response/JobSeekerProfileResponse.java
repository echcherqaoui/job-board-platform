package com.echcherqaoui.jobboard.userservice.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record JobSeekerProfileResponse(
      UUID id,
      String firstName,
      String lastName,
      String email,
      String phone,
      String location,
      String headline,
      String bio,
      String profilePicture,
      String cvUrl,
      String linkedinUrl,
      String githubUrl,
      String portfolioUrl,
      Integer yearsExperience,
      List<SkillResponse> skills,
      List<ExperienceResponse> experiences,
      List<EducationResponse> educations,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {
}