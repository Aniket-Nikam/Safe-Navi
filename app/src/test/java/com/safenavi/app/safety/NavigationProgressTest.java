package com.safenavi.app.safety;

import com.safenavi.app.navigation.NavigationProgress;
import com.safenavi.app.safety.model.*;
import com.safenavi.app.safety.model.HazardLocation.GeoPoint;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class NavigationProgressTest {
    private RouteCandidate route() {
        return new RouteCandidate("selected-road",10,2200,Arrays.asList(new GeoPoint(19,73),new GeoPoint(19.01,73),new GeoPoint(19.01,73.01)),
                Arrays.asList(new NavigationStep(0,"Head north"),new NavigationStep(1,"Turn right onto Test Road"),new NavigationStep(2,"You have arrived")));
    }
    @Test public void retainsInstructionsWithoutChangingRouteMetadata() {
        RouteCandidate road=route();
        assertEquals("selected-road",road.getProviderRouteId());assertEquals(3,road.getNavigationSteps().size());
        assertEquals(10,road.getTravelMinutes(),.001);assertEquals(2200,road.getDistanceMeters(),.001);
    }
    @Test public void advancesAlongActualShapeAndUpdatesRemainingDistanceAndTime() {
        NavigationProgress nav=new NavigationProgress(route());
        double start=nav.remainingMeters();nav.advance(nav.length()/2);
        assertEquals(start/2,nav.remainingMeters(),.01);assertEquals(5,nav.remainingMinutes(),.01);
        GeoPoint position=nav.position(); assertEquals(73,position.getLongitude(),.001);
        assertTrue(position.getLatitude()>19.005);
    }
    @Test public void upcomingInstructionIsAnchoredToItsOwnGeometry() {
        NavigationProgress nav=new NavigationProgress(route());assertEquals(1,nav.nextStepIndex());
        assertEquals("Turn right onto Test Road",nav.instruction());assertTrue(nav.toNextTurn()>1000);
        nav.advance(nav.stepMeters(1)+20);assertEquals(2,nav.nextStepIndex());
    }
    @Test public void offRouteFixCannotFalselyAdvanceJourney() {
        NavigationProgress nav=new NavigationProgress(route());nav.advance(100);
        assertTrue(nav.match(19.02,73.02)>60);assertEquals(100,nav.progress(),.01);
    }
    @Test public void onRouteFixAdvancesButJitterDoesNotRewind() {
        NavigationProgress nav=new NavigationProgress(route());assertTrue(nav.match(19.005,73)<1);
        double current=nav.progress();assertTrue(current>500);nav.match(19.0049,73);assertEquals(current,nav.progress(),.01);
    }
    @Test public void nearbyButNotAtDestinationDoesNotFalselyComplete() {
        NavigationProgress nav=new NavigationProgress(route());
        nav.match(19.01,73.0104);assertFalse(nav.arrived());
        nav.match(19.01,73.01);assertTrue(nav.arrived());
    }
    @Test public void simulationCompletesAndClampsWithoutRecordingGps() {
        NavigationProgress nav=new NavigationProgress(route());nav.advance(Double.POSITIVE_INFINITY);assertEquals(0,nav.progress(),0);
        nav.advance(nav.length()*2);assertTrue(nav.arrived());assertEquals(0,nav.remainingMeters(),0);
        assertTrue(nav.instruction().contains("end of this route"));
    }
    @Test public void missingInstructionsAreNotInvented() {
        NavigationProgress nav=new NavigationProgress(new RouteCandidate("offline",10,2200,route().getGeometry()));
        assertTrue(nav.instruction().contains("unavailable"));assertEquals(-1,nav.nextStepIndex());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsNoGeometry() {new NavigationProgress(new RouteCandidate("invalid",0,0,Collections.emptyList()));}
    @Test(expected=IllegalArgumentException.class) public void rejectsZeroLengthGeometry() {new NavigationProgress(new RouteCandidate("invalid",0,0,Arrays.asList(new GeoPoint(19,73),new GeoPoint(19,73))));}
}
