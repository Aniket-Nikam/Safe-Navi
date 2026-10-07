package com.safenavi.app.product;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Ensures a locally-ended journey is closed on the server after connectivity returns. */
public final class JourneyEndWorker extends Worker {
    private static final String JOURNEY_ID = "journey_id";
    private final OkHttpClient client = new OkHttpClient();

    public JourneyEndWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    public static void schedule(Context context, String journeyId) {
        if (journeyId == null || journeyId.isEmpty()) return;
        Constraints network = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(JourneyEndWorker.class)
                .setInputData(new Data.Builder().putString(JOURNEY_ID, journeyId).build())
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                "safe-navi-end-journey-" + journeyId, ExistingWorkPolicy.KEEP, request);
    }

    @NonNull @Override public Result doWork() {
        String journeyId = getInputData().getString(JOURNEY_ID);
        String token = ProductSession.token(getApplicationContext());
        if (journeyId == null || journeyId.isEmpty() || token.isEmpty()) return Result.success();
        Request request = new Request.Builder()
                .url(ProductApiClient.apiBaseUrl() + "/api/v1/journeys/" + journeyId + "/end")
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create("{}", MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() || response.code() == 404 || response.code() == 409) return Result.success();
            return response.code() >= 500 ? Result.retry() : Result.failure();
        } catch (Exception error) {
            return Result.retry();
        }
    }
}
