package com.echcherqaoui.jobboard.searchservice.dto.response;

import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.List;

public record JobDTO(String id,
                     String recruiterId,
                     String companyName,
                     String companyLogo,
                     String title,
                     String description,
                     String requirements,
                     String location,
                     String workModality,
                     String jobType,
                     String experienceLevel,
                     String status,
                     Double salaryMin,
                     Double salaryMax,
                     String currency,
                     List<String> skills,
                     Instant createdAt,
                     Instant expiresAt) {

    @NonNull
    public static JobDTO from(@NonNull JobDocument doc) {
        return new JobDTO(
              doc.getId(),
              doc.getRecruiterId(),
              doc.getCompanyName(),
              doc.getCompanyLogo(),
              doc.getTitle(),
              doc.getDescription(),
              doc.getRequirements(),
              doc.getLocation(),
              doc.getWorkModality(),
              doc.getJobType(),
              doc.getExperienceLevel(),
              doc.getStatus(),
              doc.getSalaryMin(),
              doc.getSalaryMax(),
              doc.getCurrency(),
              doc.getSkills(),
              doc.getCreatedAt(),
              doc.getExpiresAt()
        );
    }
}