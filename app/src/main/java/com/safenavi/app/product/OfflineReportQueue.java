package com.safenavi.app.product;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OfflineReportQueue {
    private static final String FILE = "safe_navi_offline_reports";
    private OfflineReportQueue() { }

    public static synchronized void enqueue(Context context, JSONObject payload) {
        enqueue(context, payload, new JSONArray());
    }

    public static synchronized void enqueue(Context context, JSONObject payload, JSONArray evidenceLocalIds) {
        JSONArray values = all(context);
        for(int i=0;i<values.length();i++){JSONObject existing=values.optJSONObject(i);JSONObject prior=existing==null?null:existing.optJSONObject("payload");if(prior!=null&&belongsToCurrentServer(existing)&&ProductSession.email(context).equals(existing.optString("user_id"))&&!payload.optString("client_request_id").isEmpty()&&payload.optString("client_request_id").equals(prior.optString("client_request_id")))return;}
        JSONObject item = new JSONObject();
        try {
            item.put("user_id", ProductSession.email(context));
            item.put("origin", ServerConnection.baseUrl());
            item.put("queued_at", System.currentTimeMillis());
            item.put("payload", new JSONObject(payload.toString()));
            item.put("evidence_local_ids", new JSONArray(evidenceLocalIds.toString()));
            values.put(item);
            save(context, values);
        } catch (Exception error) { throw new IllegalStateException("The report could not be saved to the upload queue",error); }
    }

    public static synchronized JSONArray pending(Context context) {
        JSONArray result = new JSONArray();
        JSONArray values = all(context);
        String user = ProductSession.email(context);
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item != null && belongsToCurrentServer(item) && user.equals(item.optString("user_id"))) result.put(item);
        }
        return result;
    }

    public static synchronized void remove(Context context, String clientRequestId) {
        JSONArray kept = new JSONArray();
        JSONArray values = all(context);
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            JSONObject payload = item == null ? null : item.optJSONObject("payload");
            if (payload == null || !belongsToCurrentServer(item) || !ProductSession.email(context).equals(item.optString("user_id"))
                    || !clientRequestId.equals(payload.optString("client_request_id"))) kept.put(item);
        }
        save(context, kept);
    }

    private static JSONArray all(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        try { return new JSONArray(preferences.getString("queue", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static void save(Context context, JSONArray values) {
        if(!context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("queue", values.toString()).commit())throw new IllegalStateException("Device storage did not confirm the queued report");
    }

    static synchronized JSONArray allEntries(Context context) { return all(context); }
    private static boolean belongsToCurrentServer(JSONObject item) {
        return ServerConnection.baseUrl().equals(item.optString("origin",ServerConnection.baseUrl()));
    }
    static synchronized JSONArray entriesForCurrentServer(Context context) {
        JSONArray result=new JSONArray(),values=all(context);
        for(int i=0;i<values.length();i++){JSONObject item=values.optJSONObject(i);if(item!=null&&belongsToCurrentServer(item))result.put(item);}
        return result;
    }
    static synchronized void bindLegacyOrigin(Context context,String origin) {
        JSONArray values=all(context);boolean changed=false;
        try {
            for(int i=0;i<values.length();i++){JSONObject item=values.optJSONObject(i);if(item!=null&&!item.has("origin")){item.put("origin",origin);changed=true;}}
        }catch(org.json.JSONException error){throw new IllegalStateException("Cannot retain the queued report origin",error);}
        if(changed)save(context,values);
    }
}
