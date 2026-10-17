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
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class OfflineRouter {
    private static final String ASSET = "offline/navi_mumbai_graph.sqlite";
    private static volatile OfflineRoadGraph cachedGraph;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    public interface Callback<T> { void onSuccess(T value); void onError(String message); }
    public static final class Result {
        public final RouteCandidate route; public final double riskExposure;
        Result(RouteCandidate route, double riskExposure) { this.route = route; this.riskExposure = riskExposure; }
    }
    private static final class State implements Comparable<State> {
        final int node; final double priority, cost;
        State(int node, double priority, double cost) { this.node = node; this.priority = priority; this.cost=cost; }
        @Override public int compareTo(State other) { return Double.compare(priority, other.priority); }
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
            OfflineRoadGraph graph=graph(db);
            int modeIndex=mode==TravelMode.WALKING?1:mode==TravelMode.CYCLING?2:0;
            int start=graph.nearest(fromLat,fromLon,modeIndex), goal=graph.nearest(toLat,toLon,modeIndex);
            if (start < 0 || goal < 0) { callback.onError("The selected points are outside the downloaded Navi Mumbai routing graph."); return; }
            Result value = aStar(graph, start, goal, fromLat, fromLon, toLat, toLon, modeIndex, profile);
            if (value == null) callback.onError("No traversable offline route was found. A cached official closure may block the available roads.");
            else callback.onSuccess(value);
        } catch (Exception error) { callback.onError("Offline routing failed: " + error.getMessage()); }});
    }

    private static OfflineRoadGraph graph(SQLiteDatabase db) {
        if(cachedGraph == null) synchronized(OfflineRouter.class) { if(cachedGraph == null) cachedGraph=OfflineRoadGraph.load(db); }
        return cachedGraph;
    }

    private Result aStar(OfflineRoadGraph graph, int start, int goal, double startLat, double startLon,
                         double goalLat, double goalLon, int mode, RouteProfile profile) {
        double riskWeight = profile == RouteProfile.SAFEST ? 1.0 : profile == RouteProfile.BALANCED ? 0.35 : 0.0;
        OfflineHazardCache.Snapshot restrictions=OfflineHazardCache.snapshot(context);
        if(restrictions.blocksEdge(startLat,startLon,graph.lat[start],graph.lon[start])
                || restrictions.blocksEdge(graph.lat[goal],graph.lon[goal],goalLat,goalLon)) return null;
        PriorityQueue<State> open=new PriorityQueue<>(); double[] cost=new double[graph.lat.length];
        java.util.Arrays.fill(cost,Double.POSITIVE_INFINITY);
        int[] previous=new int[graph.lat.length], previousEdge=new int[graph.lat.length]; java.util.Arrays.fill(previous,-1);
        cost[start]=0; open.add(new State(start,0,0));
        float[] times=graph.times(mode); long deadline=android.os.SystemClock.elapsedRealtime()+20_000;
        boolean reached=false;
        while(!open.isEmpty()) {
            if(android.os.SystemClock.elapsedRealtime()>deadline) throw new IllegalStateException("Offline route calculation exceeded its time budget. Try closer endpoints.");
            State state=open.poll(); if(state.cost>cost[state.node]) continue;
            if(state.node==goal){reached=true;break;}
            for(int edge=graph.head[state.node];edge>=0;edge=graph.next[edge]) {
                double seconds=times[edge]; if(!Double.isFinite(seconds) || seconds<=0) continue;
                int next=graph.to[edge];
                if(restrictions.blocksEdge(graph.lat[state.node],graph.lon[state.node],graph.lat[next],graph.lon[next])) continue;
                double nextCost=state.cost+seconds*(1+riskWeight*graph.risk[edge]/100.0);
                if(nextCost>=cost[next]) continue;
                cost[next]=nextCost;previous[next]=state.node;previousEdge[next]=edge;
                open.add(new State(next,nextCost+haversine(graph.lat[next],graph.lon[next],graph.lat[goal],graph.lon[goal])/graph.maxSpeed[mode],nextCost));
            }
        }
        if(!reached) return null;
        List<HazardLocation.GeoPoint> points = new ArrayList<>(); double distance = 0, seconds = 0, weightedRisk = 0; int node = goal;
        points.add(new HazardLocation.GeoPoint(goalLat, goalLon));
        points.add(new HazardLocation.GeoPoint(graph.lat[goal],graph.lon[goal]));
        while(node != start) {
            int edge=previousEdge[node]; if(previous[node]<0) return null;
            distance+=graph.distance[edge];seconds+=times[edge];weightedRisk+=graph.risk[edge]*graph.distance[edge];node=previous[node];
            points.add(new HazardLocation.GeoPoint(graph.lat[node],graph.lon[node]));
        }
        points.add(new HazardLocation.GeoPoint(startLat, startLon)); Collections.reverse(points);
        RouteCandidate candidate = new RouteCandidate("offline-graph-route", seconds / 60.0, distance, points);
        return new Result(candidate, distance <= 0 ? 0 : weightedRisk / distance);
    }

    private SQLiteDatabase database() throws Exception {
        synchronized (OfflineRouter.class) {
        File file = new File(context.getNoBackupFilesDir(), "navi_mumbai_graph_v2.sqlite");
        if (!file.isFile() || file.length() < 1_000_000) try (InputStream input = context.getAssets().open(ASSET); FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024]; int count; while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        return SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
        }
    }

    private static double haversine(double aLat, double aLon, double bLat, double bLon) {
        double p1=Math.toRadians(aLat),p2=Math.toRadians(bLat),dp=Math.toRadians(bLat-aLat),dl=Math.toRadians(bLon-aLon);
        double value=Math.sin(dp/2)*Math.sin(dp/2)+Math.cos(p1)*Math.cos(p2)*Math.sin(dl/2)*Math.sin(dl/2);
        return 6_371_000.0*2*Math.atan2(Math.sqrt(value),Math.sqrt(1-value));
    }
}
