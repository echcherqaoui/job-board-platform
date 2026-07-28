package com.echcherqaoui.jobboard.authservice.config.oauth2;

import com.echcherqaoui.jobboard.authservice.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames.ID_TOKEN;
import static org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN;

class TokenConfigTest {

    private static final String USER_ID = "user-123";
    private static final String FIRST_NAME = "Ahmed";
    private static final String LAST_NAME = "EDER";
    private static final String EMAIL = "ahmed@example.com";

    private OAuth2TokenCustomizer<JwtEncodingContext> customizer;

    @BeforeEach
    void setUp() {
        TokenConfig tokenConfig = new TokenConfig(null);
        customizer = tokenConfig.tokenCustomizer();
    }

    @NonNull
    private CustomUserDetails mockUser(GrantedAuthority... authorities) {
        CustomUserDetails user = mock(CustomUserDetails.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getFirstName()).thenReturn(FIRST_NAME);
        when(user.getLastName()).thenReturn(LAST_NAME);
        when(user.getUsername()).thenReturn(EMAIL);
        doReturn(List.of(authorities)).when(user).getAuthorities();
        return user;
    }

    @NonNull
    private Authentication mockPrincipal(Object principalDetails, List<GrantedAuthority> authorities, Object details) {
        Authentication principal = mock(Authentication.class);
        when(principal.getPrincipal()).thenReturn(principalDetails);
        doReturn(authorities).when(principal).getAuthorities();
        doReturn(details).when(principal).getDetails();
        return principal;
    }

    @NonNull
    private JwtEncodingContext mockContext(Authentication principal, OAuth2TokenType tokenType) {
        JwtEncodingContext context = mock(JwtEncodingContext.class);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder();
        when(context.getPrincipal()).thenReturn(principal);
        when(context.getTokenType()).thenReturn(tokenType);
        when(context.getClaims()).thenReturn(claims);
        return context;
    }

    @SuppressWarnings("unchecked")
    private Set<String> rolesOf(JwtClaimsSet claims) {
        return (Set<String>) claims.getClaims().get("roles");
    }

    @Test
    void nonCustomUserDetailsPrincipal_setsNoClaims() {
        Authentication principal = mockPrincipal("not-a-custom-user-details", List.of(), null);
        JwtEncodingContext context = mockContext(principal, ACCESS_TOKEN);

        customizer.customize(context);

        // context.getClaims() should never even be called when principal isn't CustomUserDetails
        verify(context, never()).getClaims();
    }

    @Test
    void accessToken_setsCoreAndRoleClaims() {
        GrantedAuthority admin = new SimpleGrantedAuthority("ROLE_ADMIN");
        CustomUserDetails user = mockUser(admin);
        Authentication principal = mockPrincipal(user, List.of(admin), null);
        JwtEncodingContext context = mockContext(principal, ACCESS_TOKEN);

        customizer.customize(context);

        JwtClaimsSet claims = context.getClaims().build();
        assertThat(claims.getSubject()).isEqualTo(USER_ID);
        assertThat(claims.getClaims())
              .containsEntry("given_name", FIRST_NAME)
              .containsEntry("family_name", LAST_NAME)
              .containsEntry("email", EMAIL);

        assertThat(rolesOf(claims)).containsExactlyInAnyOrder("ROLE_ADMIN");
    }

    @Test
    void accessToken_withMultipleRoles_setsAllRolesInClaim() {
        GrantedAuthority admin = new SimpleGrantedAuthority("ROLE_ADMIN");
        GrantedAuthority recruiter = new SimpleGrantedAuthority("ROLE_RECRUITER");
        CustomUserDetails user = mockUser(admin, recruiter);
        Authentication principal = mockPrincipal(user, List.of(admin, recruiter), null);
        JwtEncodingContext context = mockContext(principal, ACCESS_TOKEN);

        customizer.customize(context);

        JwtClaimsSet claims = context.getClaims().build();
        assertThat(rolesOf(claims)).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_RECRUITER");
    }

    @Test
    void accessToken_doesNotGetSidClaim() {
        GrantedAuthority admin = new SimpleGrantedAuthority("ROLE_ADMIN");
        CustomUserDetails user = mockUser(admin);
        Authentication principal = mockPrincipal(user, List.of(admin), null);
        JwtEncodingContext context = mockContext(principal, ACCESS_TOKEN);

        customizer.customize(context);

        JwtClaimsSet claims = context.getClaims().build();
        assertThat(claims.getClaims()).doesNotContainKey("sid");
    }

    @Test
    void idToken_withSessionId_setsSidClaim() {
        CustomUserDetails user = mockUser();
        WebAuthenticationDetails details = mock(WebAuthenticationDetails.class);
        when(details.getSessionId()).thenReturn("session-abc");
        Authentication principal = mockPrincipal(user, List.of(), details);
        JwtEncodingContext context = mockContext(principal, new OAuth2TokenType(ID_TOKEN));

        customizer.customize(context);

        JwtClaimsSet claims = context.getClaims().build();
        assertThat(claims.getClaims()).containsEntry("sid", "session-abc");
    }

    @Test
    void idToken_withoutSessionId_doesNotThrowAndOmitsSid() {
        CustomUserDetails user = mockUser();
        WebAuthenticationDetails details = mock(WebAuthenticationDetails.class);
        when(details.getSessionId()).thenReturn(null);
        Authentication principal = mockPrincipal(user, List.of(), details);
        JwtEncodingContext context = mockContext(principal, new OAuth2TokenType(ID_TOKEN));

        customizer.customize(context);

        JwtClaimsSet claims = context.getClaims().build();
        assertThat(claims.getClaims()).doesNotContainKey("sid");
    }

    @Test
    void idToken_doesNotGetRolesOrEmailClaim() {
        CustomUserDetails user = mockUser();
        Authentication principal = mockPrincipal(user, List.of(), List.of()); // no WebAuthenticationDetails, fine
        JwtEncodingContext context = mockContext(principal, new OAuth2TokenType(ID_TOKEN));

        customizer.customize(context);

        JwtClaimsSet claims = context.getClaims().build();
        assertThat(claims.getClaims())
              .doesNotContainKey("roles")
              .doesNotContainKey("email");
    }
}