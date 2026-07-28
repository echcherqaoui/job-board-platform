package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.jobservice.grpc.client.ResilientCompanyProfileClient;
import com.echcherqaoui.jobboard.jobservice.idempotency.IdempotencyGuard;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.repository.CompanyProfileRepository;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyProfileServiceImplTest {

    private CompanyProfileRepository companyProfileRepository;
    private ResilientCompanyProfileClient resilientCompanyProfileClient;
    private IdempotencyGuard idempotencyGuard;
    private CompanyProfileServiceImpl service;

    private final UUID recruiterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        companyProfileRepository = mock(CompanyProfileRepository.class);
        resilientCompanyProfileClient = mock(ResilientCompanyProfileClient.class);
        idempotencyGuard = mock(IdempotencyGuard.class);
        service = new CompanyProfileServiceImpl(companyProfileRepository, resilientCompanyProfileClient, idempotencyGuard);
    }

    private CompanyProfile buildExisting(String lastEventId, OffsetDateTime updatedAt) {
        return new CompanyProfile()
              .setRecruiterId(recruiterId)
              .setCompanyName("Old Name")
              .setCompanyLogo("old-logo.png")
              .setLastEventId(lastEventId)
              .setUpdatedAt(updatedAt);
    }

    @Test
    void upsert_skipsEntirely_whenIdempotencyGuardSaysAlreadyProcessed() {
        when(idempotencyGuard.isProcessed("evt-1")).thenReturn(true);

        service.upsert(recruiterId, "Acme", "logo.png", "evt-1", OffsetDateTime.now(ZoneOffset.UTC));

        verify(companyProfileRepository, never()).findById(any());
        verify(companyProfileRepository, never()).save(any());
    }

    @Test
    void upsert_insertsNewProfile_whenNoneExists() {
        when(idempotencyGuard.isProcessed("evt-1")).thenReturn(false);
        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.empty());

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        service.upsert(recruiterId, "Acme", "logo.png", "evt-1", now);

        org.mockito.ArgumentCaptor<CompanyProfile> captor = org.mockito.ArgumentCaptor.forClass(CompanyProfile.class);
        verify(companyProfileRepository).save(captor.capture());

        CompanyProfile saved = captor.getValue();
        assertThat(saved.getRecruiterId()).isEqualTo(recruiterId);
        assertThat(saved.getCompanyName()).isEqualTo("Acme");
        assertThat(saved.getLastEventId()).isEqualTo("evt-1");
    }

    @Test
    void upsert_updatesExisting_whenEventIsNewerAndEventIdDiffers() {
        OffsetDateTime oldTime = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime newTime = OffsetDateTime.now(ZoneOffset.UTC);
        CompanyProfile existing = buildExisting("evt-old", oldTime);

        when(idempotencyGuard.isProcessed("evt-new")).thenReturn(false);
        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.of(existing));

        service.upsert(recruiterId, "New Name", "new-logo.png", "evt-new", newTime);

        assertThat(existing.getCompanyName()).isEqualTo("New Name");
        assertThat(existing.getCompanyLogo()).isEqualTo("new-logo.png");
        assertThat(existing.getLastEventId()).isEqualTo("evt-new");
        assertThat(existing.getUpdatedAt()).isEqualTo(newTime);
        verify(companyProfileRepository, never()).save(any()); // mutated in-place, JPA dirty-checking assumed, no explicit save call
    }

    @Test
    void upsert_skipsUpdate_whenEventIdMatchesExisting_evenIfGuardSaysUnprocessed() {
        OffsetDateTime time = OffsetDateTime.now(ZoneOffset.UTC);
        CompanyProfile existing = buildExisting("evt-1", time);

        when(idempotencyGuard.isProcessed("evt-1")).thenReturn(false);
        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.of(existing));

        service.upsert(recruiterId, "Attempted New Name", "new-logo.png", "evt-1", time.plusMinutes(5));

        assertThat(existing.getCompanyName()).isEqualTo("Old Name");
    }

    @Test
    void upsert_skipsUpdate_whenIncomingEventIsStale() {
        OffsetDateTime existingTime = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime staleTime = existingTime.minusHours(2);
        CompanyProfile existing = buildExisting("evt-old", existingTime);

        when(idempotencyGuard.isProcessed("evt-stale")).thenReturn(false);
        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.of(existing));

        service.upsert(recruiterId, "Stale Name", "stale-logo.png", "evt-stale", staleTime);

        assertThat(existing.getCompanyName()).isEqualTo("Old Name");
        assertThat(existing.getLastEventId()).isEqualTo("evt-old");
    }

    @Test
    void delete_deletesProfile_whenExists() {
        when(companyProfileRepository.existsById(recruiterId)).thenReturn(true);

        service.delete(recruiterId);

        verify(companyProfileRepository).deleteById(recruiterId);
    }

    @Test
    void delete_doesNothing_whenNotExists() {
        when(companyProfileRepository.existsById(recruiterId)).thenReturn(false);

        service.delete(recruiterId);

        verify(companyProfileRepository, never()).deleteById(any());
    }

    @Test
    void getByRecruiterId_returnsCached_whenPresentInDb() {
        CompanyProfile existing = buildExisting("evt-1", OffsetDateTime.now(ZoneOffset.UTC));
        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.of(existing));

        CompanyProfile result = service.getByRecruiterId(recruiterId);

        assertThat(result).isEqualTo(existing);
        verify(resilientCompanyProfileClient, never()).fetchCompanyProfileTolerantly(anyString());
    }

    @Test
    void getByRecruiterId_fetchesAndPersists_onCacheMiss_whenGrpcSucceeds() {
        CompanySummary summary = CompanySummary.newBuilder()
              .setCompanyName("Acme")
              .setLogoUrl("logo.png")
              .build();

        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.empty());
        when(resilientCompanyProfileClient.fetchCompanyProfileTolerantly(recruiterId.toString()))
              .thenReturn(Optional.of(summary));
        when(companyProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyProfile result = service.getByRecruiterId(recruiterId);

        assertThat(result.getCompanyName()).isEqualTo("Acme");
        assertThat(result.getLastEventId()).isEqualTo("grpc-fallback");
        verify(companyProfileRepository).save(any(CompanyProfile.class));
    }

    @Test
    void getByRecruiterId_returnsTransientFallback_withoutPersisting_whenGrpcFails() {
        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.empty());
        when(resilientCompanyProfileClient.fetchCompanyProfileTolerantly(recruiterId.toString()))
              .thenReturn(Optional.empty());

        CompanyProfile result = service.getByRecruiterId(recruiterId);

        assertThat(result.getCompanyName()).isEqualTo("Unknown Company");
        assertThat(result.getLastEventId()).isEqualTo("transient-fallback");
        verify(companyProfileRepository, never()).save(any());
    }

    @Test
    void getByRecruiterId_reFetchesViaGrpcEveryTime_whenPreviousFetchWasTransientFallback() {
        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.empty());
        when(resilientCompanyProfileClient.fetchCompanyProfileTolerantly(recruiterId.toString()))
              .thenReturn(Optional.empty());

        service.getByRecruiterId(recruiterId);
        service.getByRecruiterId(recruiterId);

        verify(resilientCompanyProfileClient, times(2)).fetchCompanyProfileTolerantly(recruiterId.toString());
    }

    @Test
    void findByRecruiterId_delegatesToRepository() {
        when(companyProfileRepository.findById(recruiterId)).thenReturn(Optional.empty());

        assertThat(service.findByRecruiterId(recruiterId)).isEmpty();
    }

    @Test
    void getProfilesByRecruiterId_mapsListToMapByRecruiterId() {
        CompanyProfile profile1 = buildExisting("evt-1", OffsetDateTime.now(ZoneOffset.UTC));
        UUID otherRecruiter = UUID.randomUUID();
        CompanyProfile profile2 = new CompanyProfile().setRecruiterId(otherRecruiter).setCompanyName("Other Co");

        Set<UUID> ids = Set.of(recruiterId, otherRecruiter);
        when(companyProfileRepository.findAllById(ids)).thenReturn(java.util.List.of(profile1, profile2));

        Map<UUID, CompanyProfile> result = service.getProfilesByRecruiterId(ids);

        assertThat(result).hasSize(2);
        assertThat(result.get(recruiterId)).isEqualTo(profile1);
        assertThat(result.get(otherRecruiter)).isEqualTo(profile2);
    }

    @Test
    void getCompanyName_returnsNull_whenNotFound() {
        when(companyProfileRepository.findCompanyNameByRecruiterId(recruiterId)).thenReturn(Optional.empty());

        assertThat(service.getCompanyName(recruiterId)).isNull();
    }

    @Test
    void getCompanyName_returnsName_whenFound() {
        when(companyProfileRepository.findCompanyNameByRecruiterId(recruiterId)).thenReturn(Optional.of("Acme"));

        assertThat(service.getCompanyName(recruiterId)).isEqualTo("Acme");
    }

    @Test
    void getCompanyNames_mapsListToMap() {
        CompanyProfile profile = buildExisting("evt-1", OffsetDateTime.now(ZoneOffset.UTC));
        Set<UUID> ids = Set.of(recruiterId);

        when(companyProfileRepository.findByRecruiterIdIn(ids)).thenReturn(java.util.List.of(profile));

        Map<UUID, String> result = service.getCompanyNames(ids);

        assertThat(result).containsEntry(recruiterId, "Old Name");
    }
}