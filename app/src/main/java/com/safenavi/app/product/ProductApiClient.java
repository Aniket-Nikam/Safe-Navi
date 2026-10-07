package com.safenavi.app.product;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.safenavi.app.BuildConfig;
import java.io.IOException;
import java.util.Locale;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ProductApiClient {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final Context context;
    private final OkHttpClient client = new OkHttpClient.Builder().build();
    public interface ObjectCallback { void onSuccess(JSONObject value); void onError(String message); }
    public interface ArrayCallback { void onSuccess(JSONArray value); void onError(String message); }
    public interface BytesCallback { void onSuccess(byte[] value, String mediaType); void onError(String message); }
    public ProductApiClient(Context context) { this.context = context.getApplicationContext(); }

    public WebSocket openLiveUpdates(WebSocketListener listener) {
        String socketUrl = baseUrl().replaceFirst("^http", "ws") + "/api/v1/live";
        return client.newWebSocket(new Request.Builder().url(socketUrl).build(), listener);
    }

    public void login(String email, String password, ObjectCallback callback) {
        try { post("/api/v1/auth/login", new JSONObject().put("email", email).put("password", password), false, callback); }
        catch (JSONException error) { callback.onError("Could not prepare sign-in request."); }
    }
    public void register(String name, String email, String password, ObjectCallback callback) {
        try { post("/api/v1/auth/register", new JSONObject().put("name", name).put("email", email).put("password", password), false, callback); }
        catch (JSONException error) { callback.onError("Could not prepare registration request."); }
    }
    public void logout(ObjectCallback callback) { post("/api/v1/auth/logout", new JSONObject(), true, callback); }
    public void changePassword(String current, String replacement, ObjectCallback callback) {
        try { post("/api/v1/account/password", new JSONObject().put("current_password", current).put("new_password", replacement), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare password change."); }
    }
    public void deleteAccount(String password, ObjectCallback callback) {
        try { request("DELETE", "/api/v1/account", new JSONObject().put("password", password), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare account deletion."); }
    }
    public void submitReport(JSONObject payload, ObjectCallback callback) { post("/api/v1/reports", payload, true, callback); }
    public void reports(String status, ArrayCallback callback) {
        getArray("/api/v1/reports" + (status == null ? "" : "?status=" + status), true, callback);
    }
    public void reportsFiltered(String status, String category, String department, String ward,
                                String severity, Integer olderHours, boolean overdue, ArrayCallback callback) {
        HttpUrl base = HttpUrl.parse(baseUrl() + "/api/v1/reports");
        if (base == null) { callback.onError("Safe-Navi server URL is invalid."); return; }
        HttpUrl.Builder builder = base.newBuilder();
        if (status != null && !status.isEmpty()) builder.addQueryParameter("status", status);
        if (category != null && !category.isEmpty()) builder.addQueryParameter("category", category);
        if (department != null && !department.isEmpty()) builder.addQueryParameter("department", department);
        if (ward != null && !ward.isEmpty()) builder.addQueryParameter("ward", ward);
        if (severity != null && !severity.isEmpty()) builder.addQueryParameter("severity", severity);
        if (olderHours != null) builder.addQueryParameter("older_than_hours", String.valueOf(olderHours));
        if (overdue) builder.addQueryParameter("overdue", "true");
        getArray(builder.build(), true, callback);
    }
    public void reportDetail(String reportId, ObjectCallback callback) {
        getObject("/api/v1/reports/" + reportId, true, callback);
    }
    public void confirmReport(String reportId, ObjectCallback callback) {
        post("/api/v1/reports/" + reportId + "/confirm", new JSONObject(), true, callback);
    }
    public void suggestDuplicates(String category, double latitude, double longitude, ArrayCallback callback) {
        getArray(String.format(Locale.US, "/api/v1/reports/duplicates/suggest?category=%s&latitude=%.7f&longitude=%.7f",
                category, latitude, longitude), true, callback);
    }
    public void notifications(ArrayCallback callback) { getArray("/api/v1/notifications", true, callback); }
    public void markNotificationRead(String notificationId, ObjectCallback callback) {
        post("/api/v1/notifications/" + notificationId + "/read", new JSONObject(), true, callback);
    }
    public void savedPlaces(ArrayCallback callback) { getArray("/api/v1/saved-places", true, callback); }
    public void savePlace(String label, String address, double latitude, double longitude, ObjectCallback callback) {
        try { post("/api/v1/saved-places", new JSONObject().put("label", label).put("address", address)
                .put("latitude", latitude).put("longitude", longitude), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare saved place."); }
    }
    public void deleteSavedPlace(String placeId, ObjectCallback callback) {
        request("DELETE", "/api/v1/saved-places/" + placeId, new JSONObject(), true, callback);
    }
    public void emergencyContacts(ArrayCallback callback) { getArray("/api/v1/emergency-contacts", true, callback); }
    public void addEmergencyContact(String name, String phone, String relationship, ObjectCallback callback) {
        try { post("/api/v1/emergency-contacts", new JSONObject().put("name", name).put("phone", phone)
                .put("relationship", relationship), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare emergency contact."); }
    }
    public void deleteEmergencyContact(String contactId, ObjectCallback callback) {
        request("DELETE", "/api/v1/emergency-contacts/" + contactId, new JSONObject(), true, callback);
    }
    public void journeys(ArrayCallback callback) { getArray("/api/v1/journeys", true, callback); }
    public void startJourney(JSONObject payload, ObjectCallback callback) { post("/api/v1/journeys", payload, true, callback); }
    public void updateJourneyLocation(String journeyId, double latitude, double longitude, ObjectCallback callback) {
        try { request("PATCH", "/api/v1/journeys/" + journeyId + "/location",
                new JSONObject().put("latitude", latitude).put("longitude", longitude), true, callback); }
        catch (JSONException error) { callback.onError("Could not update journey location."); }
    }
    public void endJourney(String journeyId, ObjectCallback callback) {
        post("/api/v1/journeys/" + journeyId + "/end", new JSONObject(), true, callback);
    }
    public void scheduleCheckIn(String journeyId, Integer minutes, ObjectCallback callback) {
        try { post("/api/v1/journeys/" + journeyId + "/check-in/schedule",
                new JSONObject().put("interval_minutes", minutes == null ? JSONObject.NULL : minutes), true, callback); }
        catch (JSONException error) { callback.onError("Could not schedule safety check-ins."); }
    }
    public void acknowledgeCheckIn(String journeyId, ObjectCallback callback) {
        post("/api/v1/journeys/" + journeyId + "/check-in", new JSONObject(), true, callback);
    }
    public static String apiBaseUrl() { return baseUrl(); }
    public static String journeyShareUrl(String token) { return baseUrl() + "/api/v1/journeys/share/" + token + "/view"; }
    public void mapHazards(ObjectCallback callback) { getObject("/api/v1/hazards/map", false, callback); }
    public void triage(String reportId, String reason, ObjectCallback callback) {
        try { post("/api/v1/government/reports/" + reportId + "/triage", new JSONObject().put("reason", reason), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare triage request."); }
    }
    public void decide(String reportId, JSONObject decision, ObjectCallback callback) {
        post("/api/v1/government/reports/" + reportId + "/decision", decision, true, callback);
    }
    public void assignReport(String reportId, String department, String employee, int dueHours, ObjectCallback callback) {
        try { post("/api/v1/government/reports/" + reportId + "/assign",
                new JSONObject().put("department", department).put("assigned_to", employee).put("due_hours", dueHours), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare assignment."); }
    }
    public void requestMoreInformation(String reportId, String message, ObjectCallback callback) {
        try { post("/api/v1/government/reports/" + reportId + "/request-information",
                new JSONObject().put("message", message), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare information request."); }
    }
    public void duplicateReports(String reportId, ArrayCallback callback) {
        getArray("/api/v1/government/reports/" + reportId + "/duplicates", true, callback);
    }
    public void governmentMetrics(ObjectCallback callback) { getObject("/api/v1/government/metrics", true, callback); }
    public void newsSignals(ArrayCallback callback) { getArray("/api/v1/government/news-signals", true, callback); }
    public void ingestNews(ObjectCallback callback) { post("/api/v1/government/news/ingest", new JSONObject(), true, callback); }
    public void newsSettings(ObjectCallback callback) { getObject("/api/v1/government/news/settings", true, callback); }
    public void updateNewsSettings(int refreshHours, ObjectCallback callback) {
        try { request("PATCH", "/api/v1/government/news/settings", new JSONObject().put("refresh_hours", refreshHours), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare the news schedule."); }
    }
    public void reviewNews(String signalId, String status, ObjectCallback callback) {
        try { post("/api/v1/government/news-signals/" + signalId + "/review",
                new JSONObject().put("review_status", status), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare news review."); }
    }
    public void updateHazard(String hazardId, JSONObject update, ObjectCallback callback) {
        request("PATCH", "/api/v1/government/hazards/" + hazardId, update, true, callback);
    }
    public void communityPosts(String category, ArrayCallback callback) {
        String suffix = category == null || category.equals("all") ? "" : "?category=" + category;
        getArray("/api/v1/community/posts" + suffix, true, callback);
    }
    public void createCommunityPost(String content, String category, String evidenceId, ObjectCallback callback) {
        try {
            JSONObject payload = new JSONObject().put("content", content).put("category", category);
            if (evidenceId != null && !evidenceId.isEmpty()) payload.put("evidence_id", evidenceId);
            post("/api/v1/community/posts", payload, true, callback);
        }
        catch (JSONException error) { callback.onError("Could not prepare the community post."); }
    }
    public void createCommunityPost(String content, String category, ObjectCallback callback) {
        createCommunityPost(content, category, null, callback);
    }
    public void toggleCommunityVote(String postId, ObjectCallback callback) {
        post("/api/v1/community/posts/" + postId + "/vote", new JSONObject(), true, callback);
    }
    public void communityComments(String postId, ArrayCallback callback) {
        getArray("/api/v1/community/posts/" + postId + "/comments", true, callback);
    }
    public void createCommunityComment(String postId, String content, ObjectCallback callback) {
        try { post("/api/v1/community/posts/" + postId + "/comments", new JSONObject().put("content", content), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare the reply."); }
    }
    public void reportMessages(String reportId, ArrayCallback callback) {
        getArray("/api/v1/reports/" + reportId + "/messages", true, callback);
    }
    public void createReportMessage(String reportId, String content, String evidenceId, ObjectCallback callback) {
        try {
            JSONObject payload = new JSONObject().put("content", content);
            if (evidenceId != null && !evidenceId.isEmpty()) payload.put("evidence_id", evidenceId);
            post("/api/v1/reports/" + reportId + "/messages", payload, true, callback);
        } catch (JSONException error) { callback.onError("Could not prepare the report message."); }
    }
    public void assistant(String message, JSONArray history, ObjectCallback callback) {
        try { post("/api/v1/assistant/chat", new JSONObject().put("message", message).put("history", history), true, callback); }
        catch (JSONException error) { callback.onError("Could not prepare the assistant request."); }
    }
    public void uploadEvidence(Uri uri, ObjectCallback callback) {
        try {
            String type = context.getContentResolver().getType(uri);
            if (type == null) type = "application/octet-stream";
            String name = "evidence";
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) name = cursor.getString(index);
                }
            }
            byte[] bytes;
            try (java.io.InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) { callback.onError("Could not open selected evidence."); return; }
                bytes = readLimited(input, 15 * 1024 * 1024);
            }
            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", name, RequestBody.create(bytes, MediaType.parse(type))).build();
            HttpUrl url = HttpUrl.parse(baseUrl() + "/api/v1/evidence");
            if (url == null) { callback.onError("Safe-Navi server URL is invalid."); return; }
            Request request = new Request.Builder().url(url).header("Accept", "application/json")
                    .header("Authorization", "Bearer " + ProductSession.token(context)).post(body).build();
            client.newCall(request).enqueue(objectCallback(callback));
        } catch (IllegalArgumentException error) { callback.onError("Evidence must be 15 MB or smaller."); }
        catch (Exception error) { callback.onError("Could not read selected evidence."); }
    }
    public void downloadEvidence(String evidenceId, BytesCallback callback) {
        HttpUrl url = HttpUrl.parse(baseUrl() + "/api/v1/evidence/" + evidenceId);
        if (url == null) { callback.onError("Safe-Navi server URL is invalid."); return; }
        Request request = new Request.Builder().url(url).header("Accept", "image/*")
                .header("Authorization", "Bearer " + ProductSession.token(context)).build();
        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) { callback.onError("Could not download the photograph."); }
            @Override public void onResponse(Call call, Response response) {
                try (Response ignored = response) {
                    if (!response.isSuccessful() || response.body() == null) { callback.onError("Photograph is unavailable."); return; }
                    callback.onSuccess(response.body().bytes(), response.header("Content-Type", "image/jpeg"));
                } catch (Exception error) { callback.onError("Could not read the photograph."); }
            }
        });
    }
    private static byte[] readLimited(java.io.InputStream input, int maximum) throws IOException {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int read; int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read; if (total > maximum) throw new IllegalArgumentException("too large");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
    private void post(String path, JSONObject payload, boolean authenticated, ObjectCallback callback) {
        request("POST", path, payload, authenticated, callback);
    }
    private void request(String method, String path, JSONObject payload, boolean authenticated, ObjectCallback callback) {
        HttpUrl url = HttpUrl.parse(baseUrl() + path);
        if (url == null) { callback.onError("Safe-Navi server URL is invalid."); return; }
        Request.Builder builder = new Request.Builder().url(url).header("Accept", "application/json")
                .method(method, RequestBody.create(payload.toString(), JSON));
        if (authenticated) builder.header("Authorization", "Bearer " + ProductSession.token(context));
        client.newCall(builder.build()).enqueue(objectCallback(callback));
    }
    private void getObject(String path, boolean authenticated, ObjectCallback callback) {
        HttpUrl url = HttpUrl.parse(baseUrl() + path);
        if (url == null) { callback.onError("Safe-Navi server URL is invalid."); return; }
        Request.Builder builder = new Request.Builder().url(url).header("Accept", "application/json");
        if (authenticated) builder.header("Authorization", "Bearer " + ProductSession.token(context));
        client.newCall(builder.build()).enqueue(objectCallback(callback));
    }
    private void getArray(String path, boolean authenticated, ArrayCallback callback) {
        HttpUrl url = HttpUrl.parse(baseUrl() + path);
        if (url == null) { callback.onError("Safe-Navi server URL is invalid."); return; }
        getArray(url, authenticated, callback);
    }
    private void getArray(HttpUrl url, boolean authenticated, ArrayCallback callback) {
        Request.Builder builder = new Request.Builder().url(url).header("Accept", "application/json");
        if (authenticated) builder.header("Authorization", "Bearer " + ProductSession.token(context));
        client.newCall(builder.build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException error) { callback.onError("Cannot reach the Safe-Navi server."); }
            @Override public void onResponse(Call call, Response response) {
                try (Response ignored = response) {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) { callback.onError(errorMessage(body, response.code())); return; }
                    callback.onSuccess(new JSONArray(body));
                } catch (Exception error) { callback.onError("The server returned an unreadable response."); }
            }
        });
    }
    private Callback objectCallback(ObjectCallback callback) {
        return new Callback() {
            @Override public void onFailure(Call call, IOException error) { callback.onError("Cannot reach the Safe-Navi server. Check that the backend is running."); }
            @Override public void onResponse(Call call, Response response) {
                try (Response ignored = response) {
                    String body = response.body() == null ? "" : response.body().string();
                    if (!response.isSuccessful()) { callback.onError(errorMessage(body, response.code())); return; }
                    callback.onSuccess(new JSONObject(body));
                } catch (Exception error) { callback.onError("The server returned an unreadable response."); }
            }
        };
    }
    private static String errorMessage(String body, int code) {
        try { return new JSONObject(body).optString("detail", "Request failed (HTTP " + code + ")"); }
        catch (JSONException ignored) { return "Request failed (HTTP " + code + ")"; }
    }
    private static String baseUrl() {
        String value = BuildConfig.RISK_API_BASE_URL;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
