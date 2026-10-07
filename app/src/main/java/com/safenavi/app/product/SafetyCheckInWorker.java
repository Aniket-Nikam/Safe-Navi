package com.safenavi.app.product;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.safenavi.app.MainActivity;
import com.safenavi.app.R;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class SafetyCheckInWorker extends Worker {
    private static final String PERIODIC = "safe-navi-safety-check-in";
    private static final String ACK = "safe-navi-safety-check-in-ack";
    private static final String CHANNEL = "safe_navi_checkins";

    public SafetyCheckInWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    public static void schedule(Context context, int minutes) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(SafetyCheckInWorker.class, Math.max(15, minutes), TimeUnit.MINUTES)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void cancel(Context context) { WorkManager.getInstance(context).cancelUniqueWork(PERIODIC); }

    public static void acknowledgeNow(Context context) {
        Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SafetyCheckInWorker.class)
                .setInputData(new Data.Builder().putBoolean("ack", true).build()).setConstraints(network).build();
        WorkManager.getInstance(context).enqueueUniqueWork(ACK, ExistingWorkPolicy.REPLACE, request);
    }

    @NonNull @Override public Result doWork() {
        String journey = ProductSession.activeJourney(getApplicationContext());
        String token = ProductSession.token(getApplicationContext());
        if (journey.isEmpty() || token.isEmpty()) return Result.success();
        if (getInputData().getBoolean("ack", false)) return postAck(journey, token);
        showPrompt();
        return Result.success();
    }

    private Result postAck(String journey, String token) {
        Request request = new Request.Builder().url(ProductApiClient.apiBaseUrl() + "/api/v1/journeys/" + journey + "/check-in")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create("{}", MediaType.parse("application/json"))).build();
        try (Response response = new OkHttpClient().newCall(request).execute()) {
            if (response.isSuccessful()) {
                ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE)).cancel(7301);
                return Result.success();
            }
            return response.code() >= 500 ? Result.retry() : Result.failure();
        } catch (Exception error) { return Result.retry(); }
    }

    private void showPrompt() {
        if (Build.VERSION.SDK_INT >= 33 && getApplicationContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        createChannel();
        Intent ack = new Intent(getApplicationContext(), SafetyCheckInReceiver.class);
        PendingIntent ackIntent = PendingIntent.getBroadcast(getApplicationContext(), 7302, ack,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(getApplicationContext(), 7303, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL)
                .setSmallIcon(R.drawable.ic_safenavi_beacon).setContentTitle("Safety check-in")
                .setContentText("Let your trusted journey viewers know you are safe.")
                .setPriority(NotificationCompat.PRIORITY_HIGH).setOngoing(true).setContentIntent(openIntent)
                .addAction(0, "I’m safe", ackIntent);
        ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE)).notify(7301, notification.build());
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Journey safety check-ins", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Prompts for active consented journey sharing");
        ((NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
}
