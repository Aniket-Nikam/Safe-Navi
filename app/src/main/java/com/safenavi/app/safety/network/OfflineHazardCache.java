package com.safenavi.app.safety.network;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OfflineHazardCache {
    private static final String FILE = "safe_navi_verified_hazard_cache";
    private OfflineHazardCache() { }
    public static void clear(Context context) {
        context.getSharedPreferences(FILE, 0).edit().clear().apply();
    }
    public static void save(Context context, JSONObject response) {
        context.getSharedPreferences(FILE, 0).edit().putString("response", response.toString()).putLong("updated_at", System.currentTimeMillis()).apply();
    }
    public static JSONObject load(Context context) {
        try { return new JSONObject(context.getSharedPreferences(FILE, 0).getString("response", "{\"hazards\":[]}")); }
        catch (Exception error) { return new JSONObject(); }
    }
    public static long ageMillis(Context context) { return System.currentTimeMillis() - context.getSharedPreferences(FILE, 0).getLong("updated_at", 0); }
    public static boolean isNoGo(Context context, double latitude, double longitude) {
        return snapshot(context).blocksEdge(latitude, longitude, latitude, longitude);
    }
    public static Snapshot snapshot(Context context) { return fromJson(load(context)); }
    public static Snapshot fromJson(JSONObject response) {
        List<Zone> zones = new ArrayList<>(); JSONArray hazards = response.optJSONArray("hazards");
        if (hazards != null) for (int i=0; i<hazards.length(); i++) {
            JSONObject h=hazards.optJSONObject(i); if (h == null) continue;
            String status=h.optString("status", "ACTIVE");
            if (!"ACTIVE".equals(status) && !"MONITORING".equals(status)) continue;
            boolean closed="CLOSED".equals(h.optString("road_status"));
            if (!closed && !("CRITICAL".equals(h.optString("severity")) && "UNSAFE".equals(h.optString("road_status")))) continue;
            double radius=h.optDouble("no_go_radius_m", closed ? 60 : 40);
            if (!Double.isFinite(radius) || radius <= 0) radius=closed ? 60 : 40;
            JSONArray geometry=h.optJSONArray("geometry_coordinates"); List<double[]> points=new ArrayList<>();
            boolean polygon="POLYGON".equals(h.optString("geometry_type"));
            if (!"POINT".equals(h.optString("geometry_type", "POINT")) && geometry != null) for (int j=0; j<geometry.length(); j++) {
                JSONObject p=geometry.optJSONObject(j); if (p != null) points.add(new double[]{p.optDouble("latitude"),p.optDouble("longitude")});
            }
            if (points.size() < 2) { points.clear(); points.add(new double[]{h.optDouble("latitude"),h.optDouble("longitude")}); polygon=false; }
            zones.add(new Zone(points.toArray(new double[0][]), polygon, radius));
        }
        return new Snapshot(zones);
    }
    public static final class Snapshot {
        private final List<Zone> zones;
        private Snapshot(List<Zone> zones) { this.zones=zones; }
        public boolean blocksEdge(double aLat, double aLon, double bLat, double bLon) {
            for (Zone z:zones) {
                if (Math.max(aLat,bLat)<z.minLat || Math.min(aLat,bLat)>z.maxLat || Math.max(aLon,bLon)<z.minLon || Math.min(aLon,bLon)>z.maxLon) continue;
                if (NoGoGeometry.distance(aLat,aLon,bLat,bLon,z.points,z.polygon)<=z.radius) return true;
            }
            return false;
        }
    }
    private static final class Zone {
        final double[][] points; final boolean polygon; final double radius;
        double minLat=90,maxLat=-90,minLon=180,maxLon=-180;
        Zone(double[][] points, boolean polygon, double radius) {
            this.points=points;this.polygon=polygon;this.radius=radius;
            for (double[] p:points) { minLat=Math.min(minLat,p[0]);maxLat=Math.max(maxLat,p[0]);minLon=Math.min(minLon,p[1]);maxLon=Math.max(maxLon,p[1]); }
            double latMargin=radius/110000.0, lonMargin=latMargin/Math.max(0.01,Math.cos(Math.toRadians(Math.max(Math.abs(minLat),Math.abs(maxLat)))));
            minLat-=latMargin;maxLat+=latMargin;minLon-=lonMargin;maxLon+=lonMargin;
        }
    }
}
