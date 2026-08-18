package com.example.xavierproject;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<Complaint> reportList;
    private Context context;

    public ReportAdapter(List<Complaint> reportList, Context context) {
        this.reportList = reportList;
        this.context = context;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Complaint Complaint = reportList.get(position);
        holder.bind(Complaint);
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    class ReportViewHolder extends RecyclerView.ViewHolder {

        private ImageView imageViewThumbnail;
        private TextView textViewTitle, textViewCategory, textViewDescription;
        private TextView textViewTimestamp, textViewStatus, textViewLocation;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);

            imageViewThumbnail = itemView.findViewById(R.id.imageViewThumbnail);
            textViewTitle = itemView.findViewById(R.id.textViewTitle);
            textViewCategory = itemView.findViewById(R.id.textViewCategory);
            textViewDescription = itemView.findViewById(R.id.textViewDescription);
            textViewTimestamp = itemView.findViewById(R.id.textViewTimestamp);
            textViewStatus = itemView.findViewById(R.id.textViewStatus);
            textViewLocation = itemView.findViewById(R.id.textViewLocation);
        }

        public void bind(Complaint Complaint) {
            // Set title
            textViewTitle.setText(Complaint.getTitle());

            // Set category
            textViewCategory.setText(Complaint.getCategory());

            // Set description
            textViewDescription.setText(Complaint.getDescription());

            // Set timestamp
            String formattedDate = formatTimestamp(Complaint.getTimestamp());
            textViewTimestamp.setText(formattedDate);

            // Set status with color
            String status = Complaint.getStatus() != null ? Complaint.getStatus() : "pending";
            textViewStatus.setText(status.substring(0, 1).toUpperCase() + status.substring(1));

            int statusColor;
            switch (status.toLowerCase()) {
                case "resolved":
                    statusColor = 0xFF4CAF50; // Green
                    break;
                case "in_progress":
                    statusColor = 0xFFFF9800; // Orange
                    break;
                case "rejected":
                    statusColor = 0xFFF44336; // Red
                    break;
                default:
                    statusColor = 0xFF9E9E9E; // Gray for pending
                    break;
            }
            textViewStatus.setTextColor(statusColor);

            // Set location
            if (Complaint.getLocation() != null) {
                String locationText = String.format(Locale.getDefault(),
                        "Lat: %.4f, Lng: %.4f",
                        Complaint.getLatitude(),
                        Complaint.getLongitude());
                textViewLocation.setText(locationText);
                textViewLocation.setVisibility(View.VISIBLE);
            } else {
                textViewLocation.setVisibility(View.GONE);
            }

            // Load thumbnail image
            if (Complaint.getImageUrl() != null && !Complaint.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(Complaint.getImageUrl())
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_error_image)
                        .into(imageViewThumbnail);
            } else {
                imageViewThumbnail.setImageResource(R.drawable.ic_image_placeholder);
            }

            // Click listener for viewing full details
            itemView.setOnClickListener(v -> {
                // TODO: Open detail view or dialog
                // You can implement a detail fragment/activity here
            });
        }

        /**
         * Format timestamp to readable date
         */
        private String formatTimestamp(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}
