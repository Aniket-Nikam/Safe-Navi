package com.safenavi.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.safenavi.app.product.ProductApiClient;
import com.safenavi.app.product.ProductSession;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GovernmentSafetyDashboardActivity extends AppCompatActivity {
    private TextView pendingCount, activeCount, criticalCount, reportTitle, reportStatus, reportMeta, queueLabel;
    private MaterialButton triageButton, verifyButton, rejectButton;
    private MaterialButton assignButton, requestInfoButton, auditButton;
    private Spinner queueSpinner;
    private ProductApiClient api;
    private JSONObject currentReport;
    private JSONArray actionableReports = new JSONArray();
    private String filterStatus = "", filterCategory = "", filterDepartment = "", filterWard = "", filterSeverity = "";
    private Integer filterAgeHours;
    private boolean filterOverdue;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_government_safety_dashboard);
        api = new ProductApiClient(this);
        pendingCount = findViewById(R.id.pendingCount);
        activeCount = findViewById(R.id.activeCount);
        criticalCount = findViewById(R.id.criticalCount);
        reportTitle = findViewById(R.id.pendingReportTitle);
        reportStatus = findViewById(R.id.pendingReportStatus);
        reportMeta = findViewById(R.id.pendingReportMeta);
        queueLabel = findViewById(R.id.auditCount);
        triageButton = findViewById(R.id.reduceSeverityButton);
        verifyButton = findViewById(R.id.verifyReportButton);
        rejectButton = findViewById(R.id.resolveHazardButton);
        assignButton = findViewById(R.id.assignReportButton);
        requestInfoButton = findViewById(R.id.requestInfoButton);
        auditButton = findViewById(R.id.viewAuditButton);
        queueSpinner = findViewById(R.id.governmentQueueSpinner);
        findViewById(R.id.govBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.openGovernmentMapButton).setOnClickListener(v -> startActivity(new Intent(this, MapsActivity.class)));
        findViewById(R.id.reviewNewsSignalsButton).setOnClickListener(v -> openNewsSignals());
        findViewById(R.id.filterGovernmentQueueButton).setOnClickListener(v -> showQueueFilters());
        triageButton.setOnClickListener(v -> triage());
        verifyButton.setOnClickListener(v -> showVerificationDialog());
        rejectButton.setOnClickListener(v -> reject());
        assignButton.setOnClickListener(v -> showAssignmentDialog());
        requestInfoButton.setOnClickListener(v -> showInformationDialog());
        auditButton.setOnClickListener(v -> showOperationalDetail());
        queueSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < actionableReports.length()) {
                    currentReport = actionableReports.optJSONObject(position); renderCurrentReport();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); if (api != null) refresh(); }

    private void refresh() {
        api.reportsFiltered(filterStatus, filterCategory, filterDepartment, filterWard, filterSeverity, filterAgeHours, filterOverdue, new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray reports) { runOnUiThread(() -> renderReports(reports)); }
            @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
        });
        api.mapHazards(new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> renderHazards(value.optJSONArray("hazards"))); }
            @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(GovernmentSafetyDashboardActivity.this, message, Toast.LENGTH_LONG).show()); }
        });
        api.governmentMetrics(new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> {
                activeCount.setText(String.valueOf(value.optInt("active_hazards")));
                int overdue = value.optInt("overdue");
                if (overdue > 0) queueLabel.setText(overdue + " overdue · action required");
            }); }
            @Override public void onError(String message) { }
        });
    }

    private void showQueueFilters() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(48, 8, 48, 0);
        String[] statuses = {"Any status", "REPORTED", "UNDER_REVIEW", "REQUESTED_INFO", "VERIFIED", "REJECTED", "DUPLICATE"};
        String[] categories = {"Any category", "FLOODING", "CRIME", "POOR_LIGHTING", "ROAD_DAMAGE", "ACCIDENT", "UNSAFE_BUILDING", "CONSTRUCTION", "OTHER"};
        String[] departments = {"Any department", "Roads & Infrastructure", "Police", "Fire & Emergency", "Flood Control", "Street Lighting", "Sanitation"};
        String[] severities = {"Any severity", "LOW", "MODERATE", "HIGH", "CRITICAL"};
        String[] ages = {"Any age", "Older than 4 hours", "Older than 12 hours", "Older than 24 hours", "Older than 72 hours"};
        Spinner status = spinner(statuses), category = spinner(categories), department = spinner(departments), severity = spinner(severities), age = spinner(ages);
        TextInputEditText ward = new TextInputEditText(this); ward.setHint("Ward / locality contained in address"); ward.setText(filterWard);
        CheckBox overdue = new CheckBox(this); overdue.setText("Only overdue SLA items"); overdue.setChecked(filterOverdue);
        form.addView(label("Status")); form.addView(status); form.addView(label("Category")); form.addView(category);
        form.addView(label("Department")); form.addView(department); form.addView(ward); form.addView(label("Verified severity")); form.addView(severity);
        form.addView(label("Report age")); form.addView(age); form.addView(overdue);
        new MaterialAlertDialogBuilder(this).setTitle("Operational filters").setView(form).setNeutralButton("Clear", (d,w) -> {
            filterStatus=filterCategory=filterDepartment=filterWard=filterSeverity=""; filterAgeHours=null; filterOverdue=false; refresh();
        }).setNegativeButton("Cancel", null).setPositiveButton("Apply", (d,w) -> {
            filterStatus = status.getSelectedItemPosition() == 0 ? "" : statuses[status.getSelectedItemPosition()];
            filterCategory = category.getSelectedItemPosition() == 0 ? "" : categories[category.getSelectedItemPosition()];
            filterDepartment = department.getSelectedItemPosition() == 0 ? "" : departments[department.getSelectedItemPosition()];
            filterSeverity = severity.getSelectedItemPosition() == 0 ? "" : severities[severity.getSelectedItemPosition()];
            filterWard = ward.getText() == null ? "" : ward.getText().toString().trim();
            Integer[] hours = {null,4,12,24,72}; filterAgeHours = hours[age.getSelectedItemPosition()]; filterOverdue = overdue.isChecked(); refresh();
        }).show();
    }

    private void renderReports(JSONArray reports) {
        int pending = 0;
        currentReport = null; actionableReports = new JSONArray();
        java.util.ArrayList<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < reports.length(); i++) {
            JSONObject report = reports.optJSONObject(i);
            if (report == null) continue;
            String status = report.optString("status");
            boolean actionable = status.equals("REPORTED") || status.equals("UNDER_REVIEW") || status.equals("REQUESTED_INFO");
            if (actionable || hasQueueFilters()) {
                pending++;
                actionableReports.put(report);
                labels.add(report.optString("title") + " · " + status.replace('_', ' '));
                if (currentReport == null) currentReport = report;
            }
        }
        queueSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        pendingCount.setText(String.valueOf(pending));
        queueLabel.setText(String.format(Locale.US, "%d awaiting decision", pending));
        if (currentReport == null) {
            reportTitle.setText("Review queue is clear"); reportStatus.setText("UP TO DATE");
            reportMeta.setText("New citizen reports will appear here as soon as they arrive.");
            setActions(false); return;
        }
        renderCurrentReport();
    }

    private void renderCurrentReport() {
        if (currentReport == null) return;
        reportTitle.setText(currentReport.optString("title"));
        String status = currentReport.optString("status");
        reportStatus.setText(status.replace('_', ' '));
        reportMeta.setText(currentReport.optString("category_key").replace('_', ' ') + " · "
                + currentReport.optString("address") + "\n" + currentReport.optString("description"));
        setActions(true);
        triageButton.setEnabled(status.equals("REPORTED"));
        boolean finalState = status.equals("VERIFIED") || status.equals("REJECTED") || status.equals("DUPLICATE");
        if (finalState) { setActions(false); auditButton.setEnabled(true); }
    }

    private boolean hasQueueFilters() {
        return !filterStatus.isEmpty() || !filterCategory.isEmpty() || !filterDepartment.isEmpty()
                || !filterWard.isEmpty() || !filterSeverity.isEmpty() || filterAgeHours != null || filterOverdue;
    }

    private void showAssignmentDialog() {
        if (currentReport == null) return;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(48, 8, 48, 0);
        Spinner department = spinner(new String[]{"Roads & Infrastructure", "Police", "Fire & Emergency", "Flood Control", "Street Lighting", "Sanitation"});
        TextInputEditText employee = new TextInputEditText(this); employee.setHint("Employee / field team (optional)");
        Spinner sla = spinner(new String[]{"4 hours", "12 hours", "24 hours", "48 hours", "72 hours"}); sla.setSelection(2);
        form.addView(label("Department")); form.addView(department); form.addView(employee); form.addView(label("Resolution target")); form.addView(sla);
        new MaterialAlertDialogBuilder(this).setTitle("Assign operational owner").setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Assign", (d, w) -> {
                    int[] hours = {4,12,24,48,72};
                    api.assignReport(currentReport.optString("id"), department.getSelectedItem().toString(),
                            employee.getText() == null ? "" : employee.getText().toString().trim(), hours[sla.getSelectedItemPosition()], callback("Assignment and SLA saved"));
                }).show();
    }

    private void showInformationDialog() {
        if (currentReport == null) return;
        TextInputEditText input = new TextInputEditText(this); input.setHint("What evidence or detail is required?"); input.setMinLines(3); input.setPadding(48, 12, 48, 12);
        new MaterialAlertDialogBuilder(this).setTitle("Request more information").setView(input).setNegativeButton("Cancel", null)
                .setPositiveButton("Send request", (d, w) -> {
                    String message = input.getText() == null ? "" : input.getText().toString().trim();
                    if (message.length() < 5) { Toast.makeText(this, "Explain what is needed.", Toast.LENGTH_LONG).show(); return; }
                    api.requestMoreInformation(currentReport.optString("id"), message, callback("Citizen notified"));
                }).show();
    }

    private void showOperationalDetail() {
        if (currentReport == null) return;
        final String reportId = currentReport.optString("id");
        api.reportDetail(reportId, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject detail) { api.duplicateReports(reportId, new ProductApiClient.ArrayCallback() {
                @Override public void onSuccess(JSONArray duplicates) { runOnUiThread(() -> renderOperationalDetail(detail, duplicates)); }
                @Override public void onError(String message) { runOnUiThread(() -> renderOperationalDetail(detail, new JSONArray())); }
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
        });
    }

    private void renderOperationalDetail(JSONObject detail, JSONArray duplicates) {
        StringBuilder text = new StringBuilder();
        text.append("Citizen: ").append(detail.optString("citizen_name", "Protected identity")).append("\n")
                .append("Department: ").append(detail.optString("assigned_department", "Unassigned")).append("\n")
                .append("Assigned to: ").append(detail.optString("assigned_to", "Unassigned")).append("\n")
                .append("SLA due: ").append(detail.optString("due_at", "Not set")).append("\n")
                .append("Evidence: ").append(detail.optJSONArray("evidence") == null ? 0 : detail.optJSONArray("evidence").length()).append(" file(s)\n")
                .append("Community confirmations: ").append(detail.optInt("confirmations")).append("\n")
                .append("Possible nearby duplicates: ").append(duplicates.length()).append("\n\nAudit timeline\n");
        JSONArray timeline = detail.optJSONArray("timeline");
        if (timeline != null) for (int i = 0; i < timeline.length(); i++) {
            JSONObject event = timeline.optJSONObject(i); if (event != null) text.append("• ").append(event.optString("action").replace('_',' '))
                    .append("\n  ").append(event.optString("reason")).append("\n  ").append(event.optString("created_at")).append("\n");
        }
        JSONArray evidence = detail.optJSONArray("evidence");
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this).setTitle("Operational record")
                .setMessage(text.toString()).setNegativeButton("Close", null)
                .setPositiveButton("Conversation", (d, w) -> showReportConversation(detail.optString("id")));
        if (evidence != null && evidence.length() > 0) dialog.setNeutralButton("View evidence", (d, w) -> showEvidencePicker(evidence));
        dialog.show();
    }

    private void showReportConversation(String reportId) {
        api.reportMessages(reportId, new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { runOnUiThread(() -> {
                StringBuilder thread = new StringBuilder();
                if (values.length() == 0) thread.append("No messages yet. Send a clear request or field update to the citizen.");
                for (int i = 0; i < values.length(); i++) {
                    JSONObject item = values.optJSONObject(i); if (item == null) continue;
                    boolean citizen = "CITIZEN".equalsIgnoreCase(item.optString("author_role"));
                    thread.append(citizen ? "CITIZEN · " : "OFFICIAL · ").append(item.optString("author_name")).append("\n")
                            .append(item.optString("content"));
                    if (!item.optString("evidence_id").isEmpty()) thread.append("\n📷 Photograph supplied — open it from the evidence record");
                    thread.append("\n").append(item.optString("created_at")).append("\n\n");
                }
                TextInputEditText reply = new TextInputEditText(GovernmentSafetyDashboardActivity.this);
                reply.setHint("Message the citizen"); reply.setMinLines(2); reply.setMaxLines(5); reply.setPadding(48, 12, 48, 12);
                new MaterialAlertDialogBuilder(GovernmentSafetyDashboardActivity.this).setTitle("Citizen conversation")
                        .setMessage(thread.toString().trim()).setView(reply).setNegativeButton("Close", null)
                        .setPositiveButton("Send", (dialog, which) -> {
                            String content = reply.getText() == null ? "" : reply.getText().toString().trim();
                            if (content.isEmpty()) { Toast.makeText(GovernmentSafetyDashboardActivity.this, "Write a message first.", Toast.LENGTH_LONG).show(); return; }
                            api.createReportMessage(reportId, content, null, new ProductApiClient.ObjectCallback() {
                                @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> {
                                    Toast.makeText(GovernmentSafetyDashboardActivity.this, "Message sent to citizen", Toast.LENGTH_SHORT).show();
                                    showReportConversation(reportId);
                                }); }
                                @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
                            });
                        }).show();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
        });
    }

    private void showEvidencePicker(JSONArray evidence) {
        String[] labels = new String[evidence.length()];
        for (int i = 0; i < evidence.length(); i++) {
            JSONObject item = evidence.optJSONObject(i);
            labels[i] = item == null ? "Evidence" : item.optString("original_name", "Evidence") + " · " + item.optString("media_type");
        }
        new MaterialAlertDialogBuilder(this).setTitle("Secure evidence").setItems(labels, (dialog, which) -> {
            JSONObject item = evidence.optJSONObject(which); if (item == null) return;
            if (!item.optString("media_type").startsWith("image/")) {
                new MaterialAlertDialogBuilder(this).setTitle(item.optString("original_name"))
                        .setMessage("This video is preserved in authenticated evidence storage. Photograph preview is available in-app; video export should be handled through the authorised case system.")
                        .setPositiveButton("Close", null).show(); return;
            }
            api.downloadEvidence(item.optString("id"), new ProductApiClient.BytesCallback() {
                @Override public void onSuccess(byte[] bytes, String contentType) { runOnUiThread(() -> {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap == null) { Toast.makeText(GovernmentSafetyDashboardActivity.this, "The photograph could not be decoded.", Toast.LENGTH_LONG).show(); return; }
                    ImageView image = new ImageView(GovernmentSafetyDashboardActivity.this);
                    image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setPadding(20, 12, 20, 12); image.setImageBitmap(bitmap);
                    new MaterialAlertDialogBuilder(GovernmentSafetyDashboardActivity.this).setTitle(item.optString("original_name", "Evidence photograph"))
                            .setView(image).setPositiveButton("Close", null).show();
                }); }
                @Override public void onError(String message) { runOnUiThread(() -> Toast.makeText(GovernmentSafetyDashboardActivity.this, message, Toast.LENGTH_LONG).show()); }
            });
        }).setNegativeButton("Close", null).show();
    }

    private void openNewsSignals() {
        api.newsSignals(new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { runOnUiThread(() -> {
                if (values.length() == 0) {
                    MaterialAlertDialogBuilder emptyDialog = new MaterialAlertDialogBuilder(GovernmentSafetyDashboardActivity.this).setTitle("News intelligence")
                            .setMessage("No signals are waiting. Add permitted HTTPS RSS URLs to SAFE_NAVI_NEWS_FEEDS, then run ingestion. News is review-only and never changes routes automatically.")
                            .setNegativeButton("Close", null);
                    if (isAdmin()) emptyDialog.setNeutralButton("Schedule", (d,w) -> configureNewsSchedule())
                            .setPositiveButton("Check internet now", (d,w) -> ingestNews());
                    emptyDialog.show();
                    return;
                }
                String[] labels = new String[values.length()];
                for (int i = 0; i < values.length(); i++) { JSONObject item = values.optJSONObject(i); labels[i] = item.optString("category") + " · " + item.optString("headline"); }
                MaterialAlertDialogBuilder newsDialog = new MaterialAlertDialogBuilder(GovernmentSafetyDashboardActivity.this).setTitle("Live internet news signals")
                        .setItems(labels, (d, which) -> reviewNewsSignal(values.optJSONObject(which)))
                        .setNegativeButton("Close", null);
                if (isAdmin()) newsDialog.setNeutralButton("Schedule", (d,w) -> configureNewsSchedule())
                        .setPositiveButton("Check internet now", (d,w) -> ingestNews());
                newsDialog.show();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
        });
    }

    private void ingestNews() {
        api.ingestNews(new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> {
                Toast.makeText(GovernmentSafetyDashboardActivity.this,
                        value.optInt("inserted") + " signals added, " + value.optInt("deduplicated") + " duplicates skipped", Toast.LENGTH_LONG).show();
                openNewsSignals();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
        });
    }

    private boolean isAdmin() { return "admin".equals(ProductSession.role(this)); }

    private void configureNewsSchedule() {
        api.newsSettings(new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> {
                LinearLayout form = new LinearLayout(GovernmentSafetyDashboardActivity.this);
                form.setOrientation(LinearLayout.VERTICAL); form.setPadding(48, 8, 48, 0);
                TextView status = new TextView(GovernmentSafetyDashboardActivity.this);
                status.setText("Last internet check: " + value.optString("last_ingested_at", "Not run yet")
                        + "\nNext scheduled check: " + value.optString("next_ingestion_at", "Pending"));
                status.setPadding(0, 0, 0, 18);
                Spinner interval = spinner(new String[]{"Every 12 hours", "Every 24 hours"});
                interval.setSelection(value.optInt("refresh_hours", 12) == 24 ? 1 : 0);
                form.addView(status); form.addView(label("Detection frequency")); form.addView(interval);
                new MaterialAlertDialogBuilder(GovernmentSafetyDashboardActivity.this)
                        .setTitle("Live news detection").setView(form).setNegativeButton("Cancel", null)
                        .setPositiveButton("Save schedule", (dialog, which) -> {
                            int hours = interval.getSelectedItemPosition() == 1 ? 24 : 12;
                            api.updateNewsSettings(hours, new ProductApiClient.ObjectCallback() {
                                @Override public void onSuccess(JSONObject updated) { runOnUiThread(() -> Toast.makeText(
                                        GovernmentSafetyDashboardActivity.this,
                                        "News will be checked every " + updated.optInt("refresh_hours") + " hours",
                                        Toast.LENGTH_LONG).show()); }
                                @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
                            });
                        }).show();
            }); }
            @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
        });
    }

    private void reviewNewsSignal(JSONObject signal) {
        String message = signal.optString("summary") + "\n\nSource: " + signal.optString("publisher") + "\nPublished: "
                + signal.optString("published_at") + "\nCategory: " + signal.optString("category")
                + "\nConfidence: " + Math.round(signal.optDouble("confidence") * 100) + "%\nReview state: "
                + signal.optString("review_status") + "\n\nReference article\n" + signal.optString("source_url")
                + "\n\nThis signal cannot affect routes until a separate field-verified report creates a hazard.";
        new MaterialAlertDialogBuilder(this).setTitle(signal.optString("headline")).setMessage(message)
                .setNegativeButton("Close", null)
                .setNeutralButton("Open source", (d,w) -> openNewsSource(signal.optString("source_url")))
                .setPositiveButton("Review actions", (d,w) -> showNewsReviewActions(signal)).show();
    }

    private void openNewsSource(String sourceUrl) {
        Uri uri = Uri.parse(sourceUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            Toast.makeText(this, "Only HTTPS reference articles can be opened.", Toast.LENGTH_LONG).show(); return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (RuntimeException error) { Toast.makeText(this, "No browser is available to open this source.", Toast.LENGTH_LONG).show(); }
    }

    private void showNewsReviewActions(JSONObject signal) {
        String[] actions = {"Relevant intelligence", "Request field check", "Dismiss signal"};
        new MaterialAlertDialogBuilder(this).setTitle("Review news signal").setItems(actions, (dialog, which) -> {
            if (which == 0) api.reviewNews(signal.optString("id"), "RELEVANT", callback("Signal retained for intelligence"));
            else if (which == 1) api.reviewNews(signal.optString("id"), "NEEDS_FIELD_CHECK", callback("Field check requested"));
            else api.reviewNews(signal.optString("id"), "DISMISSED", callback("Signal dismissed"));
        }).setNegativeButton("Cancel", null).show();
    }

    private void renderHazards(JSONArray hazards) {
        int active = hazards == null ? 0 : hazards.length();
        int critical = 0;
        if (hazards != null) for (int i = 0; i < hazards.length(); i++) {
            JSONObject hazard = hazards.optJSONObject(i);
            if (hazard != null && hazard.optString("severity").equals("CRITICAL")) critical++;
        }
        activeCount.setText(String.valueOf(active)); criticalCount.setText(String.valueOf(critical));
    }

    private void triage() {
        if (currentReport == null) return;
        api.triage(currentReport.optString("id"), "Accepted into the official field-review queue",
                callback("Report assigned for review"));
    }

    private void showVerificationDialog() {
        if (currentReport == null) return;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(48, 8, 48, 0);
        Spinner severity = spinner(new String[]{"LOW", "MODERATE", "HIGH", "CRITICAL"}); severity.setSelection(2);
        Spinner road = spinner(new String[]{"SAFE", "CAUTION", "UNSAFE", "WORK_REQUIRED", "CLOSED"}); road.setSelection(2);
        TextInputEditText reason = new TextInputEditText(this); reason.setHint("Verification reason and field observation"); reason.setMinLines(3);
        TextInputEditText noGoRadius = new TextInputEditText(this);
        noGoRadius.setHint("No-go radius in metres (optional, 10–1000)");
        noGoRadius.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(label("Severity")); form.addView(severity); form.addView(label("Road status")); form.addView(road);
        form.addView(label("Routing exclusion buffer")); form.addView(noGoRadius); form.addView(reason);
        new MaterialAlertDialogBuilder(this).setTitle("Publish verified hazard").setView(form)
                .setNegativeButton("Cancel", null).setPositiveButton("Publish", (dialog, which) -> {
                    String note = reason.getText() == null ? "" : reason.getText().toString().trim();
                    if (note.length() < 3) { Toast.makeText(this, "Add a verification reason.", Toast.LENGTH_LONG).show(); return; }
                    try {
                        JSONObject payload = new JSONObject().put("decision", "verify")
                                .put("severity", severity.getSelectedItem().toString())
                                .put("road_status", road.getSelectedItem().toString())
                                .put("hazard_status", "ACTIVE").put("reason", note);
                        String radiusText = noGoRadius.getText() == null ? "" : noGoRadius.getText().toString().trim();
                        if (!radiusText.isEmpty()) {
                            double radius = Double.parseDouble(radiusText);
                            if (radius < 10 || radius > 1000) {
                                Toast.makeText(this, "No-go radius must be between 10 and 1000 metres.", Toast.LENGTH_LONG).show();
                                return;
                            }
                            payload.put("no_go_radius_m", radius);
                        }
                        api.decide(currentReport.optString("id"), payload, callback("Hazard published to citizen maps"));
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Enter a valid no-go radius.", Toast.LENGTH_LONG).show();
                    } catch (JSONException ignored) { }
                }).show();
    }

    private void reject() {
        if (currentReport == null) return;
        new MaterialAlertDialogBuilder(this).setTitle("Reject this report?")
                .setMessage("The decision is audited and the report will not affect public routing.")
                .setNegativeButton("Cancel", null).setPositiveButton("Reject", (dialog, which) -> {
                    try { api.decide(currentReport.optString("id"), new JSONObject().put("decision", "reject")
                            .put("reason", "Insufficient evidence after official review"), callback("Report rejected")); }
                    catch (JSONException ignored) { }
                }).show();
    }

    private ProductApiClient.ObjectCallback callback(String success) {
        return new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { runOnUiThread(() -> { Toast.makeText(GovernmentSafetyDashboardActivity.this, success, Toast.LENGTH_SHORT).show(); refresh(); }); }
            @Override public void onError(String message) { runOnUiThread(() -> showError(message)); }
        };
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); return spinner;
    }
    private TextView label(String text) { TextView view = new TextView(this); view.setText(text); view.setPadding(0, 18, 0, 4); return view; }
    private void setActions(boolean enabled) { triageButton.setEnabled(enabled); verifyButton.setEnabled(enabled); rejectButton.setEnabled(enabled); assignButton.setEnabled(enabled); requestInfoButton.setEnabled(enabled); auditButton.setEnabled(enabled); }
    private void showError(String message) { reportTitle.setText("Operations unavailable"); reportMeta.setText(message); setActions(false); }
}
