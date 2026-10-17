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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.safenavi.app.product.ProductApiClient;
import com.safenavi.app.product.ProductSession;
import com.safenavi.app.product.PlaceSearchSheet;
import com.safenavi.app.product.ServerConnection;
import com.safenavi.app.product.ServerConnectionDialog;
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
import com.safenavi.app.safety.model.RouteRiskPoint;
import com.safenavi.app.safety.model.RouteRiskSection;
import com.safenavi.app.safety.model.TravelMode;
import com.safenavi.app.safety.network.OpenMapService;
import com.safenavi.app.safety.network.DatasetRiskService;
import com.safenavi.app.safety.network.OfflineRouter;
import com.safenavi.app.safety.network.OfflineHazardCache;
import com.safenavi.app.safety.service.RouteRecommendations;
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
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

public class MapsActivity extends AppCompatActivity {
    private String pendingEmergencyPhone;
    private final ActivityResultLauncher<String> callPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                String phone = pendingEmergencyPhone;
                pendingEmergencyPhone = null;
                if (phone != null) placeContactCall(phone, granted);
            });
    private static final int LOCATION_REQUEST = 301;
    private static final String MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private static final String ROUTES_SOURCE = "safe-navi-routes";
    private static final String ROUTES_LAYER = "safe-navi-route-options";
    private static final String SELECTED_ROUTE_SOURCE = "safe-navi-selected-route";
    private static final String SELECTED_ROUTE_LAYER = "safe-navi-selected-route-line";
    private static final String RISK_POINTS_SOURCE = "selected-route-risk-points";
    private static final String RISK_POINTS_LAYER = "selected-route-risk-markers";
    private static final String RISK_SECTIONS_LAYER = "selected-route-exposure";
    private static final String RISK_SECTIONS_SOURCE = "selected-route-exposure-source";
    private static final List<String> HAZARD_LAYER_IDS = Arrays.asList(
            "hazard-low-point", "hazard-moderate-point", "hazard-high-point", "hazard-critical-point",
            "hazard-geometry-line", "hazard-geometry-fill", "pending-report-point", RISK_POINTS_LAYER, RISK_SECTIONS_LAYER);
    private boolean risksVisible;
    private RouteSafetyResult displayedRoute;
    private com.google.android.material.bottomsheet.BottomSheetDialog activeMapSheet;

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
    private PlaceSearchResult selectedSourcePlace, selectedDestinationPlace;
    private String selectedSourceText, selectedDestinationText;
    private int routePinTarget;
    private String lastRouteSummary = "";
    private String currentHazardId;
    private final Map<String, RouteRiskPoint> selectedRiskPoints = new LinkedHashMap<>();
    private boolean locatingSource = true;
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
        if (savedInstanceState != null) pendingEmergencyPhone = savedInstanceState.getString("pending_emergency_phone");
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
            map.getUiSettings().setCompassEnabled(false);
            map.setStyle(new Style.Builder().fromUri(MAP_STYLE), style -> {
                mapStyle = style;
                loadHazards();
                loadPendingReports();
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(19.0760, 72.9777), 10.7));
                map.addOnMapClickListener(this::onMapClick);
                map.addOnMapLongClickListener(this::onMapLongClick);
                map.addOnCameraIdleListener(() -> {
                    LatLng center = map.getCameraPosition().target;
                    if (center != null) renderScore(center.getLatitude(), center.getLongitude());
                });
            });
        });
        renderScore(19.0657, 72.9986);
    }

    private void bindViews() {
        mapView = findViewById(R.id.mapView);
        sourceInput = findViewById(R.id.sourceInput);
        destinationInput = findViewById(R.id.destinationInput);
        sourceInput.setOnClickListener(v -> openPlaceSearch(true));
        destinationInput.setOnClickListener(v -> openPlaceSearch(false));
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
            PlaceSearchResult oldSource = selectedSourcePlace;
            selectedSourcePlace = selectedDestinationPlace; selectedDestinationPlace = oldSource;
            selectedSourceText = textOf(sourceInput); selectedDestinationText = textOf(destinationInput);
            invalidateRouteForEndpoints();
            if (!textOf(sourceInput).isEmpty() && !textOf(destinationInput).isEmpty()) findRoutes();
        });
        if (getIntent().hasExtra("destinationLatitude") && getIntent().hasExtra("destinationLongitude")) {
            destinationInput.setText(String.format(Locale.US, "%.6f, %.6f",
                    getIntent().getDoubleExtra("destinationLatitude", 0),
                    getIntent().getDoubleExtra("destinationLongitude", 0)));
        }
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.locationButton).setOnClickListener(v -> { locatingSource = true; enableLocationWithContext(); });
        findViewById(R.id.mapConnectionButton).setOnClickListener(v -> showConnectionOptions());
        findViewById(R.id.mapToolsButton).setOnClickListener(v -> showMapTools());
        travelModeButton.setOnClickListener(v -> showTravelModePicker());
        routeProfileButton.setOnClickListener(v -> showRouteProfilePicker());
        findViewById(R.id.emergencyButton).setOnClickListener(v -> showEmergencyTools());
        findViewById(R.id.areaCrimeButton).setOnClickListener(v -> showAreaCrime());
        findRouteButton.setOnClickListener(v -> { if(displayedRoute==null) findRoutes(); else showRouteComparisonSheet(); });
        findViewById(R.id.mapSearchSummary).setOnClickListener(v -> openPlaceSearch(false));
        findViewById(R.id.editEndpointsButton).setOnClickListener(v -> {
            View editor=findViewById(R.id.endpointEditor);
            editor.setVisibility(editor.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });
        findViewById(R.id.riskLayerButton).setOnClickListener(v -> {
            if(displayedRoute==null) { Toast.makeText(this,"Plan a route first to inspect its coverage.",Toast.LENGTH_SHORT).show(); return; }
            risksVisible=!risksVisible; applyRiskVisibility();
            if(risksVisible) showRiskLegend();
        });
        destinationInput.setOnEditorActionListener((view, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { findRoutes(); return true; }
            return false;
        });
        findViewById(R.id.routeDetailsToggle).setOnClickListener(v -> showRouteDetailsSheet());
        findViewById(R.id.shareRouteButton).setOnClickListener(v -> shareRoute());
        findViewById(R.id.openNavigationButton).setOnClickListener(v -> openNavigation());
        findViewById(R.id.saveDestinationButton).setOnClickListener(v -> saveDestination());
        journeyButton.setOnClickListener(v -> { if (activeJourneyId == null) startSharedJourney(); else endSharedJourney(); });
        findViewById(R.id.closeDetailButton).setOnClickListener(v -> detailCard.setVisibility(View.GONE));
        detailCard.setOnLongClickListener(v -> { if (isGovernmentUser() && currentHazardId != null) { showHazardLifecycleDialog(); return true; } return false; });
        detailReason.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle(detailTitle.getText())
                .setMessage(detailReason.getText()).setPositiveButton("Close", null).show());
        RouteProfile[] initialProfiles = {RouteProfile.FASTEST, RouteProfile.BALANCED, RouteProfile.SAFEST};
        int[] initialIds = {R.id.fastestRecommendation, R.id.balancedRecommendation, R.id.safestRecommendation};
        for (int i = 0; i < initialIds.length; i++) {
            RouteProfile preference = initialProfiles[i]; TextView option = findViewById(initialIds[i]);
            styleRouteOption(option, preference, preference == routeProfile);
            option.setOnClickListener(v -> {
                routeProfile = preference; routeProfileButton.setText(profileName(preference));
                for (int j = 0; j < initialIds.length; j++) styleRouteOption(findViewById(initialIds[j]), initialProfiles[j], initialProfiles[j] == preference);
            });
        }
    }

    private void showConnectionOptions() {
        if (ServerConnection.baseUrl().startsWith("http://")) {
            ServerConnectionDialog.show(this, ServerConnection.CLOUD_ORIGIN); return;
        }
        new MaterialAlertDialogBuilder(this).setTitle("Safety connection")
                .setMessage("Server: " + ServerConnection.baseUrl() + "\n\nA free cloud server can take about a minute to wake. Offline maps do not connect the live safety service.")
                .setPositiveButton("Retry", (d,w) -> {
                    loadHazards(); LatLng center = map == null ? null : map.getCameraPosition().target;
                    if (center != null) renderScore(center.getLatitude(), center.getLongitude());
                }).setNeutralButton("Server settings", (d,w) -> ServerConnectionDialog.show(this))
                .setNegativeButton("Close", null).show();
    }

    private void openPlaceSearch(boolean source) {
        LatLng center = map == null ? null : map.getCameraPosition().target;
        new PlaceSearchSheet(this, openMapService, offlineRouter, source ? "Choose starting point" : "Search destination",
                textOf(source ? sourceInput : destinationInput), center == null ? 19.043 : center.getLatitude(),
                center == null ? 73.023 : center.getLongitude(), new PlaceSearchSheet.Listener() {
            @Override public void onPlace(PlaceSearchResult place) {
                setSelectedPlace(source, place); invalidateRouteForEndpoints();
                findViewById(R.id.endpointEditor).setVisibility(View.GONE);
                if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(place.getLatitude(), place.getLongitude()), 14.5));
                if (!textOf(sourceInput).isEmpty() && !textOf(destinationInput).isEmpty()) findRoutes();
                else if(!source && textOf(sourceInput).isEmpty()) { locatingSource=true; enableLocationWithContext(); }
            }
            @Override public void onMapPin() {
                routePinTarget = source ? 1 : 2;
                ((TextView)findViewById(R.id.routeStatusText)).setText("Tap the exact " + (source ? "starting point" : "destination") + " on the map");
            }
            @Override public void onDeviceLocation() { locatingSource = source; enableLocationWithContext(); }
        }).show();
    }

    private void setSelectedPlace(boolean source, PlaceSearchResult place) {
        TextInputEditText input = source ? sourceInput : destinationInput;
        input.setText(place.getPrimaryText()); input.setContentDescription((source ? "Start: " : "Destination: ") + place.getDisplayName());
        if (source) { selectedSourcePlace = place; selectedSourceText = textOf(input); }
        else { selectedDestinationPlace = place; selectedDestinationText = textOf(input); }
        ((TextView)findViewById(R.id.mapSearchSummary)).setText(selectedDestinationPlace==null?"Where to?":selectedDestinationPlace.getPrimaryText());
        if (mapStyle != null) renderEndpoint(source ? "start" : "finish", source ? "A" : "B", place, source ? "#08735C" : "#BF3F38");
    }

    private void invalidateRouteForEndpoints() {
        activeRouteRequest++; setRouteBusy(false); routeResultCard.setVisibility(View.GONE);
        detailCard.setVisibility(View.GONE); selectedRiskPoints.clear(); lastRouteSummary = ""; lastDestination = null;
        displayedRoute=null;
        ((TextView)findViewById(R.id.mapSearchSummary)).setText(textOf(destinationInput).isEmpty()?"Where to?":textOf(destinationInput));
        findRouteButton.setText("Plan route");
        ((TextView)findViewById(R.id.routeStatusText)).setText("Ready to compare all three preferences");
        int[] ids = {R.id.fastestRecommendation, R.id.balancedRecommendation, R.id.safestRecommendation};
        RouteProfile[] profiles = {RouteProfile.FASTEST, RouteProfile.BALANCED, RouteProfile.SAFEST};
        for (int i = 0; i < ids.length; i++) {
            TextView option = findViewById(ids[i]); option.setText(profileName(profiles[i]) + "\n—\nRisk —");
            styleRouteOption(option, profiles[i], profiles[i] == routeProfile);
            RouteProfile preference = profiles[i];
            option.setOnClickListener(v -> { routeProfile = preference; invalidateRouteForEndpoints(); });
        }
        if (mapStyle != null) {
            removeLayerAndSource(SELECTED_ROUTE_LAYER, SELECTED_ROUTE_SOURCE); removeLayerAndSource(ROUTES_LAYER, ROUTES_SOURCE);
            removeLayerAndSource(RISK_POINTS_LAYER, RISK_POINTS_SOURCE);
            removeLayerAndSource(RISK_SECTIONS_LAYER, RISK_SECTIONS_SOURCE);
        }
    }

    private int routeColor(RouteProfile profile) {
        return Color.parseColor(profile == RouteProfile.FASTEST ? "#286BC3" : profile == RouteProfile.SAFEST ? "#C76B12" : "#08735C");
    }

    private void styleRouteOption(TextView option, RouteProfile profile, boolean selected) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        int color = routeColor(profile);
        background.setColor(selected ? Color.argb(35, Color.red(color), Color.green(color), Color.blue(color))
                : androidx.core.content.ContextCompat.getColor(this, R.color.surface));
        background.setStroke(Math.round(getResources().getDisplayMetrics().density * (selected ? 2 : 1)),
                selected ? color : androidx.core.content.ContextCompat.getColor(this, R.color.divider));
        option.setBackground(background); option.setSelected(selected);
        option.setTypeface(android.graphics.Typeface.DEFAULT, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private android.widget.LinearLayout sheetContent() {
        android.widget.LinearLayout content=new android.widget.LinearLayout(this); content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad=Math.round(20*getResources().getDisplayMetrics().density); content.setPadding(pad,pad,pad,pad);
        content.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this,R.color.surface)); return content;
    }
    private TextView sheetText(String text,int size) {
        TextView label=new TextView(this); label.setText(text); label.setTextSize(size);
        label.setTextColor(androidx.core.content.ContextCompat.getColor(this,R.color.text_primary));
        int pad=Math.round(8*getResources().getDisplayMetrics().density); label.setPadding(0,pad,0,pad); return label;
    }
    private MaterialButton sheetButton(String text) {
        MaterialButton button=new MaterialButton(this,null,com.google.android.material.R.attr.borderlessButtonStyle);
        button.setText(text); button.setAllCaps(false); button.setLetterSpacing(0); button.setTextSize(14); button.setMinHeight(Math.round(48*getResources().getDisplayMetrics().density)); return button;
    }
    private com.google.android.material.bottomsheet.BottomSheetDialog presentSheet(android.widget.LinearLayout content) {
        if(activeMapSheet!=null) activeMapSheet.dismiss();
        com.google.android.material.bottomsheet.BottomSheetDialog sheet=new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        activeMapSheet=sheet; sheet.setOnDismissListener(d -> { if(activeMapSheet==sheet) activeMapSheet=null; });
        android.widget.ScrollView scroll=new android.widget.ScrollView(this); scroll.setFillViewport(false); scroll.addView(content);
        sheet.setContentView(scroll); sheet.show(); sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED); return sheet;
    }
    private void showRouteComparisonSheet() {
        if(displayedRoute==null) return;
        android.widget.LinearLayout content=sheetContent(); content.addView(sheetText("Choose your route",20));
        content.addView(sheetText(routeRiskSummaryText(),13));
        android.widget.LinearLayout options=new android.widget.LinearLayout(this);
        final com.google.android.material.bottomsheet.BottomSheetDialog[] dialog=new com.google.android.material.bottomsheet.BottomSheetDialog[1];
        int[] ids={R.id.fastestRecommendation,R.id.balancedRecommendation,R.id.safestRecommendation};
        RouteProfile[] profiles={RouteProfile.FASTEST,RouteProfile.BALANCED,RouteProfile.SAFEST};
        for(int i=0;i<ids.length;i++) {
            final int id=ids[i]; TextView state=findViewById(id); MaterialButton choice=sheetButton(state.getText().toString());
            choice.setTextSize(12); choice.setPadding(4,12,4,12); choice.setMinWidth(0); choice.setMinimumWidth(0);
            choice.setTextColor(androidx.core.content.ContextCompat.getColor(this,R.color.text_primary));
            choice.setStrokeColor(android.content.res.ColorStateList.valueOf(routeColor(profiles[i])));
            choice.setStrokeWidth(profiles[i]==routeProfile?Math.round(2*getResources().getDisplayMetrics().density):0);
            choice.setCornerRadius(Math.round(14*getResources().getDisplayMetrics().density));
            choice.setContentDescription(state.getText()+". Select route");
            choice.setOnClickListener(v -> { dialog[0].dismiss(); state.performClick(); });
            options.addView(choice,new android.widget.LinearLayout.LayoutParams(0,-2,1));
        }
        content.addView(options);
        content.addView(sheetText("All three use the same evaluated roads. If one road wins every preference, its time and risk stay the same.",12));
        MaterialButton details=sheetButton("Details, share & navigation"), compare=sheetButton("Refresh route comparison"), close=sheetButton("Back to map");
        content.addView(details); content.addView(compare); content.addView(close);
        details.setOnClickListener(v -> { dialog[0].dismiss(); showRouteDetailsSheet(); });
        compare.setOnClickListener(v -> { dialog[0].dismiss(); findRoutes(); });
        close.setOnClickListener(v -> dialog[0].dismiss()); dialog[0]=presentSheet(content);
    }
    private String routeRiskSummaryText() { return ((TextView)findViewById(R.id.routeRiskSummary)).getText().toString(); }
    private void openHazardDetailSheet() {
        detailCard.setVisibility(View.GONE);
        android.widget.LinearLayout content=sheetContent(); content.addView(sheetText(detailTitle.getText().toString(),20));
        content.addView(sheetText(detailMeta.getText().toString(),12)); content.addView(sheetText(detailReason.getText().toString(),14));
        final String hazardId=currentHazardId;
        if(isGovernmentUser() && hazardId!=null) {
            MaterialButton update=sheetButton("Update official hazard lifecycle"); content.addView(update);
            update.setOnClickListener(v -> { currentHazardId=hazardId; showHazardLifecycleDialog(); });
        }
        MaterialButton close=sheetButton("Back to map"); content.addView(close);
        com.google.android.material.bottomsheet.BottomSheetDialog sheet=presentSheet(content); close.setOnClickListener(v -> sheet.dismiss());
    }
    private void showRiskLegend() {
        android.widget.LinearLayout content=sheetContent(); content.addView(sheetText("Understand route coverage",20));
        content.addView(sheetText("Blue: lower estimated exposure\nAmber: moderate estimate\nOrange / red: elevated estimate\nGrey: no assessment\nRed ! pins: government-verified hazards",14));
        content.addView(sheetText("No colour or missing pin is proof that a road is safe. Estimates use synthetic area-cell factors, not precise crime observations. Road curves are preserved; the display samples approximately every 500 m (up to 128 sections).",13));
        if(displayedRoute!=null) {
            double total=0, assessed=0; for(RouteRiskSection section:displayedRoute.getRiskSections()) { total+=section.length; if(Double.isFinite(section.risk)) assessed+=section.length; }
            content.addView(sheetText(total>0?String.format(Locale.US,"%.0f%% of route length has an area estimate. %.1f km is unassessed.",100*assessed/total,(total-assessed)/1000):"Section coverage is unavailable from this server / offline route. Do not infer safety from missing markers.",13));
        }
        MaterialButton list=sheetButton("View every route section"), close=sheetButton("Inspect on map"); content.addView(list); content.addView(close);
        com.google.android.material.bottomsheet.BottomSheetDialog sheet=presentSheet(content);
        close.setOnClickListener(v -> sheet.dismiss()); list.setOnClickListener(v -> { sheet.dismiss(); showRiskSectionList(); });
    }
    private void showRiskSectionList() {
        if(displayedRoute==null) return;
        android.widget.LinearLayout content=sheetContent(); content.addView(sheetText("Full journey assessment",20));
        content.addView(sheetText("Area estimates, not guarantees of safety. Tap any section to locate it.",13));
        final com.google.android.material.bottomsheet.BottomSheetDialog[] dialog=new com.google.android.material.bottomsheet.BottomSheetDialog[1];
        for(RouteRiskSection section:displayedRoute.getRiskSections()) {
            String value=Double.isFinite(section.risk)?String.format(Locale.US,"%.0f/100 estimate",section.risk):"Unassessed";
            MaterialButton row=sheetButton(String.format(Locale.US,"%.1f–%.1f km: %s\n%s",section.offset/1000,(section.offset+section.length)/1000,value,section.area));
            row.setOnClickListener(v -> { dialog[0].dismiss(); currentHazardId=null; detailTitle.setText(section.area); detailMeta.setText(value); detailReason.setText(section.reason);
                if(map!=null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(section.latitude,section.longitude),14)); openHazardDetailSheet(); }); content.addView(row);
        }
        if(displayedRoute.getRiskSections().isEmpty()) content.addView(sheetText("No section assessment available. Connect to the updated server and compare again.",14));
        dialog[0]=presentSheet(content);
    }
    private void applyRiskVisibility() {
        if(mapStyle!=null) for(String id:new String[]{RISK_POINTS_LAYER,RISK_SECTIONS_LAYER}) {
            org.maplibre.android.style.layers.Layer layer=mapStyle.getLayer(id);
            if(layer!=null) layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(risksVisible?"visible":"none"));
        }
        ((MaterialButton)findViewById(R.id.riskLayerButton)).setText(risksVisible?"Hide route risks":"Route risks");
        if(risksVisible && displayedRoute!=null) ((TextView)findViewById(R.id.routeStatusText)).setText("Estimates only. Grey = unassessed, not safe.");
        else if(displayedRoute!=null) ((TextView)findViewById(R.id.routeStatusText)).setText(profileName(routeProfile)+" · "+String.format(Locale.US,"%.1f km",displayedRoute.getRoute().getDistanceMeters()/1000));
    }
    private void renderRiskSections(RouteSafetyResult result) {
        if(mapStyle==null) return;
        removeLayerAndSource(RISK_SECTIONS_LAYER,RISK_SECTIONS_SOURCE); List<Feature> features=new ArrayList<>();
        for(RouteRiskSection section:result.getRiskSections()) {
            List<Point> shape=new ArrayList<>(); for(HazardLocation.GeoPoint point:section.geometry) shape.add(Point.fromLngLat(point.getLongitude(),point.getLatitude()));
            if(shape.size()<2) continue;
            Feature feature=Feature.fromGeometry(LineString.fromLngLats(shape)); feature.addStringProperty("sectionId",section.id);
            feature.addStringProperty("color","high".equals(section.band)?"#B52F42":"elevated".equals(section.band)?"#C65B29":"moderate".equals(section.band)?"#B38426":"lower".equals(section.band)?"#2F779D":"#7D8490"); features.add(feature);
        }
        // Older servers and offline paths explicitly remain unassessed rather than looking safe.
        if(features.isEmpty()) { Feature feature=Feature.fromGeometry(lineString(result.getRoute())); feature.addStringProperty("color","#7D8490"); features.add(feature); }
        mapStyle.addSource(new GeoJsonSource(RISK_SECTIONS_SOURCE,FeatureCollection.fromFeatures(features)));
        mapStyle.addLayerBelow(new LineLayer(RISK_SECTIONS_LAYER,RISK_SECTIONS_SOURCE).withProperties(
                lineColor(org.maplibre.android.style.expressions.Expression.get("color")),lineWidth(8f),lineOpacity(.95f)),RISK_POINTS_LAYER);
    }

    private void showRouteDetailsSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.widget.LinearLayout content = new android.widget.LinearLayout(this); content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Math.round(18 * getResources().getDisplayMetrics().density); content.setPadding(pad,pad,pad,pad);
        TextView title = new TextView(this); title.setText(routeResultTitle.getText()); title.setTextSize(20);
        title.setTextColor(androidx.core.content.ContextCompat.getColor(this,R.color.text_primary)); content.addView(title);
        TextView explanation = new TextView(this); explanation.setText(routeResultRisk.getText()); explanation.setTextSize(14);
        explanation.setTextColor(androidx.core.content.ContextCompat.getColor(this,R.color.text_secondary)); explanation.setPadding(0,pad,0,pad);
        content.addView(explanation);
        String[] actions = {"Share route", "Navigate", "Save destination", activeJourneyId == null ? "Share journey" : "End shared journey", "Close"};
        for (int i=0;i<actions.length;i++) {
            final int action=i; MaterialButton button=new MaterialButton(this); button.setText(actions[i]); button.setAllCaps(false);
            button.setOnClickListener(v -> { sheet.dismiss();
                if(action==0) shareRoute(); else if(action==1) openNavigation(); else if(action==2) saveDestination();
                else if(action==3) { if(activeJourneyId==null) startSharedJourney(); else endSharedJourney(); }
            }); content.addView(button);
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(this); scroll.addView(content); sheet.setContentView(scroll); sheet.show();
    }

    private void showMapTools() {
        String[] tools = {"Saved places", "Visible safety layers", "Pin exact source on map", "Pin exact destination on map", "Safety connection", "Area safety details", "Area crime reports", "My location", "Route risk legend"};
        new MaterialAlertDialogBuilder(this).setTitle("Map tools").setItems(tools, (dialog, which) -> {
            if (which == 0) showSavedPlaces();
            else if (which == 1) showHazardFilters();
            else if(which==4) showConnectionOptions();
            else if(which==5) new MaterialAlertDialogBuilder(this).setTitle("Area safety estimate").setMessage(scoreText.getText()+"\n"+scoreLabel.getText()+"\n\nAn area estimate is not a guarantee of safety.").setPositiveButton("Close",null).show();
            else if(which==6) showAreaCrime();
            else if(which==7) { locatingSource=true; enableLocationWithContext(); }
            else if(which==8) showRiskLegend();
            else {
                routePinTarget = which == 2 ? 1 : 2;
                Toast.makeText(this, "Zoom in, then tap the exact " + (routePinTarget == 1 ? "source" : "destination") + ".", Toast.LENGTH_LONG).show();
            }
        }).setNegativeButton("Close", null).show();
    }

    private void showAreaCrime() {
        if (map == null) return;
        LatLng center = map.getCameraPosition().target;
        if (center == null) return;
        TextView crimeContent = new TextView(this);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        crimeContent.setPadding(pad, pad / 2, pad, pad);
        crimeContent.setText("Checking government-verified active reports…"); crimeContent.setTextSize(14);
        crimeContent.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(crimeContent);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Crime reports · within 3 km of map centre")
                .setView(scroll)
                .setPositiveButton("Close", null).show();
        new ProductApiClient(this).areaCrime(center.getLatitude(), center.getLongitude(), new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> {
                if (!dialog.isShowing() || isDestroyed()) return;
                StringBuilder text = new StringBuilder();
                JSONArray types = value.optJSONArray("types");
                text.append(value.optInt("verified_record_count")).append(" active verified crime report(s)\n\n");
                if (types == null || types.length() == 0) text.append("No verified crime types available for this area.\n");
                else for (int i = 0; i < types.length(); i++) {
                    JSONObject item = types.optJSONObject(i);
                    if (item != null) text.append(item.optString("type")).append(" · ").append(item.optInt("count")).append(" report(s)\n");
                }
                JSONArray records = value.optJSONArray("records");
                if (records != null) for (int i = 0; i < Math.min(records.length(), 10); i++) {
                    JSONObject record = records.optJSONObject(i);
                    if (record != null) text.append("\n• ").append(record.optString("title")).append(" · ")
                            .append(record.optInt("distance_m")).append(" m · ").append(record.optString("severity"));
                }
                text.append("\n\nLabels are keyword-derived from verified report text.\n\n").append(value.optString("notice"));
                crimeContent.setText(text);
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> {
                if (dialog.isShowing() && !isDestroyed()) crimeContent.setText("Crime information is unavailable. Connect to the updated Safe-Navi server and retry. No crime data has been inferred or invented.");
            }); }
        });
    }

    private void showTravelModePicker() {
        String[] labels = {getString(R.string.drive), getString(R.string.walk), getString(R.string.cycle)};
        TravelMode[] values = {TravelMode.DRIVING, TravelMode.WALKING, TravelMode.CYCLING};
        int checked = travelMode == TravelMode.WALKING ? 1 : travelMode == TravelMode.CYCLING ? 2 : 0;
        new MaterialAlertDialogBuilder(this).setTitle("Travel mode").setSingleChoiceItems(labels, checked, (dialog, which) -> {
            travelMode = values[which]; travelModeButton.setText(labels[which]); dialog.dismiss();
            if (routeResultCard.getVisibility() == View.VISIBLE) findRoutes();
        }).setNegativeButton("Cancel", null).show();
    }

    private void showRouteProfilePicker() {
        String[] labels = {getString(R.string.fastest), getString(R.string.balanced), getString(R.string.safest)};
        RouteProfile[] values = {RouteProfile.FASTEST, RouteProfile.BALANCED, RouteProfile.SAFEST};
        int checked = routeProfile == RouteProfile.FASTEST ? 0 : routeProfile == RouteProfile.SAFEST ? 2 : 1;
        new MaterialAlertDialogBuilder(this).setTitle("Route priority").setSingleChoiceItems(labels, checked, (dialog, which) -> {
            routeProfile = values[which]; routeProfileButton.setText(labels[which]); dialog.dismiss();
            if (routeResultCard.getVisibility() == View.VISIBLE) findRoutes();
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
        if (routePinTarget != 0) {
            boolean source = routePinTarget == 1;
            setSelectedPlace(source, new PlaceSearchResult("Pinned " + (source ? "start" : "destination"), point.getLatitude(), point.getLongitude()));
            routePinTarget = 0;
            invalidateRouteForEndpoints();
            if (!textOf(sourceInput).isEmpty() && !textOf(destinationInput).isEmpty()) findRoutes();
            return true;
        }
        detailCard.setVisibility(View.GONE);
        renderScore(point.getLatitude(), point.getLongitude());
        if (map == null || mapStyle == null) return false;
        List<Feature> features = map.queryRenderedFeatures(
                map.getProjection().toScreenLocation(point), HAZARD_LAYER_IDS.toArray(new String[0]));
        if (!features.isEmpty() && features.get(0).hasProperty("riskPointId")) {
            showRouteRiskPoint(features.get(0).getStringProperty("riskPointId")); return true;
        }
        if (!features.isEmpty() && features.get(0).hasProperty("sectionId") && displayedRoute!=null) {
            String id=features.get(0).getStringProperty("sectionId");
            for(RouteRiskSection section:displayedRoute.getRiskSections()) if(section.id.equals(id)) {
                currentHazardId=null; detailTitle.setText(section.area);
                detailMeta.setText(Double.isFinite(section.risk)?String.format(Locale.US,"Estimated exposure %.0f/100 · %.1f km from start",section.risk,section.offset/1000):"Unassessed section");
                detailReason.setText(section.reason); openHazardDetailSheet(); return true;
            }
        }
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
        openHazardDetailSheet();
    }

    private boolean onMapLongClick(@NonNull LatLng point) {
        if ("admin".equals(ProductSession.role(this))) {
            Toast.makeText(this, "Administrator map access is read-only.", Toast.LENGTH_SHORT).show();
            return true;
        }
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
        openHazardDetailSheet();
        renderScore(hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude());
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(
                hazard.getLocation().getLatitude(), hazard.getLocation().getLongitude()), 14.2));
    }

    private boolean isGovernmentUser() {
        String role = ProductSession.role(this);
        return "government".equals(role);
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
            findViewById(R.id.endpointEditor).setVisibility(View.VISIBLE);
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
        resolvePlace(sourceText, requestId, true, source -> resolvePlace(destinationText, requestId, false,
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
        routeProfile = profile; routeProfileButton.setText(profileName(profile));
        ranked = RouteRecommendations.rank(ranked, profile);
        RouteSafetyResult best = ranked.get(0);
        displayedRoute=best;
        findViewById(R.id.endpointEditor).setVisibility(View.GONE);
        RouteSafetyResult fastest = best;
        for (RouteSafetyResult result : ranked) if (result.getRoute().getTravelMinutes() < fastest.getRoute().getTravelMinutes()) fastest = result;
        // Only evaluated, non-excluded roads may be displayed as route alternatives.
        List<RouteCandidate> allowed = new ArrayList<>();
        for (RouteSafetyResult result : ranked) allowed.add(result.getRoute());
        renderRoutes(allowed, best.getRoute(), ranked, source, destination);
        List<RouteSafetyResult> evaluated = new ArrayList<>(ranked);
        RouteProfile[] profiles = {RouteProfile.FASTEST, RouteProfile.BALANCED, RouteProfile.SAFEST};
        int[] rowIds = {R.id.fastestRecommendation, R.id.balancedRecommendation, R.id.safestRecommendation};
        java.util.Set<String> distinct = new java.util.HashSet<>();
        StringBuilder objectives = new StringBuilder("\nPreference comparison (score is not ETA):\n");
        for (int i = 0; i < profiles.length; i++) {
            RouteProfile option = profiles[i];
            RouteSafetyResult recommendation = RouteRecommendations.rank(evaluated, option).get(0);
            distinct.add(recommendation.getRoute().getProviderRouteId());
            TextView row = findViewById(rowIds[i]);
            row.setText(String.format(Locale.US, "%s\n%.1f min\nRisk %.0f/100",
                    profileName(option), recommendation.getRoute().getTravelMinutes(), recommendation.getRiskExposure()));
            styleRouteOption(row, option, option == profile);
            row.setContentDescription(row.getText() + ". Tap to highlight this recommendation.");
            row.setOnClickListener(v -> {
                routeProfile = option; routeProfileButton.setText(profileName(option));
                renderRankedRoutes(evaluated, allowed, option, source, destination, routeProviderLabel, sourceLabel);
            });
            objectives.append(String.format(Locale.US, "%s: %.1f min + %.2f × %.1f risk = %.2f\n",
                    profileName(option), recommendation.getRoute().getTravelMinutes(), RouteRecommendations.weight(option),
                    recommendation.getRiskExposure(), recommendation.getScore()));
        }
        routeResultTitle.setText(profileName(profile) + " recommendation · "
                + selectedTravelMode().getLabel());
        routeResultMeta.setText(String.format(Locale.US, "%.1f km · %.0f min · %d alternative%s",
                best.getRoute().getDistanceMeters() / 1000.0, best.getRoute().getTravelMinutes(),
                allowed.size(), allowed.size() == 1 ? "" : "s"));
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
            comparison.append("Road ").append(i + 1).append(String.format(Locale.US, ": %.1f min / %.1f risk",
                    result.getRoute().getTravelMinutes(), result.getRiskExposure()));
        }
        comparison.append(objectives).append("Scores express different preferences, not different safety predictions. Same road = same time and exposure.\n")
                .append("Line colours: blue Fastest, green Balanced, orange Safest; shared roads overlap. A = start, B = destination.");
        routeResultRisk.setText(comparison.toString());
        renderSelectedRiskPoints(best);
        renderRiskSections(best);
        applyRiskVisibility();
        if (mapStyle != null) {
            renderEndpoint("start", "A", source, "#08735C");
            renderEndpoint("finish", "B", destination, "#BF3F38");
        }
        long verified = selectedRiskPoints.values().stream().filter(RouteRiskPoint::isVerified).count();
        long estimates = selectedRiskPoints.size() - verified;
        ((TextView) findViewById(R.id.routeRiskSummary)).setText((distinct.size() == 1
                ? "Same road wins all 3. " : distinct.size() + " recommended roads. ")
                + verified + " official hazard(s) · " + estimates + " estimated risk zone(s). Tap a pin for why.");
        ((TextView)findViewById(R.id.routeStatusText)).setText(profileName(profile)+" · "+String.format(Locale.US,"%.1f km",best.getRoute().getDistanceMeters()/1000));
        findRouteButton.setText(String.format(Locale.US,"Routes · %.0f min",best.getRoute().getTravelMinutes()));
        comparison.append("\n\nSelected-route explanations:\n");
        for (RouteRiskPoint point : selectedRiskPoints.values()) comparison.append(point.isVerified() ? "Official: " : "Estimate: ")
                .append(point.getTitle()).append("\n").append(point.getReason()).append("\n\n");
        if (selectedRiskPoints.isEmpty()) comparison.append("No elevated risk markers available. This is not a guarantee of safety.\n");
        comparison.append("Estimated zones use synthetic area-cell data, not precise crime observations. Red pins are verified reports; amber pins are estimates.");
        routeResultRisk.setText(comparison.toString());
        findViewById(R.id.routeDetailsPanel).setVisibility(View.GONE);
        ((MaterialButton) findViewById(R.id.routeDetailsToggle)).setText("Why this route? · Details & actions");
        lastDestination = destination;
        lastRouteSummary = routeResultTitle.getText() + "\n" + routeResultMeta.getText() + "\n" + comparison;
        routeResultCard.setVisibility(View.VISIBLE);
        sourceInput.setText(shortName(source.getDisplayName()));
        destinationInput.setText(shortName(destination.getDisplayName()));
        ((TextView)findViewById(R.id.mapSearchSummary)).setText(destination.getPrimaryText());
        selectedSourcePlace = source; selectedSourceText = textOf(sourceInput);
        selectedDestinationPlace = destination; selectedDestinationText = textOf(destinationInput);
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
        String[] actions = {"Call my emergency contact", "Call emergency services (112)", "Share my current location"};
        new MaterialAlertDialogBuilder(this).setTitle("Emergency tools")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) openTrustedContacts();
                    else if (which == 1) placeContactCall("112", false);
                    else shareCurrentLocation();
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
                            if (item != null) callEmergencyContact(item.optString("phone"));
                        }).setNegativeButton("Cancel", null).show();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(MapsActivity.this, message, Toast.LENGTH_LONG).show()); }
        });
    }

    private void callEmergencyContact(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            Toast.makeText(this, "This contact has no phone number. Update it in Profile.", Toast.LENGTH_LONG).show();
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            placeContactCall(phone, true);
        } else {
            pendingEmergencyPhone = phone;
            callPermission.launch(Manifest.permission.CALL_PHONE);
        }
    }

    private void placeContactCall(String phone, boolean direct) {
        Intent intent = new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                Uri.fromParts("tel", phone, null));
        try {
            startActivity(intent);
        } catch (SecurityException error) {
            if (direct) placeContactCall(phone, false);
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "No calling app is available on this device.", Toast.LENGTH_LONG).show();
        }
    }

    private void renderRoutes(List<RouteCandidate> candidates, RouteCandidate selected, List<RouteSafetyResult> evaluated,
                              PlaceSearchResult source, PlaceSearchResult destination) {
        if (mapStyle == null) return;
        removeLayerAndSource(SELECTED_ROUTE_LAYER, SELECTED_ROUTE_SOURCE);
        removeLayerAndSource(ROUTES_LAYER, ROUTES_SOURCE);
        List<Feature> alternatives = new ArrayList<>();
        for (RouteCandidate candidate : candidates) {
            Feature feature = Feature.fromGeometry(lineString(candidate));
            String color = "#718078";
            for (RouteProfile preference : new RouteProfile[]{RouteProfile.FASTEST, RouteProfile.BALANCED, RouteProfile.SAFEST}) {
                if (RouteRecommendations.rank(evaluated, preference).get(0).getRoute().getProviderRouteId().equals(candidate.getProviderRouteId()))
                    color = preference == RouteProfile.FASTEST ? "#286BC3" : preference == RouteProfile.SAFEST ? "#C76B12" : "#08735C";
            }
            feature.addStringProperty("color", color); alternatives.add(feature);
        }
        mapStyle.addSource(new GeoJsonSource(ROUTES_SOURCE, FeatureCollection.fromFeatures(alternatives)));
        mapStyle.addLayer(new LineLayer(ROUTES_LAYER, ROUTES_SOURCE).withProperties(
                lineColor(org.maplibre.android.style.expressions.Expression.get("color")), lineWidth(4f), lineOpacity(0.8f)));
        mapStyle.addSource(new GeoJsonSource(SELECTED_ROUTE_SOURCE,
                Feature.fromGeometry(lineString(selected))));
        mapStyle.addLayer(new LineLayer(SELECTED_ROUTE_LAYER, SELECTED_ROUTE_SOURCE).withProperties(
                lineColor(Color.parseColor(routeProfile == RouteProfile.FASTEST ? "#286BC3" : routeProfile == RouteProfile.SAFEST ? "#C76B12" : "#08735C")), lineWidth(6f), lineOpacity(0.95f)));

        renderEndpoint("start", "A", source, "#08735C");
        renderEndpoint("finish", "B", destination, "#BF3F38");

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (RouteCandidate candidate : candidates) for (HazardLocation.GeoPoint point : candidate.getGeometry())
            bounds.include(new LatLng(point.getLatitude(), point.getLongitude()));
        bounds.include(new LatLng(source.getLatitude(), source.getLongitude()));
        bounds.include(new LatLng(destination.getLatitude(), destination.getLongitude()));
        try {
            findViewById(R.id.routeControlCard).post(() -> {
                int gap = Math.round(18 * getResources().getDisplayMetrics().density);
                int top = findViewById(R.id.routeSearchCard).getBottom() + gap;
                int bottom = mapView.getHeight() - findViewById(R.id.routeControlCard).getTop() + gap;
                int available = mapView.getHeight() - top - bottom;
                if (available > gap * 2) map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), gap, top, gap, bottom));
            });
        } catch (Exception ignored) {
            // Provider geometry may contain too little information for bounds; route still renders.
        }
    }

    private void renderSelectedRiskPoints(RouteSafetyResult result) {
        selectedRiskPoints.clear();
        for (RouteRiskPoint point : result.getRiskPoints()) selectedRiskPoints.put(point.getId(), point);
        // Older servers/offline routes can still explain genuine cached verified hazards.
        for (JSONObject record : liveHazardRecords.values()) {
            String id = record.optString("id");
            if (selectedRiskPoints.containsKey(id)) continue;
            JSONArray coordinates = record.optJSONArray("geometry_coordinates");
            List<double[]> shape = new ArrayList<>();
            boolean polygon = "POLYGON".equals(record.optString("geometry_type"));
            if (coordinates != null && !"POINT".equals(record.optString("geometry_type")))
                for (int i=0;i<coordinates.length();i++) {
                    JSONObject point=coordinates.optJSONObject(i);
                    if(point!=null) shape.add(new double[]{point.optDouble("latitude"),point.optDouble("longitude")});
                }
            if(shape.size()<2) { shape.clear(); shape.add(new double[]{record.optDouble("latitude"),record.optDouble("longitude")}); polygon=false; }
            double[][] geometry=shape.toArray(new double[0][]); double distance=Double.POSITIVE_INFINITY;
            List<HazardLocation.GeoPoint> road=result.getRoute().getGeometry();
            for(int i=0;i<road.size()-1;i++) {
                HazardLocation.GeoPoint a=road.get(i),b=road.get(i+1);
                distance=Math.min(distance,com.safenavi.app.safety.network.NoGoGeometry.distance(
                        a.getLatitude(),a.getLongitude(),b.getLatitude(),b.getLongitude(),geometry,polygon));
            }
            if(distance<=750) selectedRiskPoints.put(id,new RouteRiskPoint(id,record.optDouble("latitude"),record.optDouble("longitude"),
                    record.optString("title"),record.optString("reason")+"\n"+record.optString("road_status")+" · within "+Math.round(distance)+" m of route",
                    0,true,"government_verified_hazard"));
        }
        if(mapStyle==null) return;
        removeLayerAndSource(RISK_POINTS_LAYER,RISK_POINTS_SOURCE);
        List<Feature> features=new ArrayList<>();
        for(RouteRiskPoint point:selectedRiskPoints.values()) {
            Feature feature=Feature.fromGeometry(Point.fromLngLat(point.getLongitude(),point.getLatitude()));
            feature.addStringProperty("riskPointId",point.getId());
            feature.addStringProperty("icon",point.isVerified()?"route-risk-official":"route-risk-estimate"); features.add(feature);
        }
        mapStyle.addImage("route-risk-official",riskPinBitmap(Color.parseColor("#B93D36"),"!"));
        mapStyle.addImage("route-risk-estimate",riskPinBitmap(Color.parseColor("#9A6500"),"E"));
        mapStyle.addSource(new GeoJsonSource(RISK_POINTS_SOURCE,FeatureCollection.fromFeatures(features)));
        mapStyle.addLayer(new SymbolLayer(RISK_POINTS_LAYER,RISK_POINTS_SOURCE).withProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage(org.maplibre.android.style.expressions.Expression.get("icon")),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(false),
                org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(false)));
    }

    private Bitmap riskPinBitmap(int color,String label) {
        float density=getResources().getDisplayMetrics().density; int size=Math.round(30*density);
        Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888); Canvas canvas=new Canvas(bitmap);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.WHITE); canvas.drawCircle(size/2f,size/2f,size/2f,paint);
        paint.setColor(color); canvas.drawCircle(size/2f,size/2f,size/2f-2*density,paint);
        paint.setColor(Color.WHITE); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); paint.setTextSize(16*density); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label,size/2f,size/2f-(paint.ascent()+paint.descent())/2,paint); return bitmap;
    }

    private void showRouteRiskPoint(String id) {
        RouteRiskPoint point=selectedRiskPoints.get(id); if(point==null) return;
        currentHazardId=point.isVerified()?id:null;
        detailTitle.setText(point.getTitle());
        detailMeta.setText(point.isVerified()?"Government-verified · selected route"
                :String.format(Locale.US,"Estimated exposure %.0f/100 · approximate area-cell location",point.getRisk()));
        detailReason.setText(point.getReason()+"\nTap this text for the full explanation.");
        openHazardDetailSheet();
        if(map!=null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(point.getLatitude(),point.getLongitude()),14.8));
    }

    private void renderEndpoint(String key, String label, PlaceSearchResult place, String color) {
        String sourceId = "endpoint-" + key, layerId = sourceId + "-marker";
        removeLayerAndSource(layerId, sourceId);
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(36 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE); canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
        paint.setColor(Color.parseColor(color)); canvas.drawCircle(size / 2f, size / 2f, size / 2f - 3 * density, paint);
        paint.setColor(Color.WHITE); paint.setTextSize(18 * density); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, size / 2f, size / 2f - (paint.ascent() + paint.descent()) / 2, paint);
        mapStyle.addImage(sourceId, bitmap);
        mapStyle.addSource(new GeoJsonSource(sourceId, Feature.fromGeometry(Point.fromLngLat(place.getLongitude(), place.getLatitude()))));
        mapStyle.addLayer(new SymbolLayer(layerId, sourceId).withProperties(
                org.maplibre.android.style.layers.PropertyFactory.iconImage(sourceId),
                org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap(true),
                org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement(true)));
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

    private void resolvePlace(String text, int requestId, boolean source, PlaceCallback callback) {
        if (source && selectedSourcePlace != null && text.equals(selectedSourceText)) {
            callback.accept(selectedSourcePlace); return;
        }
        if (!source && selectedDestinationPlace != null && text.equals(selectedDestinationText)) {
            callback.accept(selectedDestinationPlace); return;
        }
        PlaceSearchResult coordinates = parseCoordinates(text);
        if (coordinates != null) {
            callback.accept(coordinates);
            return;
        }
        openMapService.search(text, new OpenMapService.ResultCallback<List<PlaceSearchResult>>() {
            @Override public void onSuccess(List<PlaceSearchResult> results) {
                runOnUiThread(() -> {
                    if (!isRouteRequestActive(requestId)) return;
                    if (results.isEmpty()) showMissingPlace(text, requestId);
                    else choosePlace(text, results, requestId, source, callback);
                });
            }

            @Override public void onError(String message) {
                offlineRouter.search(text, new OfflineRouter.Callback<List<PlaceSearchResult>>() {
                    @Override public void onSuccess(List<PlaceSearchResult> results) { runOnUiThread(() -> {
                        if (isRouteRequestActive(requestId)) choosePlace(text, results, requestId, source, callback);
                    }); }
                    @Override public void onError(String offlineMessage) { runOnUiThread(() -> {
                        if (isRouteRequestActive(requestId)) showRouteError(requestId, message + "\n\n" + offlineMessage);
                    }); }
                });
            }
        });
    }

    private void choosePlace(String query, List<PlaceSearchResult> results, int requestId, boolean source, PlaceCallback callback) {
        if (results.isEmpty()) { showRouteError(requestId, "No match. Try the full place name and locality, or latitude, longitude."); return; }
        setRouteBusy(false);
        new PlaceSearchSheet(this, openMapService, offlineRouter, source ? "Choose starting point" : "Search destination",
                query, 19.043, 73.023, new PlaceSearchSheet.Listener() {
            @Override public void onPlace(PlaceSearchResult place) {
                if (isRouteRequestActive(requestId)) { setSelectedPlace(source, place); setRouteBusy(true); callback.accept(place); }
            }
            @Override public void onMapPin() { routePinTarget = source ? 1 : 2; }
            @Override public void onDeviceLocation() { locatingSource = source; enableLocationWithContext(); }
        }).showResults(results);
    }

    private void showMissingPlace(String query, int requestId) {
        setRouteBusy(false);
        new MaterialAlertDialogBuilder(this).setTitle("Exact place not found")
                .setMessage("No matching Mumbai–Navi Mumbai address was found for \"" + query
                        + "\". Select its exact position on the map instead of using an unrelated result.")
                .setPositiveButton("Pin on map", (dialog, which) -> {
                    if (!isRouteRequestActive(requestId)) return;
                    routePinTarget = query.equals(textOf(sourceInput)) ? 1 : 2;
                    int comma = query.indexOf(',');
                    if (comma >= 0) openMapService.search(query.substring(comma + 1).trim(), new OpenMapService.ResultCallback<List<PlaceSearchResult>>() {
                        @Override public void onSuccess(List<PlaceSearchResult> places) { runOnUiThread(() -> {
                            if (!places.isEmpty() && isRouteRequestActive(requestId) && map != null)
                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(places.get(0).getLatitude(), places.get(0).getLongitude()), 16));
                        }); }
                        @Override public void onError(String message) { }
                    });
                    Toast.makeText(this, "Zoom in and tap the building or entrance. Then tap Compare.", Toast.LENGTH_LONG).show();
                }).setNegativeButton("Edit search", null).show();
    }

    private PlaceSearchResult parseCoordinates(String value) {
        String[] parts = value.trim().split(",");
        if (parts.length != 2) return null;
        try {
            double latitude = Double.parseDouble(parts[0].trim());
            double longitude = Double.parseDouble(parts[1].trim());
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;
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
                            scoreLabel.setText(readable(risk.getRiskLabel()) + " risk · " + risk.getAreaName());
                            ((MaterialButton)findViewById(R.id.mapConnectionButton)).setText("Safety connected");
                        });
                    }

                    @Override public void onError(String message) {
                        runOnUiThread(() -> {
                            if (requestId == activeScoreRequest && !isFinishing() && !isDestroyed()) {
                                scoreText.setText("—");
                                boolean coverage = "OUTSIDE_COVERAGE".equals(message);
                                scoreLabel.setText(coverage ? "Outside dataset coverage" : "Connect safety service");
                                ((MaterialButton)findViewById(R.id.mapConnectionButton)).setText(coverage ? "Safety connected" : "Connect safety");
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
            setSelectedPlace(locatingSource, new PlaceSearchResult("My location", location.getLatitude(), location.getLongitude()));
            invalidateRouteForEndpoints();
            renderScore(location.getLatitude(), location.getLongitude());
            if (map != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(location.getLatitude(), location.getLongitude()), 14.0));
            if (!textOf(sourceInput).isEmpty() && !textOf(destinationInput).isEmpty()) findRoutes();
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
        for (int id : new int[]{R.id.fastestRecommendation,R.id.balancedRecommendation,R.id.safestRecommendation}) findViewById(id).setEnabled(!busy);
        if (busy) {
            displayedRoute=null;
            routeResultCard.setVisibility(View.GONE); detailCard.setVisibility(View.GONE);
            selectedRiskPoints.clear();
            if (mapStyle != null) {
                removeLayerAndSource(RISK_POINTS_LAYER, RISK_POINTS_SOURCE);
                removeLayerAndSource(RISK_SECTIONS_LAYER, RISK_SECTIONS_SOURCE);
                removeLayerAndSource(SELECTED_ROUTE_LAYER, SELECTED_ROUTE_SOURCE);
                removeLayerAndSource(ROUTES_LAYER, ROUTES_SOURCE);
            }
            for (int id : new int[]{R.id.fastestRecommendation,R.id.balancedRecommendation,R.id.safestRecommendation}) {
                TextView option = findViewById(id);
                RouteProfile profile = id == R.id.fastestRecommendation ? RouteProfile.FASTEST :
                        id == R.id.safestRecommendation ? RouteProfile.SAFEST : RouteProfile.BALANCED;
                option.setText((profile == RouteProfile.FASTEST ? "Fastest" : profile == RouteProfile.SAFEST ? "Safest" : "Balanced") + "\n…");
            }
            ((TextView)findViewById(R.id.routeStatusText)).setText("Comparing Fastest, Balanced and Safest…");
        }
    }

    private void showRouteError(int requestId, String message) {
        if (!isRouteRequestActive(requestId)) return;
        setRouteBusy(false);
        ((TextView)findViewById(R.id.routeStatusText)).setText("Route unavailable · check connection or endpoints");
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
        if(activeMapSheet!=null) activeMapSheet.dismiss();
        activeRouteRequest++;
        activeScoreRequest++;
        mapView.onDestroy();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("pending_emergency_phone", pendingEmergencyPhone);
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
