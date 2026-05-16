package com.echcherqaoui.jobboard.authservice.security;

import com.echcherqaoui.jobboard.authservice.model.AppUser;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
@JsonAutoDetect(
      fieldVisibility = JsonAutoDetect.Visibility.ANY,
      getterVisibility = JsonAutoDetect.Visibility.NONE,
      isGetterVisibility = JsonAutoDetect.Visibility.NONE,
      setterVisibility = JsonAutoDetect.Visibility.NONE
)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomUserDetails implements UserDetails {

    private final String id;
    private final String email;
    private final String password;
    private final String firstName;
    private final String lastName;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    // Jackson Deserialization Constructor
    @JsonCreator
    public CustomUserDetails(
          @JsonProperty("id") String id,
          @JsonProperty("email") String email,
          @JsonProperty("password") String password,
          @JsonProperty("firstName") String firstName,
          @JsonProperty("lastName") String lastName,
          @JsonProperty("enabled") boolean enabled,
          @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities) {

        this.id = id;
        this.email = email;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = enabled;
        this.authorities = authorities != null ? new ArrayList<>(authorities) : new ArrayList<>();
    }

    public CustomUserDetails(AppUser user) {
        this.id = user.getId().toString();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.enabled = user.isEnabled();
        this.authorities = new ArrayList<>(List.of(
              new SimpleGrantedAuthority(user.getRole().name())
        ));
    }

    @Override
    public String getUsername() {
        return email;
    }
}