package com.echcherqaoui.jobboard.userservice.repository;


import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecruiterProfileRepository extends JpaRepository<RecruiterProfile, UUID> {

    @Query("SELECT r.email FROM RecruiterProfile r WHERE r.id = :id")
    Optional<String> findEmailById(UUID id);
}