package com.example.xavierproject;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.xavierproject.safety.demo.SafetyDemoStore;
import com.example.xavierproject.safety.model.CitizenReport;
import com.example.xavierproject.safety.model.Hazard;
import com.example.xavierproject.safety.model.HazardLocation;
import com.example.xavierproject.safety.model.HazardLocationType;
import com.example.xavierproject.safety.model.HazardSeverity;
import com.example.xavierproject.safety.model.HazardStatus;
import com.example.xavierproject.safety.model.UserRole;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Locale;

public class GovernmentSafetyDashboardActivity extends AppCompatActivity {
    private TextView pendingCount;
    private TextView activeCount;
    private TextView criticalCount;
    private TextView reportStatus;
    private TextView auditCount;
    private MaterialButton verifyButton;
    private MaterialButton moderateButton;
    private MaterialButton resolveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_government_safety_dashboard);
        pendingCount = findViewById(R.id.pendingCount);
        activeCount = findViewById(R.id.activeCount);
        criticalCount = findViewById(R.id.criticalCount);
        reportStatus = findViewById(R.id.pendingReportStatus);
        auditCount = findViewById(R.id.auditCount);
        verifyButton = findViewById(R.id.verifyReportButton);
        moderateButton = findViewById(R.id.reduceSeverityButton);
        resolveButton = findViewById(R.id.resolveHazardButton);

        findViewById(R.id.govBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.openGovernmentMapButton)
                .setOnClickListener(v -> startActivity(new Intent(this, MapsActivity.class)));
        verifyButton.setOnClickListener(v -> confirmVerify());
        moderateButton.setOnClickListener(v -> updateFlooding(HazardSeverity.MODERATE, HazardStatus.MONITORING,
                "Water level reduced after drainage response."));
        resolveButton.setOnClickListener(v -> updateFlooding(HazardSeverity.LOW, HazardStatus.RESOLVED,
                "Road inspected and reopened; immediate warning removed."));
        findViewById(R.id.resetDemoButton).setOnClickListener(v -> {
            SafetyDemoStore.reset();
            refresh();
            Toast.makeText(this, "Synthetic workflow reset.", Toast.LENGTH_SHORT).show();
        });
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingCount != null) refresh();
    }

    private void confirmVerify() {
        CitizenReport report = SafetyDemoStore.repository().findReport("demo-report-flooding");
        if (report == null || report.getStatus() == HazardStatus.VERIFIED) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Publish verified hazard?")
                .setMessage("Category: Flooding\nSeverity: HIGH\nStatus: ACTIVE\nGeometry: Road segment\nReason: Heavy waterlogging after rainfall\n\nThis is synthetic demo data.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Verify and publish", (dialog, which) -> verifyReport(report))
                .show();
    }

    private void verifyReport(CitizenReport report) {
        Hazard draft = new Hazard();
        draft.setId("demo-verified-sion-flood");
        draft.setTitle("Verified flooding on Sion service road");
        draft.setCategoryKey("FLOODING");
        draft.setDescription(report.getDescription());
        draft.setReason("Heavy waterlogging after rainfall");
        draft.setLocation(new HazardLocation(HazardLocationType.ROAD_SEGMENT,
                report.getLocation().getLatitude(), report.getLocation().getLongitude(),
                report.getLocation().getDisplayName()));
        draft.setSeverity(HazardSeverity.HIGH);
        draft.setStatus(HazardStatus.ACTIVE);
        draft.setSynthetic(true);
        SafetyDemoStore.workflow().verifyReport(report.getId(), draft,
                "demo-government", UserRole.GOVERNMENT, System.currentTimeMillis());
        refresh();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Hazard published")
                .setMessage("The report is now government verified. The map and risk engine have been recalculated, and an audit entry was preserved.")
                .setPositiveButton("View map", (dialog, which) ->
                        startActivity(new Intent(this, MapsActivity.class)))
                .setNegativeButton("Stay here", null)
                .show();
    }

    private void updateFlooding(HazardSeverity severity, HazardStatus status, String reason) {
        Hazard hazard = SafetyDemoStore.repository().findHazard("demo-flooding");
        if (hazard == null) return;
        try {
            SafetyDemoStore.workflow().updateHazard(hazard.getId(), severity, status,
                    "demo-government", reason, UserRole.GOVERNMENT, System.currentTimeMillis());
            refresh();
            Toast.makeText(this, "Hazard changed to " + status.name(), Toast.LENGTH_SHORT).show();
        } catch (RuntimeException exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void refresh() {
        int pending = 0;
        for (CitizenReport report : SafetyDemoStore.repository().getReports()) {
            if (report.getStatus() == HazardStatus.REPORTED || report.getStatus() == HazardStatus.UNDER_REVIEW) {
                pending++;
            }
        }
        int active = 0;
        int critical = 0;
        for (Hazard hazard : SafetyDemoStore.repository().getHazards()) {
            if (hazard.getStatus().affectsImmediateNavigation()) active++;
            if (hazard.getStatus().affectsImmediateNavigation()
                    && hazard.getSeverity() == HazardSeverity.CRITICAL) critical++;
        }
        pendingCount.setText(String.valueOf(pending));
        activeCount.setText(String.valueOf(active));
        criticalCount.setText(String.valueOf(critical));
        auditCount.setText(String.format(Locale.US, "%d recorded actions",
                SafetyDemoStore.repository().getHistory(null).size()));

        CitizenReport report = SafetyDemoStore.repository().findReport("demo-report-flooding");
        if (report == null) {
            reportStatus.setText("No pending synthetic report");
            verifyButton.setEnabled(false);
        } else {
            reportStatus.setText(report.getStatus().name().replace('_', ' '));
            verifyButton.setEnabled(report.getStatus() != HazardStatus.VERIFIED);
        }
        Hazard flooding = SafetyDemoStore.repository().findHazard("demo-flooding");
        boolean editable = flooding != null && flooding.getStatus() != HazardStatus.RESOLVED
                && flooding.getStatus() != HazardStatus.CLOSED;
        moderateButton.setEnabled(editable);
        resolveButton.setEnabled(editable);
    }
}
