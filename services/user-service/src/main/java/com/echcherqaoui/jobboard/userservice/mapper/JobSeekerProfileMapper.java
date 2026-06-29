package com.echcherqaoui.jobboard.userservice.mapper;

import com.echcherqaoui.jobboard.userservice.dto.request.EducationRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.ExperienceRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.SkillRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.EducationResponse;
import com.echcherqaoui.jobboard.userservice.dto.response.ExperienceResponse;
import com.echcherqaoui.jobboard.userservice.dto.response.JobSeekerProfileResponse;
import com.echcherqaoui.jobboard.userservice.dto.response.SkillResponse;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerEducation;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerExperience;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerSkill;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import static org.mapstruct.NullValuePropertyMappingStrategy.IGNORE;

@Mapper(componentModel = "spring")
public interface JobSeekerProfileMapper {

    // Entity to Response ────────────────────────────────────────
    JobSeekerProfileResponse toResponse(JobSeekerProfile profile);

    SkillResponse toResponse(JobSeekerSkill skill);

    ExperienceResponse toResponse(JobSeekerExperience experience);

    EducationResponse toResponse(JobSeekerEducation education);

    // Request to Entity ────────────────────────────────────────
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    JobSeekerSkill toSkillEntity(SkillRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    JobSeekerExperience toExperienceEntity(ExperienceRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    JobSeekerEducation toEducationEntity(EducationRequest request);

    // ── Update: merge request into existing entity ────────────────
    @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "firstName", ignore = true)
    @Mapping(target = "lastName", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "cvUrl", ignore = true)
    @Mapping(target = "cvPublicId", ignore = true)
    @Mapping(target = "profilePicture", ignore = true)
    @Mapping(target = "skills", ignore = true)
    @Mapping(target = "experiences", ignore = true)
    @Mapping(target = "educations", ignore = true)
    @Mapping(target = "onboardingCompleted", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateProfileFromRequest(JobSeekerProfileRequest request, @MappingTarget JobSeekerProfile existingProfile);

    @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    void updateJobSeekerSkill(SkillRequest request, @MappingTarget JobSeekerSkill existingSkill);

    @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    void updateJobSeekerExperience(ExperienceRequest request, @MappingTarget JobSeekerExperience existingExperience);

    @BeanMapping(nullValuePropertyMappingStrategy = IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "profile", ignore = true)
    void updateJobSeekerEducation(EducationRequest request, @MappingTarget JobSeekerEducation existingEducation);
}