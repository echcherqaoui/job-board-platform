package com.echcherqaoui.jobboard.userservice.repository;

import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import com.echcherqaoui.jobboard.userservice.projection.UserEmailProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface JobSeekerProfileRepository extends JpaRepository<JobSeekerProfile, UUID> {

    List<JobSeekerSummaryProjection> findByIdIn(Set<UUID> ids);

    @Query("SELECT j.email FROM JobSeekerProfile j WHERE j.id = :id")
    Optional<String> findEmailById(UUID id);

    @Query("SELECT j.id AS id, j.email AS email FROM JobSeekerProfile j WHERE j.id IN :userIds")
    List<UserEmailProjection> findEmailsByUserIds(Set<UUID> userIds);
}