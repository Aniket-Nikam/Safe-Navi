package com.safenavi.app.product;

import android.content.Context;
import org.json.JSONObject;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.offline.OfflineManager;
import org.maplibre.android.offline.OfflineRegion;
import org.maplibre.android.offline.OfflineRegionError;
import org.maplibre.android.offline.OfflineRegionStatus;
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition;

public final class OfflineMapManager {
    private static final String STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private static final String REGION_NAME = "Navi Mumbai essential area";
    private OfflineMapManager() { }

    public interface Callback {
        void onStatus(String message, boolean downloaded);
        default void onProgress(long completed, long required, long bytes) { }
    }

    public static void status(Context context, Callback callback) {
        OfflineManager.getInstance(context).listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override public void onList(OfflineRegion[] regions) {
                OfflineRegion match = find(regions);
                if (match == null) { callback.onStatus("No offline area downloaded", false); return; }
                match.getStatus(new OfflineRegion.OfflineRegionStatusCallback() {
                    @Override public void onStatus(OfflineRegionStatus status) {
                        String size = humanBytes(status.getCompletedResourceSize());
                        callback.onStatus(status.isComplete() ? REGION_NAME + " · " + size + " · ready" : REGION_NAME + " · " + size + " · incomplete", status.isComplete());
                    }
                    @Override public void onError(String error) { callback.onStatus(error, false); }
                });
            }
            @Override public void onError(String error) { callback.onStatus(error, false); }
        });
    }

    public static void download(Context context, Callback callback) {
        OfflineManager manager = OfflineManager.getInstance(context);
        manager.listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override public void onList(OfflineRegion[] regions) {
                OfflineRegion existing = find(regions);
                if (existing != null) { observe(existing, callback); existing.setDownloadState(OfflineRegion.STATE_ACTIVE); return; }
                try {
                    LatLngBounds bounds = new LatLngBounds.Builder()
                            .include(new org.maplibre.android.geometry.LatLng(18.86, 72.76))
                            .include(new org.maplibre.android.geometry.LatLng(19.34, 73.24)).build();
                    float ratio = context.getResources().getDisplayMetrics().density;
                    OfflineTilePyramidRegionDefinition definition = new OfflineTilePyramidRegionDefinition(STYLE, bounds, 8, 14, ratio);
                    byte[] metadata = new JSONObject().put("name", REGION_NAME).put("version", 1).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    manager.createOfflineRegion(definition, metadata, new OfflineManager.CreateOfflineRegionCallback() {
                        @Override public void onCreate(OfflineRegion region) { observe(region, callback); region.setDownloadState(OfflineRegion.STATE_ACTIVE); }
                        @Override public void onError(String error) { callback.onStatus(error, false); }
                    });
                } catch (Exception error) { callback.onStatus("Could not prepare the offline area.", false); }
            }
            @Override public void onError(String error) { callback.onStatus(error, false); }
        });
    }

    public static void remove(Context context, Callback callback) {
        OfflineManager.getInstance(context).listOfflineRegions(new OfflineManager.ListOfflineRegionsCallback() {
            @Override public void onList(OfflineRegion[] regions) {
                OfflineRegion match = find(regions);
                if (match == null) { callback.onStatus("No offline area downloaded", false); return; }
                match.setDownloadState(OfflineRegion.STATE_INACTIVE);
                match.delete(new OfflineRegion.OfflineRegionDeleteCallback() {
                    @Override public void onDelete() { callback.onStatus("Offline area removed", false); }
                    @Override public void onError(String error) { callback.onStatus(error, true); }
                });
            }
            @Override public void onError(String error) { callback.onStatus(error, false); }
        });
    }

    private static void observe(OfflineRegion region, Callback callback) {
        region.setObserver(new OfflineRegion.OfflineRegionObserver() {
            @Override public void onStatusChanged(OfflineRegionStatus status) {
                callback.onProgress(status.getCompletedResourceCount(), status.getRequiredResourceCount(), status.getCompletedResourceSize());
                if (status.isComplete()) { region.setDownloadState(OfflineRegion.STATE_INACTIVE); callback.onStatus(REGION_NAME + " · " + humanBytes(status.getCompletedResourceSize()) + " · ready", true); }
            }
            @Override public void onError(OfflineRegionError error) { callback.onStatus(error.getMessage(), false); }
            @Override public void mapboxTileCountLimitExceeded(long limit) { region.setDownloadState(OfflineRegion.STATE_INACTIVE); callback.onStatus("Offline tile limit reached (" + limit + ").", false); }
        });
    }

    private static OfflineRegion find(OfflineRegion[] regions) {
        if (regions == null) return null;
        for (OfflineRegion region : regions) try {
            String name = new JSONObject(new String(region.getMetadata(), java.nio.charset.StandardCharsets.UTF_8)).optString("name");
            if (REGION_NAME.equals(name)) return region;
        } catch (Exception ignored) { }
        return null;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024 * 1024) return Math.max(1, bytes / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0);
    }
}
