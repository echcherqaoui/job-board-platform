package com.echcherqaoui.jobboard.userservice.controller;

import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.JobSeekerProfileResponse;
import com.echcherqaoui.jobboard.userservice.service.JobSeekerProfileService;
import com.echcherqaoui.jobboard.userservice.validation.OnboardingGroup;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles/job-seekers")
@RequiredArgsConstructor
public class JobSeekerProfileController {

    private final JobSeekerProfileService service;

    @PostMapping("/onboard")
    public ResponseEntity<Void> onboard(
          @Validated({Default.class, OnboardingGroup.class}) @RequestBody JobSeekerProfileRequest request) {
        service.onboard(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<JobSeekerProfileResponse> getMyProfile() {
        return ResponseEntity.ok(service.getMyProfile());
    }

    @PutMapping
    public ResponseEntity<JobSeekerProfileResponse> updateProfile(@Valid @RequestBody JobSeekerProfileRequest request) {
        return ResponseEntity.ok(service.updateProfile(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobSeekerProfileResponse> getProfileById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getProfileById(id));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteProfile() {
        service.deleteProfile();
        return ResponseEntity.noContent().build();
    }
}
