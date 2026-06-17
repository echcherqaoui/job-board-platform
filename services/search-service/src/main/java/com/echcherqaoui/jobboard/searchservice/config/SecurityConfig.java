package com.echcherqaoui.jobboard.searchservice.config;

import com.echcherqaoui.jobboard.security.jwt.JwtAuthConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final JwtAuthConverter jwtAuthConverter;
    private final String basePath;

    public SecurityConfig(JwtAuthConverter jwtAuthConverter,
                          @Value("${api.base-path}") String basePath) {
        this.jwtAuthConverter = jwtAuthConverter;
        this.basePath = basePath;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String searchPath = basePath + "/search";

        return http
              .csrf(AbstractHttpConfigurer::disable)
              .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
              .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, searchPath + "/**").authenticated()
                    .anyRequest().authenticated()
              ).oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
              ).build();
    }
}
