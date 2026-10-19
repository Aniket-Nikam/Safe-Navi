package com.safenavi.app.safety;

import static org.junit.Assert.*;
import com.safenavi.app.safety.service.RouteRecommendations;
import com.safenavi.app.safety.model.*;
import java.util.*;
import org.junit.Test;

public class RouteRecommendationsTest {
    private RouteSafetyResult result(String id, double minutes, double risk) {
        return new RouteSafetyResult(new RouteCandidate(id, minutes, 1000, Collections.emptyList()), RouteProfile.BALANCED, risk, 0);
    }
    @Test public void preferencesCanChooseThreeRealAlternatives() {
        List<RouteSafetyResult> values = Arrays.asList(result("a", 16.6, 86.54), result("b", 20.2, 53.82), result("c", 22, 49.29));
        assertEquals("a", RouteRecommendations.rank(values, RouteProfile.FASTEST).get(0).getRoute().getProviderRouteId());
        assertEquals("b", RouteRecommendations.rank(values, RouteProfile.BALANCED).get(0).getRoute().getProviderRouteId());
        assertEquals("c", RouteRecommendations.rank(values, RouteProfile.SAFEST).get(0).getRoute().getProviderRouteId());
    }
    @Test public void sameRoadKeepsSameRiskAndEtaButDifferentObjective() {
        List<RouteSafetyResult> values = Collections.singletonList(result("same", 20, 50));
        assertEquals(20, RouteRecommendations.rank(values, RouteProfile.FASTEST).get(0).getScore(), .001);
        assertEquals(37.5, RouteRecommendations.rank(values, RouteProfile.BALANCED).get(0).getScore(), .001);
        RouteSafetyResult safest = RouteRecommendations.rank(values, RouteProfile.SAFEST).get(0);
        assertEquals(50, safest.getScore(), .001);
        assertEquals(50, safest.getRiskExposure(), .001);
        assertEquals(20, safest.getRoute().getTravelMinutes(), .001);
    }
    @Test public void safestPrioritisesRiskEvenWhenFasterRoadWinsWeightedSum() {
        List<RouteSafetyResult> values=Arrays.asList(result("quick",10,50),result("lower-risk",25,49));
        assertEquals("quick",RouteRecommendations.rank(values,RouteProfile.BALANCED).get(0).getRoute().getProviderRouteId());
        assertEquals("lower-risk",RouteRecommendations.rank(values,RouteProfile.SAFEST).get(0).getRoute().getProviderRouteId());
    }
    @Test public void equalRiskUsesTravelTimeBeforeProviderIdentifier() {
        List<RouteSafetyResult> values=Arrays.asList(result("a-slow",30,49),result("z-quick",20,49));
        assertEquals("z-quick",RouteRecommendations.rank(values,RouteProfile.SAFEST).get(0).getRoute().getProviderRouteId());
    }
    @Test public void apiPayloadNeverSamplesClosureGeometry() throws Exception {
        List<HazardLocation.GeoPoint> geometry = new ArrayList<>();
        for (int i = 0; i < 201; i++) geometry.add(new HazardLocation.GeoPoint(19 + i * .00001, 73));
        RouteCandidate route = new RouteCandidate("shape", 20, 2000, geometry);
        org.json.JSONObject payload = com.safenavi.app.safety.network.DatasetRiskService.buildRoutePayload(
                Collections.singletonList(route), RouteProfile.BALANCED, "midday");
        assertEquals(201, payload.getJSONArray("routes").getJSONObject(0).getJSONArray("coordinates").length());
    }
}
