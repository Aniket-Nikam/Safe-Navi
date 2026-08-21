package com.safenavi.app.safety.model;

public enum HazardSeverity {
    LOW(20), MODERATE(40), HIGH(70), CRITICAL(90);

    private final int baseRisk;

    HazardSeverity(int baseRisk) {
        this.baseRisk = baseRisk;
    }

    public int getBaseRisk() {
        return baseRisk;
    }
}
