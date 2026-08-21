package com.safenavi.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.safenavi.app.safety.demo.SafetyDemoStore;
import com.safenavi.app.safety.model.CitizenReport;
import com.safenavi.app.safety.model.HazardLocation;
import com.safenavi.app.safety.model.HazardLocationType;
import com.safenavi.app.safety.model.HazardStatus;
import java.util.UUID;

public class DemoReportFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_demo_report, container, false);
        EditText title = view.findViewById(R.id.demoReportTitle);
        EditText description = view.findViewById(R.id.demoReportDescription);
        Spinner category = view.findViewById(R.id.demoReportCategory);
        TextView location = view.findViewById(R.id.demoReportLocation);
        view.findViewById(R.id.submitDemoReportButton).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty()
                    || description.getText().toString().trim().isEmpty()) {
                Toast.makeText(requireContext(), "Add a title and description.", Toast.LENGTH_SHORT).show();
                return;
            }
            long now = System.currentTimeMillis();
            CitizenReport report = new CitizenReport();
            report.setId("demo-" + UUID.randomUUID());
            report.setCitizenId("demo-citizen");
            report.setTitle(title.getText().toString().trim());
            report.setDescription(description.getText().toString().trim());
            report.setCategoryKey(String.valueOf(category.getSelectedItem()).toUpperCase().replace(' ', '_'));
            report.setLocation(new HazardLocation(HazardLocationType.POINT,
                    19.0425, 72.8636, location.getText().toString()));
            report.setStatus(HazardStatus.REPORTED);
            report.setSynthetic(true);
            report.setCreatedAt(now);
            report.setUpdatedAt(now);
            SafetyDemoStore.repository().saveReport(report);
            title.setText("");
            description.setText("");
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Report submitted")
                    .setMessage("Your synthetic report is now in the government review queue. It is not an official hazard until an authorized reviewer verifies it.")
                    .setPositiveButton("Done", null)
                    .show();
        });
        return view;
    }
}
