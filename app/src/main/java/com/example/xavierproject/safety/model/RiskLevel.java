package com.example.xavierproject.safety.model;

public enum RiskLevel {
    VERY_SAFE, LOW_RISK, MODERATE_RISK, HIGH_RISK, CRITICAL_RISK;

    public static RiskLevel fromScore(double score) {
        if (score <= 20) return VERY_SAFE;
        if (score <= 40) return LOW_RISK;
        if (score <= 60) return MODERATE_RISK;
        if (score <= 80) return HIGH_RISK;
        return CRITICAL_RISK;
    }
}
