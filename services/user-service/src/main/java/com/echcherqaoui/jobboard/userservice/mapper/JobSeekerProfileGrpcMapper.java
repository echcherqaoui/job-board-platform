package com.echcherqaoui.jobboard.userservice.mapper;

import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileDetail;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerSkill;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class JobSeekerProfileGrpcMapper {

    @NonNull
    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    public JobSeekerProfileDetail toGrpcDetail(@NonNull JobSeekerProfile jobSeeker) {
        List<String> skills = jobSeeker.getSkills().stream().map(JobSeekerSkill::getSkillName).toList();

        return JobSeekerProfileDetail.newBuilder()
              .setUserId(jobSeeker.getId().toString())
              .setFirstName(jobSeeker.getFirstName())
              .setLastName(jobSeeker.getLastName())
              .setEmail(jobSeeker.getEmail())
              .setHeadline(nullToEmpty(jobSeeker.getHeadline()))
              .setLocation(nullToEmpty(jobSeeker.getLocation()))
              .setCvUrl(nullToEmpty(jobSeeker.getCvUrl()))
              .setYearsExperience(jobSeeker.getYearsExperience() != null ? jobSeeker.getYearsExperience() : 0)
              .addAllSkills(skills)
              .build();
    }

    public JobSeekerProfileSummary toGrpcSummary(@NonNull JobSeekerSummaryProjection entity) {
        return JobSeekerProfileSummary.newBuilder()
              .setUserId(entity.getId().toString())
              .setFirstName(entity.getFirstName())
              .setLastName(entity.getLastName())
              .setHeadline(nullToEmpty(entity.getHeadline()))
              .setCvUrl(nullToEmpty(entity.getCvUrl()))
              .setEmail(entity.getEmail())
              .build();
    }
}