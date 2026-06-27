package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.jobservice.grpc.UserLookupSupport;
import com.echcherqaoui.jobboard.jobservice.idempotency.IdempotencyGuard;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.repository.CompanyProfileRepository;
import com.echcherqaoui.jobboard.jobservice.service.CompanyProfileService;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyProfileServiceImpl implements CompanyProfileService {

    private final CompanyProfileRepository companyProfileRepository;
    private final UserLookupSupport userLookupSupport;
    private final IdempotencyGuard idempotencyGuard;

    private CompanyProfile fetchFromGrpcAndCache(UUID recruiterId) {
        log.info("Cache miss for recruiter {} — executing gRPC backup fetch", recruiterId);

        Optional<CompanySummary> grpcResponse = userLookupSupport.fetchCompanyProfileTolerantly(recruiterId.toString());

        // If gRPC fails, DO NOT save to DB. Return a transient, in-memory fallback object.
        if (grpcResponse.isEmpty()) {
            log.warn("gRPC backup failed for recruiter {}. Returning transient fallback memory object.", recruiterId);
            return new CompanyProfile()
                  .setRecruiterId(recruiterId)
                  .setCompanyName("Unknown Company")
                  .setCompanyLogo(null)
                  .setLastEventId("transient-fallback")
                  .setUpdatedAt(OffsetDateTime.now(UTC));
        }

        // If gRPC succeeds, map and persist permanently using TransactionTemplate
        CompanySummary grpcProfile = grpcResponse.get();
        CompanyProfile profile = new CompanyProfile()
              .setRecruiterId(recruiterId)
              .setCompanyName(grpcProfile.getCompanyName())
              .setCompanyLogo(grpcProfile.getLogoUrl())
              .setLastEventId("grpc-fallback")
              .setUpdatedAt(OffsetDateTime.now(UTC));

        return companyProfileRepository.save(profile);
    }

    @Transactional
    @Override
    public void upsert(UUID recruiterId,
                       String companyName,
                       String companyLogo,
                       String eventId,
                       OffsetDateTime updatedAt) {
        if (idempotencyGuard.isProcessed(eventId)) {
            log.debug("Duplicate CompanyUpsertedEvent skipped: eventId={}", eventId);
            return;
        }

        companyProfileRepository.findById(recruiterId)
              .ifPresentOrElse(
                    existing -> {
                        if (existing.getLastEventId().equals(eventId)) {
                            log.warn("Duplicate event {} for recruiter {} — skipping", eventId, recruiterId);
                            return;
                        }
                        if (updatedAt.isAfter(existing.getUpdatedAt())) {
                            existing.setCompanyName(companyName)
                                  .setCompanyLogo(companyLogo)
                                  .setLastEventId(eventId)
                                  .setUpdatedAt(updatedAt);

                            log.info("Updated company profile for recruiter {}", recruiterId);
                        } else
                            log.warn(
                                  "Skipping stale event for recruiter {} — event time {} is not after local {}",
                                  recruiterId,
                                  updatedAt,
                                  existing.getUpdatedAt()
                            );
                    },
                    () -> {
                        CompanyProfile profile = new CompanyProfile()
                              .setRecruiterId(recruiterId)
                              .setCompanyName(companyName)
                              .setCompanyLogo(companyLogo)
                              .setLastEventId(eventId)
                              .setUpdatedAt(updatedAt);

                        companyProfileRepository.save(profile);

                        log.info("Inserted company profile for recruiter {}", recruiterId);
                    }
              );
    }

    @Transactional
    @Override
    public void delete(UUID recruiterId) {
        if (companyProfileRepository.existsById(recruiterId)) {
            companyProfileRepository.deleteById(recruiterId);
            log.info("Deleted company profile for recruiter {}", recruiterId);
        } else
            log.warn("Delete requested for unknown recruiter {} — skipping", recruiterId);
    }

    @Override
    public CompanyProfile getByRecruiterId(UUID recruiterId) {
        return companyProfileRepository.findById(recruiterId)
              .orElseGet(() -> fetchFromGrpcAndCache(recruiterId));
    }

    @Transactional(readOnly = true)
    @Override
    public Optional<CompanyProfile> findByRecruiterId(UUID recruiterId) {
        return companyProfileRepository.findById(recruiterId);
    }

    @Transactional(readOnly = true)
    @Override
    public Map<UUID, CompanyProfile> getProfilesByRecruiterId(Set<UUID> recruiterIds) {
        return companyProfileRepository
              .findAllById(recruiterIds)
              .stream()
              .collect(Collectors.toMap(CompanyProfile::getRecruiterId, Function.identity()));
    }
}