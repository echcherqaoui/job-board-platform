package com.echcherqaoui.jobboard.searchservice.mapper;

import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.echcherqaoui.jobboard.util.InstantConverter;
import com.echcherqaoui.jobboard.util.MoneyConverter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class JobMapper {
    public JobDocument toDocument(@NonNull JobUpsertedEvent event) {
        double salaryMin = MoneyConverter.fromCents(event.getSalaryMinCents(), 2).doubleValue();
        double salaryMax = MoneyConverter.fromCents(event.getSalaryMaxCents(), 2).doubleValue();

        return new JobDocument()
              .setId(event.getJobId())
              .setRecruiterId(event.getRecruiterId())
              .setCompanyName(event.getCompanyName())
              .setCompanyLogo(event.getCompanyLogo())
              .setTitle(event.getTitle())
              .setDescription(event.getDescription())
              .setRequirements(event.getRequirements())
              .setLocation(event.getLocation())
              .setWorkModality(event.getWorkModality())
              .setJobType(event.getJobType())
              .setExperienceLevel(event.getExperienceLevel())
              .setSalaryMin(salaryMin)
              .setSalaryMax(salaryMax)
              .setCurrency(event.getCurrency())
              .setStatus(event.getStatus())
              .setSkills(event.getSkillsList())
              .setCreatedAt(InstantConverter.toInstant(event.getCreatedAt()))
              .setExpiresAt(InstantConverter.toInstant(event.getExpiresAt()));
    }
}
