package com.safenavi.app.safety.service;

import com.safenavi.app.safety.model.CitizenReport;
import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.risk.RuleBasedSafetyRiskEngine;

public class DuplicateReportDetector {
    private final double distanceThresholdMeters;
    private final long timeWindowMillis;

    public DuplicateReportDetector() {
        this(250.0, 24L * 60L * 60L * 1000L);
    }

    public DuplicateReportDetector(double distanceThresholdMeters, long timeWindowMillis) {
        this.distanceThresholdMeters = distanceThresholdMeters;
        this.timeWindowMillis = timeWindowMillis;
    }

    public boolean isLikelyDuplicate(CitizenReport report, Hazard hazard) {
        if (report == null || hazard == null || report.getLocation() == null || hazard.getLocation() == null) {
            return false;
        }
        if (report.getCategoryKey() == null || !report.getCategoryKey().equalsIgnoreCase(hazard.getCategoryKey())) {
            return false;
        }
        if (Math.abs(report.getCreatedAt() - hazard.getUpdatedAt()) > timeWindowMillis) return false;
        double distance = RuleBasedSafetyRiskEngine.distanceMeters(
                report.getLocation().getLatitude(), report.getLocation().getLongitude(),
                hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude());
        return distance <= distanceThresholdMeters;
    }
}
