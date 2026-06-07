package com.echcherqaoui.jobboard.jobservice.repository;


import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, UUID> {

}