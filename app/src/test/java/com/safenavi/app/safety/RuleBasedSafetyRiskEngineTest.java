package com.safenavi.app.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.HazardLocationType;
import com.safenavi.app.safety.model.HazardSeverity;
import com.safenavi.app.safety.model.HazardStatus;
import com.safenavi.app.safety.model.RiskConfidence;
import com.safenavi.app.safety.model.SafetyScore;
import com.safenavi.app.safety.risk.RuleBasedSafetyRiskEngine;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RuleBasedSafetyRiskEngineTest {
    private static final long NOW = 1_720_000_000_000L;

    @Test public void noDataReturnsSafeButLowConfidenceResult() {
        SafetyScore result = new RuleBasedSafetyRiskEngine().calculate(19.07, 72.99, NOW,
                Collections.emptyList());
        assertEquals(0.0, result.getRiskScore(), 0.001);
        assertEquals(RiskConfidence.LOW, result.getConfidence());
        assertTrue(result.getExplanations().get(0).contains("No safety records"));
    }

    @Test public void nearbyVerifiedCriticalHazardRaisesRiskAndExplainsWhy() {
        Hazard hazard = hazard("Unsafe structure", 19.0701, 72.9901,
                HazardSeverity.CRITICAL, HazardStatus.ACTIVE, true);
        SafetyScore result = new RuleBasedSafetyRiskEngine().calculate(19.07, 72.99, NOW,
                Collections.singletonList(hazard));
        assertTrue(result.getRiskScore() > 80);
        assertEquals(1, result.getActiveHazardCount());
        assertEquals(1, result.getVerifiedHazardCount());
        assertTrue(result.getExplanations().get(0).contains("government-verified"));
    }

    @Test public void multipleVerifiedRecordsIncreaseConfidence() {
        SafetyScore result = new RuleBasedSafetyRiskEngine().calculate(19.07, 72.99, NOW,
                Arrays.asList(
                        hazard("Flooding", 19.0701, 72.9901, HazardSeverity.HIGH, HazardStatus.ACTIVE, true),
                        hazard("Poor lighting", 19.0702, 72.9902, HazardSeverity.MODERATE, HazardStatus.MONITORING, true)));
        assertEquals(RiskConfidence.HIGH, result.getConfidence());
    }

    static Hazard hazard(String title, double lat, double lon, HazardSeverity severity,
                         HazardStatus status, boolean verified) {
        Hazard hazard = new Hazard();
        hazard.setId(title);
        hazard.setTitle(title);
        hazard.setCategoryKey(title.toLowerCase().replace(' ', '_'));
        hazard.setReason("Test evidence");
        hazard.setLocation(new HazardLocation(HazardLocationType.POINT, lat, lon, title));
        hazard.setSeverity(severity);
        hazard.setStatus(status);
        hazard.setGovernmentVerified(verified);
        hazard.setCreatedAt(NOW);
        hazard.setUpdatedAt(NOW);
        return hazard;
    }
}
