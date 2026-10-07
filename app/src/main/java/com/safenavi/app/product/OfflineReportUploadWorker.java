package com.safenavi.app.product;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.File;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

public final class OfflineReportUploadWorker extends Worker {
    private static final String WORK = "safe-navi-offline-report-upload";
    private final OkHttpClient client = new OkHttpClient();
    public OfflineReportUploadWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    public static void schedule(Context context) {
        Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(OfflineReportUploadWorker.class).setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull @Override public Result doWork() {
        String token = ProductSession.token(getApplicationContext()); String user = ProductSession.email(getApplicationContext());
        if (token.isEmpty() || user.isEmpty()) return Result.success();
        JSONArray entries = OfflineReportQueue.allEntries(getApplicationContext()); boolean retry = false;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i); if (entry == null || !user.equals(entry.optString("user_id"))) continue;
            JSONObject payload = entry.optJSONObject("payload"); if (payload == null) continue;
            JSONArray localIds = entry.optJSONArray("evidence_local_ids"); JSONArray serverIds = new JSONArray(); boolean evidenceReady = true;
            if (localIds != null) for (int e = 0; e < localIds.length(); e++) {
                JSONObject item = OfflineEvidenceStore.get(getApplicationContext(), localIds.optString(e));
                if (item == null || !OfflineEvidenceStore.verify(item)) { evidenceReady = false; break; }
                String uploaded = item.optString("server_id");
                if (uploaded.isEmpty()) { uploaded = upload(item, token); if (uploaded != null) OfflineEvidenceStore.markUploaded(getApplicationContext(), item.optString("id"), uploaded); }
                if (uploaded == null || uploaded.isEmpty()) { evidenceReady = false; retry = true; break; } serverIds.put(uploaded);
            }
            if (!evidenceReady) continue;
            try { payload.put("evidence_ids", serverIds); } catch (Exception ignored) { }
            if (submit(payload, token)) {
                OfflineReportQueue.remove(getApplicationContext(), payload.optString("client_request_id"));
                if (localIds != null) for (int e = 0; e < localIds.length(); e++) OfflineEvidenceStore.delete(getApplicationContext(), localIds.optString(e));
            } else retry = true;
        }
        return retry ? Result.retry() : Result.success();
    }

    private String upload(JSONObject item, String token) {
        try {
            File file = new File(item.getString("path")); MediaType type = MediaType.parse(item.optString("media_type", "application/octet-stream"));
            MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("file", item.optString("name", "evidence"), RequestBody.create(file, type)).build();
            Request request = new Request.Builder().url(ProductApiClient.apiBaseUrl() + "/api/v1/evidence")
                    .header("Authorization", "Bearer " + token).post(body).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                return new JSONObject(response.body().string()).optString("id", null);
            }
        } catch (Exception error) { return null; }
    }

    private boolean submit(JSONObject payload, String token) {
        try {
            Request request = new Request.Builder().url(ProductApiClient.apiBaseUrl() + "/api/v1/reports")
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                    .post(RequestBody.create(payload.toString(), MediaType.parse("application/json"))).build();
            try (Response response = client.newCall(request).execute()) { return response.isSuccessful(); }
        } catch (Exception error) { return false; }
    }
}
