package com.safenavi.app;

import static org.maplibre.android.style.layers.PropertyFactory.circleColor;
import static org.maplibre.android.style.layers.PropertyFactory.circleRadius;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.safenavi.app.product.ProductApiClient;
import com.safenavi.app.product.OfflineReportQueue;
import com.safenavi.app.product.OfflineEvidenceStore;
import com.safenavi.app.product.OfflineReportUploadWorker;
import com.safenavi.app.safety.network.OpenMapService;
import java.util.Locale;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.Point;

public class ReportFragment extends Fragment {
    private static final int LOCATION_REQUEST = 410;
    private static final String MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private TextInputEditText titleInput, descriptionInput;
    private AutoCompleteTextView categoryInput;
    private TextView locationText;
    private TextView evidenceStatus;
    private CheckBox anonymousInput;
    private ProgressBar progress;
    private MapView mapView;
    private MapLibreMap map;
    private Style style;
    private FusedLocationProviderClient locationClient;
    private ProductApiClient api;
    private OpenMapService openMapService;
    private Double latitude, longitude;
    private final JSONArray evidenceIds = new JSONArray();
    private int pendingEvidenceCopies;
    private String conversationReportId;
    private String conversationEvidenceId;
    private android.app.Dialog conversationDialog;
    private final ActivityResultLauncher<String[]> evidencePicker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null) return;
                int remaining = 5 - evidenceIds.length();
                for (int i = 0; i < Math.min(remaining, uris.size()); i++) uploadEvidence(uris.get(i));
            });
    private final ActivityResultLauncher<String[]> conversationPhotoPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::uploadConversationPhoto);

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                  @Nullable ViewGroup container,
                                                  @Nullable Bundle savedInstanceState) {
        MapLibre.getInstance(requireContext());
        View view = inflater.inflate(R.layout.fragment_report, container, false);
        titleInput = view.findViewById(R.id.editTextTitle);
        descriptionInput = view.findViewById(R.id.editTextDescription);
        categoryInput = view.findViewById(R.id.autoCompleteCategory);
        locationText = view.findViewById(R.id.textViewLocationInfo);
        evidenceStatus = view.findViewById(R.id.textEvidenceStatus);
        anonymousInput = view.findViewById(R.id.checkAnonymous);
        progress = view.findViewById(R.id.progressBar);
        mapView = view.findViewById(R.id.mapFragment);
        api = new ProductApiClient(requireContext());
        openMapService = new OpenMapService();
        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        String[] categories = getResources().getStringArray(R.array.safety_hazard_categories);
        categoryInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories));
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(value -> {
            map = value;
            map.setStyle(new Style.Builder().fromUri(MAP_STYLE), ready -> {
                style = ready;
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(19.0760, 72.9777), 10.8));
                map.addOnMapClickListener(point -> { selectLocation(point.getLatitude(), point.getLongitude()); return true; });
                if (getArguments() != null && getArguments().containsKey("latitude")) {
                    selectLocation(getArguments().getDouble("latitude"), getArguments().getDouble("longitude"));
                }
            });
        });
        view.findViewById(R.id.useCurrentLocationButton).setOnClickListener(v -> useCurrentLocation());
        view.findViewById(R.id.buttonSubmitReport).setOnClickListener(v -> submit());
        view.findViewById(R.id.buttonViewHistory).setOnClickListener(v -> showHistory());
        view.findViewById(R.id.buttonAddEvidence).setOnClickListener(v ->
                evidencePicker.launch(new String[]{"image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm"}));
        return view;
    }

    private void uploadEvidence(android.net.Uri uri) {
        pendingEvidenceCopies++;
        evidenceStatus.setText("Securing evidence on this device…");
        new Thread(() -> {
            try {
                JSONObject item = OfflineEvidenceStore.store(requireContext().getApplicationContext(), uri);
                if (isAdded()) requireActivity().runOnUiThread(() -> {
                    pendingEvidenceCopies--;
                    evidenceIds.put(item.optString("id"));
                    evidenceStatus.setText(evidenceIds.length() + " file" + (evidenceIds.length() == 1 ? "" : "s") + " secured offline · uploads automatically");
                });
            } catch (Exception error) { if (isAdded()) requireActivity().runOnUiThread(() -> { pendingEvidenceCopies--; evidenceStatus.setText(error.getMessage()); }); }
        }).start();
    }

    private void selectLocation(double lat, double lng) {
        latitude = lat; longitude = lng;
        locationText.setText(String.format(Locale.US, "Resolving %.6f, %.6f…", lat, lng));
        if (style == null) return;
        if (style.getLayer("report-pin-layer") != null) style.removeLayer("report-pin-layer");
        if (style.getSource("report-pin-source") != null) style.removeSource("report-pin-source");
        style.addSource(new GeoJsonSource("report-pin-source", Feature.fromGeometry(Point.fromLngLat(lng, lat))));
        style.addLayer(new CircleLayer("report-pin-layer", "report-pin-source")
                .withProperties(circleRadius(10f), circleColor(Color.parseColor("#FF6B57"))));
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 15.5));
        openMapService.reverse(lat, lng, new OpenMapService.ResultCallback<String>() {
            @Override public void onSuccess(String address) { if (isAdded() && latitude != null && latitude == lat && longitude == lng) requireActivity().runOnUiThread(() -> locationText.setText(address)); }
            @Override public void onError(String message) { if (isAdded() && latitude != null && latitude == lat && longitude == lng) requireActivity().runOnUiThread(() -> locationText.setText(String.format(Locale.US, "Pinned at %.6f, %.6f", lat, lng))); }
        });
    }

    private void useCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) Toast.makeText(requireContext(), "No recent location is available.", Toast.LENGTH_SHORT).show();
            else selectLocation(location.getLatitude(), location.getLongitude());
        });
    }

    private void submit() {
        if (pendingEvidenceCopies > 0) { Toast.makeText(requireContext(), "Wait for selected evidence to finish securing.", Toast.LENGTH_SHORT).show(); return; }
        String title = text(titleInput);
        String description = text(descriptionInput);
        String category = categoryInput.getText().toString().trim();
        if (title.length() < 5) { titleInput.setError("Add a clear issue title"); return; }
        if (category.isEmpty()) { categoryInput.setError("Choose a category"); return; }
        if (description.length() < 10) { descriptionInput.setError("Describe the issue in more detail"); return; }
        if (latitude == null || longitude == null) { Toast.makeText(requireContext(), "Pin the issue location first.", Toast.LENGTH_SHORT).show(); return; }
        setBusy(true);
        try {
            String categoryKey = category.toUpperCase(Locale.US).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
            JSONObject payload = new JSONObject().put("title", title)
                    .put("category_key", categoryKey)
                    .put("description", description).put("latitude", latitude).put("longitude", longitude)
                    .put("address", locationText.getText().toString()).put("anonymous", anonymousInput.isChecked())
                    .put("evidence_ids", new JSONArray()).put("client_request_id", UUID.randomUUID().toString());
            api.suggestDuplicates(categoryKey, latitude, longitude, new ProductApiClient.ArrayCallback() {
                @Override public void onSuccess(JSONArray values) { requireActivity().runOnUiThread(() -> {
                    if (values.length() == 0) submitPayload(payload); else showDuplicateSuggestion(payload, values.optJSONObject(0));
                }); }
                @Override public void onError(String message) { requireActivity().runOnUiThread(() -> submitPayload(payload)); }
            });
        } catch (JSONException error) { setBusy(false); }
    }

    private void showDuplicateSuggestion(JSONObject payload, JSONObject match) {
        setBusy(false);
        if (match == null) { submitPayload(payload); return; }
        String message = match.optString("title") + "\n" + match.optString("address") + "\n"
                + match.optInt("distance_meters") + " m away · " + match.optString("status").replace('_', ' ')
                + "\n\nConfirming helps officials prioritize the existing issue and avoids duplicate work.";
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Similar issue already reported").setMessage(message)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Submit new anyway", (d,w) -> submitPayload(payload))
                .setPositiveButton("Confirm existing", (d,w) -> {
                    setBusy(true);
                    api.confirmReport(match.optString("id"), new ProductApiClient.ObjectCallback() {
                        @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                            setBusy(false); Toast.makeText(requireContext(), "Existing issue confirmed. Officials can see the added community signal.", Toast.LENGTH_LONG).show();
                        }); }
                        @Override public void onError(String error) { if (isAdded()) requireActivity().runOnUiThread(() -> { setBusy(false); Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show(); }); }
                    });
                }).show();
    }

    private void submitPayload(JSONObject payload) {
        setBusy(true);
        if (evidenceIds.length() > 0) {
            OfflineReportQueue.enqueue(requireContext(), payload, evidenceIds);
            OfflineReportUploadWorker.schedule(requireContext());
            setBusy(false); resetForm();
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Report secured for upload")
                    .setMessage("The report and its evidence are stored in Safe-Navi’s private no-backup storage. File hashes are checked before authenticated upload, which resumes automatically when a connection is available.")
                    .setPositiveButton("Done", null).show();
            return;
        }
        api.submitReport(payload, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> reportSubmitted(value)); }
            @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                setBusy(false);
                if (message.startsWith("Cannot reach")) {
                    OfflineReportQueue.enqueue(requireContext(), payload, evidenceIds);
                    OfflineReportUploadWorker.schedule(requireContext());
                    resetForm();
                    new MaterialAlertDialogBuilder(requireContext()).setTitle("Report saved offline")
                            .setMessage("Safe-Navi will submit it automatically when a connection returns. The same request ID prevents duplicate reports during retries.")
                            .setPositiveButton("Done", null).show();
                } else Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }); }
        });
    }

    private void reportSubmitted(JSONObject report) {
        setBusy(false); resetForm();
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Report received")
                .setMessage("Tracking ID: " + report.optString("id") + "\n\nA safety official can now review it. Until verified, it will not alter public routes.")
                .setPositiveButton("Done", null).show();
    }

    private void resetForm() {
        titleInput.setText(""); descriptionInput.setText(""); categoryInput.setText("", false);
        anonymousInput.setChecked(false); while (evidenceIds.length() > 0) evidenceIds.remove(evidenceIds.length() - 1);
        evidenceStatus.setText("Optional · up to 5 files · 15 MB each");
    }

    private void showHistory() {
        api.reports(null, new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { requireActivity().runOnUiThread(() -> showHistoryDialog(values)); }
            @Override public void onError(String message) { requireActivity().runOnUiThread(() -> {
                if (OfflineReportQueue.pending(requireContext()).length() > 0) showHistoryDialog(new JSONArray());
                else Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }); }
        });
    }

    private void showHistoryDialog(JSONArray values) {
        JSONArray queued = OfflineReportQueue.pending(requireContext());
        if (values.length() == 0 && queued.length() == 0) {
            new MaterialAlertDialogBuilder(requireContext()).setTitle("My reports").setMessage("You have not submitted any reports.").setPositiveButton("Close", null).show(); return;
        }
        int serverCount = Math.min(values.length(), 20); String[] labels = new String[serverCount + queued.length()];
        for (int i = 0; i < serverCount; i++) { JSONObject item = values.optJSONObject(i); labels[i] = item.optString("status").replace('_',' ') + " · " + item.optString("title"); }
        for (int i = 0; i < queued.length(); i++) { JSONObject payload = queued.optJSONObject(i).optJSONObject("payload"); labels[serverCount + i] = "QUEUED OFFLINE · " + payload.optString("title"); }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("My reports").setItems(labels, (d, which) -> {
            if (which < serverCount) showReportDetail(values.optJSONObject(which).optString("id"));
            else new MaterialAlertDialogBuilder(requireContext()).setTitle("Waiting for connection")
                    .setMessage(queued.optJSONObject(which - serverCount).optJSONObject("payload").optString("description"))
                    .setPositiveButton("Close", null).show();
        }).setNegativeButton("Close", null).show();
    }

    private void retryOfflineReports() {
        if (OfflineReportQueue.pending(requireContext()).length() > 0) OfflineReportUploadWorker.schedule(requireContext());
    }

    private void showReportDetail(String reportId) {
        api.reportDetail(reportId, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject report) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                StringBuilder message = new StringBuilder();
                message.append(report.optString("description")).append("\n\nLocation\n").append(report.optString("address"))
                        .append("\n\nDepartment\n").append(report.optString("assigned_department", "Awaiting assignment"))
                        .append("\n\nExpected resolution\n").append(report.optString("due_at", "Not scheduled"));
                if (!report.optString("requested_info").isEmpty()) message.append("\n\nInformation requested\n").append(report.optString("requested_info"));
                JSONArray evidence = report.optJSONArray("evidence"); message.append("\n\nEvidence\n").append(evidence == null ? 0 : evidence.length()).append(" secure file(s)");
                message.append("\n\nStatus timeline\n"); JSONArray timeline = report.optJSONArray("timeline");
                if (timeline != null) for (int i = 0; i < timeline.length(); i++) { JSONObject event = timeline.optJSONObject(i); if (event != null)
                    message.append("• ").append(event.optString("action").replace('_',' ')).append("\n  ").append(event.optString("reason")).append("\n  ").append(event.optString("created_at")).append("\n"); }
                new MaterialAlertDialogBuilder(requireContext()).setTitle(report.optString("title")).setMessage(message.toString())
                        .setNegativeButton("Close", null)
                        .setPositiveButton("Conversation", (dialog, which) -> showReportConversation(reportId)).show();
            }); }
            @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()); }
        });
    }

    private void showReportConversation(String reportId) {
        api.reportMessages(reportId, new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                StringBuilder thread = new StringBuilder();
                if (values.length() == 0) thread.append("No messages yet. Use this secure thread to speak with the reviewing officer.");
                for (int i = 0; i < values.length(); i++) {
                    JSONObject item = values.optJSONObject(i);
                    if (item == null) continue;
                    boolean official = !"CITIZEN".equalsIgnoreCase(item.optString("author_role"));
                    thread.append(official ? "OFFICIAL · " : "YOU · ")
                            .append(item.optString("author_name")).append("\n")
                            .append(item.optString("content"));
                    if (!item.optString("evidence_id").isEmpty()) thread.append("\n📷 Photograph attached");
                    thread.append("\n").append(item.optString("created_at")).append("\n\n");
                }

                LinearLayout form = new LinearLayout(requireContext());
                form.setOrientation(LinearLayout.VERTICAL); form.setPadding(48, 8, 48, 0);
                TextInputEditText reply = new TextInputEditText(requireContext());
                reply.setHint("Reply to the reviewing officer"); reply.setMinLines(2); reply.setMaxLines(5);
                TextView attachment = new TextView(requireContext());
                attachment.setText(conversationEvidenceId == null ? "No photograph attached" : "Photograph ready to send");
                attachment.setPadding(4, 12, 4, 8);
                MaterialButton addPhoto = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                addPhoto.setText("Attach photograph");
                addPhoto.setOnClickListener(v -> {
                    conversationReportId = reportId;
                    if (conversationDialog != null) conversationDialog.dismiss();
                    conversationPhotoPicker.launch(new String[]{"image/jpeg", "image/png", "image/webp"});
                });
                form.addView(reply); form.addView(attachment); form.addView(addPhoto);
                conversationDialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Report conversation")
                        .setMessage(thread.toString().trim()).setView(form).setNegativeButton("Close", null)
                        .setPositiveButton("Send", (dialog, which) -> {
                            String content = reply.getText() == null ? "" : reply.getText().toString().trim();
                            if (content.isEmpty() && conversationEvidenceId == null) {
                                Toast.makeText(requireContext(), "Write a reply or attach a photograph.", Toast.LENGTH_LONG).show(); return;
                            }
                            api.createReportMessage(reportId, content.isEmpty() ? "Photograph supplied for review" : content,
                                    conversationEvidenceId, new ProductApiClient.ObjectCallback() {
                                        @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                                            conversationEvidenceId = null; conversationReportId = null;
                                            Toast.makeText(requireContext(), "Reply sent securely", Toast.LENGTH_SHORT).show();
                                            showReportConversation(reportId);
                                        }); }
                                        @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()); }
                                    });
                        }).show();
            }); }
            @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()); }
        });
    }

    private void uploadConversationPhoto(Uri uri) {
        if (uri == null || conversationReportId == null) return;
        Toast.makeText(requireContext(), "Securing photograph…", Toast.LENGTH_SHORT).show();
        api.uploadEvidence(uri, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                conversationEvidenceId = value.optString("id");
                Toast.makeText(requireContext(), "Photograph attached to your next reply", Toast.LENGTH_LONG).show();
                showReportConversation(conversationReportId);
            }); }
            @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()); }
        });
    }

    private void setBusy(boolean busy) { progress.setVisibility(busy ? View.VISIBLE : View.GONE); }
    private String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString().trim(); }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) useCurrentLocation();
    }
    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() { super.onResume(); mapView.onResume(); retryOfflineReports(); }
    @Override public void onPause() { mapView.onPause(); super.onPause(); }
    @Override public void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override public void onDestroyView() { mapView.onDestroy(); super.onDestroyView(); }
}
