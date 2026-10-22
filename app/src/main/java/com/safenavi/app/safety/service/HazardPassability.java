package com.safenavi.app.safety.service;

import org.json.JSONObject;

/** Mirrors the server's fail-closed flood policy for map labels and cached routes. */
public final class HazardPassability {
    private HazardPassability() { }
    public static boolean isFlood(JSONObject hazard) {
        return hazard != null && "FLOODING".equals(hazard.optString("category_key"));
    }
    public static boolean restricted(JSONObject hazard) {
        if (hazard == null) return false;
        String road = hazard.optString("road_status");
        return "CLOSED".equals(road)
                || ("CRITICAL".equals(hazard.optString("severity")) && "UNSAFE".equals(road))
                || (isFlood(hazard) && (!"PASSABLE".equals(hazard.optString("passability")) || "UNSAFE".equals(road)));
    }
    public static String floodLabel(JSONObject hazard) {
        if (!isFlood(hazard)) return "";
        if (!restricted(hazard)) return "Waterlogging · passable with caution";
        if ("CLOSED".equals(hazard.optString("road_status")) || "UNSAFE".equals(hazard.optString("road_status"))
                || "IMPASSABLE".equals(hazard.optString("passability"))) return "Flooding · road blocked";
        return "Waterlogging · passability unconfirmed";
    }
}
