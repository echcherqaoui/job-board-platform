package com.echcherqaoui.jobboard.authservice.config.oauth2;

import com.echcherqaoui.jobboard.authservice.config.props.AuthServerProps;
import com.echcherqaoui.jobboard.authservice.security.CustomUserDetails;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.io.InputStream;
import java.security.KeyStore;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames.ID_TOKEN;
import static org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN;

@Configuration
@RequiredArgsConstructor
public class TokenConfig {
    private final AuthServerProps props;

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileSystemResource(props.keystorePath()).getInputStream()) {
                keyStore.load(is, props.keystorePassword().toCharArray());
            }

            RSAKey rsaKey = RSAKey.load(
                keyStore,
                props.keyAlias(),
                props.keystorePassword().toCharArray()
            );

            RSAKey rsaKeyWithId = new RSAKey.Builder(rsaKey)
                .keyID(props.keyAlias())
                .build();

            return new ImmutableJWKSet<>(new JWKSet(rsaKeyWithId));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWK keystore", e);
        }
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            Authentication principal = context.getPrincipal();
            if (!(principal.getPrincipal() instanceof CustomUserDetails user))
                return;

            context.getClaims().subject(user.getId())
                  .claim("given_name", user.getFirstName())
                  .claim("family_name", user.getLastName());

            // Access Token: Add Authorities/Roles
            if (ACCESS_TOKEN.equals(context.getTokenType())) {
                Set<String> authorities = principal.getAuthorities().stream()
                      .map(GrantedAuthority::getAuthority)
                      .collect(Collectors.toSet());

                context.getClaims()
                      .claim("email", user.getUsername())
                      .claim("roles", authorities);
            }

            // ID Token: Add Session ID and Personal Info
            if (ID_TOKEN.equals(context.getTokenType().getValue()) &&
                  principal.getDetails() instanceof WebAuthenticationDetails details) {
                    // Handle Session ID (sid) for OIDC Logout
                    String sessionId = details.getSessionId();
                    if (sessionId != null)
                        context.getClaims().claim("sid", sessionId);
            }
        };
    }
}