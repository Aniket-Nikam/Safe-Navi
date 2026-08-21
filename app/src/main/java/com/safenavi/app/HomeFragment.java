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
import com.safenavi.app.safety.demo.SafetyDemoStore;
import com.safenavi.app.safety.demo.DemoSession;
import com.safenavi.app.safety.model.SafetyScore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class HomeFragment extends Fragment {
    private TextView welcomeTextView;
    private TextView safetyScoreText;
    private TextView riskLevelText;
    private TextView safetyExplanationText;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        welcomeTextView = view.findViewById(R.id.welcomeTextView);
        safetyScoreText = view.findViewById(R.id.safetyScoreText);
        riskLevelText = view.findViewById(R.id.riskLevelText);
        safetyExplanationText = view.findViewById(R.id.safetyExplanationText);
        view.findViewById(R.id.openSafetyMapButton)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), MapsActivity.class)));
        view.findViewById(R.id.openCommunityButton)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), DiscussionActivity.class)));
        view.findViewById(R.id.openAssistantButton)
                .setOnClickListener(v -> requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new ChatbotFragment())
                        .addToBackStack("assistant")
                        .commit());
        view.findViewById(R.id.reportDangerButton)
                .setOnClickListener(v -> ((MainActivity) requireActivity()).openReport());
        view.findViewById(R.id.demoDisclosureButton)
                .setOnClickListener(v -> new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Synthetic demonstration data")
                        .setMessage("The hazards and scores shown in demo mode are synthetic. They demonstrate the complete Safe-Navi workflow and are not claims about current real-world safety.")
                        .setPositiveButton("Understood", null)
                        .show());
        loadUser();
        renderSafetySummary();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (safetyScoreText != null) renderSafetySummary();
    }

    private void loadUser() {
        FirebaseUser user = DemoSession.isActive() || !BuildConfig.HAS_FIREBASE_CONFIG
                ? null : FirebaseAuth.getInstance().getCurrentUser();
        String name = user == null ? "Citizen" : user.getDisplayName();
        if (name == null || name.trim().isEmpty()) name = "Citizen";
        welcomeTextView.setText("Good evening, " + name);
    }

    private void renderSafetySummary() {
        SafetyScore score = SafetyDemoStore.riskEngine().calculate(
                19.0657, 72.9986, System.currentTimeMillis(),
                SafetyDemoStore.repository().getHazards());
        safetyScoreText.setText(String.valueOf(Math.round(score.getSafetyScore())));
        riskLevelText.setText(readable(score.getRiskLevel().name()) + " · "
                + score.getConfidence().name() + " confidence");
        safetyExplanationText.setText(score.getExplanations().get(0));
    }

    private String readable(String value) {
        String lower = value.toLowerCase().replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
