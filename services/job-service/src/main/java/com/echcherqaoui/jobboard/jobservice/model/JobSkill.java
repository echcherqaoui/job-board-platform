package com.echcherqaoui.jobboard.jobservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(
      name = "job_skills",
      indexes = {@Index(name = "idx_job_skills_job_id", columnList = "job_id")}
)
@Getter
@Setter
@Accessors(chain = true)
public class JobSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String skill;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;
}
