package com.echcherqaoui.jobboard.jobservice.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.lang.NonNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.jobservice.model.JobStatus.DRAFT;
import static com.echcherqaoui.jobboard.jobservice.model.WorkModality.ON_SITE;
import static jakarta.persistence.EnumType.STRING;

@Entity
@Table(name = "jobs", indexes = {
      @Index(name = "idx_jobs_created_at", columnList = "createdAt"),
      @Index(name = "idx_jobs_recruiter_status", columnList = "recruiterId, status")
})
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID recruiterId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(columnDefinition = "TEXT")
    private String responsibilities;

    private String location;

    @Enumerated(STRING)
    @Column(nullable = false)
    private WorkModality workModality = ON_SITE;

    @Enumerated(STRING)
    @Column(nullable = false)
    private JobType jobType;

    @Enumerated(STRING)
    @Column(nullable = false)
    private ExperienceLevel experienceLevel;

    @Column(precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(length = 10)
    private String currency = "MAD";

    @Enumerated(STRING)
    @Column(nullable = false)
    private JobStatus status = DRAFT;

    private OffsetDateTime expiresAt;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<JobSkill> skills = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public void addSkills(@NonNull List<String> skillNames) {
        skillNames.forEach(name -> this.skills.add(new JobSkill().setJob(this).setSkill(name)));
    }
}
