package com.echcherqaoui.jobboard.authservice.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
                              boolean requirePkce) {
}