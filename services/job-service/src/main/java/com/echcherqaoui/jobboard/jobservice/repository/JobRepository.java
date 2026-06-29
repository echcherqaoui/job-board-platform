package com.echcherqaoui.jobboard.jobservice.repository;

import com.echcherqaoui.jobboard.jobservice.model.Job;
import com.echcherqaoui.jobboard.jobservice.projection.JobSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

    @EntityGraph(attributePaths = "skills")
    Optional<Job> findWithSkillsById(UUID id);

    Page<Job> findByRecruiterId(UUID recruiterId, Pageable pageable);

    List<JobSummaryProjection> findByIdIn(Set<UUID> ids);

    @Query("FROM Job j WHERE j.status = OPEN AND j.expiresAt < :now")
    List<Job> findExpiredJobs(@Param("now") OffsetDateTime now);

    @Query("select j from Job j where j.id = :id")
    Optional<JobSummaryProjection> findJobProjectionById(UUID id);
}
