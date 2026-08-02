package com.echcherqaoui.jobboard.userservice.controller;

import com.echcherqaoui.jobboard.userservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.userservice.dto.request.EducationRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.ExperienceRequest;
import com.echcherqaoui.jobboard.userservice.dto.request.JobSeekerProfileRequest;
import com.echcherqaoui.jobboard.userservice.model.JobSeekerProfile;
import com.echcherqaoui.jobboard.userservice.repository.JobSeekerProfileRepository;
import com.echcherqaoui.jobboard.userservice.storage.CvStorageClient;
import com.echcherqaoui.jobboard.userservice.storage.CvUploadResult;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JobSeekerProfileControllerIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobSeekerProfileRepository profileRepository;

    @MockitoBean
    private CvStorageClient cvStorageClient;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @BeforeEach
    void setUp() {
        profileRepository.deleteAll();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });
    }

    @Nested
    class Onboard {

        @Test
        void onboard_WhenValidRequest_ShouldUpdateProfileAndSetOnboardingTrue() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("candidate@test.com")
                  .setFirstName("John")
                  .setLastName("Doe")
                  .setOnboardingCompleted(false);
            profileRepository.save(profile);

            JobSeekerProfileRequest request = new JobSeekerProfileRequest(
                  "+212600000000",
                  "Casablanca, Morocco",
                  "Full Stack Engineer",
                  "Passionate about software architecture",
                  "https://example.com/avatar.png",
                  "https://linkedin.com/in/johndoe",
                  "https://github.com/johndoe",
                  "https://johndoe.dev",
                  3,
                  List.of(),
                  List.of(),
                  List.of()
            );

            mockMvc.perform(post("/api/v1/profiles/job-seekers/onboard")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                  )
                  .andExpect(status().isOk());

            JobSeekerProfile updated = profileRepository.findById(userId).orElseThrow();
            assertThat(updated.isOnboardingCompleted()).isTrue();
            assertThat(updated.getPhone()).isEqualTo("+212600000000");
            assertThat(updated.getHeadline()).isEqualTo("Full Stack Engineer");
        }

        @Test
        void onboard_WhenValidationFails_ShouldReturn400() throws Exception {
            UUID userId = UUID.randomUUID();

            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("candidate@test.com")
                  .setOnboardingCompleted(false);

            profileRepository.save(profile);

            // Missing required phone and location for OnboardingGroup
            JobSeekerProfileRequest invalidRequest = new JobSeekerProfileRequest(
                  "",
                  "",
                  "Headline",
                  "Bio",
                  null, null, null, null,
                  -1, // Invalid years of experience
                  List.of(),
                  List.of(),
                  List.of()
            );

            mockMvc.perform(post("/api/v1/profiles/job-seekers/onboard")
                  .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(invalidRequest))
            ).andExpect(status().isBadRequest());
        }

        @Test
        void onboard_WhenAlreadyOnboarded_ShouldReturnConflictOrBadRequest() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("candidate@test.com")
                  .setOnboardingCompleted(true);
            profileRepository.save(profile);

            JobSeekerProfileRequest request = new JobSeekerProfileRequest(
                  "+212600000000",
                  "Casablanca",
                  "Dev",
                  "Bio",
                  null, null, null, null,
                  2,
                  List.of(), List.of(), List.of()
            );

            mockMvc.perform(post("/api/v1/profiles/job-seekers/onboard")
                  .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isBadRequest());
        }
    }

    @Nested
    class GetMyProfile {

        @Test
        void getMyProfile_WhenExists_ShouldReturnProfile() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("candidate@test.com")
                  .setFirstName("Jane")
                  .setLastName("Doe");

            profileRepository.save(profile);

            mockMvc.perform(get("/api/v1/profiles/job-seekers/me")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                  ).andExpect(status().isOk())
                  .andExpect(jsonPath("$.email").value("candidate@test.com"))
                  .andExpect(jsonPath("$.firstName").value("Jane"));
        }

        @Test
        void getMyProfile_WhenNotFound_ShouldReturn404() throws Exception {
            UUID randomId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/profiles/job-seekers/me")
                  .with(jwt().jwt(jwt -> jwt.claim("sub", randomId.toString())))
            ).andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        @Transactional
        void updateProfile_WhenValid_ShouldUpdateDataAndChildCollections() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("candidate@test.com")
                  .setFirstName("John")
                  .setLastName("Doe");
            profileRepository.saveAndFlush(profile);

            ExperienceRequest expReq = new ExperienceRequest(
                  null,
                  "Tech Corp",
                  "Senior Engineer",
                  "Remote",
                  LocalDate.of(2022, 1, 1),
                  null,
                  true,
                  "Working on microservices"
            );

            EducationRequest eduReq = new EducationRequest(
                  null,
                  "State University",
                  "B.S. Computer Science",
                  "Software Engineering",
                  LocalDate.of(2018, 9, 1),
                  LocalDate.of(2022, 6, 1),
                  false,
                  "Graduated with Honors"
            );

            JobSeekerProfileRequest updateRequest = new JobSeekerProfileRequest(
                  "+212611111111",
                  "Rabat, Morocco",
                  "Lead Architect",
                  "Updated bio text",
                  "https://example.com/new-avatar.png",
                  null, null, null,
                  5,
                  List.of(),
                  List.of(expReq),
                  List.of(eduReq)
            );

            mockMvc.perform(put("/api/v1/profiles/job-seekers")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest))
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.headline").value("Lead Architect"))
                  .andExpect(jsonPath("$.experiences").isArray())
                  .andExpect(jsonPath("$.experiences.length()").value(1))
                  .andExpect(jsonPath("$.educations").isArray())
                  .andExpect(jsonPath("$.educations.length()").value(1));

            JobSeekerProfile updated = profileRepository.findById(userId).orElseThrow();
            assertThat(updated.getHeadline()).isEqualTo("Lead Architect");
            assertThat(updated.getExperiences()).hasSize(1);
            assertThat(updated.getEducations()).hasSize(1);
        }
    }

    @Nested
    class DeleteProfile {

        @Test
        void deleteProfile_WhenExists_ShouldRemoveProfileFromDatabase() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("delete.me@test.com");
            profileRepository.save(profile);

            mockMvc.perform(delete("/api/v1/profiles/job-seekers")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                  )
                  .andExpect(status().isNoContent());

            assertThat(profileRepository.findById(userId)).isEmpty();
        }
    }

    @Nested
    class GetProfileById {

        @Test
        void getProfileById_WhenExists_ShouldReturnPublicProfile() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("public@test.com")
                  .setFirstName("Alice")
                  .setLastName("Smith");
            profileRepository.save(profile);

            mockMvc.perform(get("/api/v1/profiles/job-seekers/{id}", userId)
                        .with(jwt())
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.id").value(userId.toString()))
                  .andExpect(jsonPath("$.firstName").value("Alice"));
        }

        @Test
        void getProfileById_WhenNotExists_ShouldReturn404() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(get("/api/v1/profiles/job-seekers/{id}", nonExistentId)
                        .with(jwt())
                  )
                  .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UploadCv {
        @Test
        void uploadCv_WhenPdfFile_ShouldSaveUrlAndReturn200() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("cv.candidate@test.com");
            profileRepository.save(profile);

            MockMultipartFile pdfFile = new MockMultipartFile(
                  "file",
                  "resume.pdf",
                  "application/pdf",
                  "%PDF-1.4 dummy pdf content".getBytes()
            );

            when(cvStorageClient.uploadCv(any(), any()))
                  .thenReturn(new CvUploadResult("https://storage.provider.com/cvs/resume.pdf", "cv_pub_123"));

            mockMvc.perform(multipart("/api/v1/profiles/job-seekers/cv")
                        .file(pdfFile)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                  )
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.url").value("https://storage.provider.com/cvs/resume.pdf"));

            JobSeekerProfile updated = profileRepository.findById(userId).orElseThrow();
            assertThat(updated.getCvUrl()).isEqualTo("https://storage.provider.com/cvs/resume.pdf");
            assertThat(updated.getCvPublicId()).isEqualTo("cv_pub_123");
        }

        @Test
        void uploadCv_WhenInvalidFileType_ShouldReturnBadRequest() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("cv.candidate@test.com");
            profileRepository.save(profile);

            MockMultipartFile textFile = new MockMultipartFile(
                  "file",
                  "resume.txt",
                  "text/plain",
                  "Plain text content".getBytes()
            );

            mockMvc.perform(multipart("/api/v1/profiles/job-seekers/cv")
                        .file(textFile)
                        .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                  )
                  .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class DeleteCv {
        @Test
        void deleteCv_WhenCvExists_ShouldDeleteFromStorageAndClearProfile() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("cv.candidate@test.com")
                  .setCvUrl("https://storage.provider.com/cvs/resume.pdf")
                  .setCvPublicId("cv_pub_123");
            profileRepository.save(profile);

            doNothing().when(cvStorageClient).deleteCv("cv_pub_123");

            mockMvc.perform(delete("/api/v1/profiles/job-seekers/cv")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                  )
                  .andExpect(status().isNoContent());

            verify(cvStorageClient).deleteCv("cv_pub_123");

            JobSeekerProfile updated = profileRepository.findById(userId).orElseThrow();
            assertThat(updated.getCvUrl()).isNull();
            assertThat(updated.getCvPublicId()).isNull();
        }

        @Test
        void deleteCv_WhenNoCvExists_ShouldReturnNotFound() throws Exception {
            UUID userId = UUID.randomUUID();
            JobSeekerProfile profile = new JobSeekerProfile()
                  .setId(userId)
                  .setEmail("cv.candidate@test.com")
                  .setCvPublicId(null);
            profileRepository.save(profile);

            mockMvc.perform(delete("/api/v1/profiles/job-seekers/cv")
                        .with(jwt().jwt(jwt -> jwt.claim("sub", userId.toString())))
                  )
                  .andExpect(status().isNotFound()); // Expect 404 instead of 400
        }
    }
}