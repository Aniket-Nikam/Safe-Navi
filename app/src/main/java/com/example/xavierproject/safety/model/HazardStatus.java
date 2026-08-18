package com.example.xavierproject.safety.model;

public enum HazardStatus {
    REPORTED,
    UNDER_REVIEW,
    VERIFIED,
    ACTIVE,
    MONITORING,
    RESOLVED,
    CLOSED,
    REJECTED,
    DUPLICATE;

    public boolean affectsImmediateNavigation() {
        return this == VERIFIED || this == ACTIVE || this == MONITORING;
    }

    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED || this == DUPLICATE;
    }
}
