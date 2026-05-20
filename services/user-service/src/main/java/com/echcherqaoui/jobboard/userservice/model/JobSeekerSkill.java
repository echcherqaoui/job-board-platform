package com.echcherqaoui.jobboard.userservice.model;

import com.echcherqaoui.jobboard.userservice.enums.SkillLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.UUID;

import static jakarta.persistence.EnumType.STRING;

@Entity
@Table(
      name = "job_seeker_skills",
      indexes = {@Index(name = "idx_job_seeker_skills_profile_id", columnList = "profile_id")}
)
@Getter
@Setter
@Accessors(chain = true)
public class JobSeekerSkill {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private JobSeekerProfile profile;

    @Column(nullable = false)
    private String skillName;

    @Enumerated(STRING)
    private SkillLevel level;
}