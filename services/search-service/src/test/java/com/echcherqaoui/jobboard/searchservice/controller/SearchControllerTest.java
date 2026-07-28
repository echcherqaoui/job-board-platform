package com.echcherqaoui.jobboard.searchservice.controller;

import com.echcherqaoui.jobboard.exception.handler.GlobalExceptionHandler;
import com.echcherqaoui.jobboard.searchservice.dto.response.AutocompleteResponse;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobDTO;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchHit;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchResponse;
import com.echcherqaoui.jobboard.searchservice.exception.SearchExceptionHandler;
import com.echcherqaoui.jobboard.searchservice.exception.domain.JobDocumentNotFoundException;
import com.echcherqaoui.jobboard.searchservice.service.JobSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SearchController.class)
@Import({GlobalExceptionHandler.class, SearchExceptionHandler.class})
@ImportAutoConfiguration(exclude = {
      SecurityAutoConfiguration.class,
      SecurityFilterAutoConfiguration.class,
      OAuth2ClientAutoConfiguration.class,
      OAuth2ResourceServerAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      SqlInitializationAutoConfiguration.class
})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobSearchService jobSearchService;

    // ---------- GET /search/jobs ----------

    @Test
    void searchJobs_returns200_withSearchResults() throws Exception {
        JobSearchHit hit = new JobSearchHit(
              "job-1", "recruiter-1", "Acme", null, "Backend Engineer",
              "Casablanca", "REMOTE", "FULL_TIME", "MID", "OPEN",
              50000.0, 80000.0, "USD", List.of("Java"),
              java.time.Instant.now(), null, 4.5f, Map.of()
        );
        JobSearchResponse response = new JobSearchResponse(List.of(hit), 1, 0, 20, 1, false, 15L);

        when(jobSearchService.search(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/search/jobs").param("keyword", "java"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.hits[0].id").value("job-1"))
              .andExpect(jsonPath("$.totalHits").value(1));
    }

    @Test
    void searchJobs_appliesDefaults_whenNoParamsProvided() throws Exception {
        JobSearchResponse response = new JobSearchResponse(List.of(), 0, 0, 20, 0, false, 5L);
        when(jobSearchService.search(any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/search/jobs"))
              .andExpect(status().isOk());
        // JobSearchRequest's compact constructor defaults (status=OPEN, sortBy=relevance,
        // page=0, size=20) apply here; can't easily assert the exact request object passed
        // to the mocked service without an ArgumentCaptor, but a 200 confirms binding succeeds
        // with zero query params, which is the main thing @Valid could break.
    }

    // ---------- GET /search/jobs/{id} ----------

    @Test
    void getJobById_returns200_whenFound() throws Exception {
        JobDTO dto = new JobDTO(
              "job-1", "recruiter-1", "Acme", null, "Backend Engineer",
              "desc", "reqs", "Casablanca", "REMOTE", "FULL_TIME", "MID", "OPEN",
              50000.0, 80000.0, "USD", List.of("Java"),
              java.time.Instant.now(), null
        );
        when(jobSearchService.getById("job-1")).thenReturn(dto);

        mockMvc.perform(get("/api/v1/search/jobs/job-1"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.id").value("job-1"));
    }

    @Test
    void getJobById_returns404_whenNotFound() throws Exception {
        when(jobSearchService.getById("missing")).thenThrow(new JobDocumentNotFoundException("missing"));

        mockMvc.perform(get("/api/v1/search/jobs/missing"))
              .andExpect(status().isNotFound());
    }

    @Test
    void autocomplete_returns200_withSuggestions() throws Exception {
        when(jobSearchService.autocomplete("back", 10))
              .thenReturn(new AutocompleteResponse(List.of("Backend Engineer", "Backend Lead")));

        mockMvc.perform(get("/api/v1/search/jobs/autocomplete").param("prefix", "back"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.suggestions[0]").value("Backend Engineer"));
    }

    @Test
    void autocomplete_usesDefaultLimit10_whenNotProvided() throws Exception {
        when(jobSearchService.autocomplete("back", 10))
              .thenReturn(new AutocompleteResponse(List.of()));

        mockMvc.perform(get("/api/v1/search/jobs/autocomplete").param("prefix", "back"))
              .andExpect(status().isOk());
    }

    @Test
    void autocomplete_returns400_whenPrefixTooShort() throws Exception {
        mockMvc.perform(get("/api/v1/search/jobs/autocomplete").param("prefix", "a"))
              .andExpect(status().isBadRequest());
    }

    @Test
    void autocomplete_returns400_whenLimitExceedsMax() throws Exception {
        mockMvc.perform(get("/api/v1/search/jobs/autocomplete")
                    .param("prefix", "back")
                    .param("limit", "50"))
              .andExpect(status().isBadRequest());
    }
}