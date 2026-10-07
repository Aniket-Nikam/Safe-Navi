package com.safenavi.app.product;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.safenavi.app.MapsActivity;
import com.safenavi.app.R;
import org.json.JSONObject;

public final class JourneyTrackingService extends Service {
    private static final String CHANNEL = "safe_navi_active_journey";
    private static final int NOTIFICATION_ID = 7401;
    private static final String ACTION_END = "com.safenavi.app.END_ACTIVE_JOURNEY";
    private FusedLocationProviderClient locationClient;
    private LocationCallback callback;

    public static void start(Context context) {
        ProductSession.setBackgroundJourneyEnabled(context, true);
        ContextCompat.startForegroundService(context, new Intent(context, JourneyTrackingService.class));
    }

    public static void stop(Context context) {
        ProductSession.setBackgroundJourneyEnabled(context, false);
        context.stopService(new Intent(context, JourneyTrackingService.class));
    }

    @Override public void onCreate() {
        super.onCreate(); createChannel();
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        callback = new LocationCallback() {
            @Override public void onLocationResult(LocationResult result) {
                android.location.Location location = result.getLastLocation();
                String journey = ProductSession.activeJourney(JourneyTrackingService.this);
                if (location == null || journey.isEmpty()) return;
                new ProductApiClient(JourneyTrackingService.this).updateJourneyLocation(journey,
                        location.getLatitude(), location.getLongitude(), silentCallback());
            }
        };
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_END.equals(intent.getAction())) { endJourney(); return START_NOT_STICKY; }
        String journey = ProductSession.activeJourney(this);
        if (journey.isEmpty() || !ProductSession.isSignedIn(this)) { stopSelf(); return START_NOT_STICKY; }
        startForeground(NOTIFICATION_ID, notification());
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ProductSession.setBackgroundJourneyEnabled(this, false); stopSelf(); return START_NOT_STICKY;
        }
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
                .setMinUpdateIntervalMillis(15_000L).setMaxUpdateDelayMillis(60_000L).build();
        locationClient.removeLocationUpdates(callback);
        locationClient.requestLocationUpdates(request, callback, getMainLooper());
        return START_STICKY;
    }

    private android.app.Notification notification() {
        Intent open = new Intent(this, MapsActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 7402, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent end = new Intent(this, JourneyTrackingService.class).setAction(ACTION_END);
        PendingIntent endAction = PendingIntent.getService(this, 7403, end, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_safenavi_beacon)
                .setContentTitle("Safe-Navi journey sharing active")
                .setContentText("Your latest location is shared with holders of your consented journey link.")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Background location sharing is active for your current journey. Tap to return to the map."))
                .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(content)
                .addAction(0, "End journey", endAction).build();
    }

    private void endJourney() {
        String journey = ProductSession.activeJourney(this);
        if (journey.isEmpty()) { stop(this); return; }
        new ProductApiClient(this).endJourney(journey, new ProductApiClient.ObjectCallback() {
            private void finish() { ProductSession.setActiveJourney(JourneyTrackingService.this, null); stop(JourneyTrackingService.this); }
            @Override public void onSuccess(JSONObject value) { finish(); }
            @Override public void onError(String message) { JourneyEndWorker.schedule(JourneyTrackingService.this, journey); finish(); }
        });
    }

    private ProductApiClient.ObjectCallback silentCallback() {
        return new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { }
            @Override public void onError(String message) { }
        };
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Active journey sharing", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Required while Safe-Navi shares a consented journey location in the background");
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    @Override public void onDestroy() {
        if (locationClient != null && callback != null) locationClient.removeLocationUpdates(callback);
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
