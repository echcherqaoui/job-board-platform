package com.echcherqaoui.jobboard.jobservice.mapper;

import com.echcherqaoui.jobboard.job.grpc.JobSummary;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class JobGrpcMapper {

    @NonNull
    private String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    public JobSummary toGrpcSummary(@NonNull JobSummaryProjection jobProjection,
                                    String companyName) {
        return JobSummary.newBuilder()
                .setJobId(jobProjection.getId().toString())
                .setRecruiterId(jobProjection.getRecruiterId().toString())
                .setCompanyName(nullToEmpty(companyName))
                .setTitle(nullToEmpty(jobProjection.getTitle()))
                .setJobStatus(jobProjection.getStatus().name())
                .build();
    }
}