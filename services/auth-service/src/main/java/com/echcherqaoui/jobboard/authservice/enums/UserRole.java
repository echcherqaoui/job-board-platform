package com.echcherqaoui.jobboard.authservice.enums;

public enum UserRole {
    CANDIDATE,
    RECRUITER,
    ADMIN;

    public static UserRole fromString(String role) {
        try {
            return UserRole.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + role);
        }
    }
}