package com.example.xavierproject;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.List;

public class ComplaintsAdapter extends RecyclerView.Adapter<ComplaintsAdapter.ComplaintViewHolder> {

    private Context context;
    private List<Complaint> complaints;
    private OnComplaintClickListener clickListener;

    public interface OnComplaintClickListener {
        void onComplaintClick(Complaint complaint);
    }

    public ComplaintsAdapter(Context context, OnComplaintClickListener listener) {
        this.context = context;
        this.complaints = new ArrayList<>();
        this.clickListener = listener;
    }

    public void setComplaints(List<Complaint> complaints) {
        this.complaints = complaints;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ComplaintViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_complaint, parent, false);
        return new ComplaintViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ComplaintViewHolder holder, int position) {
        Complaint complaint = complaints.get(position);

        holder.idTextView.setText(complaint.getId()); // This will show username
        holder.titleTextView.setText(complaint.getTitle());
        holder.descriptionTextView.setText(complaint.getDescription());
        holder.statusTextView.setText(complaint.getStatus());
        holder.dateTextView.setText(complaint.getDate());
        holder.locationTextView.setText(complaint.getLocation());

        // Set status color
        int statusColor;
        String status = complaint.getStatus() != null ? complaint.getStatus().toLowerCase() : "pending";
        switch (status) {
            case "resolved":
                statusColor = context.getColor(R.color.status_resolved);
                break;
            case "ongoing":
            case "in progress":
                statusColor = context.getColor(R.color.status_in_progress);
                break;
            case "acknowledged":
                statusColor = context.getColor(R.color.accent);
                break;
            default: // pending
                statusColor = context.getColor(R.color.status_pending);
                break;
        }
        holder.statusTextView.setTextColor(statusColor);

        // Set click listener
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onComplaintClick(complaint);
            }
        });
    }

    @Override
    public int getItemCount() {
        return complaints.size();
    }

    static class ComplaintViewHolder extends RecyclerView.ViewHolder {
        TextView idTextView, titleTextView, descriptionTextView, statusTextView, dateTextView, locationTextView;

        public ComplaintViewHolder(@NonNull View itemView) {
            super(itemView);
            idTextView = itemView.findViewById(R.id.complaintIdTextView);
            titleTextView = itemView.findViewById(R.id.complaintTitleTextView);
            descriptionTextView = itemView.findViewById(R.id.complaintDescriptionTextView);
            statusTextView = itemView.findViewById(R.id.complaintStatusTextView);
            dateTextView = itemView.findViewById(R.id.complaintDateTextView);
            locationTextView = itemView.findViewById(R.id.complaintLocationTextView);
        }
    }
}
