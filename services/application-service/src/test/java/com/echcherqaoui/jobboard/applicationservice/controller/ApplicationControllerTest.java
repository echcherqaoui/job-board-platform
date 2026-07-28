package com.echcherqaoui.jobboard.applicationservice.controller;

import com.echcherqaoui.jobboard.applicationservice.dto.request.ApplicationRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.request.StatusUpdateRequest;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationCreationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.ApplicationSummaryResponse;
import com.echcherqaoui.jobboard.applicationservice.dto.response.JobApplicationPreview;
import com.echcherqaoui.jobboard.applicationservice.dto.response.StatusUpdateResponse;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import com.echcherqaoui.jobboard.applicationservice.service.ApplicationService;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus.PENDING;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ApplicationController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = "api.base-path=/api/v1")

@ImportAutoConfiguration(
      exclude = {
            HibernateJpaAutoConfiguration.class,
            DataSourceAutoConfiguration.class,
            FlywayAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
      }
)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ApplicationService applicationService;

    private final UUID jobId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();

    @Test
    void submitApplication_validRequest_returns201WithBody() throws Exception {
        ApplicationRequest request = new ApplicationRequest(jobId, "http://cv.url", "cover letter");
        ApplicationCreationResponse response = new ApplicationCreationResponse(applicationId, PENDING, OffsetDateTime.now());
        when(applicationService.submitApplication(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/applications")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.id").value(applicationId.toString()))
              .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getMyApplications_returnsPagedResults() throws Exception {
        PaginatedResponse<ApplicationSummaryResponse> response =
              PaginatedResponse.of(new PageImpl<>(List.of()), x -> null);
        when(applicationService.getMyApplications(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/applications/my"))
              .andExpect(status().isOk());

        verify(applicationService).getMyApplications(any());
    }

    @Test
    void getApplicationsForJob_withStatusFilter_passesStatusThrough() throws Exception {
        PaginatedResponse<JobApplicationPreview> response =
              PaginatedResponse.of(new PageImpl<>(List.of()), x -> null);
        when(applicationService.getApplicationsForJob(eq(jobId), eq(ApplicationStatus.PENDING), any()))
              .thenReturn(response);

        mockMvc.perform(get("/api/v1/applications/job/{jobId}", jobId)
                    .param("status", "PENDING"))
              .andExpect(status().isOk());

        verify(applicationService).getApplicationsForJob(eq(jobId), eq(ApplicationStatus.PENDING), any());
    }

    @Test
    void getApplicationsForJob_withoutStatusFilter_passesNull() throws Exception {
        PaginatedResponse<JobApplicationPreview> response =
              PaginatedResponse.of(new PageImpl<>(List.of()), x -> null);
        when(applicationService.getApplicationsForJob(eq(jobId), isNull(), any()))
              .thenReturn(response);

        mockMvc.perform(get("/api/v1/applications/job/{jobId}", jobId))
              .andExpect(status().isOk());

        verify(applicationService).getApplicationsForJob(eq(jobId), isNull(), any());
    }

    @Test
    void getApplicationById_returnsBody() throws Exception {
        when(applicationService.getApplicationById(applicationId))
              .thenReturn(mock(ApplicationResponse.class));

        mockMvc.perform(get("/api/v1/applications/{id}", applicationId))
              .andExpect(status().isOk());

        verify(applicationService).getApplicationById(applicationId);
    }

    @Test
    void updateStatus_validRequest_returnsUpdatedResponse() throws Exception {
        StatusUpdateRequest request = new StatusUpdateRequest(ApplicationStatus.REVIEWED, "note");
        StatusUpdateResponse response = new StatusUpdateResponse(
              applicationId, PENDING, ApplicationStatus.REVIEWED, OffsetDateTime.now(), "recruiter-1"
        );
        when(applicationService.updateStatus(eq(applicationId), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/applications/{id}/status", applicationId)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.newStatus").value("REVIEWED"));
    }

}