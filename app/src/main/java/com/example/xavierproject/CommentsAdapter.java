package com.example.xavierproject;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.CommentViewHolder> {

    private Context context;
    private List<Comment> comments;
    private FirebaseUser currentUser;
    private DatabaseReference commentsRef;
    private String postId;

    public CommentsAdapter(Context context, String postId) {
        this.context = context;
        this.comments = new ArrayList<>();
        this.currentUser = FirebaseAuth.getInstance().getCurrentUser();
        this.postId = postId;
        this.commentsRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("comments").child(postId);
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = comments.get(position);

        holder.userNameTextView.setText(comment.getUserName());
        holder.contentTextView.setText(comment.getContent());
        holder.likesTextView.setText(String.valueOf(comment.getLikes()));

        // Format timestamp
        String timeAgo = getTimeAgo(comment.getTimestamp());
        holder.timeTextView.setText(timeAgo);

        // Show edited indicator
        if (comment.isEdited()) {
            holder.editedIndicator.setVisibility(View.VISIBLE);
        } else {
            holder.editedIndicator.setVisibility(View.GONE);
        }

        // Check if current user has liked
        boolean hasLiked = false;
        if (currentUser != null) {
            Map<String, Boolean> likedBy = comment.getLikedBy();
            hasLiked = likedBy != null && likedBy.containsKey(currentUser.getUid());
        }

        // Update like button appearance
        if (hasLiked) {
            holder.likeButton.setColorFilter(ContextCompat.getColor(context, R.color.primary));
            holder.likesTextView.setTextColor(ContextCompat.getColor(context, R.color.primary));
        } else {
            holder.likeButton.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary));
            holder.likesTextView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }

        // Like button click
        holder.likeButton.setOnClickListener(v -> handleLike(comment, holder));

        // More options button
        holder.moreButton.setOnClickListener(v -> showMoreOptions(comment, position));

        // Long press to like
        holder.cardView.setOnLongClickListener(v -> {
            handleLike(comment, holder);
            return true;
        });
    }

    private void handleLike(Comment comment, CommentViewHolder holder) {
        if (currentUser == null) {
            Toast.makeText(context, "Please login to like", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference commentRef = commentsRef.child(comment.getCommentId());
        Map<String, Boolean> likedBy = comment.getLikedBy();

        if (likedBy == null) {
            likedBy = new HashMap<>();
        }

        if (likedBy.containsKey(currentUser.getUid())) {
            // Remove like
            likedBy.remove(currentUser.getUid());
            comment.setLikes(Math.max(0, comment.getLikes() - 1));
        } else {
            // Add like
            likedBy.put(currentUser.getUid(), true);
            comment.setLikes(comment.getLikes() + 1);
        }

        comment.setLikedBy(likedBy);

        // Update in Firebase
        commentRef.child("likes").setValue(comment.getLikes());
        commentRef.child("likedBy").setValue(likedBy);

        notifyItemChanged(holder.getAdapterPosition());
    }

    private void showMoreOptions(Comment comment, int position) {
        List<String> optionsList = new ArrayList<>();

        if (currentUser != null && comment.getUserId().equals(currentUser.getUid())) {
            optionsList.add("Edit Comment");
            optionsList.add("Delete Comment");
        } else {
            optionsList.add("Report Comment");
        }
        optionsList.add("Cancel");

        String[] options = optionsList.toArray(new String[0]);

        new AlertDialog.Builder(context)
                .setTitle("Comment Options")
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if (selected.equals("Edit Comment")) {
                        editComment(comment, position);
                    } else if (selected.equals("Delete Comment")) {
                        deleteComment(comment, position);
                    } else if (selected.equals("Report Comment")) {
                        Toast.makeText(context, "Comment reported", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void editComment(Comment comment, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Edit Comment");

        final EditText input = new EditText(context);
        input.setText(comment.getContent());
        input.setSelection(comment.getContent().length());
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newContent = input.getText().toString().trim();
            if (!newContent.isEmpty()) {
                DatabaseReference commentRef = commentsRef.child(comment.getCommentId());
                commentRef.child("content").setValue(newContent);
                commentRef.child("isEdited").setValue(true);
                commentRef.child("editedTimestamp").setValue(System.currentTimeMillis());

                comment.setContent(newContent);
                comment.setEdited(true);
                comment.setEditedTimestamp(System.currentTimeMillis());
                notifyItemChanged(position);

                Toast.makeText(context, "Comment updated", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void deleteComment(Comment comment, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Comment")
                .setMessage("Are you sure you want to delete this comment?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    commentsRef.child(comment.getCommentId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                comments.remove(position);
                                notifyItemRemoved(position);
                                Toast.makeText(context, "Comment deleted", Toast.LENGTH_SHORT).show();

                                // Update comment count in post
                                updateCommentCount();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(context, "Failed to delete comment", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateCommentCount() {
        DatabaseReference postRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
                .getReference("posts").child(postId);
        postRef.child("commentsCount").setValue(comments.size());
        postRef.child("lastActivityTimestamp").setValue(System.currentTimeMillis());
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d ago";
        } else if (hours > 0) {
            return hours + "h ago";
        } else if (minutes > 0) {
            return minutes + "m ago";
        } else {
            return "just now";
        }
    }

    static class CommentViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView userNameTextView, contentTextView, timeTextView, likesTextView, editedIndicator;
        ImageButton likeButton, moreButton;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            userNameTextView = itemView.findViewById(R.id.commentUserNameTextView);
            contentTextView = itemView.findViewById(R.id.commentContentTextView);
            timeTextView = itemView.findViewById(R.id.commentTimeTextView);
            likesTextView = itemView.findViewById(R.id.commentLikesTextView);
            likeButton = itemView.findViewById(R.id.commentLikeButton);
            moreButton = itemView.findViewById(R.id.commentMoreButton);
            editedIndicator = itemView.findViewById(R.id.editedIndicator);
        }
    }
}
