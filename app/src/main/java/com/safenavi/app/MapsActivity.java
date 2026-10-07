package com.safenavi.app;

import static org.maplibre.android.style.layers.PropertyFactory.circleBlur;
import static org.maplibre.android.style.layers.PropertyFactory.circleColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.circleRadius;
import static org.maplibre.android.style.layers.PropertyFactory.lineColor;
import static org.maplibre.android.style.layers.PropertyFactory.lineOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.lineWidth;
import static org.maplibre.android.style.layers.PropertyFactory.fillColor;
import static org.maplibre.android.style.layers.PropertyFactory.fillOpacity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.safenavi.app.product.ProductApiClient;
import com.safenavi.app.product.ProductSession;
import com.safenavi.app.product.SafetyCheckInWorker;
import com.safenavi.app.product.JourneyTrackingService;
import com.safenavi.app.product.JourneyEndWorker;
import com.safenavi.app.safety.model.DatasetPointRisk;
import com.safenavi.app.safety.model.DatasetRouteEvaluation;
import com.safenavi.app.safety.model.Hazard;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.HazardSeverity;
import com.safenavi.app.safety.model.HazardStatus;
import com.safenavi.app.safety.model.HazardLocationType;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.model.RouteCandidate;
import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.model.RouteSafetyResult;
import com.safenavi.app.safety.model.TravelMode;
import com.safenavi.app.safety.network.OpenMapService;
import com.safenavi.app.safety.network.DatasetRiskService;
import com.safenavi.app.safety.network.OfflineRouter;
import com.safenavi.app.safety.network.OfflineHazardCache;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

public class MapsActivity extends AppCompatActivity {
    private static final int LOCATION_REQUEST = 301;
    private static final String MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private static final String ROUTES_SOURCE = "safe-navi-routes";
    private static final String ROUTES_LAYER = "safe-navi-route-options";
    private static final String SELECTED_ROUTE_SOURCE = "safe-navi-selected-route";
    private static final String SELECTED_ROUTE_LAYER = "safe-navi-selected-route-line";
    private static final List<String> HAZARD_LAYER_IDS = Arrays.asList(
            "hazard-low-point", "hazard-moderate-point", "hazard-high-point", "hazard-critical-point",
            "hazard-geometry-line", "hazard-geometry-fill", "pending-report-point");

