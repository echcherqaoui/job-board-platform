package com.echcherqaoui.jobboard.userservice.repository;

import com.echcherqaoui.jobboard.userservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.projection.JobSeekerSummaryProjection;
import com.echcherqaoui.jobboard.userservice.projection.UserEmailProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobSeekerProfileRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private JobSeekerProfileRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Nested
    class FindByIdInProjection {

        @Test
        void findByIdIn_WhenProfilesExist_ShouldMapAllProjectionFieldsCorrectly() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            OffsetDateTime now = OffsetDateTime.now();

            JobSeekerProfile profile1 = new JobSeekerProfile()
                  .setId(id1)
                  .setFirstName("John")
                  .setLastName("Doe")
                  .setHeadline("Senior Java Developer")
                  .setCvUrl("https://cloudinary.com/cv1.pdf")
                  .setEmail("john.doe@test.com")
                  .setCreatedAt(now)
                  .setUpdatedAt(now);

            JobSeekerProfile profile2 = new JobSeekerProfile()
                  .setId(id2)
                  .setFirstName("Jane")
                  .setLastName("Smith")
                  .setHeadline("Frontend Architect")
                  .setCvUrl("https://cloudinary.com/cv2.pdf")
                  .setEmail("jane.smith@test.com")
                  .setCreatedAt(now)
                  .setUpdatedAt(now);

            repository.saveAll(List.of(profile1, profile2));
            entityManager.flush();
            entityManager.clear();

            List<JobSeekerSummaryProjection> projections = repository.findByIdIn(Set.of(id1, id2));

            assertThat(projections).hasSize(2);

            JobSeekerSummaryProjection p1 = projections.stream()
                  .filter(p -> p.getId().equals(id1))
                  .findFirst()
                  .orElseThrow();

            assertThat(p1.getFirstName()).isEqualTo("John");
            assertThat(p1.getLastName()).isEqualTo("Doe");
            assertThat(p1.getHeadline()).isEqualTo("Senior Java Developer");
            assertThat(p1.getCvUrl()).isEqualTo("https://cloudinary.com/cv1.pdf");
            assertThat(p1.getEmail()).isEqualTo("john.doe@test.com");
        }

        @Test
        void findByIdIn_WhenIdsDoNotExist_ShouldReturnEmptyList() {
            List<JobSeekerSummaryProjection> projections = repository.findByIdIn(Set.of(UUID.randomUUID()));

            assertThat(projections).isEmpty();
        }
    }

    @Nested
    class FindEmailById {

        @Test
        void findEmailById_WhenProfileExists_ShouldReturnEmail() {
            UUID userId = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("email.fetch@test.com")
                  .setCreatedAt(now)
                  .setUpdatedAt(now);

            repository.save(profile);
            entityManager.flush();
            entityManager.clear();

            Optional<String> email = repository.findEmailById(userId);

            assertThat(email).contains("email.fetch@test.com");
        }

        @Test
        void findEmailById_WhenProfileDoesNotExist_ShouldReturnEmptyOptional() {
            Optional<String> email = repository.findEmailById(UUID.randomUUID());

            assertThat(email).isEmpty();
        }
    }

    @Nested
    class FindEmailsByUserIds {

        @Test
        void findEmailsByUserIds_WhenProfilesExist_ShouldMapIdAndEmailToProjection() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();

            OffsetDateTime now = OffsetDateTime.now();
            JobSeekerProfile p1 = new JobSeekerProfile().setId(id1).setEmail("dev1@test.com").setCreatedAt(now).setUpdatedAt(now);
            JobSeekerProfile p2 = new JobSeekerProfile().setId(id2).setEmail("dev2@test.com").setCreatedAt(now).setUpdatedAt(now);

            repository.saveAll(List.of(p1, p2));
            entityManager.flush();
            entityManager.clear();

            List<UserEmailProjection> projections = repository.findEmailsByUserIds(Set.of(id1, id2));

            assertThat(projections).hasSize(2);
            assertThat(projections)
                  .extracting(UserEmailProjection::getEmail)
                  .containsExactlyInAnyOrder("dev1@test.com", "dev2@test.com");
        }

        @Test
        void findEmailsByUserIds_WhenEmptySetPassed_ShouldReturnEmptyList() {
            List<UserEmailProjection> projections = repository.findEmailsByUserIds(Set.of());

            assertThat(projections).isEmpty();
        }
    }
}