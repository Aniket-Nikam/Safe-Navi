package com.safenavi.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safenavi.app.product.ProductSession;
import com.safenavi.app.safety.model.DatasetPointRisk;
import com.safenavi.app.safety.network.DatasetRiskService;
import java.util.Calendar;
import java.util.Locale;

public class HomeFragment extends Fragment {
    private TextView safetyScoreText, riskLevelText, safetyExplanationText;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                  @Nullable ViewGroup container,
                                                  @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        ((TextView) view.findViewById(R.id.welcomeTextView)).setText("Good evening, " + ProductSession.name(requireContext()));
        safetyScoreText = view.findViewById(R.id.safetyScoreText);
        riskLevelText = view.findViewById(R.id.riskLevelText);
        safetyExplanationText = view.findViewById(R.id.safetyExplanationText);
        view.findViewById(R.id.openSafetyMapButton).setOnClickListener(v -> startActivity(new Intent(requireContext(), MapsActivity.class)));
        view.findViewById(R.id.openCommunityButton).setOnClickListener(v -> ((MainActivity) requireActivity()).openCommunity());
        view.findViewById(R.id.openAssistantButton).setOnClickListener(v -> ((MainActivity) requireActivity()).openAssistant());
        view.findViewById(R.id.reportDangerButton).setOnClickListener(v -> ((MainActivity) requireActivity()).openReport());
        view.findViewById(R.id.scoreDisclosureButton).setOnClickListener(v -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle("How the score works")
                .setMessage("Safe-Navi combines the baseline road-risk dataset with active government-verified hazards. Citizen reports remain private to the review workflow until an authorized official verifies them.")
                .setPositiveButton("Got it", null).show());
        renderSafetySummary();
        return view;
    }

    @Override public void onResume() { super.onResume(); if (safetyScoreText != null) renderSafetySummary(); }

    private void renderSafetySummary() {
        riskLevelText.setText("Refreshing live intelligence…");
        new DatasetRiskService().pointRisk(19.0657, 72.9986, currentPeriod(), new DatasetRiskService.ResultCallback<DatasetPointRisk>() {
            @Override public void onSuccess(DatasetPointRisk risk) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    safetyScoreText.setText(String.valueOf(Math.round(risk.getSafetyScore())));
                    riskLevelText.setText(readable(risk.getRiskLabel()) + " · " + risk.getAreaName());
                    safetyExplanationText.setText("Baseline road intelligence and current verified hazards · " + readable(risk.getTimePeriod()));
                });
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> { safetyScoreText.setText("—"); riskLevelText.setText("Live service unavailable"); safetyExplanationText.setText(message); });
            }
        });
    }

    private String currentPeriod() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 7 && hour < 11) return "morning_peak";
        if (hour >= 11 && hour < 17) return "midday";
        if (hour >= 17 && hour < 22) return "evening_peak";
        return "night";
    }
    private String readable(String value) { String lower = value.toLowerCase(Locale.US).replace('_', ' '); return lower.isEmpty() ? "Unknown" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1); }
}
