package com.echcherqaoui.jobboard.applicationservice.service;

import com.echcherqaoui.jobboard.applicationservice.dto.request.ApplicationRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.StatusUpdateRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicantApplicationDetailResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationCreationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationSummaryResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.JobApplicationPreview;
import com.echcherqaoui.jobboard.applicationservice.dto.response.StatusUpdateResponse;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ApplicationService {
    ApplicationCreationResponse submitApplication(ApplicationRequest request);

    PaginatedResponse<ApplicationSummaryResponse> getMyApplications(Pageable pageable);

    PaginatedResponse<JobApplicationPreview> getApplicationsForJob(UUID jobId,
                                                      ApplicationStatus status,
                                                      Pageable pageable);

    ApplicationResponse getApplicationById(UUID applicationId);

    ApplicantApplicationDetailResponse getApplicationForRecruiter(UUID applicationId);

    StatusUpdateResponse updateStatus(UUID applicationId,
                                      StatusUpdateRequest request);
}
