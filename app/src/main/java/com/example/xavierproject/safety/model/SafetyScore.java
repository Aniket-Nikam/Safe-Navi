package com.example.xavierproject.safety.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SafetyScore {
    private final double riskScore;
    private final double safetyScore;
    private final RiskLevel riskLevel;
    private final RiskConfidence confidence;
    private final List<String> explanations;
    private final int activeHazardCount;
    private final int verifiedHazardCount;

    public SafetyScore(double riskScore, RiskConfidence confidence, List<String> explanations,
                       int activeHazardCount, int verifiedHazardCount) {
        this.riskScore = Math.max(0, Math.min(100, riskScore));
        this.safetyScore = 100 - this.riskScore;
        this.riskLevel = RiskLevel.fromScore(this.riskScore);
        this.confidence = confidence;
        this.explanations = new ArrayList<>(explanations);
        this.activeHazardCount = activeHazardCount;
        this.verifiedHazardCount = verifiedHazardCount;
    }

    public double getRiskScore() { return riskScore; }
    public double getSafetyScore() { return safetyScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public RiskConfidence getConfidence() { return confidence; }
    public List<String> getExplanations() { return Collections.unmodifiableList(explanations); }
    public int getActiveHazardCount() { return activeHazardCount; }
    public int getVerifiedHazardCount() { return verifiedHazardCount; }
}
