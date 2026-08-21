package com.safenavi.app.safety;

import static org.junit.Assert.assertEquals;

import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.HazardSeverity;
import com.safenavi.app.safety.model.HazardStatus;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.model.RouteSafetyResult;
import com.safenavi.app.safety.risk.RuleBasedSafetyRiskEngine;
import com.safenavi.app.safety.service.RouteSafetyEvaluator;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RouteSafetyEvaluatorTest {
    @Test public void safestProfilePrefersSmallDetourWithFarLessExposure() {
        long now = 1_720_000_000_000L;
        Hazard hazard = RuleBasedSafetyRiskEngineTest.hazard("Flooding", 19.0700, 72.9900,
                HazardSeverity.CRITICAL, HazardStatus.ACTIVE, true);
        RouteCandidate shortRisky = new RouteCandidate("short-risky", 10, 4000,
                Collections.singletonList(new HazardLocation.GeoPoint(19.0700, 72.9900)));
        RouteCandidate safeDetour = new RouteCandidate("safe-detour", 14, 5200,
                Collections.singletonList(new HazardLocation.GeoPoint(19.0900, 73.0200)));

        List<RouteSafetyResult> ranked = new RouteSafetyEvaluator(new RuleBasedSafetyRiskEngine())
                .rank(Arrays.asList(shortRisky, safeDetour), Collections.singletonList(hazard),
                        RouteProfile.SAFEST, now);

        assertEquals("safe-detour", ranked.get(0).getRoute().getProviderRouteId());
    }
}
