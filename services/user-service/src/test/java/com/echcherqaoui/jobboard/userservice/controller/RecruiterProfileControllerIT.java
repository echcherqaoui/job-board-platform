package com.echcherqaoui.jobboard.userservice.controller;

import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.user.event.CompanyUpsertedEvent;
import com.echcherqaoui.jobboard.userservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.userservice.dto.request.RecruiterProfileRequest;
import com.echcherqaoui.jobboard.userservice.enums.CompanySize;
import com.echcherqaoui.jobboard.userservice.model.RecruiterProfile;
import com.echcherqaoui.jobboard.userservice.repository.RecruiterProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecruiterProfileControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecruiterProfileRepository recruiterProfileRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        recruiterProfileRepository.deleteAll();

        // Standardized across services: bypass Confluent Schema Registry wire format
        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });
    }

    @Nested
    class Onboard {
        @Test
        void onboard_ShouldUpdateProfileAndWriteToOutboxInSameTransaction() throws Exception {
            // Arrange
            UUID recruiterId = UUID.randomUUID();

            RecruiterProfile profile = new RecruiterProfile();
            profile.setId(recruiterId);
            profile.setEmail("recruiter@acme.com");
            profile.setFirstName("John");
            profile.setLastName("Doe");
            profile.setOnboardingCompleted(false);
            recruiterProfileRepository.save(profile);

            RecruiterProfileRequest request = new RecruiterProfileRequest(
                  "Acme Corp",
                  CompanySize.MEDIUM,
                  "Desc",
                  "http://logos.com/acmepng",
                  "http://www.acmecorp.com",
                  "Rabat"
            );

            // Act
            mockMvc.perform(post("/api/v1/profiles/recruiters/onboard")
                  .with(jwt().jwt(jwt -> jwt.claim("sub", recruiterId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isOk());

            // Profile Updated
            RecruiterProfile updatedProfile = recruiterProfileRepository.findById(recruiterId).orElseThrow();
            assertThat(updatedProfile.getCompanyName()).isEqualTo("Acme Corp");
            assertThat(updatedProfile.isOnboardingCompleted()).isTrue();

            // Outbox Record Inserted
            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).hasSize(1);

            OutboxEvent event = outboxEvents.get(0);
            assertThat(event.getAggregateType()).isEqualTo("company");
            assertThat(event.getAggregateId()).isEqualTo(recruiterId.toString());
            assertThat(event.getEventType()).isEqualTo("company-upserted");
            assertThat(event.getPayload()).isNotEmpty();

            // Verification: Clean byte deserialization works identically to job-service
            CompanyUpsertedEvent parsedEvent = CompanyUpsertedEvent.parseFrom(event.getPayload());
            assertThat(parsedEvent.getCompanyName()).isEqualTo("Acme Corp");
            assertThat(parsedEvent.getRecruiterId()).isEqualTo(recruiterId.toString());
        }

        @Test
        void onboard_WhenValidationFails_ShouldReturn400AndWriteNothingToDatabase() throws Exception {
            // Arrange
            UUID recruiterId = UUID.randomUUID();

            RecruiterProfile profile = new RecruiterProfile();
            profile.setId(recruiterId);
            profile.setEmail("recruiter@acme.com");
            profile.setFirstName("John");
            profile.setLastName("Doe");
            profile.setOnboardingCompleted(false);
            recruiterProfileRepository.save(profile);

            // Invalid request: missing required companyName and invalid URL format
            RecruiterProfileRequest invalidRequest = new RecruiterProfileRequest(
                  "",
                  CompanySize.MEDIUM,
                  "Desc",
                  "not-a-valid-url",
                  "www.acmecorp.com",
                  "Rabat"
            );

            // Act & Assert
            mockMvc.perform(post("/api/v1/profiles/recruiters/onboard")
                  .with(jwt().jwt(jwt -> jwt.claim("sub", recruiterId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest))
            ).andExpect(status().isBadRequest());

            // Verify DB State: Profile unchanged, zero outbox events written
            RecruiterProfile unchangedProfile = recruiterProfileRepository.findById(recruiterId).orElseThrow();
            assertThat(unchangedProfile.isOnboardingCompleted()).isFalse();
            assertThat(unchangedProfile.getCompanyName()).isNull();

            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).isEmpty();
        }

        @Test
        void onboard_WhenOutboxSerializationFails_ShouldRollbackProfileUpdate() throws Exception {
            // Arrange
            UUID recruiterId = UUID.randomUUID();

            RecruiterProfile profile = new RecruiterProfile();
            profile.setId(recruiterId);
            profile.setEmail("recruiter@acme.com");
            profile.setFirstName("John");
            profile.setLastName("Doe");
            profile.setOnboardingCompleted(false);
            recruiterProfileRepository.save(profile);

            RecruiterProfileRequest request = new RecruiterProfileRequest(
                  "Acme Corp",
                  CompanySize.MEDIUM,
                  "Desc",
                  "https://logo.com/png",
                  "https://acmecorp.com",
                  "Rabat"
            );

            // Simulate an unrecoverable failure during outbox preparation
            when(serializer.serialize(anyString(), any(Message.class)))
                  .thenThrow(new RuntimeException("Simulated Outbox Failure"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/profiles/recruiters/onboard")
                  .with(jwt().jwt(jwt -> jwt.claim("sub", recruiterId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isInternalServerError());

            // Verify Transactional Integrity: DB changes rolled back completely
            RecruiterProfile rolledBackProfile = recruiterProfileRepository.findById(recruiterId).orElseThrow();
            assertThat(rolledBackProfile.isOnboardingCompleted()).isFalse();
            assertThat(rolledBackProfile.getCompanyName()).isNull();

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }

        @Test
        void onboard_WhenUserNotFound_ShouldReturn404AndWriteNothingToDatabase() throws Exception {
            // Arrange: Non-existent user ID
            UUID nonExistentUserId = UUID.randomUUID();

            RecruiterProfileRequest request = new RecruiterProfileRequest(
                  "Acme Corp",
                  CompanySize.MEDIUM,
                  "Desc",
                  "https://logo.com/png",
                  "https://acmecorp.com",
                  "Rabat"
            );

            // Act & Assert
            mockMvc.perform(post("/api/v1/profiles/recruiters/onboard")
                  .with(jwt().jwt(jwt -> jwt.claim("sub", nonExistentUserId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isNotFound());

            // Verify no outbox events produced
            assertThat(outboxEventRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class GetMe {
        @Test
        void getMe_WhenAuthenticated_ShouldReturnCurrentProfile() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            RecruiterProfile profile = new RecruiterProfile();
            profile.setId(recruiterId);
            profile.setEmail("recruiter@acme.com");
            profile.setFirstName("John");
            profile.setLastName("Doe");
            profile.setCompanyName("Acme Corp");
            profile.setOnboardingCompleted(true);
            recruiterProfileRepository.save(profile);

            mockMvc.perform(get("/api/v1/profiles/recruiters/me")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", recruiterId.toString())))
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.email").value("recruiter@acme.com"))
                  .andExpect(jsonPath("$.companyName").value("Acme Corp"));
        }

        @Test
        void getMe_WhenProfileNotFound_ShouldReturn404() throws Exception {
            UUID randomId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/profiles/recruiters/me")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", randomId.toString())))
                  )
                  .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateRecruiterProfile {
        @Test
        void updateRecruiterProfile_WhenValid_ShouldUpdateAndEmitOutboxEvent() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            RecruiterProfile profile = new RecruiterProfile();
            profile.setId(recruiterId);
            profile.setEmail("recruiter@acme.com");
            profile.setFirstName("John");
            profile.setLastName("Doe");
            profile.setCompanyName("Old Name");
            profile.setOnboardingCompleted(true);
            recruiterProfileRepository.save(profile);

            RecruiterProfileRequest updateRequest = new RecruiterProfileRequest(
                  "Updated Corp",
                  CompanySize.LARGE,
                  "Updated Desc",
                  "https://logo.com/new.png",
                  "https://newcorp.com",
                  "Casablanca"
            );

            mockMvc.perform(put("/api/v1/profiles/recruiters")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", recruiterId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.companyName").value("Updated Corp"));

            // Verify Database state
            RecruiterProfile updatedProfile = recruiterProfileRepository.findById(recruiterId).orElseThrow();
            assertThat(updatedProfile.getCompanyName()).isEqualTo("Updated Corp");

            // Verify Outbox Event created
            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
            assertThat(outboxEvents).hasSize(1);
            CompanyUpsertedEvent parsed = CompanyUpsertedEvent.parseFrom(outboxEvents.get(0).getPayload());
            assertThat(parsed.getCompanyName()).isEqualTo("Updated Corp");
        }

        @Test
        void updateRecruiterProfile_WhenInvalidPayload_ShouldReturn400() throws Exception {
            UUID recruiterId = UUID.randomUUID();

            RecruiterProfileRequest invalidRequest = new RecruiterProfileRequest(
                  "Updated Corp",
                  CompanySize.LARGE,
                  "Desc",
                  "not-a-valid-url", // Violates @URL constraint
                  "https://newcorp.com",
                  "Casablanca"
            );

            mockMvc.perform(put("/api/v1/profiles/recruiters")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", recruiterId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                  )
                  .andExpect(status().isBadRequest());

            assertThat(outboxEventRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class getRecruiterProfileById {

        @Test
        void getRecruiterProfileById_WhenExists_ShouldReturnProfile() throws Exception {
            UUID recruiterId = UUID.randomUUID();
            RecruiterProfile profile = new RecruiterProfile();
            profile.setId(recruiterId);
            profile.setEmail("public@acme.com");
            profile.setFirstName("Jane");
            profile.setLastName("Smith");
            profile.setCompanyName("Acme Public");
            recruiterProfileRepository.save(profile);

            mockMvc.perform(get("/api/v1/profiles/recruiters/{id}", recruiterId).with(jwt()))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.id").value(recruiterId.toString()))
                  .andExpect(jsonPath("$.companyName").value("Acme Public"));
        }

        @Test
        void getRecruiterProfileById_WhenNotExists_ShouldReturn404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/profiles/recruiters/{id}", nonExistentId).with(jwt()))
                  .andExpect(status().isNotFound());
        }
    }
}