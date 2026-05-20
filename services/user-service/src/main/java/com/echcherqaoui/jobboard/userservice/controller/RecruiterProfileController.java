package com.echcherqaoui.jobboard.userservice.controller;

import com.echcherqaoui.jobboard.userservice.dto.request.RecruiterProfileRequest;
import com.echcherqaoui.jobboard.userservice.dto.response.RecruiterProfileResponse;
import com.echcherqaoui.jobboard.userservice.service.RecruiterProfileService;
import com.echcherqaoui.jobboard.userservice.validation.OnboardingGroup;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles/recruiters")
@RequiredArgsConstructor
public class RecruiterProfileController {

    private final RecruiterProfileService recruiterProfileService;

    @PostMapping("/onboard")
    public ResponseEntity<Void> onboard(
          @Validated({Default.class, OnboardingGroup.class}) @RequestBody RecruiterProfileRequest request) {
        recruiterProfileService.onboard(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<RecruiterProfileResponse> getMe() {
        return ResponseEntity.ok(recruiterProfileService.getMe());
    }

    @PutMapping
    public ResponseEntity<RecruiterProfileResponse> updateRecruiterProfile(@Valid @RequestBody RecruiterProfileRequest request) {
        return ResponseEntity.ok(recruiterProfileService.update(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecruiterProfileResponse> getRecruiterProfileById(@PathVariable UUID id) {
        return ResponseEntity.ok(recruiterProfileService.getRecruiterById(id));
    }
}