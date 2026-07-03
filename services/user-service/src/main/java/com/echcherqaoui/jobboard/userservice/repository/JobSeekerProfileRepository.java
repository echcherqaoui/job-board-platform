package com.echcherqaoui.jobboard.userservice.repository;

import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JobSeekerProfileRepository extends JpaRepository<JobSeekerProfile, UUID> {

    List<JobSeekerSummaryProjection> findByIdIn(Set<UUID> ids);
}