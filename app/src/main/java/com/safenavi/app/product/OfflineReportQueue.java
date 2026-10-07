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
        JSONObject item = new JSONObject();
        try {
            item.put("user_id", ProductSession.email(context));
            item.put("queued_at", System.currentTimeMillis());
            item.put("payload", new JSONObject(payload.toString()));
            item.put("evidence_local_ids", new JSONArray(evidenceLocalIds.toString()));
            values.put(item);
            save(context, values);
        } catch (Exception ignored) { }
    }

    public static synchronized JSONArray pending(Context context) {
        JSONArray result = new JSONArray();
        JSONArray values = all(context);
        String user = ProductSession.email(context);
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item != null && user.equals(item.optString("user_id"))) result.put(item);
        }
        return result;
    }

    public static synchronized void remove(Context context, String clientRequestId) {
        JSONArray kept = new JSONArray();
        JSONArray values = all(context);
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            JSONObject payload = item == null ? null : item.optJSONObject("payload");
            if (payload == null || !clientRequestId.equals(payload.optString("client_request_id"))) kept.put(item);
        }
        save(context, kept);
    }

    private static JSONArray all(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        try { return new JSONArray(preferences.getString("queue", "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static void save(Context context, JSONArray values) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("queue", values.toString()).apply();
    }

    static synchronized JSONArray allEntries(Context context) { return all(context); }
}
