package com.echcherqaoui.jobboard.jobservice.dto.request;

import com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import com.echcherqaoui.jobboard.jobservice.model.JobType;
import com.echcherqaoui.jobboard.jobservice.model.WorkModality;

import java.math.BigDecimal;

public record JobSearchCriteria(String keyword,
                                String location,
                                WorkModality workModality,
                                JobType jobType,
                                ExperienceLevel experienceLevel,
                                BigDecimal salaryMin,
                                JobStatus status) {
}
