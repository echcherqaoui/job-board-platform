package com.echcherqaoui.jobboard.userservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static jakarta.persistence.CascadeType.ALL;

@Entity
@Table(name = "job_seeker_profiles")
@Getter
@Setter
@Accessors(chain = true)
@EntityListeners(AuditingEntityListener.class)
public class JobSeekerProfile {
    @Id
    private UUID id;

    private String firstName;

    private String lastName;

    @Column(unique = true)
    private String email;

    private String phone;

    private String location;

    private String headline;

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String profilePicture;

    private String cvUrl;

    private String linkedinUrl;

    private String githubUrl;

    private String portfolioUrl;

    private Integer yearsExperience;

    private boolean onboardingCompleted = false;

    @OneToMany(mappedBy = "profile", cascade = ALL, orphanRemoval = true)
    private List<JobSeekerSkill> skills = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = ALL, orphanRemoval = true)
    private List<JobSeekerExperience> experiences = new ArrayList<>();

    @OneToMany(mappedBy = "profile", cascade = ALL, orphanRemoval = true)
    private List<JobSeekerEducation> educations = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;
}