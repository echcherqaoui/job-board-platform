package com.echcherqaoui.jobboard.bffservice.config;

import com.echcherqaoui.jobboard.bffservice.config.props.BffProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.server.DefaultServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    private final BffProps bffProps;

    public SecurityConfig(BffProps bffProps) {
        this.bffProps = bffProps;
    }

    private ServerLogoutSuccessHandler logoutHandler(ReactiveClientRegistrationRepository clientRepository) {
        var logoutHandler = new OidcClientInitiatedServerLogoutSuccessHandler(clientRepository);
        logoutHandler.setPostLogoutRedirectUri(bffProps.postLogoutRedirectUri());
        return logoutHandler;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ServerOAuth2AuthorizationRequestResolver resolver,
                                                            ReactiveClientRegistrationRepository clientRepository) {
        http
              .authorizeExchange(exchanges -> exchanges
                    .anyExchange().authenticated()
              )
              .oauth2Login(oauth2 -> oauth2
                    .authorizationRequestResolver(resolver)
              ).csrf(csrf -> csrf
                    .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
              ).logout(logout -> logout
                    .logoutUrl("/api/logout")
                    .logoutSuccessHandler(logoutHandler(clientRepository))
              );

        return http.build();
    }

    @Bean
    public ServerOAuth2AuthorizationRequestResolver pkceResolver(ReactiveClientRegistrationRepository repo) {
        var resolver = new DefaultServerOAuth2AuthorizationRequestResolver(repo);
        // This is what generates the "Hint" correctly for Reactive apps
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }
}