package com.echcherqaoui.jobboard.jobservice.repository;

import com.echcherqaoui.jobboard.jobservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.jobservice.model.ProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.OffsetDateTime;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessedEventRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void saveAndExistsById_ShouldPersistProcessedEventAndVerifyExistence() {
        String eventId = "evt-processed-123";
        ProcessedEvent event = new ProcessedEvent();
        event.setEventId(eventId);
        event.setProcessedAt(OffsetDateTime.now(UTC));

        processedEventRepository.save(event);

        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }
}