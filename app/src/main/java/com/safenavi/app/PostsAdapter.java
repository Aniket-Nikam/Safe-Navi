package com.safenavi.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.PostViewHolder> {

    private Context context;
    private List<Post> posts;
    private FirebaseUser currentUser;
    private DatabaseReference postsRef;

    public PostsAdapter(Context context, FirebaseUser currentUser) {
        this.context = context;
        this.posts = new ArrayList<>();
        this.currentUser = currentUser;
        this.postsRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("posts");
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = posts.get(position);

        // Set basic info
        holder.userNameTextView.setText(post.getUserName());
        holder.contentTextView.setText(post.getContent());
        holder.upvotesTextView.setText(String.valueOf(post.getUpvotes()));
        holder.commentsTextView.setText(String.valueOf(post.getCommentsCount()));

        // Format timestamp
        String timeAgo = getTimeAgo(post.getTimestamp());
        holder.timeTextView.setText(timeAgo);

        // Show category chip
        holder.categoryChip.setText(getCategoryDisplayName(post.getCategory()));
        holder.categoryChip.setChipBackgroundColorResource(getCategoryColor(post.getCategory()));

        // Show pinned indicator
        holder.pinnedIndicator.setVisibility(post.isPinned() ? View.VISIBLE : View.GONE);

        // Check if current user has upvoted
        boolean hasUpvoted = false;
        boolean hasBookmarked = false;

        if (currentUser != null) {
            Map<String, Boolean> upvotedBy = post.getUpvotedBy();
            hasUpvoted = upvotedBy != null && upvotedBy.containsKey(currentUser.getUid());

            Map<String, Boolean> bookmarkedBy = post.getBookmarkedBy();
            hasBookmarked = bookmarkedBy != null && bookmarkedBy.containsKey(currentUser.getUid());
        }

        // Update upvote button appearance
        if (hasUpvoted) {
            holder.upvoteButton.setColorFilter(ContextCompat.getColor(context, R.color.primary));
            holder.upvotesTextView.setTextColor(ContextCompat.getColor(context, R.color.primary));
        } else {
            holder.upvoteButton.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary));
            holder.upvotesTextView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        }

        // Update bookmark button appearance
        if (hasBookmarked) {
            holder.bookmarkButton.setColorFilter(ContextCompat.getColor(context, R.color.accent));
        } else {
            holder.bookmarkButton.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary));
        }

        // Upvote button click
        holder.upvoteButton.setOnClickListener(v -> handleUpvote(post, holder));

        // Comment button click
        holder.commentButton.setOnClickListener(v -> {
            Intent intent = new Intent(context, CommentsActivity.class);
            intent.putExtra("POST_ID", post.getPostId());
            intent.putExtra("POST_CONTENT", post.getContent());
            context.startActivity(intent);
        });

        // Bookmark button click
        holder.bookmarkButton.setOnClickListener(v -> handleBookmark(post, holder));

        // Share button click
        holder.shareButton.setOnClickListener(v -> sharePost(post));

        // More options button
        holder.moreButton.setOnClickListener(v -> showMoreOptions(post, position));

        // Card click - open comments
        holder.cardView.setOnClickListener(v -> {
            Intent intent = new Intent(context, CommentsActivity.class);
            intent.putExtra("POST_ID", post.getPostId());
            intent.putExtra("POST_CONTENT", post.getContent());
            context.startActivity(intent);
        });
    }

    private void handleUpvote(Post post, PostViewHolder holder) {
        if (currentUser == null) {
            Toast.makeText(context, "Please login to upvote", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference postRef = postsRef.child(post.getPostId());
        Map<String, Boolean> upvotedBy = post.getUpvotedBy();

        if (upvotedBy == null) {
            upvotedBy = new HashMap<>();
        }

        if (upvotedBy.containsKey(currentUser.getUid())) {
            // Remove upvote
            upvotedBy.remove(currentUser.getUid());
            post.setUpvotes(Math.max(0, post.getUpvotes() - 1));
        } else {
            // Add upvote
            upvotedBy.put(currentUser.getUid(), true);
            post.setUpvotes(post.getUpvotes() + 1);
        }

        post.setUpvotedBy(upvotedBy);

        // Update in Firebase
        postRef.child("upvotes").setValue(post.getUpvotes());
        postRef.child("upvotedBy").setValue(upvotedBy);

        notifyItemChanged(holder.getAdapterPosition());
    }

    private void handleBookmark(Post post, PostViewHolder holder) {
        if (currentUser == null) {
            Toast.makeText(context, "Please login to bookmark", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference postRef = postsRef.child(post.getPostId());
        Map<String, Boolean> bookmarkedBy = post.getBookmarkedBy();

        if (bookmarkedBy == null) {
            bookmarkedBy = new HashMap<>();
        }

        if (bookmarkedBy.containsKey(currentUser.getUid())) {
            bookmarkedBy.remove(currentUser.getUid());
            Toast.makeText(context, "Removed from bookmarks", Toast.LENGTH_SHORT).show();
        } else {
            bookmarkedBy.put(currentUser.getUid(), true);
            Toast.makeText(context, "Added to bookmarks", Toast.LENGTH_SHORT).show();
        }

        post.setBookmarkedBy(bookmarkedBy);
        postRef.child("bookmarkedBy").setValue(bookmarkedBy);

        notifyItemChanged(holder.getAdapterPosition());
    }

    private void sharePost(Post post) {
        String shareText = post.getContent() + "\n\n- " + post.getUserName();
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        context.startActivity(Intent.createChooser(shareIntent, "Share post via"));
    }

    private void showMoreOptions(Post post, int position) {
        String[] options;

        if (currentUser != null && post.getUserId().equals(currentUser.getUid())) {
            options = new String[]{"Delete Post", "Cancel"};
        } else {
            options = new String[]{"Complaint Post", "Cancel"};
        }

        new AlertDialog.Builder(context)
                .setTitle("Post Options")
                .setItems(options, (dialog, which) -> {
                    if (options[which].equals("Delete Post")) {
                        deletePost(post, position);
                    } else if (options[which].equals("Complaint Post")) {
                        Toast.makeText(context, "Post reported", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void deletePost(Post post, int position) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Post")
                .setMessage("Are you sure you want to delete this post?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    postsRef.child(post.getPostId()).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                posts.remove(position);
                                notifyItemRemoved(position);
                                Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(context, "Failed to delete post", Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    private String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;

        if (weeks > 0) {
            return weeks + "w ago";
        } else if (days > 0) {
            return days + "d ago";
        } else if (hours > 0) {
            return hours + "h ago";
        } else if (minutes > 0) {
            return minutes + "m ago";
        } else {
            return "just now";
        }
    }

    private String getCategoryDisplayName(String category) {
        switch (category) {
            case "question": return "Question";
            case "discussion": return "Discussion";
            case "announcement": return "Announcement";
            case "help": return "Help";
            default: return "General";
        }
    }

    private int getCategoryColor(String category) {
        switch (category) {
            case "question": return R.color.category_question;
            case "discussion": return R.color.category_discussion;
            case "announcement": return R.color.category_announcement;
            case "help": return R.color.category_help;
            default: return R.color.category_general;
        }
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        TextView userNameTextView, contentTextView, timeTextView;
        TextView upvotesTextView, commentsTextView;
        ImageButton upvoteButton, commentButton, bookmarkButton, shareButton, moreButton;
        Chip categoryChip;
        ImageView pinnedIndicator;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            userNameTextView = itemView.findViewById(R.id.postUserNameTextView);
            contentTextView = itemView.findViewById(R.id.postContentTextView);
            timeTextView = itemView.findViewById(R.id.postTimeTextView);
            upvotesTextView = itemView.findViewById(R.id.upvotesTextView);
            commentsTextView = itemView.findViewById(R.id.commentsTextView);
            upvoteButton = itemView.findViewById(R.id.upvoteButton);
            commentButton = itemView.findViewById(R.id.commentButton);
            bookmarkButton = itemView.findViewById(R.id.bookmarkButton);
            shareButton = itemView.findViewById(R.id.shareButton);
            moreButton = itemView.findViewById(R.id.moreButton);
            categoryChip = itemView.findViewById(R.id.categoryChip);
            pinnedIndicator = itemView.findViewById(R.id.pinnedIndicator);
        }
    }
}
