package com.example.xavierproject.safety;

import static org.junit.Assert.assertEquals;

import com.example.xavierproject.safety.model.PlaceSearchResult;
import com.example.xavierproject.safety.model.RouteCandidate;
import com.example.xavierproject.safety.network.OpenMapService;
import java.util.List;
import org.junit.Test;

public class OpenMapServiceParserTest {
    @Test public void parsesNominatimPlaceWithoutDependingOnNetwork() throws Exception {
        List<PlaceSearchResult> results = OpenMapService.parsePlaces(
                "[{\"display_name\":\"Vashi, Navi Mumbai\",\"lat\":\"19.076\",\"lon\":\"72.998\"}]");
        assertEquals(1, results.size());
        assertEquals("Vashi, Navi Mumbai", results.get(0).getDisplayName());
        assertEquals(19.076, results.get(0).getLatitude(), 0.0001);
    }

    @Test public void parsesValhallaTripAndPolyline6() throws Exception {
        String json = "{\"trip\":{\"summary\":{\"time\":600,\"length\":4.2},"
                + "\"legs\":[{\"shape\":\"A@\"}]}}";
        List<RouteCandidate> results = OpenMapService.parseRoutes(json);
        assertEquals(1, results.size());
        assertEquals(10.0, results.get(0).getTravelMinutes(), 0.001);
        assertEquals(4200.0, results.get(0).getDistanceMeters(), 0.001);
        assertEquals(0.000001, results.get(0).getGeometry().get(0).getLatitude(), 0.0000001);
        assertEquals(-0.000001, results.get(0).getGeometry().get(0).getLongitude(), 0.0000001);
    }
}
