package com.echcherqaoui.jobboard.authservice.config.oauth2;

import com.echcherqaoui.jobboard.authservice.config.props.AuthServerProps;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {
    private final AuthServerProps props;

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
              .issuer(props.issuerUri())
              .build();
    }
}