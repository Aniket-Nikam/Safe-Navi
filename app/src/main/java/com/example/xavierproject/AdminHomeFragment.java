package com.example.xavierproject;

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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import de.hdodenhof.circleimageview.CircleImageView;

public class AdminHomeFragment extends Fragment {

    private FirebaseAuth mAuth;
    private TextView welcomeTextView, subtitleTextView;
    private CircleImageView profileImageView;
    private View mapsContainer;
    private View complaintBtn;
    private View manageUsersBtn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_home, container, false);

        mAuth = FirebaseAuth.getInstance();

        initializeViews(view);
        loadUserData();
        setupClickListeners();

        return view;
    }

    private void initializeViews(View view) {
        welcomeTextView = view.findViewById(R.id.welcomeTextView);
        subtitleTextView = view.findViewById(R.id.subtitleTextView);
        profileImageView = view.findViewById(R.id.profileImageView);
        mapsContainer = view.findViewById(R.id.maps_container);
        complaintBtn = view.findViewById(R.id.complaint_btn);
        manageUsersBtn = view.findViewById(R.id.manage_users_btn);
    }

    private void setupClickListeners() {
        mapsContainer.setOnClickListener(v -> showCitySelector("maps"));
        complaintBtn.setOnClickListener(v -> showCitySelector("complaints"));
        if (manageUsersBtn != null) {
            manageUsersBtn.setOnClickListener(v -> {
                Intent intent = new Intent(getActivity(), ManageUsersActivity.class);
                startActivity(intent);
            });
        }
    }

    private void showCitySelector(String actionType) {
        String[] cities = {"Mumbai", "Delhi", "Bangalore", "Hyderabad", "Chennai", "Kolkata", "Pune", "Ahmedabad"};

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        builder.setTitle("Select City");
        builder.setItems(cities, (dialog, which) -> {
            String selectedCity = cities[which];
            if (selectedCity.equals("Mumbai")) {
                if (actionType.equals("maps")) {
                    Intent intent = new Intent(getActivity(), MapsActivity.class);
                    intent.putExtra("CITY_NAME", selectedCity);
                    startActivity(intent);
                } else if (actionType.equals("complaints")) {
                    Intent intent = new Intent(getActivity(), ComplaintsActivity.class);
                    intent.putExtra("CITY_NAME", selectedCity);
                    startActivity(intent);
                }
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Coming Soon")
                        .setMessage(selectedCity + " is currently not available. We're working on it!")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String displayName = user.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                welcomeTextView.setText("Hello, " + displayName + "!");
            } else {
                welcomeTextView.setText("Hello Admin!");
            }

            String email = user.getEmail();
            if (email != null) {
                subtitleTextView.setText(email);
            }
        }
    }
}
