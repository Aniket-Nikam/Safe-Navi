package com.example.xavierproject;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.example.xavierproject.safety.demo.SafetyDemoStore;
import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.HazardSeverity;
import com.example.xavierproject.safety.model.SafetyScore;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Locale;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener, GoogleMap.OnMapClickListener {
    private static final int LOCATION_REQUEST = 301;
    private GoogleMap map;
    private View detailCard;
    private TextView scoreText;
    private TextView scoreLabel;
    private TextView detailTitle;
    private TextView detailMeta;
    private TextView detailReason;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        detailCard = findViewById(R.id.hazardDetailCard);
        scoreText = findViewById(R.id.mapSafetyScore);
        scoreLabel = findViewById(R.id.mapSafetyLabel);
        detailTitle = findViewById(R.id.hazardTitle);
        detailMeta = findViewById(R.id.hazardMeta);
        detailReason = findViewById(R.id.hazardReason);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.locationButton).setOnClickListener(v -> enableLocationWithContext());
        findViewById(R.id.routeProfilesButton).setOnClickListener(v -> showRoutingExplanation());
        findViewById(R.id.closeDetailButton).setOnClickListener(v -> detailCard.setVisibility(View.GONE));

        SupportMapFragment fragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (fragment != null) fragment.getMapAsync(this);
        renderScore(19.0657, 72.9986);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        map = googleMap;
        map.setOnMarkerClickListener(this);
        map.setOnMapClickListener(this);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setZoomControlsEnabled(false);
        LatLng mumbai = new LatLng(19.0760, 72.9777);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(mumbai, 10.8f));
        renderHazards();
    }

    private void renderHazards() {
        map.clear();
        for (Hazard hazard : SafetyDemoStore.repository().getHazards()) {
            if (hazard.getLocation() == null || !hazard.getStatus().affectsImmediateNavigation()) continue;
            LatLng point = new LatLng(hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude());
            Marker marker = map.addMarker(new MarkerOptions()
                    .position(point)
                    .title(hazard.getTitle())
                    .snippet(hazard.getSeverity().name() + " · " + hazard.getStatus().name())
                    .icon(BitmapDescriptorFactory.defaultMarker(markerHue(hazard.getSeverity()))));
            if (marker != null) marker.setTag(hazard);
        }
    }

    private float markerHue(HazardSeverity severity) {
        if (severity == HazardSeverity.CRITICAL) return BitmapDescriptorFactory.HUE_RED;
        if (severity == HazardSeverity.HIGH) return BitmapDescriptorFactory.HUE_ORANGE;
        if (severity == HazardSeverity.MODERATE) return BitmapDescriptorFactory.HUE_YELLOW;
        return BitmapDescriptorFactory.HUE_GREEN;
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        Object tag = marker.getTag();
        if (!(tag instanceof Hazard)) return false;
        Hazard hazard = (Hazard) tag;
        detailTitle.setText(hazard.getTitle());
        String verified = hazard.isGovernmentVerified() ? "Government verified" : "Citizen report";
        String synthetic = hazard.isSynthetic() ? " · Synthetic demo" : "";
        detailMeta.setText(hazard.getSeverity().name() + " · " + hazard.getStatus().name()
                + " · " + verified + synthetic);
        detailReason.setText(hazard.getReason() + "\n" + hazard.getLocation().getDisplayName());
        detailCard.setVisibility(View.VISIBLE);
        renderScore(hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude());
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 14.5f));
        return true;
    }

    @Override
    public void onMapClick(@NonNull LatLng point) {
        detailCard.setVisibility(View.GONE);
        renderScore(point.latitude, point.longitude);
    }

    private void renderScore(double latitude, double longitude) {
        SafetyScore score = SafetyDemoStore.riskEngine().calculate(latitude, longitude,
                System.currentTimeMillis(), SafetyDemoStore.repository().getHazards());
        scoreText.setText(String.format(Locale.US, "%.0f", score.getSafetyScore()));
        scoreLabel.setText(readable(score.getRiskLevel().name()) + " · "
                + score.getActiveHazardCount() + " active nearby");
    }

    private void enableLocationWithContext() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            if (map != null) map.setMyLocationEnabled(true);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Use your location")
                .setMessage("Safe-Navi uses your location while this map is open to show nearby hazards and calculate a local safety summary. The demo still works without it.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue", (dialog, which) -> ActivityCompat.requestPermissions(
                        this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST))
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && map != null) {
            try {
                map.setMyLocationEnabled(true);
            } catch (SecurityException ignored) {
                Toast.makeText(this, "Location permission could not be applied.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showRoutingExplanation() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Safety-aware route profiles")
                .setSingleChoiceItems(new String[]{
                        "Fastest — travel time first",
                        "Balanced — time + hazard exposure",
                        "Safest — safety within a reasonable detour"
                }, 1, null)
                .setMessage("Safe-Navi scores legitimate candidate routes returned by a configured Directions provider. It never invents road geometry. Add a routing provider in local configuration to enable navigation.")
                .setPositiveButton("Understood", null)
                .show();
    }

    private String readable(String value) {
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
