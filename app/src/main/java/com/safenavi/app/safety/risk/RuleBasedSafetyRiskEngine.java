package com.safenavi.app.safety.risk;

import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.HazardStatus;
import com.safenavi.app.safety.model.RiskConfidence;
import com.safenavi.app.safety.model.SafetyScore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class RuleBasedSafetyRiskEngine implements SafetyRiskEngine {
    private final RiskConfiguration configuration;

    public RuleBasedSafetyRiskEngine() {
        this(new RiskConfiguration());
    }

    public RuleBasedSafetyRiskEngine(RiskConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public SafetyScore calculate(double latitude, double longitude, long atTimeMillis, List<Hazard> hazards) {
        if (hazards == null || hazards.isEmpty()) {
            return new SafetyScore(0, RiskConfidence.LOW,
                    Collections.singletonList("No safety records are available near this location."),
                    0, 0);
        }

        double remainingSafeProbability = 1.0;
        int activeCount = 0;
        int verifiedCount = 0;
        List<Contribution> contributions = new ArrayList<>();

        for (Hazard hazard : hazards) {
            if (hazard == null || hazard.getLocation() == null || hazard.getSeverity() == null
                    || hazard.getStatus() == null || hazard.getStatus().isTerminal()) {
                continue;
            }

            HazardLocation location = hazard.getLocation();
            double distance = distanceMeters(latitude, longitude,
                    location.getLatitude(), location.getLongitude());
            if (distance > configuration.maximumRadiusMeters) continue;

            double distanceWeight = distanceWeight(distance);
            double statusWeight = statusWeight(hazard.getStatus());
            double verificationWeight = hazard.isGovernmentVerified()
                    ? configuration.verifiedMultiplier : configuration.unverifiedMultiplier;
            double ageHours = Math.max(0, atTimeMillis - hazard.getUpdatedAt()) / 3_600_000.0;
            double timeWeight = timeWeight(ageHours, hazard.getStatus());
            double recurrence = Math.min(12.0,
                    hazard.getHistoricalRecurrence() * configuration.recurrenceBonus);
            double confirmations = Math.min(8.0,
                    hazard.getLinkedReportCount() * configuration.confirmationBonus);
            double base = hazard.getSeverity().getBaseRisk() + recurrence + confirmations;
            double contribution = Math.min(95.0,
                    base * distanceWeight * statusWeight * verificationWeight * timeWeight);

            if (contribution <= 0.05) continue;
            remainingSafeProbability *= (1.0 - contribution / 100.0);
            if (hazard.getStatus().affectsImmediateNavigation()) activeCount++;
            if (hazard.isGovernmentVerified()) verifiedCount++;
            contributions.add(new Contribution(hazard, contribution, distance));
        }

        double risk = 100.0 * (1.0 - remainingSafeProbability);
        contributions.sort(Comparator.comparingDouble(Contribution::getValue).reversed());
        List<String> explanations = new ArrayList<>();
        for (int i = 0; i < Math.min(3, contributions.size()); i++) {
            Contribution item = contributions.get(i);
            String verified = item.hazard.isGovernmentVerified()
                    ? "government-verified" : "unverified";
            explanations.add(String.format(Locale.US, "%s %s is %.0f m away and adds %.1f risk points.",
                    verified, item.hazard.getTitle(), item.distance, item.value));
        }
        if (explanations.isEmpty()) {
            explanations.add("No currently relevant hazards fall within the configured radius.");
        }

        RiskConfidence confidence;
        if (verifiedCount >= 2) confidence = RiskConfidence.HIGH;
        else if (verifiedCount == 1 || contributions.size() >= 3) confidence = RiskConfidence.MEDIUM;
        else confidence = RiskConfidence.LOW;

        return new SafetyScore(risk, confidence, explanations, activeCount, verifiedCount);
    }

    private double statusWeight(HazardStatus status) {
        if (status == HazardStatus.RESOLVED) return configuration.resolvedMultiplier;
        if (status == HazardStatus.MONITORING) return configuration.monitoringMultiplier;
        if (status == HazardStatus.REPORTED || status == HazardStatus.UNDER_REVIEW) return 0.45;
        return 1.0;
    }

    private double timeWeight(double ageHours, HazardStatus status) {
        if (status == HazardStatus.ACTIVE || status == HazardStatus.MONITORING) {
            return Math.max(0.55, Math.pow(0.5, ageHours / (configuration.halfLifeHours * 4.0)));
        }
        return Math.max(0.1, Math.pow(0.5, ageHours / configuration.halfLifeHours));
    }

    private double distanceWeight(double meters) {
        if (meters <= 100) return 1.0;
        if (meters <= 300) return 0.8;
        if (meters <= 500) return 0.6;
        if (meters <= 1000) return 0.3;
        return 0.0;
    }

    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static class Contribution {
        final Hazard hazard;
        final double value;
        final double distance;

        Contribution(Hazard hazard, double value, double distance) {
            this.hazard = hazard;
            this.value = value;
            this.distance = distance;
        }

        double getValue() { return value; }
    }
}
