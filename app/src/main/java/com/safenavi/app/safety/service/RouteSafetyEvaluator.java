package com.safenavi.app.safety.service;

import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.model.RouteSafetyResult;
import com.safenavi.app.safety.model.SafetyScore;
import com.safenavi.app.safety.risk.SafetyRiskEngine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RouteSafetyEvaluator {
    private final SafetyRiskEngine riskEngine;

    public RouteSafetyEvaluator(SafetyRiskEngine riskEngine) {
        this.riskEngine = riskEngine;
    }

    public List<RouteSafetyResult> rank(List<RouteCandidate> providerCandidates, List<Hazard> hazards,
                                        RouteProfile profile, long atTimeMillis) {
        List<RouteSafetyResult> results = new ArrayList<>();
        for (RouteCandidate candidate : providerCandidates) {
            double exposure = exposure(candidate, hazards, atTimeMillis);
            double normalizedTravel = candidate.getTravelMinutes();
            double score = normalizedTravel + profile.getSafetyWeight() * exposure;
            results.add(new RouteSafetyResult(candidate, profile, exposure, score));
        }
        results.sort(Comparator.comparingDouble(RouteSafetyResult::getScore));
        return results;
    }

    private double exposure(RouteCandidate route, List<Hazard> hazards, long atTimeMillis) {
        if (route.getGeometry().isEmpty()) return 0;
        double sum = 0;
        for (HazardLocation.GeoPoint point : route.getGeometry()) {
            SafetyScore score = riskEngine.calculate(point.getLatitude(), point.getLongitude(),
                    atTimeMillis, hazards);
            sum += score.getRiskScore();
        }
        return sum / route.getGeometry().size();
    }
}
