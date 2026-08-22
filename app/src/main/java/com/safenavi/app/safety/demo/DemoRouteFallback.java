package com.safenavi.app.safety.demo;

import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.TravelMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, explicitly synthetic fallback for the classroom demo corridor.
 * Public OSM-backed services remain the primary route provider.
 */
public final class DemoRouteFallback {
    private static final PlaceSearchResult VASHI = new PlaceSearchResult(
            "Vashi Railway Station, Navi Mumbai", 19.06345, 72.99855);
    private static final PlaceSearchResult BELAPUR = new PlaceSearchResult(
            "CBD Belapur, Navi Mumbai", 19.01820, 73.03920);

    private DemoRouteFallback() { }

    public static PlaceSearchResult resolveKnownPlace(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (normalized.contains("vashi") && (normalized.contains("station")
                || normalized.contains("railway"))) return VASHI;
        if (normalized.contains("belapur")) return BELAPUR;
        return null;
    }

    public static List<RouteCandidate> routes(PlaceSearchResult source,
                                               PlaceSearchResult destination,
                                               TravelMode mode) {
        boolean forward = near(source, VASHI) && near(destination, BELAPUR);
        boolean reverse = near(source, BELAPUR) && near(destination, VASHI);
        if (!forward && !reverse) return Collections.emptyList();

        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.add(route("offline-demo-1", mode, 11_600, 28,
                points(19.06345, 72.99855, 19.0565, 73.0045, 19.0475, 73.0105,
                        19.0380, 73.0165, 19.0290, 73.0215, 19.0205, 73.0300,
                        19.01820, 73.03920), reverse));
        candidates.add(route("offline-demo-2", mode, 13_200, 33,
                points(19.06345, 72.99855, 19.0685, 73.0140, 19.0570, 73.0220,
                        19.0430, 73.0250, 19.0300, 73.0310, 19.01820, 73.03920), reverse));
        candidates.add(route("offline-demo-3", mode, 12_400, 31,
                points(19.06345, 72.99855, 19.0580, 73.0040, 19.0490, 73.0000,
                        19.0380, 73.0100, 19.0270, 73.0240, 19.01820, 73.03920), reverse));
        return candidates;
    }

    private static RouteCandidate route(String id, TravelMode mode, double meters,
                                        double drivingMinutes,
                                        List<HazardLocation.GeoPoint> geometry,
                                        boolean reverse) {
        if (reverse) Collections.reverse(geometry);
        double minutes = drivingMinutes;
        if (mode == TravelMode.WALKING) minutes = meters / 1000.0 / 4.8 * 60.0;
        if (mode == TravelMode.CYCLING) minutes = meters / 1000.0 / 15.0 * 60.0;
        return new RouteCandidate(id, minutes, meters, geometry);
    }

    private static boolean near(PlaceSearchResult first, PlaceSearchResult second) {
        return Math.abs(first.getLatitude() - second.getLatitude()) < 0.003
                && Math.abs(first.getLongitude() - second.getLongitude()) < 0.003;
    }

    private static List<HazardLocation.GeoPoint> points(double... values) {
        List<HazardLocation.GeoPoint> points = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2) {
            points.add(new HazardLocation.GeoPoint(values[i], values[i + 1]));
        }
        return points;
    }
}
