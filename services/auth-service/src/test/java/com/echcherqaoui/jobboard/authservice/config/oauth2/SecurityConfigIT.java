package com.echcherqaoui.jobboard.authservice.config.oauth2;

import com.echcherqaoui.jobboard.authservice.AbstractIntegrationTest;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JWKSource<SecurityContext> jwkSource;

    private RegisteredClient registeredClient;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
              .privateKey((RSAPrivateKey) keyPair.getPrivate())
              .keyID(UUID.randomUUID().toString())
              .build();
        JWKSet jwkSet = new JWKSet(rsaKey);

        Mockito.when(jwkSource.get(any(), any())).thenReturn(jwkSet.getKeys());

        jdbcTemplate.execute("DELETE FROM oauth2_authorization");
        jdbcTemplate.execute("DELETE FROM oauth2_registered_client");

        registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
              .clientId("test-client")
              .clientSecret(passwordEncoder.encode("secret"))
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
              .scope(OidcScopes.OPENID)
              .build();

        registeredClientRepository.save(registeredClient);
    }

    @Nested
    class AuthorizationServerEndpoints {

        @Test
        void getOpenIdConfiguration_ShouldReturnDiscoveryDocument() throws Exception {
            mockMvc.perform(get("/.well-known/openid-configuration"))
                  .andExpect(status().isOk())
                  .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                  .andExpect(jsonPath("$.issuer", notNullValue()))
                  .andExpect(jsonPath("$.token_endpoint", notNullValue()))
                  .andExpect(jsonPath("$.jwks_uri", notNullValue()));
        }

        @Test
        void tokenEndpoint_ClientCredentialsGrant_ValidClient_ShouldReturnAccessToken() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(registeredClient.getClientId(), "secret"))
                        .param("grant_type", "client_credentials")
                        .param("scope", registeredClient.getScopes().iterator().next()))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.access_token", notNullValue()))
                  .andExpect(jsonPath("$.token_type").value("Bearer"))
                  .andExpect(jsonPath("$.expires_in", notNullValue()));
        }

        @Test
        void tokenEndpoint_InvalidClientCredentials_ShouldReturnUnauthorized() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("invalid-client-id", "invalid-secret"))
                        .param("grant_type", "client_credentials"))
                  .andExpect(status().isUnauthorized());
        }

        @Test
        void tokenEndpoint_UnsupportedGrantType_ShouldReturnBadRequest() throws Exception {
            mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(registeredClient.getClientId(), "secret"))
                        .param("grant_type", "invalid_grant_type"))
                  .andExpect(status().isBadRequest());
        }

        @Test
        void jwksEndpoint_ShouldReturnJwkSet() throws Exception {
            mockMvc.perform(get("/oauth2/jwks"))
                  .andExpect(status().isOk())
                  .andExpect(jsonPath("$.keys").isArray());
        }
    }

    @Nested
    class DefaultFilterChainEndpoints {

        @Test
        void protectedResource_Unauthenticated_ShouldRedirectToLogin() throws Exception {
            mockMvc.perform(get("/user/me"))
                  .andExpect(status().is3xxRedirection())
                  .andExpect(header().string("Location", containsString("/login")));
        }
    }
}