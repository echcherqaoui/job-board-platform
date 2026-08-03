package com.echcherqaoui.jobboard.authservice.security;

import com.echcherqaoui.jobboard.authservice.AbstractIntegrationTest;
import com.echcherqaoui.jobboard.authservice.enums.UserRole;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import com.echcherqaoui.jobboard.authservice.repository.UserRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CustomUserDetailsServiceIT extends AbstractIntegrationTest {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private JWKSource<SecurityContext> jwkSource;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void loadUserByUsername_ExistingUser_ShouldReturnCustomUserDetailsWithCorrectMapping() {
        AppUser user = new AppUser()
              .setEmail("candidate@jobboard.com")
              .setUsername("candidate_user")
              .setFirstName("Ahmed")
              .setLastName("Eder")
              .setPassword("encoded_secret")
              .setRole(UserRole.CANDIDATE)
              .setEnabled(true);

        AppUser savedUser = userRepository.save(user);

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("candidate@jobboard.com");

        assertThat(userDetails).isInstanceOf(CustomUserDetails.class);

        CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
        assertThat(customUserDetails.getId()).isEqualTo(savedUser.getId().toString());
        assertThat(customUserDetails.getUsername()).isEqualTo("candidate@jobboard.com");
        assertThat(customUserDetails.getEmail()).isEqualTo("candidate@jobboard.com");
        assertThat(customUserDetails.getFirstName()).isEqualTo("Ahmed");
        assertThat(customUserDetails.getLastName()).isEqualTo("Eder");
        assertThat(customUserDetails.getPassword()).isEqualTo("encoded_secret");
        assertThat(customUserDetails.isEnabled()).isTrue();
        assertThat(customUserDetails.getAuthorities())
              .extracting(GrantedAuthority::getAuthority)
              .containsExactly("CANDIDATE");
    }

    @Test
    void loadUserByUsername_NonExistentEmail_ShouldThrowUsernameNotFoundException() {
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("nonexistent@jobboard.com"))
              .isInstanceOf(UsernameNotFoundException.class)
              .hasMessage("User not found: nonexistent@jobboard.com");
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void loadUserByUsername_AllRoles_ShouldMapRoleNameToGrantedAuthority(UserRole role) {
        AppUser user = new AppUser()
              .setEmail(role.name().toLowerCase() + "@jobboard.com")
              .setUsername(role.name().toLowerCase() + "_user")
              .setFirstName("Role")
              .setLastName("Test")
              .setPassword("encoded_secret")
              .setRole(role)
              .setEnabled(true);

        userRepository.save(user);

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(user.getEmail());

        // Fixed: Extracting string authority
        assertThat(userDetails.getAuthorities())
              .extracting(GrantedAuthority::getAuthority)
              .containsExactly(role.name());
    }
}