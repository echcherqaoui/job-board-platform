package com.echcherqaoui.jobboard.searchservice.mapper;

import com.echcherqaoui.jobboard.job.event.JobUpsertedEvent;
import com.echcherqaoui.jobboard.searchservice.document.JobDocument;
import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobMapperTest {

    private final JobMapper mapper = new JobMapper();

    private JobUpsertedEvent.Builder baseEventBuilder() {
        return JobUpsertedEvent.newBuilder()
              .setJobId("job-1")
              .setRecruiterId("recruiter-1")
              .setCompanyName("Acme")
              .setCompanyLogo("logo.png")
              .setTitle("Backend Engineer")
              .setDescription("Great role")
              .setRequirements("5 years Java")
              .setLocation("Casablanca")
              .setWorkModality("REMOTE")
              .setJobType("FULL_TIME")
              .setExperienceLevel("MID")
              .setSalaryMinCents(5_000_000L) // 50000.00
              .setSalaryMaxCents(8_000_000L) // 80000.00
              .setCurrency("USD")
              .setStatus("OPEN")
              .addAllSkills(java.util.List.of("Java", "Spring"))
              .setCreatedAt(Timestamp.newBuilder().setSeconds(1_700_000_000L).build());
    }

    @Test
    void toDocument_mapsAllScalarFields() {
        JobUpsertedEvent event = baseEventBuilder().build();

        JobDocument doc = mapper.toDocument(event);

        assertThat(doc.getId()).isEqualTo("job-1");
        assertThat(doc.getRecruiterId()).isEqualTo("recruiter-1");
        assertThat(doc.getCompanyName()).isEqualTo("Acme");
        assertThat(doc.getCompanyLogo()).isEqualTo("logo.png");
        assertThat(doc.getTitle()).isEqualTo("Backend Engineer");
        assertThat(doc.getDescription()).isEqualTo("Great role");
        assertThat(doc.getRequirements()).isEqualTo("5 years Java");
        assertThat(doc.getLocation()).isEqualTo("Casablanca");
        assertThat(doc.getWorkModality()).isEqualTo("REMOTE");
        assertThat(doc.getJobType()).isEqualTo("FULL_TIME");
        assertThat(doc.getExperienceLevel()).isEqualTo("MID");
        assertThat(doc.getCurrency()).isEqualTo("USD");
        assertThat(doc.getStatus()).isEqualTo("OPEN");
        assertThat(doc.getSkills()).containsExactly("Java", "Spring");
    }

    @Test
    void toDocument_convertsSalaryCentsToDecimal_correctly() {
        JobUpsertedEvent event = baseEventBuilder().build();

        JobDocument doc = mapper.toDocument(event);

        assertThat(doc.getSalaryMin()).isEqualTo(50000.0);
        assertThat(doc.getSalaryMax()).isEqualTo(80000.0);
    }

    @Test
    void toDocument_convertsCreatedAtTimestamp_toInstant() {
        JobUpsertedEvent event = baseEventBuilder().build();

        JobDocument doc = mapper.toDocument(event);

        assertThat(doc.getCreatedAt()).isEqualTo(java.time.Instant.ofEpochSecond(1_700_000_000L));
    }

    @Test
    void toDocument_convertsExplicitExpiresAt_whenSet() {
        JobUpsertedEvent event = baseEventBuilder()
              .setExpiresAt(Timestamp.newBuilder().setSeconds(1_800_000_000L).build())
              .build();

        JobDocument doc = mapper.toDocument(event);

        assertThat(doc.getExpiresAt()).isEqualTo(java.time.Instant.ofEpochSecond(1_800_000_000L));
    }
}