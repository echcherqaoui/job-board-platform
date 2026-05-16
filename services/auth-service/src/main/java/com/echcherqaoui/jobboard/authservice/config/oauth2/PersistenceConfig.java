package com.echcherqaoui.jobboard.authservice.config.oauth2;

import com.echcherqaoui.jobboard.authservice.security.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;

@Configuration
public class PersistenceConfig {

    @Bean
    public RegisteredClientRepository clientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public OAuth2AuthorizationService oAuth2AuthorizationService(JdbcOperations jdbcOperations,
                                                                 RegisteredClientRepository clientRepository) {

        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(jdbcOperations, clientRepository);

        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.registerModules(
              SecurityJackson2Modules.getModules(CustomUserDetails.class.getClassLoader())
        );

        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());

        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
              new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(clientRepository);
        rowMapper.setObjectMapper(objectMapper);

        JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper parametersMapper =
              new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        parametersMapper.setObjectMapper(objectMapper);

        service.setAuthorizationRowMapper(rowMapper);
        service.setAuthorizationParametersMapper(parametersMapper);

        return service;
    }
}