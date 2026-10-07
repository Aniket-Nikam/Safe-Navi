package com.safenavi.app.safety.network;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OfflineHazardCache {
    private static final String FILE = "safe_navi_verified_hazard_cache";
    private OfflineHazardCache() { }

    public static void save(Context context, JSONObject response) {
        context.getSharedPreferences(FILE, 0).edit().putString("response", response.toString())
                .putLong("updated_at", System.currentTimeMillis()).apply();
    }

    public static JSONObject load(Context context) {
        try { return new JSONObject(context.getSharedPreferences(FILE, 0).getString("response", "{\"hazards\":[]}")); }
        catch (Exception error) { return new JSONObject(); }
    }

    public static long ageMillis(Context context) { return System.currentTimeMillis() - context.getSharedPreferences(FILE, 0).getLong("updated_at", 0); }

    public static boolean isNoGo(Context context, double latitude, double longitude) {
        JSONArray hazards = load(context).optJSONArray("hazards");
        if (hazards == null) return false;
        for (int i = 0; i < hazards.length(); i++) {
            JSONObject hazard = hazards.optJSONObject(i); if (hazard == null) continue;
            boolean closed = "CLOSED".equals(hazard.optString("road_status"));
            boolean critical = "CRITICAL".equals(hazard.optString("severity")) && "UNSAFE".equals(hazard.optString("road_status"));
            if (!closed && !critical) continue;
            double radius = hazard.optDouble("no_go_radius_m", closed ? 60.0 : 40.0);
            if (distance(latitude, longitude, hazard) <= radius) return true;
        }
        return false;
    }

    private static double distance(double lat, double lon, JSONObject hazard) {
        JSONArray values = hazard.optJSONArray("geometry_coordinates");
        if (values == null || values.length() < 2 || "POINT".equals(hazard.optString("geometry_type", "POINT")))
            return haversine(lat, lon, hazard.optDouble("latitude"), hazard.optDouble("longitude"));
        double best = Double.MAX_VALUE;
        for (int i = 1; i < values.length(); i++) best = Math.min(best, segment(lat, lon, values.optJSONObject(i - 1), values.optJSONObject(i)));
        if ("POLYGON".equals(hazard.optString("geometry_type"))) best = Math.min(best, segment(lat, lon, values.optJSONObject(values.length() - 1), values.optJSONObject(0)));
        return best;
    }

    private static double segment(double lat, double lon, JSONObject a, JSONObject b) {
        if (a == null || b == null) return Double.MAX_VALUE;
        double radius = 6_371_000.0, cosine = Math.cos(Math.toRadians(lat));
        double px = Math.toRadians(lon) * radius * cosine, py = Math.toRadians(lat) * radius;
        double ax = Math.toRadians(a.optDouble("longitude")) * radius * cosine, ay = Math.toRadians(a.optDouble("latitude")) * radius;
        double bx = Math.toRadians(b.optDouble("longitude")) * radius * cosine, by = Math.toRadians(b.optDouble("latitude")) * radius;
        double dx = bx - ax, dy = by - ay; if (dx == 0 && dy == 0) return Math.hypot(px - ax, py - ay);
        double t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    private static double haversine(double aLat, double aLon, double bLat, double bLon) {
        double p1 = Math.toRadians(aLat), p2 = Math.toRadians(bLat), dp = Math.toRadians(bLat - aLat), dl = Math.toRadians(bLon - aLon);
        double value = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 6_371_000.0 * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }
}
