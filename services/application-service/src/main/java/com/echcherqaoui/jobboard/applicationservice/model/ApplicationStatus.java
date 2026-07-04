package com.echcherqaoui.jobboard.applicationservice.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ApplicationStatus {
    PENDING,
    REVIEWED,
    ACCEPTED,
    REJECTED,
    CANCELED;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
          PENDING,  EnumSet.of(REVIEWED, REJECTED, CANCELED),
          REVIEWED, EnumSet.of(ACCEPTED, REJECTED, CANCELED),
          ACCEPTED, EnumSet.of(CANCELED),
          REJECTED, EnumSet.noneOf(ApplicationStatus.class),
          CANCELED, EnumSet.noneOf(ApplicationStatus.class)
    );

    public boolean canTransitionTo(ApplicationStatus next) {
        return ALLOWED_TRANSITIONS.get(this).contains(next);
    }
}