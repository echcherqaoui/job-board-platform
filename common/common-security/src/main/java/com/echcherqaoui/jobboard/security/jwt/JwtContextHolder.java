package com.echcherqaoui.jobboard.security.jwt;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

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

    public String getFullName() {
        Jwt jwt = getJwt();
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");

        if (givenName != null || familyName != null)
            return String.join(" ",
                  givenName != null ? givenName : "",
                  familyName != null ? familyName : ""
            ).trim();

        // 3. Fallback to username or subject (sub)
        String username = jwt.getClaimAsString("preferred_username");
        return (username != null && !username.isBlank()) ? username : jwt.getSubject();
    }

    public AuthenticatedUser getAuthenticatedUser() {
        Jwt jwt = getJwt();
        String email = jwt.getClaimAsString("email");
        if (email == null) throw new IllegalStateException("Email claim missing");
        return new AuthenticatedUser(UUID.fromString(jwt.getSubject()), email);
    }
}