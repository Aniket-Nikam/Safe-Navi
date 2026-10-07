package com.safenavi.app.product;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.safenavi.app.BuildConfig;
import com.safenavi.app.MainActivity;
import com.safenavi.app.R;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SafetySyncWorker extends Worker {
    private static final String WORK_NAME = "safe-navi-background-safety-sync";
    private static final String CHANNEL_ID = "safe_navi_safety_updates";
    private static final String PREFS = "safe_navi_background_alerts";
    private final OkHttpClient client = new OkHttpClient();

    public SafetySyncWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SafetySyncWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
    }

    @NonNull @Override public Result doWork() {
        if (!getApplicationContext().getSharedPreferences("AppPreferences", 0)
                .getBoolean("notifications_enabled", true)) return Result.success();
        try {
            createChannel();
            syncAccountNotifications();
            syncCriticalHazards();
            return Result.success();
        } catch (Exception error) {
            return Result.retry();
        }
    }

    private void syncAccountNotifications() throws Exception {
        String token = ProductSession.token(getApplicationContext());
        if (token.isEmpty()) return;
        Request request = new Request.Builder().url(baseUrl() + "/api/v1/notifications")
                .header("Authorization", "Bearer " + token).header("Accept", "application/json").build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;
            JSONArray values = new JSONArray(response.body().string());
            SharedPreferences preferences = getApplicationContext().getSharedPreferences(PREFS, 0);
            Set<String> seen = new HashSet<>(preferences.getStringSet("notification_ids", new HashSet<>()));
            int shown = 0;
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.optJSONObject(i);
                if (item == null || !item.isNull("read_at") || seen.contains(item.optString("id"))) continue;
                seen.add(item.optString("id"));
                if (shown++ < 3) notifyUser(item.optString("title", "Safety update"), item.optString("message"), 1000 + i);
            }
            preferences.edit().putStringSet("notification_ids", seen).apply();
        }
    }

    private void syncCriticalHazards() throws Exception {
        Request request = new Request.Builder().url(baseUrl() + "/api/v1/hazards/map").header("Accept", "application/json").build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return;
            JSONArray values = new JSONObject(response.body().string()).optJSONArray("hazards");
            if (values == null) return;
            SharedPreferences preferences = getApplicationContext().getSharedPreferences(PREFS, 0);
            Set<String> seen = new HashSet<>(preferences.getStringSet("critical_hazard_ids", new HashSet<>()));
            boolean initialized = preferences.getBoolean("critical_initialized", false);
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.optJSONObject(i);
                if (item == null || !"CRITICAL".equals(item.optString("severity"))) continue;
                String id = item.optString("id");
                if (initialized && !seen.contains(id)) {
                    notifyUser("Critical verified hazard", item.optString("title") + "\n" + item.optString("address"), 2000 + i);
                }
                seen.add(id);
            }
            preferences.edit().putStringSet("critical_hazard_ids", seen).putBoolean("critical_initialized", true).apply();
        }
    }

    private void notifyUser(String title, String body, int id) {
        if (Build.VERSION.SDK_INT >= 33 && getApplicationContext().checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(getApplicationContext(), MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_safenavi_beacon).setContentTitle(title).setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(pendingIntent);
        ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, builder.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Safety updates", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Official report updates and newly verified critical hazards");
        ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    private static String baseUrl() {
        String value = BuildConfig.RISK_API_BASE_URL;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }
}
