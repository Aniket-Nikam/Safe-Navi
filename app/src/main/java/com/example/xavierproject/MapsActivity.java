package com.example.xavierproject;

import static org.maplibre.android.style.layers.PropertyFactory.circleBlur;
import static org.maplibre.android.style.layers.PropertyFactory.circleColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.circleRadius;
import static org.maplibre.android.style.layers.PropertyFactory.lineColor;
import static org.maplibre.android.style.layers.PropertyFactory.lineOpacity;
import static org.maplibre.android.style.layers.PropertyFactory.lineWidth;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.example.xavierproject.safety.demo.SafetyDemoStore;
import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.HazardLocation;
import com.example.xavierproject.safety.model.HazardSeverity;
import com.example.xavierproject.safety.model.PlaceSearchResult;
import com.example.xavierproject.safety.model.RouteCandidate;
import com.example.xavierproject.safety.model.RouteProfile;
import com.example.xavierproject.safety.model.RouteSafetyResult;
import com.example.xavierproject.safety.model.SafetyScore;
import com.example.xavierproject.safety.model.TravelMode;
import com.example.xavierproject.safety.network.OpenMapService;
import com.example.xavierproject.safety.service.RouteSafetyEvaluator;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
            "hazard-low-point", "hazard-moderate-point", "hazard-high-point", "hazard-critical-point");

    private MapView mapView;
    private MapLibreMap map;
    private Style mapStyle;
    private OpenMapService openMapService;
    private FusedLocationProviderClient locationClient;
    private TextInputEditText sourceInput;
    private TextInputEditText destinationInput;
    private ProgressBar routeProgress;
    private View findRouteButton;
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

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapLibre.getInstance(this);
        setContentView(R.layout.activity_maps);
        openMapService = new OpenMapService();
        locationClient = LocationServices.getFusedLocationProviderClient(this);
        bindViews();
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(mapLibreMap -> {
            map = mapLibreMap;
            map.getUiSettings().setCompassEnabled(true);
            map.setStyle(new Style.Builder().fromUri(MAP_STYLE), style -> {
                mapStyle = style;
                renderHazards();
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(19.0760, 72.9777), 10.7));
                map.addOnMapClickListener(this::onMapClick);
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
        findRouteButton.setOnClickListener(v -> findRoutes());
        findViewById(R.id.closeDetailButton).setOnClickListener(v -> detailCard.setVisibility(View.GONE));
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
        return false;
    }

    private void renderHazards() {
        if (mapStyle == null) return;
        for (HazardSeverity severity : HazardSeverity.values()) {
            List<Feature> features = new ArrayList<>();
            for (Hazard hazard : SafetyDemoStore.repository().getHazards()) {
                if (hazard.getSeverity() != severity || hazard.getLocation() == null
                        || !hazard.getStatus().affectsImmediateNavigation()) continue;
                Feature feature = Feature.fromGeometry(Point.fromLngLat(
                        hazard.getLocation().getLongitude(), hazard.getLocation().getLatitude()));
                feature.addStringProperty("hazardId", hazard.getId());
                features.add(feature);
            }
            String key = severity.name().toLowerCase(Locale.US);
            String sourceId = "hazard-" + key + "-source";
            String heatId = "hazard-" + key + "-heat";
            String pointId = "hazard-" + key + "-point";
            mapStyle.addSource(new GeoJsonSource(sourceId, FeatureCollection.fromFeatures(features)));
            int color = severityColor(severity);
            mapStyle.addLayer(new CircleLayer(heatId, sourceId).withProperties(
                    circleRadius(40f), circleColor(color), circleOpacity(0.16f), circleBlur(0.65f)));
            mapStyle.addLayer(new CircleLayer(pointId, sourceId).withProperties(
                    circleRadius(8f), circleColor(color), circleOpacity(0.95f)));
        }
    }

    private void showHazard(String hazardId) {
        Hazard hazard = SafetyDemoStore.repository().findHazard(hazardId);
        if (hazard == null) return;
        detailTitle.setText(hazard.getTitle());
        String verified = hazard.isGovernmentVerified() ? "Government verified" : "Citizen report";
        String synthetic = hazard.isSynthetic() ? " · Synthetic demo" : "";
        detailMeta.setText(hazard.getSeverity().name() + " · " + hazard.getStatus().name()
                + " · " + verified + synthetic);
        detailReason.setText(hazard.getReason() + "\n" + hazard.getLocation().getDisplayName());
        detailCard.setVisibility(View.VISIBLE);
        renderScore(hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude());
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
                hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude()), 14.2));
    }

    private void findRoutes() {
        String sourceText = textOf(sourceInput);
        String destinationText = textOf(destinationInput);
        if (sourceText.isEmpty() || destinationText.isEmpty()) {
            Toast.makeText(this, "Enter both source and destination.", Toast.LENGTH_SHORT).show();
            return;
        }
        setRouteBusy(true);
        resolvePlace(sourceText, source -> resolvePlace(destinationText, destination ->
                requestRoutes(source, destination)));
    }

    private void requestRoutes(PlaceSearchResult source, PlaceSearchResult destination) {
        openMapService.routes(source.getLatitude(), source.getLongitude(), destination.getLatitude(),
                destination.getLongitude(), selectedTravelMode(),
                new OpenMapService.ResultCallback<List<RouteCandidate>>() {
                    @Override public void onSuccess(List<RouteCandidate> candidates) {
                        runOnUiThread(() -> showRankedRoutes(candidates, source, destination));
                    }

                    @Override public void onError(String message) {
                        runOnUiThread(() -> showRouteError(message));
                    }
                });
    }

    private void showRankedRoutes(List<RouteCandidate> candidates, PlaceSearchResult source,
                                  PlaceSearchResult destination) {
        setRouteBusy(false);
        if (candidates.isEmpty()) {
            showRouteError("No route was returned for this travel mode.");
            return;
        }
        RouteProfile profile = selectedRouteProfile();
        List<RouteSafetyResult> ranked = new RouteSafetyEvaluator(SafetyDemoStore.riskEngine())
                .rank(candidates, SafetyDemoStore.repository().getHazards(), profile,
                        System.currentTimeMillis());
        RouteSafetyResult best = ranked.get(0);
        renderRoutes(candidates, best.getRoute());
        routeResultTitle.setText(profileName(profile) + " recommendation · "
                + selectedTravelMode().getLabel());
        routeResultMeta.setText(String.format(Locale.US, "%.1f km · %.0f min · %d alternative%s",
                best.getRoute().getDistanceMeters() / 1000.0, best.getRoute().getTravelMinutes(),
                candidates.size(), candidates.size() == 1 ? "" : "s"));
        routeResultRisk.setText(String.format(Locale.US,
                "Average verified-hazard exposure: %.0f/100 · Route data: OpenStreetMap/Valhalla demo",
                best.getRiskExposure()));
        routeResultCard.setVisibility(View.VISIBLE);
        sourceInput.setText(shortName(source.getDisplayName()));
        destinationInput.setText(shortName(destination.getDisplayName()));
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

    private void resolvePlace(String text, PlaceCallback callback) {
        PlaceSearchResult coordinates = parseCoordinates(text);
        if (coordinates != null) {
            callback.accept(coordinates);
            return;
        }
        openMapService.search(text, new OpenMapService.ResultCallback<List<PlaceSearchResult>>() {
            @Override public void onSuccess(List<PlaceSearchResult> results) {
                runOnUiThread(() -> {
                    if (results.isEmpty()) showRouteError("No matching place was found for: " + text);
                    else callback.accept(results.get(0));
                });
            }

            @Override public void onError(String message) {
                runOnUiThread(() -> showRouteError(message));
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
        int checked = ((com.google.android.material.chip.ChipGroup) findViewById(R.id.travelModeGroup))
                .getCheckedChipId();
        if (checked == R.id.modeWalk) return TravelMode.WALKING;
        if (checked == R.id.modeCycle) return TravelMode.CYCLING;
        return TravelMode.DRIVING;
    }

    private RouteProfile selectedRouteProfile() {
        int checked = ((com.google.android.material.chip.ChipGroup) findViewById(R.id.routeProfileGroup))
                .getCheckedChipId();
        if (checked == R.id.profileFastest) return RouteProfile.FASTEST;
        if (checked == R.id.profileSafest) return RouteProfile.SAFEST;
        return RouteProfile.BALANCED;
    }

    private void renderScore(double latitude, double longitude) {
        SafetyScore score = SafetyDemoStore.riskEngine().calculate(latitude, longitude,
                System.currentTimeMillis(), SafetyDemoStore.repository().getHazards());
        scoreText.setText(String.format(Locale.US, "%.0f", score.getSafetyScore()));
        scoreLabel.setText(readable(score.getRiskLevel().name()) + " · "
                + score.getActiveHazardCount() + " nearby");
    }

    private void enableLocationWithContext() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            useLastLocation();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Use your location")
                .setMessage("Safe-Navi uses a recent device location only to fill the source and calculate nearby synthetic risk. It is not uploaded by this demo.")
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
        routeProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy) routeResultCard.setVisibility(View.GONE);
    }

    private void showRouteError(String message) {
        setRouteBusy(false);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Route unavailable")
                .setMessage(message + "\n\nThe bundled public endpoints are for light demonstration use. A production routing/geocoding provider can be configured later without changing the safety engine.")
                .setPositiveButton("OK", null)
                .show();
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
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override protected void onStart() { super.onStart(); mapView.onStart(); }
    @Override protected void onResume() { super.onResume(); mapView.onResume(); }
    @Override protected void onPause() { mapView.onPause(); super.onPause(); }
    @Override protected void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override protected void onDestroy() { mapView.onDestroy(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
