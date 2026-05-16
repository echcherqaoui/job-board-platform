package com.echcherqaoui.jobboard.security.jwt;

import java.util.UUID;

public record AuthenticatedUser(UUID userId,
                                String email) {}
