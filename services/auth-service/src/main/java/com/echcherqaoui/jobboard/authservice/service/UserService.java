package com.echcherqaoui.jobboard.authservice.service;

import com.echcherqaoui.jobboard.authservice.dto.CreateUserRequest;
import jakarta.validation.Valid;

public interface UserService {
    void createUser(@Valid CreateUserRequest request);
}
