package com.echcherqaoui.jobboard.authservice.service.impl;

import com.echcherqaoui.jobboard.authservice.dto.CreateUserRequest;
import com.echcherqaoui.jobboard.authservice.enums.UserRole;
import com.echcherqaoui.jobboard.authservice.exception.domain.PasswordMismatchException;
import com.echcherqaoui.jobboard.authservice.exception.domain.UserAlreadyExistsException;
import com.echcherqaoui.jobboard.authservice.mapper.UserMapper;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import com.echcherqaoui.jobboard.authservice.repository.UserRepository;
import com.echcherqaoui.jobboard.authservice.service.OutboxService;
import com.echcherqaoui.jobboard.authservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.echcherqaoui.jobboard.authservice.enums.UserRole.CANDIDATE;
import static com.echcherqaoui.jobboard.authservice.enums.UserRole.RECRUITER;
import static com.echcherqaoui.jobboard.authservice.exception.enums.AuthErrorCode.EMAIL_ALREADY_EXISTS;
import static com.echcherqaoui.jobboard.authservice.exception.enums.AuthErrorCode.USERNAME_ALREADY_EXISTS;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final OutboxService outboxService;

    @Transactional
    @Override
    public void createUser(@Valid CreateUserRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword()))
            throw new PasswordMismatchException();

        if (userRepository.existsByEmailIgnoreCase(request.getEmail()))
            throw new UserAlreadyExistsException(EMAIL_ALREADY_EXISTS, request.getEmail());

        if (userRepository.existsByUsernameIgnoreCase(request.getUsername()))
            throw new UserAlreadyExistsException(USERNAME_ALREADY_EXISTS, request.getUsername());

        AppUser user = userMapper.toAppUser(request)
              .setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);

        UserRole userRole = UserRole.fromString(request.getRole());

        if (userRole == CANDIDATE)
            outboxService.publishJobSeekerCreated(user);
        else if (userRole == RECRUITER)
            outboxService.publishRecruiterCreated(user);
    }
}