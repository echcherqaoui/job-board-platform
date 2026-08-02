package com.echcherqaoui.jobboard.jobservice.service.impl;

import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.grpc.client.ResilientCompanyProfileClient;
import com.echcherqaoui.jobboard.jobservice.idempotency.IdempotencyGuard;
import com.echcherqaoui.jobboard.jobservice.model.CompanyProfile;
import com.echcherqaoui.jobboard.jobservice.repository.CompanyProfileRepository;
import com.echcherqaoui.jobboard.user.grpc.CompanySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.MICROS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class CompanyProfileServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private CompanyProfileServiceImpl companyProfileService;

    @Autowired
    private CompanyProfileRepository companyProfileRepository;

    @MockitoBean
    private ResilientCompanyProfileClient resilientCompanyProfileClient;

    @MockitoBean
    private IdempotencyGuard idempotencyGuard;

    private final UUID recruiterId1 = UUID.randomUUID();
    private final UUID recruiterId2 = UUID.randomUUID();
    private final OffsetDateTime now = OffsetDateTime.now(UTC).truncatedTo(MICROS);

    @BeforeEach
    void setUp() {
        companyProfileRepository.deleteAll();
    }

    @Nested
    @DisplayName("getByRecruiterId")
    class GetByRecruiterId {

        @Test
        void returnsPersistedEntity_whenPresentInDatabase() {
            CompanyProfile seeded = companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId1)
                  .setCompanyName("AgileCorp")
                  .setCompanyLogo("logo.png")
                  .setLastEventId("seed-1")
                  .setUpdatedAt(now));

            CompanyProfile result = companyProfileService.getByRecruiterId(recruiterId1);

            assertThat(result.getRecruiterId()).isEqualTo(seeded.getRecruiterId());
            assertThat(result.getCompanyName()).isEqualTo("AgileCorp");
            verifyNoInteractions(resilientCompanyProfileClient);
        }

        @Test
        void fetchesFromGrpcAndPersistsToDatabase_whenCacheMissAndGrpcSucceeds() {
            CompanySummary grpcSummary = CompanySummary.newBuilder()
                  .setCompanyName("TechGlobal")
                  .setLogoUrl("tech_logo.png")
                  .build();

            when(resilientCompanyProfileClient.fetchCompanyProfileTolerantly(recruiterId1.toString()))
                  .thenReturn(Optional.of(grpcSummary));

            CompanyProfile result = companyProfileService.getByRecruiterId(recruiterId1);

            assertThat(result.getCompanyName()).isEqualTo("TechGlobal");
            assertThat(result.getCompanyLogo()).isEqualTo("tech_logo.png");
            assertThat(result.getLastEventId()).isEqualTo("grpc-fallback");

            Optional<CompanyProfile> dbEntity = companyProfileRepository.findById(recruiterId1);
            assertThat(dbEntity).isPresent();
            assertThat(dbEntity.get().getCompanyName()).isEqualTo("TechGlobal");
        }

        @Test
        void reFetchesFromGrpcEveryTime_whenSustainedOutage_documentingRisk() {
            when(resilientCompanyProfileClient.fetchCompanyProfileTolerantly(recruiterId1.toString()))
                  .thenReturn(Optional.empty());

            companyProfileService.getByRecruiterId(recruiterId1);
            companyProfileService.getByRecruiterId(recruiterId1);

            Mockito.verify(resilientCompanyProfileClient, Mockito.times(2))
                  .fetchCompanyProfileTolerantly(recruiterId1.toString());
            assertThat(companyProfileRepository.findById(recruiterId1)).isEmpty();
        }

        @Test
        void returnsTransientFallbackAndDoesNotSave_whenCacheMissAndGrpcDegrades() {
            when(resilientCompanyProfileClient.fetchCompanyProfileTolerantly(recruiterId1.toString()))
                  .thenReturn(Optional.empty());

            CompanyProfile result = companyProfileService.getByRecruiterId(recruiterId1);

            assertThat(result.getCompanyName()).isEqualTo("Unknown Company");
            assertThat(result.getLastEventId()).isEqualTo("transient-fallback");

            assertThat(companyProfileRepository.findById(recruiterId1)).isEmpty();
        }
    }

    @Nested
    @DisplayName("upsert")
    class Upsert {

        @Test
        void skipsDatabaseInsert_whenIdempotencyGuardFlagsAsProcessed() {
            String eventId = "evt-processed";
            when(idempotencyGuard.isProcessed(eventId)).thenReturn(true);

            companyProfileService.upsert(recruiterId1, "NewCorp", "logo.png", eventId, now);

            assertThat(companyProfileRepository.findById(recruiterId1)).isEmpty();
        }

        @Test
        void skipsUpdate_whenEventIdMatchesExisting_evenIfGuardSaysUnprocessed() {
            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId1)
                  .setCompanyName("OriginalName")
                  .setCompanyLogo("original.png")
                  .setLastEventId("evt-1")
                  .setUpdatedAt(now));

            when(idempotencyGuard.isProcessed("evt-1")).thenReturn(false);

            companyProfileService.upsert(recruiterId1, "AttemptedNewName", "new.png", "evt-1", now.plusMinutes(5));

            CompanyProfile dbEntity = companyProfileRepository.findById(recruiterId1).orElseThrow();
            assertThat(dbEntity.getCompanyName()).isEqualTo("OriginalName");
        }

        @Test
        void insertsNewRecordInDatabase_whenProfileDoesNotExist() {
            String eventId = "evt-new";
            when(idempotencyGuard.isProcessed(eventId)).thenReturn(false);

            companyProfileService.upsert(recruiterId1, "NewCorp", "new_logo.png", eventId, now);

            Optional<CompanyProfile> dbEntity = companyProfileRepository.findById(recruiterId1);
            assertThat(dbEntity).isPresent();
            assertThat(dbEntity.get().getCompanyName()).isEqualTo("NewCorp");
            assertThat(dbEntity.get().getLastEventId()).isEqualTo(eventId);
        }

        @Test
        void updatesExistingDatabaseRecord_whenEventIsNewer() {
            OffsetDateTime oldTime = now.minusHours(2);
            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId1)
                  .setCompanyName("OldName")
                  .setCompanyLogo("old.png")
                  .setLastEventId("evt-old")
                  .setUpdatedAt(oldTime));

            String eventId = "evt-update";
            when(idempotencyGuard.isProcessed(eventId)).thenReturn(false);

            companyProfileService.upsert(recruiterId1, "UpdatedName", "updated.png", eventId, now);

            CompanyProfile dbEntity = companyProfileRepository.findById(recruiterId1).orElseThrow();
            assertThat(dbEntity.getCompanyName()).isEqualTo("UpdatedName");
            assertThat(dbEntity.getCompanyLogo()).isEqualTo("updated.png");
            assertThat(dbEntity.getLastEventId()).isEqualTo(eventId);
            assertThat(dbEntity.getUpdatedAt()).isEqualTo(now);
        }

        @Test
        void ignoresUpdate_whenEventTimestampIsStale() {
            OffsetDateTime futureTime = now.plusHours(1);
            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId1)
                  .setCompanyName("FutureName")
                  .setCompanyLogo("future.png")
                  .setLastEventId("evt-future")
                  .setUpdatedAt(futureTime));

            String eventId = "evt-stale";
            when(idempotencyGuard.isProcessed(eventId)).thenReturn(false);

            companyProfileService.upsert(recruiterId1, "StaleName", "stale.png", eventId, now);

            CompanyProfile dbEntity = companyProfileRepository.findById(recruiterId1).orElseThrow();
            assertThat(dbEntity.getCompanyName()).isEqualTo("FutureName");
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void removesProfileFromDatabase_whenExists() {
            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId1)
                  .setCompanyName("ToDelete")
                  .setLastEventId("evt-1")
                  .setUpdatedAt(now));

            companyProfileService.delete(recruiterId1);

            assertThat(companyProfileRepository.existsById(recruiterId1)).isFalse();
        }

        @Test
        void doesNothing_whenRecruiterIdNotFound() {
            companyProfileService.delete(recruiterId1);

            assertThat(companyProfileRepository.existsById(recruiterId1)).isFalse();
        }
    }

    @Nested
    @DisplayName("Batch & Projection Queries")
    class BatchQueries {

        @Test
        void getProfilesByRecruiterId_executesInClauseAndMapsToMap() {
            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId1)
                  .setCompanyName("CorpOne")
                  .setLastEventId("evt-1")
                  .setUpdatedAt(now));

            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId2)
                  .setCompanyName("CorpTwo")
                  .setLastEventId("evt-2")
                  .setUpdatedAt(now));

            Map<UUID, CompanyProfile> result = companyProfileService.getProfilesByRecruiterId(Set.of(recruiterId1, recruiterId2));

            assertThat(result).hasSize(2);
            assertThat(result.get(recruiterId1).getCompanyName()).isEqualTo("CorpOne");
            assertThat(result.get(recruiterId2).getCompanyName()).isEqualTo("CorpTwo");
        }

        @Test
        void getCompanyName_executesProjectionQuery() {
            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId1)
                  .setCompanyName("ProjectedCorp")
                  .setLastEventId("evt-1")
                  .setUpdatedAt(now));

            String name = companyProfileService.getCompanyName(recruiterId1);

            assertThat(name).isEqualTo("ProjectedCorp");
        }

        @Test
        void getCompanyNames_returnsMappedNamesFromCustomQuery() {
            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId1)
                  .setCompanyName("AlphaCorp")
                  .setLastEventId("evt-1")
                  .setUpdatedAt(now));

            companyProfileRepository.save(new CompanyProfile()
                  .setRecruiterId(recruiterId2)
                  .setCompanyName("BetaCorp")
                  .setLastEventId("evt-2")
                  .setUpdatedAt(now));

            Map<UUID, String> names = companyProfileService.getCompanyNames(Set.of(recruiterId1, recruiterId2));

            assertThat(names)
                  .hasSize(2)
                  .containsEntry(recruiterId1, "AlphaCorp")
                  .containsEntry(recruiterId2, "BetaCorp");
        }
    }
}