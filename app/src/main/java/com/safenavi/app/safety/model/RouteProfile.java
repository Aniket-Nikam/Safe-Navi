package com.safenavi.app.safety.model;

public enum RouteProfile {
    FASTEST(0.0), BALANCED(0.35), SAFEST(1.0);

    private final double safetyWeight;

    RouteProfile(double safetyWeight) {
        this.safetyWeight = safetyWeight;
    }

    public double getSafetyWeight() { return safetyWeight; }
}
