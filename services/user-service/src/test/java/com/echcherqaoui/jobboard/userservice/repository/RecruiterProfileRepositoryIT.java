package com.echcherqaoui.jobboard.userservice.repository;

import com.echcherqaoui.jobboard.userservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecruiterProfileRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private RecruiterProfileRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    @Nested
    class FindEmailById {

        @Test
        void findEmailById_WhenRecruiterExists_ShouldReturnEmail() {
            UUID id = UUID.randomUUID();
            OffsetDateTime now = OffsetDateTime.now();

            RecruiterProfile recruiter = new RecruiterProfile()
                  .setId(id)
                  .setEmail("recruiter@acme.com")
                  .setCompanyName("Acme Corp")
                  .setCreatedAt(now)
                  .setUpdatedAt(now);

            repository.save(recruiter);
            entityManager.flush();
            entityManager.clear();

            Optional<String> email = repository.findEmailById(id);

            assertThat(email).contains("recruiter@acme.com");
        }

        @Test
        void findEmailById_WhenRecruiterDoesNotExist_ShouldReturnEmptyOptional() {
            Optional<String> email = repository.findEmailById(UUID.randomUUID());

            assertThat(email).isEmpty();
        }
    }
}