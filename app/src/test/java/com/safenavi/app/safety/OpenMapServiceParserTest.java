package com.safenavi.app.safety;

import static org.junit.Assert.assertEquals;

import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.network.OpenMapService;
import java.util.List;
import org.junit.Test;

public class OpenMapServiceParserTest {
    @Test public void searchRowsKeepShortNameAndSeparateLocalityAndRemoveNearbyDuplicates() {
        PlaceSearchResult place = new PlaceSearchResult("Nahur, Mumbai, India", 19.15, 72.94);
        assertEquals("Nahur", place.getPrimaryText());
        assertEquals("Mumbai, India", place.getSecondaryText());
        assertEquals(2, OpenMapService.uniquePlaces(java.util.Arrays.asList(place,
                new PlaceSearchResult("Nahur, S Ward, Mumbai", 19.1501, 72.9401),
                new PlaceSearchResult("Nahur Station, Mumbai", 19.15, 72.94))).size());
    }
    @Test public void rejectsForeignAndWrongLocalityMatches() {
        List<PlaceSearchResult> values = java.util.Arrays.asList(
                new PlaceSearchResult("Shubh Aangan, Italy", 41.9, 12.5),
                new PlaceSearchResult("Shubh Aangan, K/W Ward, Mumbai", 19.1066613, 72.8335461),
                new PlaceSearchResult("Shubh Aangan, Nahur, Mumbai", 19.159, 72.946));
        List<PlaceSearchResult> matches = OpenMapService.filterSearchResults("Shubh Aangan, Nahur", values);
        assertEquals(1, matches.size());
        assertEquals("Shubh Aangan, Nahur, Mumbai", matches.get(0).getDisplayName());
    }

    @Test public void parsesFuzzyCollegeResultWithCorrectCoordinateOrder() throws Exception {
        String json = "{\"features\":[{\"geometry\":{\"coordinates\":[73.023428,19.0426435]},"
                + "\"properties\":{\"name\":\"SIES College, nerul\",\"district\":\"Nerul East\",\"city\":\"Navi Mumbai\"}}]}";
        List<PlaceSearchResult> results = OpenMapService.parsePhotonPlaces(json);
        assertEquals(1, results.size());
        assertEquals("SIES College, nerul, Nerul East, Navi Mumbai", results.get(0).getDisplayName());
        assertEquals(19.0426435, results.get(0).getLatitude(), 0.0000001);
        assertEquals(73.023428, results.get(0).getLongitude(), 0.0000001);
    }

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
