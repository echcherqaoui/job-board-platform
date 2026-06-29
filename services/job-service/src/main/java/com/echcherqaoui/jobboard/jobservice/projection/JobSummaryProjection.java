package com.echcherqaoui.jobboard.jobservice.projection;

import com.echcherqaoui.jobboard.jobservice.model.JobStatus;

import java.util.UUID;

public interface JobSummaryProjection {
    UUID getId();
    UUID getRecruiterId();
    String getTitle();
    JobStatus getStatus();
}