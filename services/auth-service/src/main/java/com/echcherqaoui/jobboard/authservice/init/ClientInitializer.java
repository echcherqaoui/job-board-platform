package com.echcherqaoui.jobboard.authservice.init;

import com.echcherqaoui.jobboard.authservice.config.props.AuthServerProps;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE;
import static org.springframework.security.oauth2.core.AuthorizationGrantType.REFRESH_TOKEN;
import static org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
import static org.springframework.security.oauth2.core.oidc.OidcScopes.OPENID;
import static org.springframework.security.oauth2.core.oidc.OidcScopes.PROFILE;

@Component
@RequiredArgsConstructor
public class ClientInitializer implements CommandLineRunner {

    private final RegisteredClientRepository repository;
    private final AuthServerProps props;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        ClientSettings clientSettings = ClientSettings.builder()
              .requireAuthorizationConsent(props.requireConsent())
              .requireProofKey(props.requirePkce())
              .build();

        TokenSettings tokenSettings = TokenSettings.builder()
              .accessTokenTimeToLive(props.accessTokenTtl())
              .refreshTokenTimeToLive(props.refreshTokenTtl())
              .reuseRefreshTokens(false)
              .build();

        // Check by clientId to ensure we don't duplicate on every restart
        if (repository.findByClientId(props.clientId()) == null) {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                  .clientId(props.clientId())
                  .clientSecret(encoder.encode(props.clientSecret()))
                  .clientAuthenticationMethod(CLIENT_SECRET_BASIC)
                  .authorizationGrantType(AUTHORIZATION_CODE)
                  .authorizationGrantType(REFRESH_TOKEN)
                  .redirectUri(props.redirectUri())
                  .scope(OPENID)
                  .scope(PROFILE)
                  .clientSettings(clientSettings)
                  .tokenSettings(tokenSettings)
                  .postLogoutRedirectUri(props.postLogoutRedirectUri())
                  .build();
            repository.save(client);
        }
    }
}