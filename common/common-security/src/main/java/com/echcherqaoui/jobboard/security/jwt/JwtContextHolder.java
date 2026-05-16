package com.echcherqaoui.jobboard.security.jwt;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtContextHolder {

    public Jwt getJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt)
            return jwt;

        throw new AuthenticationCredentialsNotFoundException("No valid JWT in SecurityContext");
    }

    public UUID getUserId() {
        return UUID.fromString(getJwt().getSubject());
    }

    public String getEmail() {
        String email = getJwt().getClaimAsString("email");
        if (email == null) throw new IllegalStateException("Email claim missing");
        return email;
    }

    public AuthenticatedUser getAuthenticatedUser() {
        Jwt jwt = getJwt();
        return new AuthenticatedUser(UUID.fromString(jwt.getSubject()), getEmail());
    }
}