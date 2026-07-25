package com.echcherqaoui.jobboard.applicationservice.config;

import com.echcherqaoui.jobboard.security.jwt.JwtAuthConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
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
        String appPath = basePath + "/applications";

        return http.csrf(AbstractHttpConfigurer::disable)
              .sessionManagement(session -> session
                    .sessionCreationPolicy(STATELESS)
              ).authorizeHttpRequests(auth ->  auth
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers(POST, appPath).hasRole("CANDIDATE")
                    .requestMatchers(GET, appPath + "/my").hasRole("CANDIDATE")
                    .requestMatchers(GET, appPath + "/job/**").hasRole("RECRUITER")
                    .requestMatchers(GET, appPath + "/**").hasAnyRole("CANDIDATE", "RECRUITER")
                    .requestMatchers(PATCH, appPath + "/**").hasRole("RECRUITER")
                    .anyRequest().authenticated()
              ).oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
              ).build();
    }
}
