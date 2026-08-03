package com.echcherqaoui.jobboard.authservice.init;

import com.echcherqaoui.jobboard.authservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.authservice.config.props.AuthServerProps;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ClientInitializerIT extends AbstractIntegrationTest {

    @Autowired
    private ClientInitializer clientInitializer;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private AuthServerProps authServerProps;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JWKSource<SecurityContext> jwkSource;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM oauth2_authorization");
        jdbcTemplate.execute("DELETE FROM oauth2_registered_client");
    }

    @Test
    @DisplayName("Should initialize clients in DB according to AuthServerProps")
    void run_ShouldSaveConfiguredClients_WhenDatabaseIsEmpty() {
        // Act
        clientInitializer.run();

        // Assert Primary Client
        RegisteredClient primaryClient = registeredClientRepository.findByClientId(authServerProps.clientId());
        assertThat(primaryClient).isNotNull();
        assertThat(passwordEncoder.matches(authServerProps.clientSecret(), primaryClient.getClientSecret())).isTrue();
        assertThat(primaryClient.getAuthorizationGrantTypes())
              .containsExactlyInAnyOrder(
                    AuthorizationGrantType.AUTHORIZATION_CODE,
                    AuthorizationGrantType.REFRESH_TOKEN
              );
        assertThat(primaryClient.getRedirectUris()).contains(authServerProps.redirectUri());
        assertThat(primaryClient.getScopes()).contains("openid", "profile");

        // Assert M2M Clients
        for (AuthServerProps.M2mClient m2m : authServerProps.m2mClients()) {
            RegisteredClient m2mClient = registeredClientRepository.findByClientId(m2m.clientId());
            assertThat(m2mClient).isNotNull();
            assertThat(passwordEncoder.matches(m2m.clientSecret(), m2mClient.getClientSecret())).isTrue();
            assertThat(m2mClient.getAuthorizationGrantTypes())
                  .containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
            assertThat(m2mClient.getScopes()).contains(m2m.scope());
        }
    }

    @Test
    @DisplayName("Should be idempotent and avoid creating duplicates on repeated runs")
    void run_ShouldNotDuplicateClients_WhenRunMultipleTimes() {
        // First execution
        clientInitializer.run();

        Integer countAfterFirstRun = jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM oauth2_registered_client", Integer.class
        );

        // Second execution
        clientInitializer.run();

        Integer countAfterSecondRun = jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM oauth2_registered_client", Integer.class
        );

        assertThat(countAfterSecondRun).isEqualTo(countAfterFirstRun);
    }
}