package com.example.xavierproject;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import android.location.Address;
import android.location.Geocoder;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ComplaintDetailActivity extends AppCompatActivity {

    private static final String TAG = "ComplaintDetail";
    private TextView titleTextView, categoryTextView, descriptionTextView, locationTextView;
    private ImageView complaintImageView;
    private Button btnPending, btnAcknowledged, btnOngoing, btnResolved, btnViewOnMap;
    private ProgressBar imageProgressBar;

    private String reportId;
    private String currentStatus;
    private double latitude;
    private double longitude;
    private DatabaseReference reportRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_complaint_detail);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Complaint Details");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        initializeViews();
        loadComplaintData();
        setupStatusButtons();
    }

    private void initializeViews() {
        titleTextView = findViewById(R.id.detailTitleTextView);
        categoryTextView = findViewById(R.id.detailCategoryTextView);
        descriptionTextView = findViewById(R.id.detailDescriptionTextView);
        locationTextView = findViewById(R.id.detailLocationTextView);
        complaintImageView = findViewById(R.id.detailImageView);
        imageProgressBar = findViewById(R.id.imageProgressBar);

        btnPending = findViewById(R.id.btnPending);
        btnAcknowledged = findViewById(R.id.btnAcknowledged);
        btnOngoing = findViewById(R.id.btnOngoing);
        btnResolved = findViewById(R.id.btnResolved);
        btnViewOnMap = findViewById(R.id.btnViewOnMap);
    }

    private void loadComplaintData() {
        // Get data from intent
        reportId = getIntent().getStringExtra("REPORT_ID");
        String title = getIntent().getStringExtra("TITLE");
        String category = getIntent().getStringExtra("CATEGORY");
        String description = getIntent().getStringExtra("DESCRIPTION");
        String imageUrl = getIntent().getStringExtra("IMAGE_URL");
        currentStatus = getIntent().getStringExtra("STATUS");
        latitude = getIntent().getDoubleExtra("LATITUDE", 0.0);
        longitude = getIntent().getDoubleExtra("LONGITUDE", 0.0);

        // Set data to views
        titleTextView.setText(title != null ? title : "Untitled");
        categoryTextView.setText(category != null ? category : "N/A");
        descriptionTextView.setText(description != null ? description : "No description available");

        // Get location from coordinates
        if (latitude != 0.0 && longitude != 0.0) {
            getAddressFromCoordinates(latitude, longitude);
            btnViewOnMap.setEnabled(true);
        } else {
            locationTextView.setText("Location not available");
            btnViewOnMap.setEnabled(false);
        }

        // Load image using Glide
        if (imageUrl != null && !imageUrl.isEmpty()) {
            imageProgressBar.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(imageUrl)
                    .placeholder(R.drawable.ic_launcher_background)
                    .error(R.drawable.ic_launcher_background)
                    .into(complaintImageView);
            imageProgressBar.setVisibility(View.GONE);
        } else {
            complaintImageView.setImageResource(R.drawable.ic_launcher_background);
        }

        // Initialize Firebase reference
        reportRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("reports").child(reportId);

        // Highlight current status
        updateStatusButtons(currentStatus);
    }

    private void getAddressFromCoordinates(double latitude, double longitude) {
        // Show loading state
        locationTextView.setText("Loading location...");

        // Run geocoding in background thread
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);

                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);

                    // Build location string
                    StringBuilder locationBuilder = new StringBuilder();

                    // Add address line
                    if (address.getSubLocality() != null) {
                        locationBuilder.append(address.getSubLocality()).append(", ");
                    } else if (address.getLocality() != null) {
                        locationBuilder.append(address.getLocality()).append(", ");
                    }

                    // Add city
                    if (address.getLocality() != null && address.getSubLocality() != null) {
                        locationBuilder.append(address.getLocality()).append(", ");
                    }

                    // Add state
                    if (address.getAdminArea() != null) {
                        locationBuilder.append(address.getAdminArea());
                    }

                    String locationText = locationBuilder.toString();
                    if (locationText.isEmpty()) {
                        locationText = latitude + ", " + longitude;
                    }

                    final String finalLocation = locationText;

                    // Update UI on main thread
                    runOnUiThread(() -> locationTextView.setText(finalLocation));

                } else {
                    // If geocoding fails, show coordinates
                    runOnUiThread(() -> locationTextView.setText(latitude + ", " + longitude));
                }

            } catch (IOException e) {
                Log.e(TAG, "Geocoding error: " + e.getMessage());
                // Show coordinates if geocoding fails
                runOnUiThread(() -> locationTextView.setText(latitude + ", " + longitude));
            }
        }).start();
    }

    private void setupStatusButtons() {
        btnPending.setOnClickListener(v -> updateStatus("pending"));
        btnAcknowledged.setOnClickListener(v -> updateStatus("acknowledged"));
        btnOngoing.setOnClickListener(v -> updateStatus("ongoing"));
        btnResolved.setOnClickListener(v -> updateStatus("resolved"));

        // View on Map button
        btnViewOnMap.setOnClickListener(v -> openLocationInMaps());
    }

    private void openLocationInMaps() {
        if (latitude != 0.0 && longitude != 0.0) {
            // Create URI for Google Maps
            Uri gmmIntentUri = Uri.parse("geo:" + latitude + "," + longitude + "?q=" + latitude + "," + longitude);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");

            // Check if Google Maps is installed
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // If Google Maps not installed, open in browser
                Uri browserUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + latitude + "," + longitude);
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
                startActivity(browserIntent);
            }
        } else {
            Toast.makeText(this, "Location coordinates not available", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus(String newStatus) {
        // Update status in Firebase
        reportRef.child("status").setValue(newStatus)
                .addOnSuccessListener(aVoid -> {
                    currentStatus = newStatus;
                    updateStatusButtons(newStatus);
                    Toast.makeText(ComplaintDetailActivity.this,
                            "Status updated to: " + newStatus,
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(ComplaintDetailActivity.this,
                            "Failed to update status: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updateStatusButtons(String status) {
        // Reset all buttons to default state
        btnPending.setBackgroundTintList(getColorStateList(R.color.button_default));
        btnAcknowledged.setBackgroundTintList(getColorStateList(R.color.button_default));
        btnOngoing.setBackgroundTintList(getColorStateList(R.color.button_default));
        btnResolved.setBackgroundTintList(getColorStateList(R.color.button_default));

        // Highlight selected status
        if (status != null) {
            switch (status.toLowerCase()) {
                case "pending":
                    btnPending.setBackgroundTintList(getColorStateList(R.color.status_pending));
                    break;
                case "acknowledged":
                    btnAcknowledged.setBackgroundTintList(getColorStateList(R.color.accent));
                    break;
                case "ongoing":
                    btnOngoing.setBackgroundTintList(getColorStateList(R.color.status_in_progress));
                    break;
                case "resolved":
                    btnResolved.setBackgroundTintList(getColorStateList(R.color.status_resolved));
                    break;
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
