package com.echcherqaoui.jobboard.userservice.controller;

import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.JobSeekerProfileResponse;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JobSeekerProfileController.class)
@AutoConfigureMockMvc
@ImportAutoConfiguration(exclude = {
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class,
      OAuth2ClientWebSecurityAutoConfiguration.class,
      OAuth2ResourceServerAutoConfiguration.class,
      DataSourceAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      FlywayAutoConfiguration.class
})
class JobSeekerProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JobSeekerProfileService service;

    private JobSeekerProfileRequest validOnboardRequest() {
        return new JobSeekerProfileRequest(
              "0612345678", "Casablanca", "headline", "bio",
              "http://pic.png", "http://linkedin.com/x", "http://github.com/x",
              "http://portfolio.com", 3, null, null, null);
    }

    @Test
    void onboard_validRequest_returns200() throws Exception {
        JobSeekerProfileRequest request = validOnboardRequest();

        mockMvc.perform(post("/api/v1/profiles/job-seekers/onboard")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isOk());

        verify(service).onboard(any());
    }

    @Test
    void getMyProfile_returnsServiceResponse() throws Exception {
        when(service.getMyProfile()).thenReturn(mock(JobSeekerProfileResponse.class));

        mockMvc.perform(get("/api/v1/profiles/job-seekers/me"))
              .andExpect(status().isOk());
    }

    @Test
    void updateProfile_validRequest_returns200() throws Exception {
        JobSeekerProfileRequest request = validOnboardRequest();
        when(service.updateProfile(any())).thenReturn(mock(JobSeekerProfileResponse.class));

        mockMvc.perform(put("/api/v1/profiles/job-seekers")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isOk());
    }

    @Test
    void deleteProfile_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/profiles/job-seekers"))
              .andExpect(status().isNoContent());

        verify(service).deleteProfile();
    }

    @Test
    void getProfileById_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.getProfileById(id)).thenReturn(mock(JobSeekerProfileResponse.class));

        mockMvc.perform(get("/api/v1/profiles/job-seekers/" + id))
              .andExpect(status().isOk());
    }

    @Test
    void uploadCv_returnsUrlInResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
              "file", "cv.pdf", "application/pdf", "dummy-pdf-content".getBytes());
        when(service.uploadCv(any())).thenReturn("http://cdn.example.com/cv.pdf");

        mockMvc.perform(multipart("/api/v1/profiles/job-seekers/cv").file(file))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.url").value("http://cdn.example.com/cv.pdf"));
    }

    @Test
    void deleteCv_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/profiles/job-seekers/cv"))
              .andExpect(status().isNoContent());

        verify(service).deleteCv();
    }
}