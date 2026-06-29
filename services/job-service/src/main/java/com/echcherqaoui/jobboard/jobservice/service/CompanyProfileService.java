package com.echcherqaoui.jobboard.jobservice.service;

import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CompanyProfileService {
    void upsert(UUID recruiterId,
                String companyName,
                String companyLogo,
                String eventId,
                OffsetDateTime updatedAt);

    void delete(UUID recruiterId);

    CompanyProfile getByRecruiterId(UUID recruiterId);

    Optional<CompanyProfile> findByRecruiterId(UUID recruiterId);

    Map<UUID, CompanyProfile> getProfilesByRecruiterId(Set<UUID> recruiterIds);

    String getCompanyName(UUID recruiterId);

    Map<UUID, String> getCompanyNames(Set<UUID> recruiterId);
}
