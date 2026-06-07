package com.echcherqaoui.jobboard.jobservice.mapper;

import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobResponse;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobSummaryResponse;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.model.JobSkill;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

import static org.mapstruct.NullValuePropertyMappingStrategy.IGNORE;

@Mapper(componentModel = "spring")
public interface JobMapper {
    default List<String> mapSkills(List<JobSkill> skills) {
        if (skills == null) return List.of();
        return skills.stream()
              .map(JobSkill::getSkill)
              .toList();
    }

    // Entity to Response ────────────────────────────────────
    @Mapping(target = "companyName", ignore = true)   // enriched in service
    @Mapping(target = "companyLogo", ignore = true)   // enriched in service
    JobResponse toResponse(Job job);

    @Mapping(target = "companyName", source = "profile.companyName")
    @Mapping(target = "companyLogo", source = "profile.companyLogo")
    @Mapping(target = "recruiterId", source = "job.recruiterId")
    @Mapping(target = "updatedAt", source = "job.updatedAt")
    JobResponse toResponse(Job job,  CompanyProfile profile);

    @Mapping(target = "companyName", ignore = true)
    @Mapping(target = "companyLogo", ignore = true)
    JobSummaryResponse toSummaryResponse(Job job);

    @Mapping(target = "companyName", source = "profile.companyName")
    @Mapping(target = "companyLogo", source = "profile.companyLogo")
    @Mapping(target = "recruiterId", source = "job.recruiterId")
    JobSummaryResponse toSummaryResponse(Job job, CompanyProfile profile);

    // Request to Entity ──────────────────────────────────────────
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recruiterId", ignore = true)
    @Mapping(target = "status", ignore = true)   // default DRAFT
    @Mapping(target = "skills", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Job toJobEntity(JobRequest request);

    // ── Update: merge request into existing entity ─────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recruiterId", ignore = true)
    @Mapping(target = "status", ignore = true)   // use dedicated status endpoint
    @Mapping(target = "skills", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(JobRequest request, @MappingTarget Job job);
}
