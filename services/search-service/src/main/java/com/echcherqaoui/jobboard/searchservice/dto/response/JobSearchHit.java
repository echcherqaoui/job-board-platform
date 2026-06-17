package com.echcherqaoui.jobboard.searchservice.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record JobSearchHit(
        String id,
        String recruiterId,
        String companyName,
        String companyLogo,
        String title,
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
        Instant expiresAt,
        float score,
        Map<String, List<String>> highlights
) {}
