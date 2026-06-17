package com.echcherqaoui.jobboard.searchservice.controller;

import com.echcherqaoui.jobboard.searchservice.dto.request.JobSearchRequest;
import com.echcherqaoui.jobboard.searchservice.dto.response.AutocompleteResponse;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobDTO;
import com.echcherqaoui.jobboard.searchservice.dto.response.JobSearchResponse;
import com.echcherqaoui.jobboard.searchservice.service.JobSearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("${api.base-path}/search")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final JobSearchService jobSearchService;

    @GetMapping("/jobs")
    public ResponseEntity<JobSearchResponse> searchJobs(@Valid JobSearchRequest request) {
        return ResponseEntity.ok(jobSearchService.search(request));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobDTO> getJobById(@PathVariable String id) {
        return ResponseEntity.ok(jobSearchService.getById(id));
    }

    // Returns up to 10 job title suggestions for the search bar.
    // Uses edge-ngram analyzer on the title.autocomplete field.
    @GetMapping("/jobs/autocomplete")
    public ResponseEntity<AutocompleteResponse> autocomplete(@RequestParam @Size(min = 2, max = 20) String prefix,
                                                             @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return ResponseEntity.ok(jobSearchService.autocomplete(prefix, limit));
    }
}
