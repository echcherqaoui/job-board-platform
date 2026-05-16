package com.echcherqaoui.jobboard.gatewayservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
              .authorizeExchange(exchanges -> exchanges
                    .pathMatchers("/*/actuator/**").denyAll()
                    .anyExchange().authenticated()
              ).oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));

        return http.build();
    }
}
