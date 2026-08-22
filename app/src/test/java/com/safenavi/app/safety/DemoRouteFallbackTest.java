package com.safenavi.app.safety;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.safenavi.app.safety.demo.DemoRouteFallback;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.TravelMode;
import java.util.List;
import org.junit.Test;

public class DemoRouteFallbackTest {
    @Test public void resolvesDemoPlacesAndBuildsThreeAlternatives() {
        PlaceSearchResult source = DemoRouteFallback.resolveKnownPlace("Vashi Railway Station");
        PlaceSearchResult destination = DemoRouteFallback.resolveKnownPlace("CBD Belapur");
        assertNotNull(source);
        assertNotNull(destination);

        List<RouteCandidate> routes = DemoRouteFallback.routes(source, destination, TravelMode.DRIVING);
        assertEquals(3, routes.size());
        assertTrue(routes.get(0).getGeometry().size() >= 2);
        assertTrue(routes.get(0).getTravelMinutes() > 0);
    }

    @Test public void walkingFallbackUsesARealisticSlowerDuration() {
        PlaceSearchResult source = DemoRouteFallback.resolveKnownPlace("Vashi station");
        PlaceSearchResult destination = DemoRouteFallback.resolveKnownPlace("Belapur");
        double drive = DemoRouteFallback.routes(source, destination, TravelMode.DRIVING)
                .get(0).getTravelMinutes();
        double walk = DemoRouteFallback.routes(source, destination, TravelMode.WALKING)
                .get(0).getTravelMinutes();
        assertTrue(walk > drive);
    }
}
