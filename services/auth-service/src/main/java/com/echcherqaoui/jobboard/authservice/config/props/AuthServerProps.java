package com.echcherqaoui.jobboard.authservice.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "auth.server")
public record AuthServerProps(String clientId,
                              String clientSecret,
                              String redirectUri,
                              String postLogoutRedirectUri,
                              String issuerUri,
                              String keystorePath,
                              String keystorePassword,
                              String keyAlias,
                              Duration accessTokenTtl,
                              Duration refreshTokenTtl,
                              boolean requireConsent,
                              boolean requirePkce,
                              List<M2mClient> m2mClients) {

    public record M2mClient(String clientId, String clientSecret, String scope) {}
}