package com.safenavi.app.safety;

import com.safenavi.app.safety.model.*;
import com.safenavi.app.safety.service.RouteUserSummary;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteUserSummaryTest {
    private RouteSafetyResult route(boolean verified) {
        RouteCandidate candidate=new RouteCandidate("test",69.7,24758,Collections.emptyList());
        return new RouteSafetyResult(candidate,RouteProfile.FASTEST,42.75,69.7,Collections.singletonList(
                new RouteRiskPoint("test",19,73,"Test","synthetic diagnostics",42.75,verified,"test")));
    }
    @Test public void distanceAlwaysIncludesKilometresAndDoesNotHideShortTrips() {
        assertEquals("24.8 km",RouteUserSummary.distance(24758));
        assertEquals("0.5 km",RouteUserSummary.distance(500));
        assertEquals("0.04 km",RouteUserSummary.distance(40));
        assertEquals("0.0 km",RouteUserSummary.distance(0));
    }
    @Test public void invalidMetricsAreNotDisplayedAsAZeroDistanceOrTime() {
        assertEquals("Distance unavailable",RouteUserSummary.distance(Double.NaN));
        assertEquals("Distance unavailable",RouteUserSummary.distance(-1));
        assertEquals("Time unavailable",RouteUserSummary.duration(Double.POSITIVE_INFINITY));
        assertEquals("Time unavailable",RouteUserSummary.duration(-1));
    }
    @Test public void etaReadsLikeAnEverydayJourney() {
        assertEquals("21 min",RouteUserSummary.duration(20.6));
        assertEquals("1 hr 10 min",RouteUserSummary.duration(69.7));
        assertEquals("2 hr",RouteUserSummary.duration(120));
        assertEquals("1 min",RouteUserSummary.duration(.1));
    }
    @Test public void choicesContainDistanceAndTimeNotModelDiagnostics() {
        assertEquals("Fastest\n1 hr 10 min\n24.8 km",RouteUserSummary.choice(route(false),RouteProfile.FASTEST));
        assertEquals("24.8 km · 1 hr 10 min",RouteUserSummary.metrics(route(false)));
        assertFalse(RouteUserSummary.choice(route(false),RouteProfile.SAFEST).contains("Risk"));
    }
    @Test public void estimatesAreNeverCountedAsOfficialAlerts() {
        assertTrue(RouteUserSummary.alerts(route(false)).startsWith("No official alerts found"));
        assertTrue(RouteUserSummary.alerts(route(true)).startsWith("1 official alert along"));
        assertTrue(RouteUserSummary.purpose(RouteProfile.SAFEST).contains("not a guarantee"));
    }
    @Test public void directionsPreserveBothEndpointsAndTravelModeWithoutAnApiKey() {
        String url=RouteUserSummary.directions(19.1846144,72.9710278,19.0209123,73.0398886,TravelMode.WALKING);
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1&"));
        assertTrue(url.contains("origin=19.184614%2C72.971028"));
        assertTrue(url.contains("destination=19.020912%2C73.039889"));
        assertTrue(url.endsWith("travelmode=walking"));
        assertTrue(RouteUserSummary.directions(19,73,20,74,TravelMode.CYCLING).endsWith("travelmode=bicycling"));
        assertTrue(RouteUserSummary.directions(19,73,20,74,TravelMode.DRIVING).endsWith("travelmode=driving"));
    }
}
