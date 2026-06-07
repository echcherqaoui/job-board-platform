package com.echcherqaoui.jobboard.jobservice.dto.response;


import com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import com.echcherqaoui.jobboard.jobservice.model.JobType;
import com.echcherqaoui.jobboard.jobservice.model.WorkModality;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record JobSummaryResponse(
        UUID id,
        UUID recruiterId,
        String companyName,
        String companyLogo,
        String title,
        String location,
        WorkModality workModality,
        JobType jobType,
        ExperienceLevel experienceLevel,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        JobStatus status,
        List<String> skills,
        OffsetDateTime createdAt
) {}
