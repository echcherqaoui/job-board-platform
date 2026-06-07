package com.echcherqaoui.jobboard.jobservice.controller;

import com.echcherqaoui.jobboard.jobservice.dto.request.JobRequest;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobSearchCriteria;
import com.echcherqaoui.jobboard.jobservice.dto.request.JobStatusUpdateRequest;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobResponse;
import com.echcherqaoui.jobboard.jobservice.dto.response.JobSummaryResponse;
import com.echcherqaoui.jobboard.jobservice.service.JobService;
import com.echcherqaoui.jobboard.sharedutils.dto.PaginatedResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("${api.base-path}/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    public ResponseEntity<JobResponse> postJob(@Valid @RequestBody JobRequest request) {
        return ResponseEntity
              .status(HttpStatus.CREATED)
              .body(jobService.postJob(request));
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<JobSummaryResponse>> searchJobs(@ModelAttribute JobSearchCriteria criteria,
                                                                            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(jobService.searchJobs(criteria, pageable));
    }

    @GetMapping("/my")
    public ResponseEntity<PaginatedResponse<JobSummaryResponse>> getMyJobs(@PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return ResponseEntity.ok(jobService.getMyJobs(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJobById(@PathVariable UUID id) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobResponse> updateJob(@PathVariable UUID id,
                                                 @Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(jobService.updateJob(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<JobResponse> updateJobStatus(@PathVariable UUID id,
                                                       @Valid @RequestBody JobStatusUpdateRequest request) {
        return ResponseEntity.ok(jobService.updateJobStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJob(@PathVariable UUID id) {
        jobService.deleteJob(id);
        return ResponseEntity.noContent().build();
    }
}
