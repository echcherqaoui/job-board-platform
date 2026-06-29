package com.echcherqaoui.jobboard.jobservice.config;

import com.echcherqaoui.jobboard.security.jwt.JwtAuthConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableMethodSecurity
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
        String jobPath = basePath + "/jobs";

        return http
              .csrf(AbstractHttpConfigurer::disable)
              .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
              .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health").permitAll()

                    // My jobs — company viewing their own listings
                    .requestMatchers(jobPath + "/my/**").hasRole("RECRUITER")

                    // Job management — only COMPANY role
                    .requestMatchers(POST, jobPath).hasRole("RECRUITER")
                    .requestMatchers(PUT, jobPath + "/**").hasRole("RECRUITER")
                    .requestMatchers(PATCH, jobPath + "/**").hasRole("RECRUITER")
                    .requestMatchers(DELETE, jobPath + "/**").hasRole("RECRUITER")

                    .anyRequest().authenticated()
              )
              .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
              ).build();
    }
}
