package com.echcherqaoui.jobboard.authservice.model;

import com.echcherqaoui.jobboard.authservice.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.UUID;

import static com.echcherqaoui.jobboard.authservice.enums.UserRole.CANDIDATE;
import static jakarta.persistence.EnumType.STRING;

@Entity
@Table(name = "users", indexes = { @Index(name = "idx_user_email", columnList = "email") })
@Setter
@Getter
@Accessors(chain = true)
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(STRING)
    @Column(nullable = false)
    private UserRole role = CANDIDATE;
}