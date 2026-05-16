package com.echcherqaoui.jobboard.authservice.mapper;

import com.echcherqaoui.jobboard.authservice.dto.CreateUserRequest;
import com.echcherqaoui.jobboard.authservice.enums.UserRole;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public AppUser toAppUser(CreateUserRequest request) {
        return new AppUser()
              .setEmail(request.getEmail())
              .setUsername(request.getUsername())
              .setFirstName(request.getFirstName())
              .setLastName(request.getLastName())
              .setRole(UserRole.fromString(request.getRole()));
    }
}
