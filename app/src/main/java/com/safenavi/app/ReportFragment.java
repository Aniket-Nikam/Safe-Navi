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
import com.safenavi.app.product.JsonDisplay;
import com.safenavi.app.product.EvidenceViewer;
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
    private boolean submitting,restoring;
    private String draftRequestId=UUID.randomUUID().toString(),draftIdentity,draftOrigin;
    private final android.os.Handler draftHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable saveDraftTask=this::saveDraft;
    private static final int LOCATION_REQUEST = 410;
    private static final String MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty";
    private TextInputEditText titleInput, descriptionInput;
    private AutoCompleteTextView categoryInput;
    private TextView locationText;
    private TextView evidenceStatus,draftStatus;
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
                int remaining = Math.max(0,5 - evidenceIds.length()-pendingEvidenceCopies);
                for (int i = 0; i < Math.min(remaining, uris.size()); i++) uploadEvidence(uris.get(i));
            });

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                  @Nullable ViewGroup container,
                                                  @Nullable Bundle savedInstanceState) {
        MapLibre.getInstance(requireContext());
        submitting=false;
        View view = inflater.inflate(R.layout.fragment_report, container, false);
        titleInput = view.findViewById(R.id.editTextTitle);
        descriptionInput = view.findViewById(R.id.editTextDescription);
        categoryInput = view.findViewById(R.id.autoCompleteCategory);
        locationText = view.findViewById(R.id.textViewLocationInfo);
        evidenceStatus = view.findViewById(R.id.textEvidenceStatus);
        draftStatus=view.findViewById(R.id.textDraftStatus);
        anonymousInput = view.findViewById(R.id.checkAnonymous);
        progress = view.findViewById(R.id.progressBar);
        mapView = view.findViewById(R.id.mapFragment);
        api = new ProductApiClient(requireContext());
        openMapService = new OpenMapService();
        locationClient = LocationServices.getFusedLocationProviderClient(requireActivity());
        draftIdentity=com.safenavi.app.product.ProductSession.token(requireContext());draftOrigin=com.safenavi.app.product.ServerConnection.baseUrl();
        String[] categories = getResources().getStringArray(R.array.safety_hazard_categories);
        categoryInput.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, categories));
        restoreDraft();
        android.text.TextWatcher changes=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void afterTextChanged(android.text.Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){if(!restoring){draftRequestId=UUID.randomUUID().toString();draftHandler.removeCallbacks(saveDraftTask);draftHandler.postDelayed(saveDraftTask,500);}}};
        titleInput.addTextChangedListener(changes);descriptionInput.addTextChangedListener(changes);categoryInput.addTextChangedListener(changes);
        anonymousInput.setOnCheckedChangeListener((b,checked)->{if(!restoring){draftRequestId=UUID.randomUUID().toString();saveDraft();}});
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(value -> {
            map = value;
            map.setStyle(new Style.Builder().fromUri(MAP_STYLE), ready -> {
                style = ready;
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(19.0760, 72.9777), 10.8));
                map.addOnMapClickListener(point -> { selectLocation(point.getLatitude(), point.getLongitude()); return true; });
                if (getArguments() != null && getArguments().containsKey("latitude")) {
                    selectLocation(getArguments().getDouble("latitude"), getArguments().getDouble("longitude"));
                } else if(latitude!=null&&longitude!=null) selectLocation(latitude,longitude);
            });
        });
        view.findViewById(R.id.useCurrentLocationButton).setOnClickListener(v -> useCurrentLocation());
        view.findViewById(R.id.buttonSubmitReport).setOnClickListener(v -> submit());
        view.findViewById(R.id.buttonViewHistory).setOnClickListener(v -> showHistory());
        view.findViewById(R.id.buttonDiscardDraft).setOnClickListener(v->{if(pendingEvidenceCopies>0||submitting){Toast.makeText(requireContext(),"Wait for the current operation",Toast.LENGTH_SHORT).show();return;}new MaterialAlertDialogBuilder(requireContext()).setTitle("Discard unsent draft?").setMessage("This removes only this unsent form and its local attachments. Submitted reports are unaffected.").setNegativeButton("Keep",null).setPositiveButton("Discard",(d,w)->{for(int i=0;i<evidenceIds.length();i++)OfflineEvidenceStore.delete(requireContext(),evidenceIds.optString(i));resetForm();}).show();});
        evidenceStatus.setOnClickListener(v->manageEvidence());
        view.findViewById(R.id.buttonAddEvidence).setOnClickListener(v ->
                evidencePicker.launch(new String[]{"image/jpeg", "image/png", "image/webp", "video/mp4", "video/webm"}));
        return view;
    }

    private void uploadEvidence(android.net.Uri uri) {
        if(submitting)return;
        final android.content.Context app=requireContext().getApplicationContext();final View screen=getView();final String identity=draftIdentity,origin=draftOrigin;saveDraft();
        pendingEvidenceCopies++;
        evidenceStatus.setText("Securing evidence on this device…");
        new Thread(() -> {
            try {
                JSONObject item = OfflineEvidenceStore.store(app, uri);
                draftHandler.post(() -> {
                    pendingEvidenceCopies--;
                    if(!identity.equals(com.safenavi.app.product.ProductSession.token(app))||!origin.equals(com.safenavi.app.product.ServerConnection.baseUrl())){OfflineEvidenceStore.delete(app,item.optString("id"));return;}
                    com.safenavi.app.product.ScopedDraftStore.appendEvidence(app,"report",item.optString("id"));
                    if(!isAdded()||getView()!=screen)return;
                    evidenceIds.put(item.optString("id"));
                    draftRequestId=UUID.randomUUID().toString();saveDraft();
                    evidenceStatus.setText(evidenceIds.length() + " file" + (evidenceIds.length() == 1 ? "" : "s") + " secured offline · uploads automatically");
                });
            } catch (Exception error) { draftHandler.post(() -> { pendingEvidenceCopies--;if(isAdded()&&getView()==screen)evidenceStatus.setText(error.getMessage()); }); }
        }).start();
    }

    private void selectLocation(double lat, double lng) {
        if(submitting)return;
        latitude = lat; longitude = lng;
        locationText.setText(String.format(Locale.US, "Resolving %.6f, %.6f…", lat, lng));
        if(!restoring)draftRequestId=UUID.randomUUID().toString();saveDraft();
        if (style == null) return;
        if (style.getLayer("report-pin-layer") != null) style.removeLayer("report-pin-layer");
        if (style.getSource("report-pin-source") != null) style.removeSource("report-pin-source");
        style.addSource(new GeoJsonSource("report-pin-source", Feature.fromGeometry(Point.fromLngLat(lng, lat))));
        style.addLayer(new CircleLayer("report-pin-layer", "report-pin-source")
                .withProperties(circleRadius(10f), circleColor(Color.parseColor("#FF6B57"))));
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 15.5));
        openMapService.reverse(lat, lng, new OpenMapService.ResultCallback<String>() {
            @Override public void onSuccess(String address) { onScreen(()->{if(latitude!=null&&latitude==lat&&longitude==lng){locationText.setText(address);saveDraft();}}); }
            @Override public void onError(String message) { onScreen(()->{if(latitude!=null&&latitude==lat&&longitude==lng){locationText.setText(String.format(Locale.US,"Pinned at %.6f, %.6f",lat,lng));saveDraft();}}); }
        });
    }

    private void useCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
            return;
        }
        locationClient.getLastLocation().addOnSuccessListener(location -> {
            if(!isAdded()||getView()==null)return;
            if (location == null) Toast.makeText(requireContext(), "No recent location is available.", Toast.LENGTH_SHORT).show();
            else selectLocation(location.getLatitude(), location.getLongitude());
        });
    }

    private void submit() {
        if(submitting)return;
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
                    .put("evidence_ids", new JSONArray()).put("client_request_id", draftRequestId);
            saveDraft();
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Review before sending").setMessage(title+"\n"+category+"\n\n"+description+"\n\nLocation: "+locationText.getText()+"\n"+evidenceIds.length()+" evidence file(s)\n"+(anonymousInput.isChecked()?"Identity hidden in operational views":"Identity visible to authorised officials"))
                .setNegativeButton("Edit",(d,w)->setBusy(false)).setOnCancelListener(d->setBusy(false)).setPositiveButton("Send report",(d,w)->checkDuplicates(payload)).show();
        } catch (JSONException error) { setBusy(false); }
    }

    private void checkDuplicates(JSONObject payload){api.suggestDuplicates(payload.optString("category_key"),payload.optDouble("latitude"),payload.optDouble("longitude"),new ProductApiClient.ArrayCallback(){public void onSuccess(JSONArray values){onScreen(()->{if(values.length()==0)submitPayload(payload);else showDuplicateSuggestion(payload,values.optJSONObject(0));});}public void onError(String message){onScreen(()->submitPayload(payload));}});}

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
                        @Override public void onSuccess(JSONObject value) { if (isAdded()) onScreen(() -> {
                            setBusy(false); Toast.makeText(requireContext(), "Existing issue confirmed. Officials can see the added community signal.", Toast.LENGTH_LONG).show();
                        }); }
                        @Override public void onError(String error) { if (isAdded()) onScreen(() -> { setBusy(false); Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show(); }); }
                    });
                }).show();
    }

    private void submitPayload(JSONObject payload) {
        setBusy(true);
        if (evidenceIds.length() > 0) {
            if(!enqueueSafely(payload))return;
            OfflineReportUploadWorker.schedule(requireContext());
            setBusy(false); resetForm();
            new MaterialAlertDialogBuilder(requireContext()).setTitle("Report secured for upload")
                    .setMessage("The report and its evidence are stored in Safe-Navi’s private no-backup storage. File hashes are checked before authenticated upload, which resumes automatically when a connection is available.")
                    .setPositiveButton("Done", null).show();
            return;
        }
        api.submitReport(payload, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (isAdded()) onScreen(() -> reportSubmitted(value)); }
            @Override public void onError(String message) { if (isAdded()) onScreen(() -> {
                setBusy(false);
                if (message.startsWith("Cannot reach")) {
                    if(!enqueueSafely(payload))return;
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
    private boolean enqueueSafely(JSONObject payload){try{OfflineReportQueue.enqueue(requireContext(),payload,evidenceIds);return true;}catch(IllegalStateException error){setBusy(false);saveDraft();new MaterialAlertDialogBuilder(requireContext()).setTitle("Report not queued").setMessage("Device storage could not confirm saving the upload. Your form has not been cleared. Free storage and retry.").setPositiveButton("Keep draft",null).show();return false;}}

    private void resetForm() {
        restoring=true;
        titleInput.setText(""); descriptionInput.setText(""); categoryInput.setText("", false);
        anonymousInput.setChecked(false); while (evidenceIds.length() > 0) evidenceIds.remove(evidenceIds.length() - 1);
        evidenceStatus.setText("Optional · up to 5 files · 15 MB each");
        latitude=null;longitude=null;locationText.setText("Select the exact issue location on the map");
        if(style!=null){if(style.getLayer("report-pin-layer")!=null)style.removeLayer("report-pin-layer");if(style.getSource("report-pin-source")!=null)style.removeSource("report-pin-source");}
        draftRequestId=UUID.randomUUID().toString();com.safenavi.app.product.ScopedDraftStore.clear(requireContext(),"report");draftStatus.setText("Draft is empty");restoring=false;
    }

    private void showHistory() {
        api.reports(null, new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { onScreen(() -> showHistoryDialog(values)); }
            @Override public void onError(String message) { onScreen(() -> {
                if (OfflineReportQueue.pending(requireContext()).length() > 0) showHistoryDialog(new JSONArray());
                else Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            }); }
        });
    }

    private void showHistoryDialog(JSONArray values) {
        com.safenavi.app.product.ReportHistorySheet.show(requireActivity(),values,OfflineReportQueue.pending(requireContext()),this::showReportDetail,this::showHistory);
    }

    private void retryOfflineReports() {
        if (OfflineReportQueue.pending(requireContext()).length() > 0) OfflineReportUploadWorker.schedule(requireContext());
    }

    private void showReportDetail(String reportId) {
        api.reportDetail(reportId,new ProductApiClient.ObjectCallback(){
            public void onSuccess(JSONObject report){onScreen(()->com.safenavi.app.product.ReportHistorySheet.detail(requireActivity(),report,()->showReportConversation(reportId)));}
            public void onError(String message){onScreen(()->Toast.makeText(requireContext(),message,Toast.LENGTH_LONG).show());}
        });
    }

    private void showReportConversation(String reportId) {
        CaseConversationActivity.open(requireContext(),reportId);
    }

    private void onScreen(Runnable action){View screen=getView();if(screen!=null)screen.post(()->{if(isAdded()&&getView()==screen)action.run();});}
    private void saveDraft(){if(!isAdded()||getView()==null||draftIdentity==null||!draftIdentity.equals(com.safenavi.app.product.ProductSession.token(requireContext()))||!draftOrigin.equals(com.safenavi.app.product.ServerConnection.baseUrl()))return;try{JSONObject value=new JSONObject().put("title",text(titleInput)).put("description",text(descriptionInput)).put("category",categoryInput.getText().toString()).put("anonymous",anonymousInput.isChecked()).put("latitude",latitude).put("longitude",longitude).put("address",locationText.getText().toString()).put("evidence",new JSONArray(evidenceIds.toString())).put("request_id",draftRequestId);boolean saved=com.safenavi.app.product.ScopedDraftStore.save(requireContext(),"report",value);draftStatus.setText(saved?"Draft saved on this device":"Draft could not be saved. Keep this screen open.");}catch(Exception e){draftStatus.setText("Draft could not be saved");}}
    private void restoreDraft(){JSONObject draft=com.safenavi.app.product.ScopedDraftStore.load(requireContext(),"report");restoring=true;titleInput.setText(draft.optString("title"));descriptionInput.setText(draft.optString("description"));categoryInput.setText(draft.optString("category"),false);anonymousInput.setChecked(draft.optBoolean("anonymous"));draftRequestId=draft.optString("request_id",UUID.randomUUID().toString());while(evidenceIds.length()>0)evidenceIds.remove(evidenceIds.length()-1);JSONArray files=draft.optJSONArray("evidence");if(files!=null)for(int i=0;i<files.length();i++)evidenceIds.put(files.optString(i));if(!draft.isNull("latitude")&&!draft.isNull("longitude")){double lat=draft.optDouble("latitude",Double.NaN),lon=draft.optDouble("longitude",Double.NaN);if(Double.isFinite(lat)&&Double.isFinite(lon)){latitude=lat;longitude=lon;locationText.setText(draft.optString("address"));}}evidenceStatus.setText(evidenceIds.length()==0?"Optional · up to 5 files · 15 MB each":evidenceIds.length()+" secured file(s). Tap here to manage.");draftStatus.setText(draft.length()==0?"Your unsent report is saved automatically":"Restored your unsent draft");restoring=false;}
    private void manageEvidence(){if(submitting||evidenceIds.length()==0)return;String[] labels=new String[evidenceIds.length()];for(int i=0;i<labels.length;i++){JSONObject file=OfflineEvidenceStore.get(requireContext(),evidenceIds.optString(i));labels[i]=file==null?"Unavailable file":file.optString("name");}new MaterialAlertDialogBuilder(requireContext()).setTitle("Selected evidence").setItems(labels,(d,index)->new MaterialAlertDialogBuilder(requireContext()).setTitle(labels[index]).setMessage("Remove this file from the unsent report?").setNegativeButton("Keep",null).setPositiveButton("Remove",(dialog,w)->{OfflineEvidenceStore.delete(requireContext(),evidenceIds.optString(index));evidenceIds.remove(index);draftRequestId=UUID.randomUUID().toString();saveDraft();evidenceStatus.setText(evidenceIds.length()+" secured file(s). Tap to manage.");}).show()).setNegativeButton("Close",null).show();}
    private void setBusy(boolean busy) { submitting=busy;progress.setVisibility(busy ? View.VISIBLE : View.GONE);if(getView()!=null){for(int id:new int[]{R.id.buttonSubmitReport,R.id.buttonAddEvidence,R.id.useCurrentLocationButton,R.id.buttonDiscardDraft})getView().findViewById(id).setEnabled(!busy);}titleInput.setEnabled(!busy);descriptionInput.setEnabled(!busy);categoryInput.setEnabled(!busy);anonymousInput.setEnabled(!busy); }
    private String text(TextInputEditText input) { return input.getText() == null ? "" : input.getText().toString().trim(); }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == LOCATION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) useCurrentLocation();
    }
    @Override public void onStart() { super.onStart(); mapView.onStart(); }
    @Override public void onResume() { super.onResume(); mapView.onResume(); retryOfflineReports(); }
    @Override public void onPause() { saveDraft();draftHandler.removeCallbacks(saveDraftTask);mapView.onPause(); super.onPause(); }
    @Override public void onStop() { mapView.onStop(); super.onStop(); }
    @Override public void onLowMemory() { super.onLowMemory(); mapView.onLowMemory(); }
    @Override public void onDestroyView() { saveDraft();draftHandler.removeCallbacks(saveDraftTask);mapView.onDestroy();map=null;style=null;super.onDestroyView(); }
}
