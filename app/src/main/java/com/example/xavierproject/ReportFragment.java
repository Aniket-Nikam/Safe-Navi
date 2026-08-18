package com.example.xavierproject;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class ReportFragment extends Fragment implements OnMapReadyCallback {

    private EditText editTextTitle, editTextDescription;
    private AutoCompleteTextView autoCompleteCategory;
    private ImageView imageViewPreview;
    private Button buttonSelectImage, buttonSubmitReport, buttonViewHistory;
    private ProgressBar progressBar;
    private TextView textViewUploadProgress, textViewLocationInfo;

    private Uri selectedImageUri;
    private DatabaseReference databaseReference;
    private FirebaseAuth mAuth;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Location currentLocation;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    private static final String UPLOAD_PRESET = "XavierProject";
    private static final float DEFAULT_ZOOM = 15f;

    // Category options
    private static final String[] CATEGORIES = {
            "Road", "Water", "Garbage", "Streetlight", "Drainage",
            "Electricity", "Park", "Public Property", "Other"
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("reports");

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Initialize image picker launcher
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            displaySelectedImage();
                        } else {
                            Toast.makeText(getContext(), "Failed to get image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // Initialize camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            displaySelectedImage();
                        }
                    }
                }
        );

        // Initialize location permission launcher
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean fineLocationGranted = permissions.getOrDefault(
                            Manifest.permission.ACCESS_FINE_LOCATION, false);
                    boolean coarseLocationGranted = permissions.getOrDefault(
                            Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (fineLocationGranted || coarseLocationGranted) {
                        getCurrentLocation();
                    } else {
                        Toast.makeText(getContext(),
                                "Location permission required to submit Complaint",
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_report, container, false);

        initializeViews(view);
        setupCategoryDropdown();
        setupClickListeners();
        setupMap();

        return view;
    }

    private void initializeViews(View view) {
        editTextTitle = view.findViewById(R.id.editTextTitle);
        editTextDescription = view.findViewById(R.id.editTextDescription);
        autoCompleteCategory = view.findViewById(R.id.autoCompleteCategory);
        imageViewPreview = view.findViewById(R.id.imageViewPreview);
        buttonSelectImage = view.findViewById(R.id.buttonSelectImage);
        buttonSubmitReport = view.findViewById(R.id.buttonSubmitReport);
        buttonViewHistory = view.findViewById(R.id.buttonViewHistory);
        progressBar = view.findViewById(R.id.progressBar);
        textViewUploadProgress = view.findViewById(R.id.textViewUploadProgress);
        textViewLocationInfo = view.findViewById(R.id.textViewLocationInfo);
    }

    private void setupCategoryDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                CATEGORIES
        );
        autoCompleteCategory.setAdapter(adapter);
    }

    private void setupClickListeners() {
        buttonSelectImage.setOnClickListener(v -> showImageSourceDialog());
        buttonSubmitReport.setOnClickListener(v -> submitReport());
        buttonViewHistory.setOnClickListener(v -> navigateToHistory());
    }

    /**
     * Navigate to history fragment/activity
     */
    private void navigateToHistory() {
        // Replace current fragment with HistoryFragment
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new HistoryFragment())
                .addToBackStack(null)
                .commit();
    }

    /**
     * Setup Google Map
     */
    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);

        // Request location permission and get current location
        checkLocationPermission();
    }

    /**
     * Check and request location permission
     */
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    /**
     * Get current location
     */
    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (googleMap != null) {
            googleMap.setMyLocationEnabled(true);
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        currentLocation = location;
                        updateLocationUI(location);
                    } else {
                        Toast.makeText(getContext(),
                                "Getting current location...",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(),
                            "Failed to get location: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Update UI with location information
     */
    private void updateLocationUI(Location location) {
        if (location == null || !isAdded()) return;

        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Update map
        if (googleMap != null) {
            googleMap.clear();
            googleMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("Current Location"));
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, DEFAULT_ZOOM));
        }

        // Update location text
        String locationText = String.format("Lat: %.6f, Lng: %.6f",
                location.getLatitude(), location.getLongitude());
        textViewLocationInfo.setText(locationText);
        textViewLocationInfo.setVisibility(View.VISIBLE);
    }

    /**
     * Show dialog to choose between camera and gallery
     */
    private void showImageSourceDialog() {
        String[] options = {"Take Photo", "Choose from Gallery"};
        androidx.appcompat.app.AlertDialog.Builder builder =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext());
        builder.setTitle("Select Image Source");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                openCamera();
            } else {
                openImagePicker();
            }
        });
        builder.show();
    }

    /**
     * Open camera to take photo
     */
    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            cameraLauncher.launch(intent);
        } else {
            Toast.makeText(getContext(), "Camera not available", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Open gallery to pick image
     */
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    /**
     * Display the selected image in preview
     */
    private void displaySelectedImage() {
        if (selectedImageUri != null && isAdded()) {
            Glide.with(this)
                    .load(selectedImageUri)
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_error_image)
                    .into(imageViewPreview);
            imageViewPreview.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Submit Complaint with validation
     */
    private void submitReport() {
        String title = editTextTitle.getText().toString().trim();
        String category = autoCompleteCategory.getText().toString().trim();
        String description = editTextDescription.getText().toString().trim();

        if (!validateInputs(title, category, description)) {
            return;
        }

        if (selectedImageUri == null) {
            Toast.makeText(getContext(), "Please select an image", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentLocation == null) {
            Toast.makeText(getContext(),
                    "Location not available. Please wait or enable location services",
                    Toast.LENGTH_LONG).show();
            checkLocationPermission();
            return;
        }

        showLoading(true);
        uploadImageToCloudinary(title, category, description);
    }

    /**
     * Validate user inputs
     */
    private boolean validateInputs(String title, String category, String description) {
        if (TextUtils.isEmpty(title)) {
            editTextTitle.setError("Title is required");
            editTextTitle.requestFocus();
            return false;
        }

        if (title.length() < 3) {
            editTextTitle.setError("Title must be at least 3 characters");
            editTextTitle.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(category)) {
            autoCompleteCategory.setError("Category is required");
            autoCompleteCategory.requestFocus();
            return false;
        }

        if (TextUtils.isEmpty(description)) {
            editTextDescription.setError("Description is required");
            editTextDescription.requestFocus();
            return false;
        }

        if (description.length() < 10) {
            editTextDescription.setError("Description must be at least 10 characters");
            editTextDescription.requestFocus();
            return false;
        }

        return true;
    }

    /**
     * Upload image to Cloudinary
     */
    private void uploadImageToCloudinary(String title, String category, String description) {
        if (getContext() == null) {
            showLoading(false);
            Toast.makeText(requireContext(), "Error: Context not available", Toast.LENGTH_SHORT).show();
            return;
        }

        CloudinaryHelper.uploadImage(requireContext(), selectedImageUri, UPLOAD_PRESET,
                new CloudinaryHelper.CloudinaryUploadCallback() {
                    @Override
                    public void onStart() {
                        if (isAdded()) {
                            updateUploadProgress("Preparing image...");
                        }
                    }

                    @Override
                    public void onProgress(int progress) {
                        if (isAdded()) {
                            String progressText = progress < 70 ? "Uploading: " + progress + "%" :
                                    progress < 90 ? "Processing: " + progress + "%" :
                                            "Finalizing: " + progress + "%";
                            updateUploadProgress(progressText);
                        }
                    }

                    @Override
                    public void onSuccess(String imageUrl, String publicId) {
                        if (isAdded()) {
                            updateUploadProgress("Upload complete! Saving Complaint...");
                            saveReportToFirebase(title, category, description, imageUrl, publicId);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (isAdded()) {
                            showLoading(false);
                            Toast.makeText(getContext(), "Upload failed: " + error,
                                    Toast.LENGTH_LONG).show();
                            updateUploadProgress("");
                        }
                    }
                }
        );
    }

    /**
     * Save Complaint data to Firebase
     */
    private void saveReportToFirebase(String title, String category, String description,
                                      String imageUrl, String publicId) {
        String reportId = databaseReference.push().getKey();
        String userId = mAuth.getCurrentUser() != null ?
                mAuth.getCurrentUser().getUid() : "anonymous";

        if (reportId == null) {
            showLoading(false);
            Toast.makeText(getContext(), "Failed to generate Complaint ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("reportId", reportId);
        reportData.put("userId", userId);
        reportData.put("title", title);
        reportData.put("category", category);
        reportData.put("description", description);
        reportData.put("imageUrl", imageUrl);
        reportData.put("imagePublicId", publicId);
        reportData.put("thumbnailUrl", CloudinaryHelper.getThumbnailUrl(publicId));
        reportData.put("timestamp", System.currentTimeMillis());
        reportData.put("status", "pending");

        // Add location data
        if (currentLocation != null) {
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("latitude", currentLocation.getLatitude());
            locationData.put("longitude", currentLocation.getLongitude());
            locationData.put("accuracy", currentLocation.getAccuracy());
            reportData.put("location", locationData);
        }

        databaseReference.child(reportId).setValue(reportData)
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) {
                        showLoading(false);
                        Toast.makeText(getContext(),
                                "Complaint submitted successfully!",
                                Toast.LENGTH_SHORT).show();
                        clearForm();

                        // Show option to view history
                        showSuccessDialog();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        showLoading(false);
                        Toast.makeText(getContext(),
                                "Failed to save Complaint: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Show success dialog with option to view history
     */
    private void showSuccessDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Success!")
                .setMessage("Your Complaint has been submitted successfully. Would you like to view your Complaint history?")
                .setPositiveButton("View History", (dialog, which) -> navigateToHistory())
                .setNegativeButton("Submit Another", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }

    /**
     * Show/hide loading state
     */
    private void showLoading(boolean show) {
        if (!isAdded()) return;

        if (show) {
            progressBar.setVisibility(View.VISIBLE);
            textViewUploadProgress.setVisibility(View.VISIBLE);
            buttonSubmitReport.setEnabled(false);
            buttonSelectImage.setEnabled(false);
            buttonViewHistory.setEnabled(false);
            editTextTitle.setEnabled(false);
            autoCompleteCategory.setEnabled(false);
            editTextDescription.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            textViewUploadProgress.setVisibility(View.GONE);
            textViewUploadProgress.setText("");
            buttonSubmitReport.setEnabled(true);
            buttonSelectImage.setEnabled(true);
            buttonViewHistory.setEnabled(true);
            editTextTitle.setEnabled(true);
            autoCompleteCategory.setEnabled(true);
            editTextDescription.setEnabled(true);
        }
    }

    /**
     * Update upload progress message
     */
    private void updateUploadProgress(String message) {
        if (isAdded() && textViewUploadProgress != null) {
            textViewUploadProgress.setText(message);
        }
    }

    /**
     * Clear form after successful submission
     */
    private void clearForm() {
        if (!isAdded()) return;

        editTextTitle.setText("");
        autoCompleteCategory.setText("");
        editTextDescription.setText("");
        imageViewPreview.setImageDrawable(null);
        imageViewPreview.setVisibility(View.GONE);
        selectedImageUri = null;
        textViewUploadProgress.setText("");

        // Reset map to current location
        if (currentLocation != null) {
            updateLocationUI(currentLocation);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        selectedImageUri = null;
    }
}
