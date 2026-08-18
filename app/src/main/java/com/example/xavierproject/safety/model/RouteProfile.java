package com.example.xavierproject.safety.model;

public enum RouteProfile {
    FASTEST(0.20), BALANCED(1.00), SAFEST(2.25);

    private final double safetyWeight;

    RouteProfile(double safetyWeight) {
        this.safetyWeight = safetyWeight;
    }

    public double getSafetyWeight() { return safetyWeight; }
}
