package com.echcherqaoui.jobboard.jobservice.repository;


import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, UUID> {

    @Query(" SELECT c.companyName FROM CompanyProfile c WHERE c.recruiterId = :recruiterId ")
    Optional<String> findCompanyNameByRecruiterId(UUID recruiterId);

    List<CompanyProfile> findByRecruiterIdIn(Collection<UUID> recruiterIds);
}