package com.echcherqaoui.jobboard.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
      name = "job_seeker_experiences",
      indexes = {@Index(name = "idx_job_seeker_experiences_profile_id", columnList = "profile_id")}
)
@Getter
@Setter
@Accessors(chain = true)
public class JobSeekerExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private JobSeekerProfile profile;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String jobTitle;

    private String location;

    @Column(nullable = false)
    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private boolean current = false;

    @Column(columnDefinition = "TEXT")
    private String description;
}