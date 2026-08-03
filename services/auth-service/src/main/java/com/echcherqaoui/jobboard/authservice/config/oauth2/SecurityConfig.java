package com.echcherqaoui.jobboard.authservice.config.oauth2;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        OAuth2AuthorizationServerConfigurer authServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        return http
              .securityMatcher("/oauth2/**", "/connect/**", "/.well-known/**")
              .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
              .with(authServerConfigurer, endpoints -> endpoints
                    .oidc(oidc -> oidc
                          .logoutEndpoint(Customizer.withDefaults())
                    )
              ).exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
              ).build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
              .securityMatcher(EndpointRequest.toAnyEndpoint()) // Specifically targets actuator paths
              .authorizeHttpRequests(authorize -> authorize
                    .anyRequest().permitAll() // Allow health, info, etc.
              ).csrf(AbstractHttpConfigurer::disable)
              .build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain defaultSecurityFilterChain(
          HttpSecurity http,
          SessionRegistry sessionRegistry) throws Exception {

        return http
              .securityContext(context -> context.requireExplicitSave(false))
              .sessionManagement(session -> session
                    .sessionConcurrency(concurrency -> concurrency
                          .maximumSessions(1)
                          .sessionRegistry(sessionRegistry)
                    )
              ).authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/.well-known/**", "/oauth2/**", "/connect/**").permitAll()
                    .requestMatchers("/assets/**", "/css/**", "/js/**", "/images/**").permitAll()
                    .requestMatchers("/signup", "/error", "/login").permitAll()
                    .anyRequest().authenticated()
              ).formLogin(form -> form.loginPage("/login").permitAll())
              .logout(logout -> logout
                    .logoutUrl("/logout")
                    .logoutSuccessUrl("/login?logout=true")
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID")
              ).build();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}