    private MapView mapView;
    private MapLibreMap map;
    private Style mapStyle;
    private OpenMapService openMapService;
    private DatasetRiskService datasetRiskService;
    private FusedLocationProviderClient locationClient;
    private OfflineRouter offlineRouter;
    private TextInputEditText sourceInput;
    private TextInputEditText destinationInput;
    private ProgressBar routeProgress;
    private MaterialButton findRouteButton;
    private MaterialButton journeyButton;
    private MaterialButton travelModeButton;
    private MaterialButton routeProfileButton;
    private View routeResultCard;
    private View detailCard;
    private TextView scoreText;
    private TextView scoreLabel;
    private TextView routeResultTitle;
    private TextView routeResultMeta;
    private TextView routeResultRisk;
    private TextView detailTitle;
    private TextView detailMeta;
    private TextView detailReason;
    private int activeRouteRequest;
    private int activeScoreRequest;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable hazardRefresh = new Runnable() {
        @Override public void run() {
            loadHazards();
            refreshHandler.postDelayed(this, 30_000L);
        }
    };
    private String hazardSignature = "";
    private String hazardCategoryFilter = "ALL";
    private PlaceSearchResult lastDestination;
    private String lastRouteSummary = "";
    private String currentHazardId;
    private String activeJourneyId;
    private String activeJourneyToken;
    private TravelMode travelMode = TravelMode.DRIVING;
    private RouteProfile routeProfile = RouteProfile.BALANCED;
    private WebSocket liveUpdateSocket;
    private final Runnable journeyRefresh = new Runnable() {
        @Override public void run() {
            if (activeJourneyId != null) { updateActiveJourneyLocation(); refreshHandler.postDelayed(this, 30_000L); }
        }
    };
    private final Map<String, Hazard> liveHazards = new LinkedHashMap<>();
    private final Map<String, JSONObject> liveHazardRecords = new LinkedHashMap<>();
    private final Map<String, JSONObject> pendingReports = new LinkedHashMap<>();
    private final List<LatLng> geometryDraft = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_maps);
        openMapService = new OpenMapService();
        datasetRiskService = new DatasetRiskService();
        offlineRouter = new OfflineRouter(this);
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        bindViews();
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.getUiSettings().setCompassEnabled(true);
            map.setStyle(new Style.Builder().fromUri(MAP_STYLE), style -> {
                mapStyle = style;
                loadHazards();
                loadPendingReports();
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(19.0760, 72.9777), 10.7));
                map.addOnMapClickListener(this::onMapClick);
                map.addOnMapLongClickListener(this::onMapLongClick);
            });
        });
        renderScore(19.0657, 72.9986);
    }

    private void bindViews() {
        mapView = findViewById(R.id.mapView);
        sourceInput = findViewById(R.id.sourceInput);
        destinationInput = findViewById(R.id.destinationInput);
        routeProgress = findViewById(R.id.routeProgress);
        findRouteButton = findViewById(R.id.findRouteButton);
        journeyButton = findViewById(R.id.startJourneyButton);
        travelModeButton = findViewById(R.id.travelModeButton);
        routeProfileButton = findViewById(R.id.routeProfileButton);
        routeResultCard = findViewById(R.id.routeResultCard);
        detailCard = findViewById(R.id.hazardDetailCard);
        scoreText = findViewById(R.id.mapSafetyScore);
        scoreLabel = findViewById(R.id.mapSafetyLabel);
        routeResultTitle = findViewById(R.id.routeResultTitle);
        routeResultMeta = findViewById(R.id.routeResultMeta);
        routeResultRisk = findViewById(R.id.routeResultRisk);
        detailTitle = findViewById(R.id.hazardTitle);
        detailMeta = findViewById(R.id.hazardMeta);
        detailReason = findViewById(R.id.hazardReason);
        TextInputLayout destinationLayout = findViewById(R.id.destinationInputLayout);
        destinationLayout.setEndIconContentDescription("Swap source and destination");
        destinationLayout.setEndIconOnClickListener(v -> {
            String source = textOf(sourceInput);
            sourceInput.setText(textOf(destinationInput));
            destinationInput.setText(source);
        });
        if (getIntent().hasExtra("destinationLatitude") && getIntent().hasExtra("destinationLongitude")) {
            destinationInput.setText(String.format(Locale.US, "%.6f, %.6f",
                    getIntent().getDoubleExtra("destinationLatitude", 0),
                    getIntent().getDoubleExtra("destinationLongitude", 0)));
        }
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.locationButton).setOnClickListener(v -> enableLocationWithContext());
        findViewById(R.id.mapToolsButton).setOnClickListener(v -> showMapTools());
        travelModeButton.setOnClickListener(v -> showTravelModePicker());
        routeProfileButton.setOnClickListener(v -> showRouteProfilePicker());
        findViewById(R.id.emergencyButton).setOnClickListener(v -> showEmergencyTools());
        findRouteButton.setOnClickListener(v -> findRoutes());
        findViewById(R.id.shareRouteButton).setOnClickListener(v -> shareRoute());
        findViewById(R.id.openNavigationButton).setOnClickListener(v -> openNavigation());
        findViewById(R.id.saveDestinationButton).setOnClickListener(v -> saveDestination());
        journeyButton.setOnClickListener(v -> { if (activeJourneyId == null) startSharedJourney(); else endSharedJourney(); });
        findViewById(R.id.closeDetailButton).setOnClickListener(v -> detailCard.setVisibility(View.GONE));
        detailCard.setOnLongClickListener(v -> { if (isGovernmentUser() && currentHazardId != null) { showHazardLifecycleDialog(); return true; } return false; });
    }

    private void showMapTools() {
        String[] tools = {"Saved places", "Visible safety layers"};
        new MaterialAlertDialogBuilder(this).setTitle("Map tools").setItems(tools, (dialog, which) -> {
            if (which == 0) showSavedPlaces(); else showHazardFilters();
        }).setNegativeButton("Close", null).show();
    }

    private void showTravelModePicker() {
        String[] labels = {getString(R.string.drive), getString(R.string.walk), getString(R.string.cycle)};
        TravelMode[] values = {TravelMode.DRIVING, TravelMode.WALKING, TravelMode.CYCLING};
        int checked = travelMode == TravelMode.WALKING ? 1 : travelMode == TravelMode.CYCLING ? 2 : 0;
        new MaterialAlertDialogBuilder(this).setTitle("Travel mode").setSingleChoiceItems(labels, checked, (dialog, which) -> {
            travelMode = values[which]; travelModeButton.setText(labels[which]); dialog.dismiss();
        }).setNegativeButton("Cancel", null).show();
    }

    private void showRouteProfilePicker() {
        String[] labels = {getString(R.string.fastest), getString(R.string.balanced), getString(R.string.safest)};
        RouteProfile[] values = {RouteProfile.FASTEST, RouteProfile.BALANCED, RouteProfile.SAFEST};
        int checked = routeProfile == RouteProfile.FASTEST ? 0 : routeProfile == RouteProfile.SAFEST ? 2 : 1;
        new MaterialAlertDialogBuilder(this).setTitle("Route priority").setSingleChoiceItems(labels, checked, (dialog, which) -> {
            routeProfile = values[which]; routeProfileButton.setText(labels[which]); dialog.dismiss();
        }).setNegativeButton("Cancel", null).show();
    }

    private void showHazardFilters() {
        String[] labels = {"All verified hazards", "Crime", "Flooding", "Poor lighting", "Road and construction", "Other civic issues"};
        String[] values = {"ALL", "CRIME", "FLOOD", "LIGHT", "ROAD", "OTHER"};
        int selected = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(hazardCategoryFilter)) selected = i;
        new MaterialAlertDialogBuilder(this).setTitle("Visible safety layers")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    hazardCategoryFilter = values[which]; renderHazards(); dialog.dismiss();
                    Toast.makeText(this, labels[which] + " shown", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Cancel", null).show();
    }

    private boolean onMapClick(@NonNull LatLng point) {
        detailCard.setVisibility(View.GONE);
        renderScore(point.getLatitude(), point.getLongitude());
        if (map == null || mapStyle == null) return false;
        List<Feature> features = map.queryRenderedFeatures(
                map.getProjection().toScreenLocation(point), HAZARD_LAYER_IDS.toArray(new String[0]));
        if (!features.isEmpty() && features.get(0).hasProperty("hazardId")) {
            showHazard(features.get(0).getStringProperty("hazardId"));
            return true;
        }
        if (!features.isEmpty() && features.get(0).hasProperty("reportId")) {
            showPendingReport(features.get(0).getStringProperty("reportId"));
            return true;
        }
        return false;
    }

    private void loadPendingReports() {
        new ProductApiClient(this).reports(null, new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) {
                Map<String, JSONObject> loaded = new LinkedHashMap<>();
                for (int i = 0; i < values.length(); i++) {
                    JSONObject report = values.optJSONObject(i);
                    if (report == null) continue;
                    String status = report.optString("status");
                    if (status.equals("REPORTED") || status.equals("UNDER_REVIEW") || status.equals("REQUESTED_INFO")) loaded.put(report.optString("id"), report);
                }
                runOnUiThread(() -> { pendingReports.clear(); pendingReports.putAll(loaded); renderPendingReports(); });
            }
            @Override public void onError(String message) { /* Public hazard map remains usable without a citizen session. */ }
        });
    }

    private void renderPendingReports() {
        if (mapStyle == null) return;
        String sourceId = "pending-report-source";
        String layerId = "pending-report-point";
        if (mapStyle.getLayer(layerId) != null) mapStyle.removeLayer(layerId);
        if (mapStyle.getSource(sourceId) != null) mapStyle.removeSource(sourceId);
        List<Feature> features = new ArrayList<>();
        for (JSONObject report : pendingReports.values()) {
            if (!report.has("latitude") || !report.has("longitude")) continue;
            Feature feature = Feature.fromGeometry(Point.fromLngLat(report.optDouble("longitude"), report.optDouble("latitude")));
            feature.addStringProperty("reportId", report.optString("id"));
            features.add(feature);
        }
        mapStyle.addSource(new GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features)));
        mapStyle.addLayer(new CircleLayer(layerId, sourceId).withProperties(circleRadius(7f),
                circleColor(Color.parseColor("#3578C8")), circleOpacity(0.92f)));
    }

    private void showPendingReport(String reportId) {
        JSONObject report = pendingReports.get(reportId);
        if (report == null) return;
        currentHazardId = null;
        detailTitle.setText(report.optString("title", "Your pending report"));
        detailMeta.setText(report.optString("status").replace('_', ' ') + " · Visible only to you and reviewers");
        detailReason.setText(report.optString("description") + "\n" + report.optString("address"));
        detailCard.setVisibility(View.VISIBLE);
    }

    private boolean onMapLongClick(@NonNull LatLng point) {
        if (isGovernmentUser()) {
            if (currentHazardId == null) {
                new MaterialAlertDialogBuilder(this).setTitle("Select a verified hazard")
                        .setMessage("Tap a hazard first. Then long-press points along the affected road or around the affected area.")
                        .setPositiveButton("Got it", null).show();
                return true;
            }
            geometryDraft.add(point);
            renderGeometryDraft();
            showGeometryDraftActions();
            return true;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Report this location?")
                .setMessage(String.format(Locale.US, "Create a citizen report at %.5f, %.5f. It will enter official review before affecting routes.", point.getLatitude(), point.getLongitude()))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Start report", (dialog, which) -> startActivity(new Intent(this, MainActivity.class)
                        .putExtra("reportLatitude", point.getLatitude())
                        .putExtra("reportLongitude", point.getLongitude())))
                .show();
        return true;
    }

    private void showGeometryDraftActions() {
        String[] actions = {"Add another point", "Save as road corridor", "Save as affected area", "Clear draft"};
        new MaterialAlertDialogBuilder(this).setTitle(geometryDraft.size() + " geometry point" + (geometryDraft.size() == 1 ? "" : "s"))
                .setMessage("Long-press the map to add each next point. A road needs at least 2 points and an area needs at least 3.")
                .setItems(actions, (dialog, which) -> {
                    if (which == 1) saveHazardGeometry("LINESTRING", 2);
                    else if (which == 2) saveHazardGeometry("POLYGON", 3);
                    else if (which == 3) { geometryDraft.clear(); renderGeometryDraft(); }
                }).setNegativeButton("Close", null).show();
    }

    private void saveHazardGeometry(String type, int minimumPoints) {
        if (geometryDraft.size() < minimumPoints) {
            Toast.makeText(this, "Add at least " + minimumPoints + " points.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray coordinates = new JSONArray();
            for (LatLng point : geometryDraft) coordinates.put(new JSONObject()
                    .put("latitude", point.getLatitude()).put("longitude", point.getLongitude()));
            JSONObject update = new JSONObject().put("geometry_type", type).put("geometry_coordinates", coordinates)
                    .put("reason", type.equals("POLYGON") ? "Affected area mapped by the operations team"
                            : "Affected road corridor mapped by the operations team");
            new ProductApiClient(this).updateHazard(currentHazardId, update, new ProductApiClient.ObjectCallback() {
                @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> {
                    geometryDraft.clear(); renderGeometryDraft(); detailCard.setVisibility(View.GONE); loadHazards();
                    Toast.makeText(MapsActivity.this, type.equals("POLYGON") ? "Affected area published" : "Road corridor published", Toast.LENGTH_LONG).show();
                }); }
                @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_LONG).show()); }
            });
        } catch (JSONException ignored) { }
    }

    private void renderGeometryDraft() {
        if (mapStyle == null) return;
        String sourceId = "hazard-geometry-draft-source", layerId = "hazard-geometry-draft-line";
        if (mapStyle.getLayer(layerId) != null) mapStyle.removeLayer(layerId);
        if (mapStyle.getSource(sourceId) != null) mapStyle.removeSource(sourceId);
        if (geometryDraft.size() < 2) return;
        List<Point> points = new ArrayList<>();
        for (LatLng value : geometryDraft) points.add(Point.fromLngLat(value.getLongitude(), value.getLatitude()));
        mapStyle.addSource(new GeoJsonSource(sourceId, Feature.fromGeometry(LineString.fromLngLats(points))));
        mapStyle.addLayer(new LineLayer(layerId, sourceId).withProperties(
                lineColor(Color.parseColor("#087F6A")), lineWidth(7f), lineOpacity(0.9f)));
    }

    private void renderHazards() {
        if (mapStyle == null) return;
        for (HazardSeverity severity : HazardSeverity.values()) {
            List<Feature> features = new ArrayList<>();
            for (Hazard hazard : liveHazards.values()) {
                if (hazard.getSeverity() != severity || hazard.getLocation() == null
                        || !hazard.getStatus().affectsImmediateNavigation()) continue;
                if (!matchesHazardFilter(hazard.getCategoryKey())) continue;
                Feature feature = Feature.fromGeometry(Point.fromLngLat(
                        hazard.getLocation().getLongitude(), hazard.getLocation().getLatitude()));
                feature.addStringProperty("hazardId", hazard.getId());
                features.add(feature);
            }
            String key = severity.name().toLowerCase(Locale.US);
            String sourceId = "hazard-" + key + "-source";
            String heatId = "hazard-" + key + "-heat";
            String pointId = "hazard-" + key + "-point";
            if (mapStyle.getLayer(pointId) != null) mapStyle.removeLayer(pointId);
            if (mapStyle.getLayer(heatId) != null) mapStyle.removeLayer(heatId);
            if (mapStyle.getSource(sourceId) != null) mapStyle.removeSource(sourceId);
            mapStyle.addSource(new GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features)));
            int color = severityColor(severity);
            mapStyle.addLayer(new CircleLayer(heatId, sourceId).withProperties(
                    circleRadius(40f), circleColor(color), circleOpacity(0.16f), circleBlur(0.65f)));
            mapStyle.addLayer(new CircleLayer(pointId, sourceId).withProperties(
                    circleRadius(8f), circleColor(color), circleOpacity(0.95f)));
        }
        renderHazardGeometries();
    }

    private void renderHazardGeometries() {
        String sourceId = "hazard-geometry-source", fillId = "hazard-geometry-fill", lineId = "hazard-geometry-line";
        if (mapStyle.getLayer(lineId) != null) mapStyle.removeLayer(lineId);
        if (mapStyle.getLayer(fillId) != null) mapStyle.removeLayer(fillId);
        if (mapStyle.getSource(sourceId) != null) mapStyle.removeSource(sourceId);
        List<Feature> features = new ArrayList<>();
        for (JSONObject record : liveHazardRecords.values()) {
            JSONArray coordinates = record.optJSONArray("geometry_coordinates");
            String type = record.optString("geometry_type", "POINT");
            if (coordinates == null || coordinates.length() < 2 || type.equals("POINT")) continue;
            List<Point> points = new ArrayList<>();
            for (int i = 0; i < coordinates.length(); i++) {
                JSONObject point = coordinates.optJSONObject(i);
                if (point != null) points.add(Point.fromLngLat(point.optDouble("longitude"), point.optDouble("latitude")));
            }
            Feature feature;
            if (type.equals("POLYGON") && points.size() >= 3) {
                if (!points.get(0).equals(points.get(points.size() - 1))) points.add(points.get(0));
                feature = Feature.fromGeometry(org.maplibre.geojson.Polygon.fromLngLats(java.util.Collections.singletonList(points)));
            } else if (points.size() >= 2) feature = Feature.fromGeometry(LineString.fromLngLats(points));
            else continue;
            feature.addStringProperty("hazardId", record.optString("id")); features.add(feature);
        }
        mapStyle.addSource(new GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features)));
        mapStyle.addLayer(new org.maplibre.android.style.layers.FillLayer(fillId, sourceId).withProperties(
                fillColor(Color.parseColor("#F26B5E")), fillOpacity(0.18f)));
        mapStyle.addLayer(new LineLayer(lineId, sourceId).withProperties(
                lineColor(Color.parseColor("#D95046")), lineWidth(6f), lineOpacity(0.88f)));
    }

    private boolean matchesHazardFilter(String category) {
        String value = category == null ? "" : category.toUpperCase(Locale.US);
        if (hazardCategoryFilter.equals("ALL")) return true;
        if (hazardCategoryFilter.equals("CRIME")) return value.contains("CRIME");
        if (hazardCategoryFilter.equals("FLOOD")) return value.contains("FLOOD");
        if (hazardCategoryFilter.equals("LIGHT")) return value.contains("LIGHT");
        if (hazardCategoryFilter.equals("ROAD")) return value.contains("ROAD") || value.contains("CONSTRUCTION") || value.contains("ACCIDENT");
        return !value.contains("CRIME") && !value.contains("FLOOD") && !value.contains("LIGHT") && !value.contains("ROAD") && !value.contains("CONSTRUCTION");
    }

    private void loadHazards() {
        new ProductApiClient(this).mapHazards(new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) {
                OfflineHazardCache.save(MapsActivity.this, value);
                runOnUiThread(() -> applyHazardResponse(value, false));
            }
            @Override public void onError(String message) {
                JSONObject cached = OfflineHazardCache.load(MapsActivity.this);
                runOnUiThread(() -> { applyHazardResponse(cached, true); Toast.makeText(MapsActivity.this,
                        "Offline: showing the last verified safety snapshot.", Toast.LENGTH_SHORT).show(); });
            }
        });
    }

    private void applyHazardResponse(JSONObject value, boolean cached) {
        JSONArray items = value.optJSONArray("hazards"); Map<String, Hazard> loaded = new LinkedHashMap<>();
        Map<String, JSONObject> loadedRecords = new LinkedHashMap<>(); StringBuilder signature = new StringBuilder();
        if (items != null) for (int i = 0; i < items.length(); i++) { JSONObject item = items.optJSONObject(i); if (item == null) continue;
            try { Hazard hazard = new Hazard(); hazard.setId(item.getString("id")); hazard.setTitle(item.getString("title"));
                hazard.setCategoryKey(item.getString("category_key")); hazard.setDescription(item.optString("description"));
                hazard.setReason(item.getString("reason")); hazard.setSeverity(HazardSeverity.valueOf(item.getString("severity")));
                hazard.setStatus(HazardStatus.valueOf(item.getString("status"))); hazard.setGovernmentVerified(true);
                hazard.setLocation(new HazardLocation(HazardLocationType.POINT, item.getDouble("latitude"), item.getDouble("longitude"), item.optString("address")));
                loaded.put(hazard.getId(), hazard); loadedRecords.put(hazard.getId(), item);
                signature.append(hazard.getId()).append(hazard.getSeverity()).append(hazard.getStatus()).append(hazard.getReason());
            } catch (Exception ignored) { }
        }
        boolean changed = !cached && !hazardSignature.isEmpty() && !hazardSignature.equals(signature.toString());
        hazardSignature = signature.toString(); liveHazards.clear(); liveHazards.putAll(loaded);
        liveHazardRecords.clear(); liveHazardRecords.putAll(loadedRecords); renderHazards();
        if (changed && routeResultCard.getVisibility() == View.VISIBLE && !textOf(sourceInput).isEmpty() && !textOf(destinationInput).isEmpty()) {
            Toast.makeText(this, "Verified safety conditions changed. Re-ranking routes...", Toast.LENGTH_LONG).show(); findRoutes();
        }
    }

    private void showHazard(String hazardId) {
        Hazard hazard = liveHazards.get(hazardId);
        if (hazard == null) return;
        currentHazardId = hazardId;
        detailTitle.setText(hazard.getTitle());
        detailMeta.setText(hazard.getSeverity().name() + " · " + hazard.getStatus().name()
                + " · Government verified");
        detailReason.setText(hazard.getReason() + "\n" + hazard.getLocation().getDisplayName());
        detailCard.setVisibility(View.VISIBLE);
        renderScore(hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude());
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
                hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude()), 14.2));
        if (isGovernmentUser()) detailReason.append("\n\nLong-press this card to update the official lifecycle.");
    }

    private boolean isGovernmentUser() {
        String role = ProductSession.role(this);
        return "government".equals(role) || "admin".equals(role);
    }

    private void showHazardLifecycleDialog() {
        String[] labels = {"Keep active", "Monitor", "Resolve as safe", "Close road record"};
        String[] statuses = {"ACTIVE", "MONITORING", "RESOLVED", "CLOSED"};
        new MaterialAlertDialogBuilder(this).setTitle("Update verified hazard").setItems(labels, (dialog, which) -> {
            TextInputEditText reason = new TextInputEditText(this); reason.setHint("Field observation or resolution evidence"); reason.setMinLines(3); reason.setPadding(48, 12, 48, 12);
            new MaterialAlertDialogBuilder(this).setTitle(labels[which]).setView(reason).setNegativeButton("Cancel", null)
                    .setPositiveButton("Save audited update", (d,w) -> {
                        String note = reason.getText() == null ? "" : reason.getText().toString().trim();
                        if (note.length() < 3) { Toast.makeText(this, "Add an audit reason.", Toast.LENGTH_LONG).show(); return; }
                        try {
                            JSONObject payload = new JSONObject().put("status", statuses[which]).put("reason", note);
                            if ("RESOLVED".equals(statuses[which])) payload.put("road_status", "SAFE");
                            new ProductApiClient(this).updateHazard(currentHazardId, payload, new ProductApiClient.ObjectCallback() {
                                @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> { Toast.makeText(MapsActivity.this, "Hazard lifecycle updated", Toast.LENGTH_SHORT).show(); detailCard.setVisibility(View.GONE); loadHazards(); }); }
                                @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_LONG).show()); }
                            });
                        } catch (Exception ignored) { }
                    }).show();
        }).show();
    }

    private void findRoutes() {
        String sourceText = textOf(sourceInput);
        String destinationText = textOf(destinationInput);
        if (sourceText.isEmpty() || destinationText.isEmpty()) {
            Toast.makeText(this, "Enter both source and destination.", Toast.LENGTH_SHORT).show();
            return;
        }
        int requestId = ++activeRouteRequest;
        sourceInput.clearFocus();
        destinationInput.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View focused = getCurrentFocus();
        if (keyboard != null && focused != null) keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        setRouteBusy(true);
        resolvePlace(sourceText, requestId, source -> resolvePlace(destinationText, requestId,
                destination -> requestRoutes(source, destination, requestId)));
    }

    private void requestRoutes(PlaceSearchResult source, PlaceSearchResult destination, int requestId) {
        TravelMode mode = selectedTravelMode();
        openMapService.routes(source.getLatitude(), source.getLongitude(), destination.getLatitude(),
                destination.getLongitude(), mode,
                new OpenMapService.ResultCallback<List<RouteCandidate>>() {
                    @Override public void onSuccess(List<RouteCandidate> candidates) {
                        runOnUiThread(() -> {
                            if (isRouteRequestActive(requestId)) showRankedRoutes(candidates, source,
                                    destination, requestId, "OpenStreetMap/Valhalla");
                        });
                    }

                    @Override public void onError(String message) {
                        if (!isRouteRequestActive(requestId)) return;
                        if (message.toLowerCase(Locale.US).contains("authoritative closure")
                                || message.toLowerCase(Locale.US).contains("no confirmed safe alternative")) {
                            runOnUiThread(() -> showRouteError(requestId, "No confirmed safe alternative. " + message));
                        } else requestOfflineRoute(source, destination, requestId, message);
                    }
                });
    }

    private void requestOfflineRoute(PlaceSearchResult source, PlaceSearchResult destination, int requestId, String onlineFailure) {
        offlineRouter.route(source.getLatitude(), source.getLongitude(), destination.getLatitude(), destination.getLongitude(),
                selectedTravelMode(), selectedRouteProfile(), new OfflineRouter.Callback<OfflineRouter.Result>() {
                    @Override public void onSuccess(OfflineRouter.Result result) { runOnUiThread(() -> {
                        if (!isRouteRequestActive(requestId)) return;
                        RouteSafetyResult ranked = new RouteSafetyResult(result.route, selectedRouteProfile(), result.riskExposure,
                                result.route.getTravelMinutes() + (selectedRouteProfile() == RouteProfile.SAFEST ? 1.0 : selectedRouteProfile() == RouteProfile.BALANCED ? 0.35 : 0.0) * result.riskExposure);
                        renderRankedRoutes(java.util.Collections.singletonList(ranked), java.util.Collections.singletonList(result.route),
                                selectedRouteProfile(), source, destination, "On-device Navi Mumbai graph",
                                "Offline conservative dataset risk · verified closure cache");
                    }); }
                    @Override public void onError(String message) { runOnUiThread(() -> {
                        if (isRouteRequestActive(requestId)) showRouteError(requestId, onlineFailure + "\n\n" + message);
                    }); }
                });
    }

    private void showRankedRoutes(List<RouteCandidate> candidates, PlaceSearchResult source,
                                  PlaceSearchResult destination, int requestId,
                                  String routeProviderLabel) {
        if (candidates.isEmpty()) {
            showRouteError(requestId, "No route was returned for this travel mode.");
            return;
        }
        RouteProfile profile = selectedRouteProfile();
        datasetRiskService.evaluateRoutes(candidates, profile, currentTimePeriod(),
                new DatasetRiskService.ResultCallback<DatasetRouteEvaluation>() {
                    @Override public void onSuccess(DatasetRouteEvaluation evaluation) {
                        runOnUiThread(() -> {
                            if (isRouteRequestActive(requestId)) renderRankedRoutes(
                                    evaluation.getRankedResults(), candidates, profile, source,
                                    destination, routeProviderLabel, (evaluation.getProvider().endsWith("_ml")
                                            ? "ML " + evaluation.getModelVersion() + " · " + Math.round(evaluation.getConfidence() * 100) + "% confidence · "
                                            : "Baseline dataset · ")
                                            + readable(evaluation.getTimePeriod()) + " · "
                                            + Math.round(evaluation.getCoverageRatio() * 100)
                                            + "% coverage · " + evaluation.getFactorSummary());
                        });
                    }

                    @Override public void onError(String message) {
                        if (!isRouteRequestActive(requestId)) return;
                        String lower = message.toLowerCase(Locale.US);
                        if (lower.contains("authoritative closure") || lower.contains("no confirmed safe alternative"))
                            runOnUiThread(() -> showRouteError(requestId, "No confirmed safe alternative. " + message));
                        else requestOfflineRoute(source, destination, requestId, message);
                    }
                });
    }

    private void renderRankedRoutes(List<RouteSafetyResult> ranked, List<RouteCandidate> candidates,
                                    RouteProfile profile, PlaceSearchResult source,
                                    PlaceSearchResult destination, String routeProviderLabel,
                                    String sourceLabel) {
        setRouteBusy(false);
        if (ranked.isEmpty()) {
            showRouteError(activeRouteRequest, "The risk service returned no ranked route.");
            return;
        }
        RouteSafetyResult best = ranked.get(0);
        RouteSafetyResult fastest = best;
        for (RouteSafetyResult result : ranked) if (result.getRoute().getTravelMinutes() < fastest.getRoute().getTravelMinutes()) fastest = result;
        renderRoutes(candidates, best.getRoute());
        routeResultTitle.setText(profileName(profile) + " recommendation · "
                + selectedTravelMode().getLabel());
        routeResultMeta.setText(String.format(Locale.US, "%.1f km · %.0f min · %d alternative%s",
                best.getRoute().getDistanceMeters() / 1000.0, best.getRoute().getTravelMinutes(),
                candidates.size(), candidates.size() == 1 ? "" : "s"));
        StringBuilder comparison = new StringBuilder(String.format(Locale.US,
                "Safety exposure %.0f/100 · %s · %s", best.getRiskExposure(), sourceLabel, routeProviderLabel));
        double extraMinutes = best.getRoute().getTravelMinutes() - fastest.getRoute().getTravelMinutes();
        double avoidedRisk = fastest.getRiskExposure() - best.getRiskExposure();
        if (extraMinutes > 0.5 && avoidedRisk > 0.5) comparison.append(String.format(Locale.US,
                "\nRecommended route is %.0f min longer and reduces exposure by %.0f points.", extraMinutes, avoidedRisk));
        comparison.append("\n");
        for (int i = 0; i < ranked.size(); i++) {
            RouteSafetyResult result = ranked.get(i);
            if (i > 0) comparison.append("  •  ");
            comparison.append((char) ('A' + i)).append(String.format(Locale.US, ": %.0f min / %.0f risk",
                    result.getRoute().getTravelMinutes(), result.getRiskExposure()));
        }
        routeResultRisk.setText(comparison.toString());
        lastDestination = destination;
        lastRouteSummary = routeResultTitle.getText() + "\n" + routeResultMeta.getText() + "\n" + comparison;
        routeResultCard.setVisibility(View.VISIBLE);
        sourceInput.setText(shortName(source.getDisplayName()));
        destinationInput.setText(shortName(destination.getDisplayName()));
    }

    private void shareRoute() {
        if (lastRouteSummary.isEmpty()) { Toast.makeText(this, "Compare a route first.", Toast.LENGTH_SHORT).show(); return; }
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Safe-Navi route")
                .putExtra(Intent.EXTRA_TEXT, lastRouteSummary + "\nShared from Safe-Navi. Safety scores are guidance, not a guarantee."), "Share route"));
    }

    private void saveDestination() {
        if (lastDestination == null) { Toast.makeText(this, "Compare a route first.", Toast.LENGTH_SHORT).show(); return; }
        TextInputEditText label = new TextInputEditText(this); label.setHint("Label, for example College or Home"); label.setPadding(48, 8, 48, 8);
        new MaterialAlertDialogBuilder(this).setTitle("Save destination").setView(label).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d,w) -> {
                    String name = label.getText() == null ? "" : label.getText().toString().trim();
                    if (name.isEmpty()) name = shortName(lastDestination.getDisplayName());
                    new ProductApiClient(this).savePlace(name, lastDestination.getDisplayName(), lastDestination.getLatitude(), lastDestination.getLongitude(),
                            simpleApiCallback("Destination saved"));
                }).show();
    }

    private void showSavedPlaces() {
        new ProductApiClient(this).savedPlaces(new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { runOnUiThread(() -> {
                if (values.length() == 0) {
                    new MaterialAlertDialogBuilder(MapsActivity.this).setTitle("No saved places yet")
                            .setMessage("Compare a route, then use Save place to keep a destination here.")
                            .setPositiveButton("Got it", null).show();
                    return;
                }
                String[] labels = new String[values.length()]; for (int i = 0; i < values.length(); i++) {
                    JSONObject place = values.optJSONObject(i); labels[i] = place.optString("label") + " · " + place.optString("address");
                }
                new MaterialAlertDialogBuilder(MapsActivity.this).setTitle("Saved destinations").setItems(labels, (d, which) -> {
                    JSONObject place = values.optJSONObject(which); if (place == null) return;
                    new MaterialAlertDialogBuilder(MapsActivity.this).setTitle(place.optString("label"))
                            .setMessage(place.optString("address"))
                            .setPositiveButton("Use destination", (choice, button) -> {
                                destinationInput.setText(place.optString("address"));
                                lastDestination = new PlaceSearchResult(place.optString("address"), place.optDouble("latitude"), place.optDouble("longitude"));
                            })
                            .setNegativeButton("Delete", (choice, button) -> new ProductApiClient(MapsActivity.this)
                                    .deleteSavedPlace(place.optString("id"), simpleApiCallback("Saved place deleted")))
                            .setNeutralButton("Cancel", null).show();
                }).setNegativeButton("Close", null).show();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_LONG).show()); }
        });
    }

    private void restoreActiveJourney() {
        new ProductApiClient(this).journeys(new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { runOnUiThread(() -> {
                for (int i = 0; i < values.length(); i++) { JSONObject journey = values.optJSONObject(i);
                    if (journey != null && "ACTIVE".equals(journey.optString("status"))) {
                        activeJourneyId = journey.optString("id"); activeJourneyToken = journey.optString("share_token");
                        ProductSession.setActiveJourney(MapsActivity.this, activeJourneyId);
                        if (ProductSession.backgroundJourneyEnabled(MapsActivity.this)
                                && ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            JourneyTrackingService.start(MapsActivity.this);
                        }
                        journeyButton.setText("End journey"); refreshHandler.removeCallbacks(journeyRefresh); refreshHandler.post(journeyRefresh); break;
                    }
                }
            }); }
            @Override public void onError(String message) { }
        });
    }

    private void startSharedJourney() {
        if (lastDestination == null || lastRouteSummary.isEmpty()) { Toast.makeText(this, "Compare a route before sharing a journey.", Toast.LENGTH_SHORT).show(); return; }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.getLastLocation().addOnSuccessListener(location -> createSharedJourney(location == null ? null : location.getLatitude(), location == null ? null : location.getLongitude()));
        } else createSharedJourney(null, null);
    }

    private void createSharedJourney(Double latitude, Double longitude) {
        try {
            JSONObject payload = new JSONObject().put("source_label", textOf(sourceInput)).put("destination_label", lastDestination.getDisplayName())
                    .put("destination_latitude", lastDestination.getLatitude()).put("destination_longitude", lastDestination.getLongitude())
                    .put("travel_mode", selectedTravelMode().name()).put("route_profile", selectedRouteProfile().name())
                    .put("route_summary", lastRouteSummary);
            if (latitude != null) payload.put("latitude", latitude); if (longitude != null) payload.put("longitude", longitude);
            new ProductApiClient(this).startJourney(payload, new ProductApiClient.ObjectCallback() {
                @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> {
                    activeJourneyId = value.optString("id"); activeJourneyToken = value.optString("share_token");
                    ProductSession.setActiveJourney(MapsActivity.this, activeJourneyId);
                    journeyButton.setText("End journey"); refreshHandler.removeCallbacks(journeyRefresh); refreshHandler.post(journeyRefresh);
                    shareJourneyLink();
                    offerBackgroundJourneyTracking();
                }); }
                @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_LONG).show()); }
            });
        } catch (Exception ignored) { }
    }

    private void shareJourneyLink() {
        if (activeJourneyToken == null) return;
        String text = "Follow my Safe-Navi journey to " + (lastDestination == null ? "my destination" : shortName(lastDestination.getDisplayName()))
                + ": " + ProductApiClient.journeyShareUrl(activeJourneyToken);
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share live journey"));
    }

    private void offerBackgroundJourneyTracking() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        new MaterialAlertDialogBuilder(this).setTitle("Keep this journey live?")
                .setMessage("Safe-Navi can continue sharing your latest location after you leave the map. Android will show a persistent notification, and you can end the journey from that notification at any time.")
                .setNegativeButton("Only while map is open", null)
                .setPositiveButton("Enable background sharing", (dialog, which) -> JourneyTrackingService.start(this))
                .show();
    }

    private void updateActiveJourneyLocation() {
        if (activeJourneyId == null || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null || activeJourneyId == null) return;
            new ProductApiClient(this).updateJourneyLocation(activeJourneyId, location.getLatitude(), location.getLongitude(), silentCallback());
        });
    }

    private void endSharedJourney() {
        if (activeJourneyId == null) return;
        String journeyToEnd = activeJourneyId;
        new ProductApiClient(this).endJourney(journeyToEnd, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> {
                finishJourneyLocally("Journey ended and retained in your private history.");
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> {
                JourneyEndWorker.schedule(MapsActivity.this, journeyToEnd);
                finishJourneyLocally("Sharing stopped. Server closure will retry when connectivity returns.");
            }); }
        });
    }

    private void finishJourneyLocally(String message) {
        activeJourneyId = null; activeJourneyToken = null; journeyButton.setText("Share journey");
        ProductSession.setActiveJourney(this, null); SafetyCheckInWorker.cancel(this);
        JourneyTrackingService.stop(this); refreshHandler.removeCallbacks(journeyRefresh);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private ProductApiClient.ObjectCallback simpleApiCallback(String success) {
        return new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> Toast.makeText(MapsActivity.this, success, Toast.LENGTH_SHORT).show()); }
            @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_LONG).show()); }
        };
    }
    private ProductApiClient.ObjectCallback silentCallback() { return new ProductApiClient.ObjectCallback() {
        @Override public void onSuccess(JSONObject value) { }
        @Override public void onError(String message) { }
    }; }

    private void openNavigation() {
        if (lastDestination == null) { Toast.makeText(this, "Compare a route first.", Toast.LENGTH_SHORT).show(); return; }
        Uri uri = Uri.parse(String.format(Locale.US, "geo:0,0?q=%.6f,%.6f(%s)", lastDestination.getLatitude(),
                lastDestination.getLongitude(), Uri.encode(shortName(lastDestination.getDisplayName()))));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
        else Toast.makeText(this, "No navigation application is installed.", Toast.LENGTH_LONG).show();
    }

    private void showEmergencyTools() {
        String[] actions = {"Call emergency services (112)", "Share my current location", "Call a trusted contact"};
        new MaterialAlertDialogBuilder(this).setTitle("Emergency tools")
                .setMessage("If you are in immediate danger, move to a visible public place and contact emergency services.")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")));
                    else if (which == 1) shareCurrentLocation();
                    else openTrustedContacts();
                }).setNegativeButton("Cancel", null).show();
    }

    private void shareCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            enableLocationWithContext();
            return;
        }
        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                Toast.makeText(this, "Current location is not available yet.", Toast.LENGTH_SHORT).show();
                return;
            }
            String locationUrl = String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f",
                    location.getLatitude(), location.getLongitude());
            String message = "I may need assistance. This is my current location: " + locationUrl
                    + "\nShared from Safe-Navi. Please contact emergency services if I stop responding.";
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, message), "Share emergency location"));
        });
    }

    private void openTrustedContacts() {
        new ProductApiClient(this).emergencyContacts(new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { runOnUiThread(() -> {
                if (values.length() == 0) {
                    new MaterialAlertDialogBuilder(MapsActivity.this).setTitle("No trusted contacts")
                            .setMessage("Add emergency contacts from Profile before you need them.")
                            .setPositiveButton("Close", null).show();
                    return;
                }
                String[] labels = new String[values.length()];
                for (int i = 0; i < values.length(); i++) {
                    JSONObject item = values.optJSONObject(i);
                    labels[i] = item.optString("name") + "\n" + item.optString("relationship") + "  " + item.optString("phone");
                }
                new MaterialAlertDialogBuilder(MapsActivity.this).setTitle("Call a trusted contact")
                        .setItems(labels, (dialog, index) -> {
                            JSONObject item = values.optJSONObject(index);
                            if (item != null) startActivity(new Intent(Intent.ACTION_DIAL,
                                    Uri.parse("tel:" + Uri.encode(item.optString("phone")))));
                        }).setNegativeButton("Cancel", null).show();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_LONG).show()); }
        });
    }

    private void renderRoutes(List<RouteCandidate> candidates, RouteCandidate selected) {
        if (mapStyle == null) return;
        removeLayerAndSource(SELECTED_ROUTE_LAYER, SELECTED_ROUTE_SOURCE);
        removeLayerAndSource(ROUTES_LAYER, ROUTES_SOURCE);
        List<Feature> alternatives = new ArrayList<>();
        for (RouteCandidate candidate : candidates) {
            alternatives.add(Feature.fromGeometry(lineString(candidate)));
        }
        mapStyle.addSource(new GeoJsonSource(ROUTES_SOURCE, FeatureCollection.fromFeatures(alternatives)));
        mapStyle.addLayer(new LineLayer(ROUTES_LAYER, ROUTES_SOURCE).withProperties(
                lineColor(Color.parseColor("#718078")), lineWidth(5f), lineOpacity(0.7f)));
        mapStyle.addSource(new GeoJsonSource(SELECTED_ROUTE_SOURCE,
                Feature.fromGeometry(lineString(selected))));
        mapStyle.addLayer(new LineLayer(SELECTED_ROUTE_LAYER, SELECTED_ROUTE_SOURCE).withProperties(
                lineColor(Color.parseColor("#08735C")), lineWidth(8f), lineOpacity(0.95f)));

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (HazardLocation.GeoPoint point : selected.getGeometry()) {
            bounds.include(new LatLng(point.getLatitude(), point.getLongitude()));
        }
        try {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 90));
        } catch (Exception ignored) {
            // Provider geometry may contain too little information for bounds; route still renders.
        }
    }

    private LineString lineString(RouteCandidate route) {
        List<Point> points = new ArrayList<>();
        for (HazardLocation.GeoPoint point : route.getGeometry()) {
            points.add(Point.fromLngLat(point.getLongitude(), point.getLatitude()));
        }
        return LineString.fromLngLats(points);
    }

    private void removeLayerAndSource(String layerId, String sourceId) {
        if (mapStyle.getLayer(layerId) != null) mapStyle.removeLayer(layerId);
        if (mapStyle.getSource(sourceId) != null) mapStyle.removeSource(sourceId);
    }

    private interface PlaceCallback { void accept(PlaceSearchResult place); }

    private void resolvePlace(String text, int requestId, PlaceCallback callback) {
        PlaceSearchResult coordinates = parseCoordinates(text);
        if (coordinates != null) {
            callback.accept(coordinates);
            return;
        }
        openMapService.search(text, new OpenMapService.ResultCallback<List<PlaceSearchResult>>() {
            @Override public void onSuccess(List<PlaceSearchResult> results) {
                runOnUiThread(() -> {
                    if (!isRouteRequestActive(requestId)) return;
                    if (results.isEmpty()) showRouteError(requestId,
                            "No matching place was found for: " + text);
                    else callback.accept(results.get(0));
                });
            }

            @Override public void onError(String message) {
                offlineRouter.search(text, new OfflineRouter.Callback<List<PlaceSearchResult>>() {
                    @Override public void onSuccess(List<PlaceSearchResult> results) { runOnUiThread(() -> {
                        if (isRouteRequestActive(requestId)) callback.accept(results.get(0));
                    }); }
                    @Override public void onError(String offlineMessage) { runOnUiThread(() -> {
                        if (isRouteRequestActive(requestId)) showRouteError(requestId, message + "\n\n" + offlineMessage);
                    }); }
                });
            }
        });
    }

    private PlaceSearchResult parseCoordinates(String value) {
        String[] parts = value.trim().split(",");
        if (parts.length != 2) return null;
        try {
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;
            return new PlaceSearchResult(value, latitude, longitude);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private TravelMode selectedTravelMode() {
        return travelMode;
    }

    private RouteProfile selectedRouteProfile() {
        return routeProfile;
    }

    private void renderScore(double latitude, double longitude) {
        int requestId = ++activeScoreRequest;
        scoreText.setText("...");
        scoreLabel.setText("Loading live safety intelligence…");
        datasetRiskService.pointRisk(latitude, longitude, currentTimePeriod(),
                new DatasetRiskService.ResultCallback<DatasetPointRisk>() {
                    @Override public void onSuccess(DatasetPointRisk risk) {
                        runOnUiThread(() -> {
                            if (requestId != activeScoreRequest || isFinishing() || isDestroyed()) return;
                            scoreText.setText(String.format(Locale.US, "%.0f", risk.getSafetyScore()));
                            scoreLabel.setText(readable(risk.getRiskLabel()) + " risk · "
                                    + risk.getAreaName() + " baseline");
                        });
                    }

                    @Override public void onError(String message) {
                        runOnUiThread(() -> {
                            if (requestId == activeScoreRequest && !isFinishing() && !isDestroyed()) {
                                scoreLabel.setText("Safety service unavailable");
                            }
                        });
                    }
                });
    }

    private String currentTimePeriod() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 7 && hour < 11) return "morning_peak";
        if (hour >= 11 && hour < 17) return "midday";
        if (hour >= 17 && hour < 22) return "evening_peak";
        return "night";
    }

    private void enableLocationWithContext() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            useLastLocation();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Use your location")
                .setMessage("Safe-Navi uses your recent location to fill the source and calculate nearby risk. Your location is processed only for the route request.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (dialog, which) -> ActivityCompat.requestPermissions(
                        this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST))
                .show();
    }

    private void useLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;
        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                Toast.makeText(this, "No recent device location is available.", Toast.LENGTH_SHORT).show();
                return;
            }
            String coordinates = String.format(Locale.US, "%.6f, %.6f",
                    location.getLatitude(), location.getLongitude());
            sourceInput.setText(coordinates);
            renderScore(location.getLatitude(), location.getLongitude());
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()), 14.0));
        });
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                                      @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) useLastLocation();
    }

    private void setRouteBusy(boolean busy) {
        findRouteButton.setEnabled(!busy);
        findRouteButton.setText(busy ? "Comparing..." : getString(R.string.compare_short));
        routeProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) routeResultCard.setVisibility(View.GONE);
    }

    private void showRouteError(int requestId, String message) {
        if (!isRouteRequestActive(requestId)) return;
        setRouteBusy(false);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Route unavailable")
                .setMessage(message + "\n\nCheck your connection and try again. Safe-Navi will never invent a route when routing intelligence is unavailable.")
                .setPositiveButton("OK", null)
                .show();
    }

    private boolean isRouteRequestActive(int requestId) {
        return requestId == activeRouteRequest && !isFinishing() && !isDestroyed();
    }

    private int severityColor(HazardSeverity severity) {
        if (severity == HazardSeverity.CRITICAL) return Color.parseColor("#D32F2F");
        if (severity == HazardSeverity.HIGH) return Color.parseColor("#EF6C00");
        if (severity == HazardSeverity.MODERATE) return Color.parseColor("#F9A825");
        return Color.parseColor("#2E7D32");
    }

    private String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String shortName(String value) {
        int comma = value.indexOf(',');
        return comma > 0 ? value.substring(0, comma) : value;
    }

    private String profileName(RouteProfile profile) {
        return readable(profile.name());
    }

    private String readable(String value) {
        String lower = value.toLowerCase(Locale.US).replace('_', ' ');
        if (lower.isEmpty()) return "Unknown";
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (mapStyle != null) loadHazards();
        if (mapStyle != null) loadPendingReports();
        refreshHandler.removeCallbacks(hazardRefresh);
        refreshHandler.postDelayed(hazardRefresh, 30_000L);
        if (activeJourneyId == null) restoreActiveJourney();
        else { refreshHandler.removeCallbacks(journeyRefresh); refreshHandler.post(journeyRefresh); }
        connectLiveUpdates();
    }
    @Override protected void onPause() {
        refreshHandler.removeCallbacks(hazardRefresh);
        refreshHandler.removeCallbacks(journeyRefresh);
        if (liveUpdateSocket != null) { liveUpdateSocket.close(1000, "Map paused"); liveUpdateSocket = null; }
        mapView.onPause();
        super.onPause();
    }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onDestroy() {
        activeRouteRequest++;
        activeScoreRequest++;
        mapView.onDestroy();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    private void connectLiveUpdates() {
        if (liveUpdateSocket != null) return;
        liveUpdateSocket = new ProductApiClient(this).openLiveUpdates(new WebSocketListener() {
            @Override public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                if (text.contains("hazards_changed")) runOnUiThread(() -> {
                    loadHazards();
                    loadPendingReports();
                });
            }

            @Override public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable error, Response response) {
                if (liveUpdateSocket == webSocket) liveUpdateSocket = null;
            }

            @Override public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                if (liveUpdateSocket == webSocket) liveUpdateSocket = null;
            }
        });
    }
}
