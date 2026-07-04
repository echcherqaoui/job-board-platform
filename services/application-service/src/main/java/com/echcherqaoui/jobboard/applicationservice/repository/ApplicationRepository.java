package com.echcherqaoui.jobboard.applicationservice.repository;

import com.echcherqaoui.jobboard.applicationservice.model.Application;
import com.echcherqaoui.jobboard.applicationservice.model.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    boolean existsByJobIdAndApplicantId(UUID jobId, UUID applicantId);

    Page<Application> findByApplicantId(UUID applicantId, Pageable pageable);

    @EntityGraph(attributePaths = "statusHistory")
    Optional<Application> findWithHistoryById(UUID id);

    Page<Application> findByJobId(UUID jobId, Pageable pageable);

    Page<Application> findByJobIdAndStatus(UUID jobId, ApplicationStatus status, Pageable pageable);

    List<Application> findByJobIdAndStatus(UUID jobId, ApplicationStatus status);
}
