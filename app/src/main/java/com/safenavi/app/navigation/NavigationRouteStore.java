package com.safenavi.app.navigation;

import android.content.Context;
import android.content.Intent;
import com.safenavi.app.NavigationActivity;
import com.safenavi.app.safety.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** App-private, file-backed handoff avoids Android's Binder transaction size limit. */
public final class NavigationRouteStore {
    private static final int MAX_BYTES=5*1024*1024;
    private NavigationRouteStore() { }
    public static Intent intent(Context context,RouteSafetyResult result,String from,String to,boolean demo) throws IOException,JSONException {
        RouteCandidate route=result.getRoute();
        JSONObject data=new JSONObject().put("id",route.getProviderRouteId()).put("minutes",route.getTravelMinutes())
                .put("meters",route.getDistanceMeters()).put("profile",result.getProfile().name()).put("from",from).put("to",to);
        JSONArray points=new JSONArray(),steps=new JSONArray(),alerts=new JSONArray();
        for(HazardLocation.GeoPoint point:route.getGeometry()) points.put(new JSONArray().put(point.getLatitude()).put(point.getLongitude()));
        for(NavigationStep step:route.getNavigationSteps()) steps.put(new JSONObject().put("index",step.shapeIndex).put("text",step.instruction));
        for(RouteRiskPoint point:result.getRiskPoints()) if(point.isVerified()) alerts.put(new JSONObject().put("lat",point.getLatitude()).put("lon",point.getLongitude()).put("title",point.getTitle()).put("reason",point.getReason()));
        byte[] bytes=data.put("points",points).put("steps",steps).put("alerts",alerts).toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES) throw new IOException("This route is too large for the navigation preview.");
        String name="navigation-"+UUID.randomUUID()+".json";
        // These are disposable route snapshots only, never reports, credentials or drafts.
        File[] old=context.getCacheDir().listFiles((folder,file)->file.matches("navigation-[a-f0-9-]+\\.json"));
        if(old!=null) for(File file:old) if(System.currentTimeMillis()-file.lastModified()>86400000L) file.delete();
        try(FileOutputStream stream=new FileOutputStream(new File(context.getCacheDir(),name))) { stream.write(bytes); }
        return new Intent(context,NavigationActivity.class).putExtra("route_file",name).putExtra("demo",demo);
    }
    public static JSONObject read(Context context,String name) throws IOException,JSONException {
        if(name==null || !name.matches("navigation-[a-f0-9-]+\\.json")) throw new IOException("Navigation route is unavailable. Please plan it again.");
        try(FileInputStream stream=new FileInputStream(new File(context.getCacheDir(),name));ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int count;
            while((count=stream.read(buffer))!=-1) { if(bytes.size()+count>MAX_BYTES) throw new IOException("Route snapshot is too large."); bytes.write(buffer,0,count); }
            return new JSONObject(bytes.toString("UTF-8"));
        }
    }
    public static RouteCandidate route(JSONObject data) throws JSONException {
        List<HazardLocation.GeoPoint> points=new ArrayList<>(); List<NavigationStep> steps=new ArrayList<>();
        JSONArray shape=data.getJSONArray("points");
        for(int i=0;i<shape.length();i++) {
            double lat=shape.getJSONArray(i).getDouble(0),lon=shape.getJSONArray(i).getDouble(1);
            if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180) throw new JSONException("Invalid route coordinates");
            points.add(new HazardLocation.GeoPoint(lat,lon));
        }
        JSONArray instructions=data.getJSONArray("steps");
        for(int i=0;i<instructions.length();i++) {
            JSONObject step=instructions.getJSONObject(i); int index=step.getInt("index");
            if(index>=0 && index<points.size()) steps.add(new NavigationStep(index,step.getString("text")));
        }
        return new RouteCandidate(data.getString("id"),data.getDouble("minutes"),data.getDouble("meters"),points,steps);
    }
    public static void discard(Context context,String name) {
        if(name!=null && name.matches("navigation-[a-f0-9-]+\\.json")) new File(context.getCacheDir(),name).delete();
    }
}
