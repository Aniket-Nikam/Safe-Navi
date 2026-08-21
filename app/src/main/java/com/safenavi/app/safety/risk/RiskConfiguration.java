package com.safenavi.app.safety.risk;

public class RiskConfiguration {
    public final double verifiedMultiplier;
    public final double unverifiedMultiplier;
    public final double resolvedMultiplier;
    public final double monitoringMultiplier;
    public final double recurrenceBonus;
    public final double confirmationBonus;
    public final double halfLifeHours;
    public final double maximumRadiusMeters;

    public RiskConfiguration() {
        this(1.0, 0.18, 0.05, 0.65, 2.5, 0.8, 48.0, 1000.0);
    }

    public RiskConfiguration(double verifiedMultiplier, double unverifiedMultiplier,
                             double resolvedMultiplier, double monitoringMultiplier,
                             double recurrenceBonus, double confirmationBonus,
                             double halfLifeHours, double maximumRadiusMeters) {
        this.verifiedMultiplier = verifiedMultiplier;
        this.unverifiedMultiplier = unverifiedMultiplier;
        this.resolvedMultiplier = resolvedMultiplier;
        this.monitoringMultiplier = monitoringMultiplier;
        this.recurrenceBonus = recurrenceBonus;
        this.confirmationBonus = confirmationBonus;
        this.halfLifeHours = halfLifeHours;
        this.maximumRadiusMeters = maximumRadiusMeters;
    }
}
