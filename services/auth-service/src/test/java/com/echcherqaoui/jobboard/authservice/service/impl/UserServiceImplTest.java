package com.echcherqaoui.jobboard.authservice.service.impl;

import com.echcherqaoui.jobboard.authservice.dto.CreateUserRequest;
import com.echcherqaoui.jobboard.authservice.exception.domain.PasswordMismatchException;
import com.echcherqaoui.jobboard.authservice.exception.domain.UserAlreadyExistsException;
import com.echcherqaoui.jobboard.authservice.mapper.UserMapper;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import com.echcherqaoui.jobboard.authservice.repository.UserRepository;
import com.echcherqaoui.jobboard.authservice.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    private UserRepository userRepository;
    private UserMapper userMapper;
    private PasswordEncoder passwordEncoder;
    private OutboxService outboxService;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userMapper = mock(UserMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        outboxService = mock(OutboxService.class);
        service = new UserServiceImpl(userRepository, userMapper, passwordEncoder, outboxService);
    }

    private CreateUserRequest request(String role, String password, String confirmPassword) {
        CreateUserRequest req = mock(CreateUserRequest.class);
        when(req.getEmail()).thenReturn("ahmed@example.com");
        when(req.getUsername()).thenReturn("ahmed");
        when(req.getPassword()).thenReturn(password);
        when(req.getConfirmPassword()).thenReturn(confirmPassword);
        when(req.getRole()).thenReturn(role);
        return req;
    }

    @Test
    void passwordMismatch_throwsAndNeverSaves() {
        CreateUserRequest req = request("CANDIDATE", "pass1", "pass2");

        assertThatThrownBy(() -> service.createUser(req))
              .isInstanceOf(PasswordMismatchException.class);

        verifyNoInteractions(userRepository, outboxService);
    }

    @Test
    void emailAlreadyExists_throwsAndNeverSaves() {
        CreateUserRequest req = request("CANDIDATE", "pass1", "pass1");
        when(userRepository.existsByEmailIgnoreCase("ahmed@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(req))
              .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void usernameAlreadyExists_throwsAndNeverSaves() {
        CreateUserRequest req = request("CANDIDATE", "pass1", "pass1");
        when(userRepository.existsByEmailIgnoreCase("ahmed@example.com")).thenReturn(false);
        when(userRepository.existsByUsernameIgnoreCase("ahmed")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser(req))
              .isInstanceOf(UserAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void validCandidateRequest_encodesPasswordSavesAndPublishesJobSeekerEvent() {
        CreateUserRequest req = request("CANDIDATE", "raw-pass", "raw-pass");
        AppUser mappedUser = new AppUser();
        when(userMapper.toAppUser(req)).thenReturn(mappedUser);
        when(passwordEncoder.encode("raw-pass")).thenReturn("encoded-pass");

        service.createUser(req);

        verify(userRepository).save(mappedUser);

        // password must be encoded
        assert mappedUser.getPassword().equals("encoded-pass");
        verify(outboxService).publishJobSeekerCreated(mappedUser);
        verify(outboxService, never()).publishRecruiterCreated(any());
    }

    @Test
    void validRecruiterRequest_publishesRecruiterEvent() {
        CreateUserRequest req = request("RECRUITER", "raw-pass", "raw-pass");
        AppUser mappedUser = new AppUser();
        when(userMapper.toAppUser(req)).thenReturn(mappedUser);
        when(passwordEncoder.encode("raw-pass")).thenReturn("encoded-pass");

        service.createUser(req);

        verify(outboxService).publishRecruiterCreated(mappedUser);
        verify(outboxService, never()).publishJobSeekerCreated(any());
    }

    @Test
    void lowercaseRoleString_stillPublishesJobSeekerEvent() {
        CreateUserRequest req = request("candidate", "raw-pass", "raw-pass");
        AppUser mappedUser = new AppUser();
        when(userMapper.toAppUser(req)).thenReturn(mappedUser);
        when(passwordEncoder.encode("raw-pass")).thenReturn("encoded-pass");

        service.createUser(req);

        verify(userRepository).save(mappedUser);
        verify(outboxService).publishJobSeekerCreated(mappedUser);
    }

    @Test
    void adminRole_currentlyPublishesNoOutboxEvent_documentingGap() {
        CreateUserRequest req = request("ADMIN", "raw-pass", "raw-pass");
        AppUser mappedUser = new AppUser();
        when(userMapper.toAppUser(req)).thenReturn(mappedUser);
        when(passwordEncoder.encode("raw-pass")).thenReturn("encoded-pass");

        service.createUser(req);

        verify(userRepository).save(mappedUser);
        verifyNoInteractions(outboxService);
    }
}