package com.safenavi.app.safety.network;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.model.TravelMode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OfflineRouter {
    private static final String ASSET = "offline/navi_mumbai_graph.sqlite";
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    public interface Callback<T> { void onSuccess(T value); void onError(String message); }
    public static final class Result {
        public final RouteCandidate route; public final double riskExposure;
        Result(RouteCandidate route, double riskExposure) { this.route = route; this.riskExposure = riskExposure; }
    }
    private static final class State implements Comparable<State> {
        final long node; final double priority;
        State(long node, double priority) { this.node = node; this.priority = priority; }
        @Override public int compareTo(State other) { return Double.compare(priority, other.priority); }
    }
    private static final class Step {
        final long from; final double lat, lon, distance, seconds, risk;
        Step(long from, double lat, double lon, double distance, double seconds, double risk) {
            this.from = from; this.lat = lat; this.lon = lon; this.distance = distance; this.seconds = seconds; this.risk = risk;
        }
    }

    public OfflineRouter(Context context) { this.context = context.getApplicationContext(); }

    public void search(String query, Callback<List<PlaceSearchResult>> callback) {
        executor.execute(() -> { try (SQLiteDatabase db = database()) {
            List<PlaceSearchResult> values = new ArrayList<>(); String pattern = "%" + query.trim() + "%";
            try (Cursor cursor = db.rawQuery("SELECT COALESCE(NULLIF(street_name,''),area_name),AVG(to_lat),AVG(to_lon) FROM edges WHERE street_name LIKE ? COLLATE NOCASE OR area_name LIKE ? COLLATE NOCASE GROUP BY street_name,area_name LIMIT 5", new String[]{pattern, pattern})) {
                while (cursor.moveToNext()) values.add(new PlaceSearchResult(cursor.getString(0), cursor.getDouble(1), cursor.getDouble(2)));
            }
            if (values.isEmpty()) callback.onError("No offline place match was found inside the Navi Mumbai graph."); else callback.onSuccess(values);
        } catch (Exception error) { callback.onError("Offline place search is unavailable: " + error.getMessage()); }});
    }

    public void route(double fromLat, double fromLon, double toLat, double toLon, TravelMode mode,
                      RouteProfile profile, Callback<Result> callback) {
        executor.execute(() -> { try (SQLiteDatabase db = database()) {
            long start = nearest(db, fromLat, fromLon), goal = nearest(db, toLat, toLon);
            if (start < 0 || goal < 0) { callback.onError("The selected points are outside the downloaded Navi Mumbai routing graph."); return; }
            Result value = aStar(db, start, goal, fromLat, fromLon, toLat, toLon, mode, profile);
            if (value == null) callback.onError("No traversable offline route was found. A cached official closure may block the available roads.");
            else callback.onSuccess(value);
        } catch (Exception error) { callback.onError("Offline routing failed: " + error.getMessage()); }});
    }

    private Result aStar(SQLiteDatabase db, long start, long goal, double startLat, double startLon,
                         double goalLat, double goalLon, TravelMode mode, RouteProfile profile) {
        String timeColumn = mode == TravelMode.WALKING ? "walk_seconds" : mode == TravelMode.CYCLING ? "cycle_seconds" : "drive_seconds";
        double riskWeight = profile == RouteProfile.SAFEST ? 1.0 : profile == RouteProfile.BALANCED ? 0.35 : 0.0;
        double maxMetersPerSecond = mode == TravelMode.WALKING ? 1.35 : mode == TravelMode.CYCLING ? 5.0 : 22.2;
        PriorityQueue<State> open = new PriorityQueue<>(); Map<Long, Double> cost = new HashMap<>(); Map<Long, Step> previous = new HashMap<>();
        cost.put(start, 0.0); open.add(new State(start, 0)); long deadline = System.currentTimeMillis() + 12_000L; int expanded = 0;
        while (!open.isEmpty() && expanded++ < 175_000 && System.currentTimeMillis() < deadline) {
            State state = open.poll(); double known = cost.getOrDefault(state.node, Double.MAX_VALUE);
            if (state.node == goal) break;
            try (Cursor cursor = db.rawQuery("SELECT to_node,to_lat,to_lon,distance_m,risk," + timeColumn + " FROM edges WHERE from_node=? AND " + timeColumn + " IS NOT NULL", new String[]{Long.toString(state.node)})) {
                while (cursor.moveToNext()) {
                    long next = cursor.getLong(0); double lat = cursor.getDouble(1), lon = cursor.getDouble(2);
                    if (OfflineHazardCache.isNoGo(context, lat, lon)) continue;
                    double distance = cursor.getDouble(3), risk = cursor.getDouble(4), seconds = cursor.getDouble(5);
                    double nextCost = known + seconds * (1.0 + riskWeight * risk / 100.0);
                    if (nextCost >= cost.getOrDefault(next, Double.MAX_VALUE)) continue;
                    cost.put(next, nextCost); previous.put(next, new Step(state.node, lat, lon, distance, seconds, risk));
                    open.add(new State(next, nextCost + haversine(lat, lon, goalLat, goalLon) / maxMetersPerSecond));
                }
            }
        }
        if (!previous.containsKey(goal)) return null;
        List<HazardLocation.GeoPoint> points = new ArrayList<>(); double distance = 0, seconds = 0, weightedRisk = 0; long node = goal;
        points.add(new HazardLocation.GeoPoint(goalLat, goalLon));
        while (node != start) { Step step = previous.get(node); if (step == null) return null;
            points.add(new HazardLocation.GeoPoint(step.lat, step.lon)); distance += step.distance; seconds += step.seconds; weightedRisk += step.risk * step.distance; node = step.from;
        }
        points.add(new HazardLocation.GeoPoint(startLat, startLon)); Collections.reverse(points);
        RouteCandidate candidate = new RouteCandidate("offline-graph-route", seconds / 60.0, distance, points);
        return new Result(candidate, distance <= 0 ? 0 : weightedRisk / distance);
    }

    private long nearest(SQLiteDatabase db, double lat, double lon) {
        double[] radii = {0.005, 0.02, 0.08};
        for (double radius : radii) try (Cursor cursor = db.rawQuery("SELECT n.node_id FROM nodes n WHERE n.latitude BETWEEN ? AND ? AND n.longitude BETWEEN ? AND ? ORDER BY ((n.latitude-?)*(n.latitude-?)+(n.longitude-?)*(n.longitude-?)) LIMIT 1",
                new String[]{Double.toString(lat-radius),Double.toString(lat+radius),Double.toString(lon-radius),Double.toString(lon+radius),Double.toString(lat),Double.toString(lat),Double.toString(lon),Double.toString(lon)})) {
            if (cursor.moveToFirst()) return cursor.getLong(0);
        }
        return -1;
    }

    private SQLiteDatabase database() throws Exception {
        File file = new File(context.getNoBackupFilesDir(), "navi_mumbai_graph_v2.sqlite");
        if (!file.isFile() || file.length() < 1_000_000) try (InputStream input = context.getAssets().open(ASSET); FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
    }

    private static double haversine(double aLat, double aLon, double bLat, double bLon) {
        double p1=Math.toRadians(aLat),p2=Math.toRadians(bLat),dp=Math.toRadians(bLat-aLat),dl=Math.toRadians(bLon-aLon);
        double value=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 6_371_000.0*2*Math.atan2(Math.sqrt(value),Math.sqrt(1-value));
    }
}
