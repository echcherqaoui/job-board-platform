package com.echcherqaoui.jobboard.applicationservice.mapper;

import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicantApplicationDetailResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationSummaryResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.JobApplicationPreview;
import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileDetail;
import com.echcherqaoui.jobboard.user.grpc.JobSeekerProfileSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ApplicationMapper {
    default String mapApplicantName(JobSeekerProfileDetail profile) {
        if (profile == null) return "";
        return (profile.getFirstName() + " " + profile.getLastName()).trim();
    }

    default String mapApplicantSummaryName(JobSeekerProfileSummary profile) {
        if (profile == null) return "";
        return (profile.getFirstName() + " " + profile.getLastName()).trim();
    }

    @Mapping(target = "jobId", source = "application.jobId")
    @Mapping(target = "jobTitle", source = "job.title")
    @Mapping(target = "statusHistory", source = "application.statusHistory")
    @Mapping(target = "companyName", source = "job.companyName")
    @Mapping(target = "submittedAt", source = "application.submittedAt")
    ApplicationResponse toApplicationResponse(Application application, JobSummary job);

    @Mapping(target = "jobId", source = "application.jobId")
    @Mapping(target = "jobTitle", source = "job.title")
    @Mapping(target = "companyName", source = "job.companyName")
    @Mapping(target = "applicantName", source = "profile")
    @Mapping(target = "applicantHeadline", source = "profile.headline")
    @Mapping(target = "applicantCvUrl", source = "profile.cvUrl")
    @Mapping(target = "statusHistory", source = "application.statusHistory")
    @Mapping(target = "submittedAt", source = "application.submittedAt")
    ApplicantApplicationDetailResponse toRecruiterDetailResponse(Application application,
                                                                 JobSummary job,
                                                                 JobSeekerProfileDetail profile);

    @Mapping(target = "jobId", source = "application.jobId")
    @Mapping(target = "jobTitle", source = "job.title")
    @Mapping(target = "companyName", source = "job.companyName")
    @Mapping(target = "submittedAt", source = "application.submittedAt")
    ApplicationSummaryResponse toSummaryResponse(Application application, JobSummary job);

    @Mapping(target = "applicantId", source = "application.applicantId")
    @Mapping(target = "applicantName", source = "profile")
    @Mapping(target = "applicantHeadline", source = "profile.headline")
    @Mapping(target = "applicantCvUrl", source = "profile.cvUrl")
    @Mapping(target = "submittedAt", source = "application.submittedAt")
    JobApplicationPreview toApplicationPreview(Application application, JobSeekerProfileSummary profile);
}
