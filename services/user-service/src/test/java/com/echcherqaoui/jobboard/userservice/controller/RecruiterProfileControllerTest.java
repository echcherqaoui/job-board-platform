package com.echcherqaoui.jobboard.userservice.controller;

import com.echcherqaoui.jobboard.userservice.dto.request.RecruiterProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.RecruiterProfileResponse;
import com.echcherqaoui.jobboard.userservice.enums.CompanySize;
import com.echcherqaoui.jobboard.userservice.service.RecruiterProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RecruiterProfileController.class)
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
class RecruiterProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecruiterProfileService recruiterProfileService;

    private RecruiterProfileRequest validOnboardRequest() {
        return new RecruiterProfileRequest(
              "Acme Corp", CompanySize.MEDIUM, "desc", "http://logo.png",
              "http://acme.com", "NYC");
    }

    @Test
    void onboard_validRequest_returns200() throws Exception {
        RecruiterProfileRequest request = validOnboardRequest();

        mockMvc.perform(post("/api/v1/profiles/recruiters/onboard")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isOk());
    }

    @Test
    void getMe_returnsServiceResponse() throws Exception {
        RecruiterProfileResponse response = Mockito.mock(RecruiterProfileResponse.class);
        when(recruiterProfileService.getMe()).thenReturn(response);

        mockMvc.perform(get("/api/v1/profiles/recruiters/me"))
              .andExpect(status().isOk());
    }

    @Test
    void updateRecruiterProfile_validRequest_returns200() throws Exception {
        RecruiterProfileRequest request = validOnboardRequest();
        when(recruiterProfileService.update(any())).thenReturn(Mockito.mock(RecruiterProfileResponse.class));

        mockMvc.perform(put("/api/v1/profiles/recruiters")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isOk());
    }

    @Test
    void getRecruiterProfileById_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(recruiterProfileService.getRecruiterById(id)).thenReturn(Mockito.mock(RecruiterProfileResponse.class));

        mockMvc.perform(get("/api/v1/profiles/recruiters/" + id))
              .andExpect(status().isOk());
    }
}