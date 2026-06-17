package com.echcherqaoui.jobboard.searchservice.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record JobSearchRequest(
      String keyword,
      String companyName,
      String location,
      List<String> workModalities,
      List<String> jobTypes,
      List<String> experienceLevels,
      List<String> skills,
      @Positive Double salaryMin,
      @Positive Double salaryMax,
      String status,                 // defaults to OPEN if null
      @Min(0) Integer page,
      @Min(1) @Max(100) Integer size,
      String sortBy,                 // "relevance" | "createdAt" | "salaryMin" | "salaryMax"
      String sortDirection) {
    public JobSearchRequest {
        if (status == null) status = "OPEN";
        if (sortBy == null) sortBy = "relevance";
        if (sortDirection == null) sortDirection = "desc";
        if (page == null) page = 0;
        if (size == null) size = 20;
    }
}
