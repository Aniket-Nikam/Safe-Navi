package com.example.xavierproject.safety;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.xavierproject.safety.model.CitizenReport;
import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.HazardLocation;
import com.example.xavierproject.safety.model.HazardLocationType;
import com.example.xavierproject.safety.model.HazardSeverity;
import com.example.xavierproject.safety.model.HazardStatus;
import com.example.xavierproject.safety.service.DuplicateReportDetector;
import org.junit.Test;

public class DuplicateReportDetectorTest {
    @Test public void matchesOnlyNearbyRecentSameCategoryReports() {
        long now = 1_720_000_000_000L;
        Hazard hazard = RuleBasedSafetyRiskEngineTest.hazard("Flooding", 19.0700, 72.9900,
                HazardSeverity.HIGH, HazardStatus.ACTIVE, true);
        hazard.setCategoryKey("flooding");
        hazard.setUpdatedAt(now);

        CitizenReport report = new CitizenReport();
        report.setCategoryKey("flooding");
        report.setLocation(new HazardLocation(HazardLocationType.POINT, 19.0705, 72.9905, "Nearby"));
        report.setCreatedAt(now);
        DuplicateReportDetector detector = new DuplicateReportDetector();
        assertTrue(detector.isLikelyDuplicate(report, hazard));

        report.setCategoryKey("poor_lighting");
        assertFalse(detector.isLikelyDuplicate(report, hazard));
    }
}
