package com.example.xavierproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView textViewEmpty;
    private ReportAdapter reportAdapter;
    private List<Complaint> reportList;

    private DatabaseReference databaseReference;
    private FirebaseAuth mAuth;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("reports");
        reportList = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        initializeViews(view);
        setupRecyclerView();
        loadReports();

        return view;
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewHistory);
        progressBar = view.findViewById(R.id.progressBarHistory);
        textViewEmpty = view.findViewById(R.id.textViewEmpty);
    }

    private void setupRecyclerView() {
        reportAdapter = new ReportAdapter(reportList, requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(reportAdapter);
    }

    /**
     * Load reports from Firebase
     */
    private void loadReports() {
        if (mAuth.getCurrentUser() == null) {
            showEmpty("Please sign in to view your reports");
            return;
        }

        showLoading(true);
        String userId = mAuth.getCurrentUser().getUid();

        // Query reports for current user, ordered by timestamp (newest first)
        Query query = databaseReference
                .orderByChild("userId")
                .equalTo(userId);

        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reportList.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        Complaint Complaint = dataSnapshot.getValue(Complaint.class);
                        if (Complaint != null) {
                            reportList.add(Complaint);
                        }
                    }

                    // Sort by timestamp (newest first)
                    Collections.sort(reportList, (r1, r2) ->
                            Long.compare(r2.getTimestamp(), r1.getTimestamp()));

                    reportAdapter.notifyDataSetChanged();
                    showContent();
                } else {
                    showEmpty("No reports found. Submit your first Complaint!");
                }

                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Toast.makeText(getContext(),
                        "Failed to load reports: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
                showEmpty("Failed to load reports. Please try again.");
            }
        });
    }

    /**
     * Show loading state
     */
    private void showLoading(boolean show) {
        if (!isAdded()) return;

        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * Show content (list of reports)
     */
    private void showContent() {
        if (!isAdded()) return;

        recyclerView.setVisibility(View.VISIBLE);
        textViewEmpty.setVisibility(View.GONE);
    }

    /**
     * Show empty state
     */
    private void showEmpty(String message) {
        if (!isAdded()) return;

        recyclerView.setVisibility(View.GONE);
        textViewEmpty.setVisibility(View.VISIBLE);
        textViewEmpty.setText(message);
    }
}
