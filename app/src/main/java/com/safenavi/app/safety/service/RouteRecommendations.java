package com.safenavi.app.safety.service;

import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.model.RouteSafetyResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** One evaluated set, three transparent preferences. Never changes the physical route's risk or ETA. */
public final class RouteRecommendations {
    private RouteRecommendations() { }
    public static double weight(RouteProfile profile) {
        return profile == RouteProfile.SAFEST ? 1.0 : profile == RouteProfile.BALANCED ? 0.35 : 0.0;
    }
    public static List<RouteSafetyResult> rank(List<RouteSafetyResult> evaluated, RouteProfile profile) {
        List<RouteSafetyResult> output = new ArrayList<>();
        for (RouteSafetyResult value : evaluated) output.add(new RouteSafetyResult(value.getRoute(), profile,
                value.getRiskExposure(), value.getRoute().getTravelMinutes() + weight(profile) * value.getRiskExposure()));
        output.sort(Comparator.comparingDouble(RouteSafetyResult::getScore)
                .thenComparing(value -> value.getRoute().getProviderRouteId()));
        return output;
    }
}
