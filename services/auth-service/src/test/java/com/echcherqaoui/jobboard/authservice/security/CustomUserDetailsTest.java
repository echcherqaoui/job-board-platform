package com.echcherqaoui.jobboard.authservice.security;

import com.echcherqaoui.jobboard.authservice.enums.UserRole;
import com.echcherqaoui.jobboard.authservice.model.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.UUID;

import static com.echcherqaoui.jobboard.authservice.enums.UserRole.ADMIN;
import static com.echcherqaoui.jobboard.authservice.enums.UserRole.CANDIDATE;
import static com.echcherqaoui.jobboard.authservice.enums.UserRole.RECRUITER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsTest {

    private AppUser mockAppUser(UserRole role, boolean enabled) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(user.getEmail()).thenReturn("ahmed@example.com");
        when(user.getPassword()).thenReturn("hashed-pw");
        when(user.getFirstName()).thenReturn("Ahmed");
        when(user.getLastName()).thenReturn("EDER");
        when(user.isEnabled()).thenReturn(enabled);
        when(user.getRole()).thenReturn(role);
        return user;
    }

    @Test
    void mapsFieldsFromAppUser() {
        AppUser appUser = mockAppUser(RECRUITER, true);

        CustomUserDetails details = new CustomUserDetails(appUser);

        assertThat(details.getId()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(details.getEmail()).isEqualTo("ahmed@example.com");
        assertThat(details.getPassword()).isEqualTo("hashed-pw");
        assertThat(details.getFirstName()).isEqualTo("Ahmed");
        assertThat(details.getLastName()).isEqualTo("EDER");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void getUsernameReturnsEmail() {
        AppUser appUser = mockAppUser(CANDIDATE, true);

        CustomUserDetails details = new CustomUserDetails(appUser);

        assertThat(details.getUsername()).isEqualTo("ahmed@example.com");
    }

    @Test
    void authoritiesContainRoleNameWithoutPrefix() {
        AppUser appUser = mockAppUser(ADMIN, true);

        CustomUserDetails details = new CustomUserDetails(appUser);

        List<String> authorityNames = details.getAuthorities().stream()
              .map(GrantedAuthority::getAuthority)
              .toList();
        // No "ROLE_" prefix applied here — raw enum name only.
        assertThat(authorityNames).containsExactly("ADMIN");
    }

    @Test
    void accountStatusFlagsAreAlwaysTrue_regardlessOfEnabledState() {
        AppUser disabledUser = mockAppUser(CANDIDATE, false);

        CustomUserDetails details = new CustomUserDetails(disabledUser);

        // Current behavior: only `enabled` reflects AppUser state.
        // isAccountNonLocked/isAccountNonExpired/isCredentialsNonExpired
        // are unconditionally true because CustomUserDetails doesn't override them.
        assertThat(details.isEnabled()).isFalse();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.isAccountNonExpired()).isTrue();
        assertThat(details.isCredentialsNonExpired()).isTrue();
    }
}