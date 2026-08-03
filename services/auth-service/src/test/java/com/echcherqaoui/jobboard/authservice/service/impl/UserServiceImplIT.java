package com.echcherqaoui.jobboard.authservice.service.impl;

import com.echcherqaoui.jobboard.authservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.authservice.dto.CreateUserRequest;
import com.echcherqaoui.jobboard.authservice.enums.UserRole;
import com.echcherqaoui.jobboard.authservice.exception.domain.PasswordMismatchException;
import com.echcherqaoui.jobboard.authservice.exception.domain.UserAlreadyExistsException;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import com.echcherqaoui.jobboard.authservice.repository.UserRepository;
import com.echcherqaoui.jobboard.authservice.service.UserService;
import com.echcherqaoui.jobboard.commonoutbox.model.OutboxEvent;
import com.echcherqaoui.jobboard.commonoutbox.repository.OutboxEventRepository;
import com.echcherqaoui.jobboard.security.service.SignatureService;
import com.google.protobuf.Message;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class UserServiceImplIT extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private KafkaProtobufSerializer<Message> serializer;

    @MockitoBean
    private SignatureService signatureService;

    @MockitoBean
    private JWKSource<SecurityContext> jwkSource;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        userRepository.deleteAll();

        when(serializer.serialize(anyString(), any(Message.class)))
              .thenAnswer(invocation -> {
                  Message proto = invocation.getArgument(1);
                  return proto.toByteArray();
              });

        when(signatureService.sign(anyString(), anyString(), anyString()))
              .thenReturn("mocked-hmac-signature");
    }

    private CreateUserRequest buildRequest(String email, String username, String role) {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail(email);
        request.setUsername(username);
        request.setFirstName("Ahmed");
        request.setLastName("Eder");
        request.setPassword("SecurePassword123");
        request.setConfirmPassword("SecurePassword123");
        request.setRole(role);
        return request;
    }

    @Test
    void createUser_CandidateRole_ShouldPersistUserAndOutboxEvent() {
        CreateUserRequest request = buildRequest("candidate@jobboard.com", "candidate_user", "CANDIDATE");

        userService.createUser(request);

        AppUser savedUser = userRepository.findByEmail("candidate@jobboard.com").orElseThrow();
        assertThat(savedUser.getFirstName()).isEqualTo("Ahmed");
        assertThat(savedUser.getLastName()).isEqualTo("Eder");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CANDIDATE);
        assertThat(passwordEncoder.matches("SecurePassword123", savedUser.getPassword())).isTrue();

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);

        OutboxEvent event = outboxEvents.get(0);
        assertThat(event.getAggregateType()).isEqualTo("auth");
        assertThat(event.getAggregateId()).isEqualTo(savedUser.getId().toString());
        assertThat(event.getEventType()).isEqualTo("job-seeker-registered");
        assertThat(event.getPayload()).isNotEmpty();
    }

    @Test
    void createUser_RecruiterRole_ShouldPersistUserAndRecruiterOutboxEvent() {
        CreateUserRequest request = buildRequest("recruiter@jobboard.com", "recruiter_user", "RECRUITER");

        userService.createUser(request);

        AppUser savedUser = userRepository.findByEmail("recruiter@jobboard.com").orElseThrow();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.RECRUITER);

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        assertThat(outboxEvents).hasSize(1);

        OutboxEvent event = outboxEvents.get(0);
        assertThat(event.getEventType()).isEqualTo("recruiter-registered");
    }

    @Test
    void createUser_DuplicateEmail_ShouldRollbackAndNotCreateOutbox() {
        CreateUserRequest request1 = buildRequest("duplicate@jobboard.com", "user_one", "CANDIDATE");
        userService.createUser(request1);

        CreateUserRequest request2 = buildRequest("duplicate@jobboard.com", "user_two", "CANDIDATE");

        assertThatThrownBy(() -> userService.createUser(request2))
              .isInstanceOf(UserAlreadyExistsException.class);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void createUser_PasswordMismatch_ShouldThrowExceptionAndNotPersistAnything() {
        CreateUserRequest request = buildRequest("mismatch@jobboard.com", "mismatch_user", "CANDIDATE");
        request.setConfirmPassword("WrongPassword123");

        assertThatThrownBy(() -> userService.createUser(request))
              .isInstanceOf(PasswordMismatchException.class);

        assertThat(userRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

}