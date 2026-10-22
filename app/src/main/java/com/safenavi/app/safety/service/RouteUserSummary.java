package com.safenavi.app.safety.service;

import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.model.RouteSafetyResult;
import com.safenavi.app.safety.model.TravelMode;
import java.util.Locale;

/** Consumer-facing copy. Keep model diagnostics separate from journey information. */
public final class RouteUserSummary {
    private RouteUserSummary() { }

    public static String distance(double meters) {
        if (!Double.isFinite(meters) || meters < 0) return "Distance unavailable";
        return String.format(Locale.US, meters > 0 && meters < 100 ? "%.2f km" : "%.1f km", meters / 1000);
    }

    public static String duration(double minutes) {
        if (!Double.isFinite(minutes) || minutes < 0) return "Time unavailable";
        long rounded = Math.max(minutes > 0 ? 1 : 0, Math.round(minutes));
        if (rounded < 60) return rounded + " min";
        return rounded / 60 + " hr" + (rounded % 60 == 0 ? "" : " " + rounded % 60 + " min");
    }

    public static String metrics(RouteSafetyResult result) {
        return distance(result.getRoute().getDistanceMeters()) + " · " + duration(result.getRoute().getTravelMinutes());
    }

    public static String preference(RouteProfile profile) {
        return profile == RouteProfile.FASTEST ? "Fastest" : profile == RouteProfile.SAFEST ? "Safest" : "Balanced";
    }

    public static String choice(RouteSafetyResult result, RouteProfile profile) {
        return preference(profile) + "\n" + duration(result.getRoute().getTravelMinutes()) + "\n" + distance(result.getRoute().getDistanceMeters());
    }

    public static String purpose(RouteProfile profile) {
        if (profile == RouteProfile.FASTEST) return "The quickest estimated journey.";
        if (profile == RouteProfile.SAFEST) return "The lowest estimated risk among the available routes, not a guarantee of safety.";
        return "Weighs journey time against estimated risk.";
    }

    public static String alerts(RouteSafetyResult result) {
        long count = result.getRiskPoints().stream().filter(point -> point.isVerified()).count();
        return count == 0 ? "No official alerts found along this route. Stay aware of your surroundings."
                : count + (count == 1 ? " official alert" : " official alerts") + " along this route. Tap an alert pin for details.";
    }

    /** Universal directions link: preserves the selected A, B and mode, not Safe-Navi's road geometry. */
    public static String directions(double fromLat, double fromLon, double toLat, double toLon, TravelMode mode) {
        String travel = mode == TravelMode.WALKING ? "walking" : mode == TravelMode.CYCLING ? "bicycling" : "driving";
        return String.format(Locale.US, "https://www.google.com/maps/dir/?api=1&origin=%.6f%%2C%.6f&destination=%.6f%%2C%.6f&travelmode=%s",
                fromLat, fromLon, toLat, toLon, travel);
    }
}
