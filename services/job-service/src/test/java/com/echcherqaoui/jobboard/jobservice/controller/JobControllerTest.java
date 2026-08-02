package com.echcherqaoui.jobboard.jobservice.controller;

import com.echcherqaoui.jobboard.exception.handler.GlobalExceptionHandler;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobStatusUpdateRequest;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobResponse;
import com.echcherqaoui.jobboard.jobservice.exception.domain.JobExpiredException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.JobNotFoundException;
import com.echcherqaoui.jobboard.jobservice.exception.domain.UnauthorizedJobAccessException;
import com.echcherqaoui.jobboard.jobservice.exception.enums.JobErrorCode;
import com.echcherqaoui.jobboard.jobservice.model.ExperienceLevel;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import com.echcherqaoui.jobboard.jobservice.model.JobType;
import com.echcherqaoui.jobboard.jobservice.model.WorkModality;
import com.echcherqaoui.jobboard.jobservice.service.JobService;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobController.class)
@Import(GlobalExceptionHandler.class)
@ImportAutoConfiguration(exclude = {
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class,
      OAuth2ClientAutoConfiguration.class,
      OAuth2ResourceServerAutoConfiguration.class,
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class
})
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobService jobService;

    private final UUID jobId = UUID.randomUUID();

    private JobResponse mockJobResponse() {
        return Mockito.mock(JobResponse.class);
    }

    private JobRequest validJobRequest() {
        return new JobRequest(
              "Backend Engineer",
              "We are looking for a backend engineer",
              "5 years Java experience",
              "Design and implement APIs",
              "Casablanca",
              WorkModality.REMOTE,
              JobType.FULL_TIME,
              ExperienceLevel.MID,
              new BigDecimal("50000.00"),
              new BigDecimal("80000.00"),
              "USD",
              null,
              List.of("Java", "Spring")
        );
    }

    // ---------- POST /jobs ----------

    @Test
    void postJob_returns201_withCreatedJob() throws Exception {
        JobRequest request = validJobRequest();
        when(jobService.postJob(any(JobRequest.class))).thenReturn(mockJobResponse());

        mockMvc.perform(post("/api/v1/jobs")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isCreated());
    }

    @Test
    void postJob_returns400_whenRequestBodyInvalid() throws Exception {
        // Sending an empty JSON object - relies on JobRequest's own @NotBlank/@Valid
        // constraints (not seen yet) to trigger MethodArgumentNotValidException.
        mockMvc.perform(post("/api/v1/jobs")
                    .contentType("application/json")
                    .content("{}"))
              .andExpect(status().isBadRequest());
    }

    @Test
    void searchJobs_returns200_withPaginatedResults() throws Exception {
        PaginatedResponse<?> response = org.mockito.Mockito.mock(PaginatedResponse.class);
        when(jobService.searchJobs(any(), any(Pageable.class))).thenReturn((PaginatedResponse) response);

        mockMvc.perform(get("/api/v1/jobs").param("keyword", "java"))
              .andExpect(status().isOk());
    }

    @Test
    void searchJobs_appliesDefaultPageableFromAnnotation() throws Exception {
        PaginatedResponse<?> response = Mockito.mock(PaginatedResponse.class);
        when(jobService.searchJobs(any(), any(Pageable.class))).thenReturn((PaginatedResponse) response);

        mockMvc.perform(get("/api/v1/jobs"))
              .andExpect(status().isOk());

        org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(jobService).searchJobs(any(), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
    }

    @Test
    void getMyJobs_returns200() throws Exception {
        PaginatedResponse<?> response = org.mockito.Mockito.mock(PaginatedResponse.class);
        when(jobService.getMyJobs(any(Pageable.class))).thenReturn((PaginatedResponse) response);

        mockMvc.perform(get("/api/v1/jobs/my"))
              .andExpect(status().isOk());
    }

    @Test
    void getJobById_returns200_whenFound() throws Exception {
        when(jobService.getJobById(jobId)).thenReturn(mockJobResponse());

        mockMvc.perform(get("/api/v1/jobs/" + jobId))
              .andExpect(status().isOk());
    }

    @Test
    void getJobById_returns404_whenJobNotFound() throws Exception {
        when(jobService.getJobById(jobId)).thenThrow(new JobNotFoundException(jobId));

        mockMvc.perform(get("/api/v1/jobs/" + jobId))
              .andExpect(status().isNotFound());
    }

    @Test
    void updateJob_returns200_onHappyPath() throws Exception {
        JobRequest request = validJobRequest();
        when(jobService.updateJob(eq(jobId), any(JobRequest.class))).thenReturn(mockJobResponse());

        mockMvc.perform(put("/api/v1/jobs/" + jobId)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isOk());
    }

    @Test
    void updateJob_returns403_whenNotOwner() throws Exception {
        JobRequest request = validJobRequest();
        when(jobService.updateJob(eq(jobId), any(JobRequest.class)))
              .thenThrow(new UnauthorizedJobAccessException(
                    JobErrorCode.COMPANY_DOES_NOT_OWN_JOB,
                    UUID.randomUUID(), jobId
              ));

        mockMvc.perform(put("/api/v1/jobs/" + jobId)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isForbidden());
    }

    @Test
    void updateJobStatus_returns200_onHappyPath() throws Exception {
        JobStatusUpdateRequest request = new JobStatusUpdateRequest(JobStatus.CLOSED);
        when(jobService.updateJobStatus(eq(jobId), any(JobStatusUpdateRequest.class))).thenReturn(mockJobResponse());

        mockMvc.perform(patch("/api/v1/jobs/" + jobId + "/status")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isOk());
    }

    @Test
    void updateJobStatus_returns409or400_whenJobExpired() throws Exception {
        JobStatusUpdateRequest request = new JobStatusUpdateRequest(JobStatus.CLOSED);
        when(jobService.updateJobStatus(eq(jobId), any(JobStatusUpdateRequest.class)))
              .thenThrow(new JobExpiredException(jobId));

        mockMvc.perform(patch("/api/v1/jobs/" + jobId + "/status")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().is4xxClientError());
    }

    @Test
    void deleteJob_returns204_onHappyPath() throws Exception {
        mockMvc.perform(delete("/api/v1/jobs/" + jobId))
              .andExpect(status().isNoContent());

        verify(jobService).deleteJob(jobId);
    }

    @Test
    void deleteJob_returns403_whenNotOwner() throws Exception {
        Mockito.doThrow(new UnauthorizedJobAccessException(
                    JobErrorCode.COMPANY_DOES_NOT_OWN_JOB,
                    UUID.randomUUID(), jobId
              ))
              .when(jobService).deleteJob(jobId);

        mockMvc.perform(delete("/api/v1/jobs/" + jobId))
              .andExpect(status().isForbidden());
    }
}