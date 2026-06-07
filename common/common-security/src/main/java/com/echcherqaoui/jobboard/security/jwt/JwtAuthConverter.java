package com.echcherqaoui.jobboard.security.jwt;

import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class JwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final String rolesClaim;

    public JwtAuthConverter(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractRoles(jwt).stream()
              .map(SimpleGrantedAuthority::new)
              .collect(Collectors.toUnmodifiableSet());

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    private Collection<String> extractRoles(@NonNull Jwt jwt) {
        Object roles = jwt.getClaim(rolesClaim);
        if (roles instanceof Collection<?> collection)
            return collection.stream()
                  .filter(String.class::isInstance)
                  .map(String.class::cast)
                  .collect(Collectors.toUnmodifiableSet());

        return Collections.emptySet();
    }
}