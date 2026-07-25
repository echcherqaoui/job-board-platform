package com.echcherqaoui.jobboard.notificationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class OAuth2ClientConfig {
    /**
     * Configures a request-decoupled manager so non-web threads
     * can fetch and cache OAuth2 tokens without requiring an active HttpServletRequest context.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository clientRegistrationRepository,
                                                                 OAuth2AuthorizedClientService authorizedClientService) {
        AuthorizedClientServiceOAuth2AuthorizedClientManager manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
              clientRegistrationRepository,
              authorizedClientService
        );

        manager.setAuthorizedClientProvider(
              OAuth2AuthorizedClientProviderBuilder.builder()
                    .clientCredentials()
                    .build()
        );

        return manager;
    }
}