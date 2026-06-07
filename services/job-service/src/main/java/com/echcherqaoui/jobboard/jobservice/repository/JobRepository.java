package com.echcherqaoui.jobboard.jobservice.repository;

import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.model.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    @EntityGraph(attributePaths = "skills")
    Optional<Job> findWithSkillsById(UUID id);

    Page<Job> findByRecruiterId(UUID recruiterId, Pageable pageable);

    Page<Job> findByRecruiterIdAndStatus(UUID recruiterId, JobStatus status, Pageable pageable);

    boolean existsByIdAndRecruiterId(UUID id, UUID recruiterId);
}
