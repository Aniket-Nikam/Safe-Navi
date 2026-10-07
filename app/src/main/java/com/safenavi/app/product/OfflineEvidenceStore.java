package com.safenavi.app.product;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OfflineEvidenceStore {
    private static final String PREFS = "safe_navi_offline_evidence";
    private static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;
    private OfflineEvidenceStore() { }

    public static synchronized JSONObject store(Context context, Uri uri) throws Exception {
        String id = UUID.randomUUID().toString();
        String name = displayName(context, uri);
        String type = context.getContentResolver().getType(uri);
        if (type == null) type = "application/octet-stream";
        File directory = new File(context.getNoBackupFilesDir(), "pending_evidence");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("Could not create protected evidence storage");
        long used = 0; File[] existing = directory.listFiles(); if (existing != null) for (File file : existing) used += file.length();
        if (used >= MAX_TOTAL_BYTES) throw new IllegalStateException("Offline evidence storage is full. Connect to upload pending reports.");
        File destination = new File(directory, id + ".bin");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long total = 0;
        try (InputStream input = context.getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IllegalArgumentException("Could not open selected evidence");
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) throw new IllegalArgumentException("Evidence must be 15 MB or smaller");
                if (used + total > MAX_TOTAL_BYTES) throw new IllegalStateException("Offline evidence storage is limited to 100 MB. Connect to upload pending reports.");
                digest.update(buffer, 0, count); output.write(buffer, 0, count);
            }
        } catch (Exception error) { destination.delete(); throw error; }
        JSONObject item = new JSONObject().put("id", id).put("path", destination.getAbsolutePath())
                .put("name", name).put("media_type", type).put("size", total)
                .put("sha256", hex(digest.digest())).put("user_id", ProductSession.email(context))
                .put("created_at", System.currentTimeMillis());
        JSONArray items = all(context); items.put(item); save(context, items); return item;
    }

    public static synchronized JSONObject get(Context context, String id) {
        JSONArray items = all(context);
        for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item != null && id.equals(item.optString("id"))) return item; }
        return null;
    }

    public static boolean verify(JSONObject item) {
        try {
            File file = new File(item.getString("path"));
            if (!file.isFile() || file.length() != item.getLong("size") || file.length() > MAX_BYTES) return false;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream input = new FileInputStream(file)) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count); }
            return hex(digest.digest()).equals(item.getString("sha256"));
        } catch (Exception error) { return false; }
    }

    public static synchronized void delete(Context context, String id) {
        JSONArray kept = new JSONArray(); JSONArray items = all(context);
        for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item == null) continue;
            if (id.equals(item.optString("id"))) new File(item.optString("path")).delete(); else kept.put(item);
        }
        save(context, kept);
    }

    public static synchronized void markUploaded(Context context, String id, String serverId) {
        JSONArray items = all(context);
        for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item != null && id.equals(item.optString("id"))) {
            try { item.put("server_id", serverId); } catch (Exception ignored) { }
        }}
        save(context, items);
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) { int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (index >= 0) return cursor.getString(index); }
        } catch (Exception ignored) { }
        return "evidence";
    }
    private static JSONArray all(Context context) { try { return new JSONArray(context.getSharedPreferences(PREFS, 0).getString("items", "[]")); } catch (Exception error) { return new JSONArray(); } }
    private static void save(Context context, JSONArray items) { context.getSharedPreferences(PREFS, 0).edit().putString("items", items.toString()).apply(); }
    private static String hex(byte[] bytes) { StringBuilder value = new StringBuilder(); for (byte b : bytes) value.append(String.format(java.util.Locale.US, "%02x", b)); return value.toString(); }
}
