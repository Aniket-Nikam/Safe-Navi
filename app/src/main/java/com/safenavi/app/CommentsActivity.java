package com.safenavi.app;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommentsActivity extends AppCompatActivity {

    private TextView postContentTextView, postUserNameTextView, postStatsTextView, emptyTextView;
    private RecyclerView commentsRecyclerView;
    private CommentsAdapter commentsAdapter;
    private EditText commentEditText;
    private ImageButton sendCommentButton;
    private ProgressBar progressBar;

    private String postId;
    private Post currentPost;
    private DatabaseReference commentsRef;
    private DatabaseReference postRef;
    private FirebaseAuth mAuth;
    private ValueEventListener commentsListener;
    private ValueEventListener postListener;

    private String sortOrder = "oldest"; // or "newest"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_comments);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Comments");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        postId = getIntent().getStringExtra("POST_ID");
        String postContent = getIntent().getStringExtra("POST_CONTENT");

        commentsRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("comments").child(postId);
        postRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("posts").child(postId);

        initializeViews();
        if (postContent != null) {
            postContentTextView.setText(postContent);
        }
        loadPostDetails();
        loadComments();
        setupCommentButton();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        try {
            getMenuInflater().inflate(R.menu.menu_comments, menu);
        } catch (Exception e) {
            // Menu file not found - menu features disabled
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_sort_comments) {
            toggleSortOrder();
            return true;
        } else if (id == R.id.action_refresh_comments) {
            loadComments();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleSortOrder() {
        if (sortOrder.equals("oldest")) {
            sortOrder = "newest";
            Toast.makeText(this, "Sorted by newest first", Toast.LENGTH_SHORT).show();
        } else {
            sortOrder = "oldest";
            Toast.makeText(this, "Sorted by oldest first", Toast.LENGTH_SHORT).show();
        }
        loadComments();
    }

    private void initializeViews() {
        postContentTextView = findViewById(R.id.postContentTextView);
        postUserNameTextView = findViewById(R.id.postUserNameTextView);
        postStatsTextView = findViewById(R.id.postStatsTextView);
        commentsRecyclerView = findViewById(R.id.commentsRecyclerView);
        commentEditText = findViewById(R.id.commentEditText);
        sendCommentButton = findViewById(R.id.sendCommentButton);
        progressBar = findViewById(R.id.progressBar);
        emptyTextView = findViewById(R.id.emptyTextView);

        commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        commentsAdapter = new CommentsAdapter(this, postId);
        commentsRecyclerView.setAdapter(commentsAdapter);
    }

    private void loadPostDetails() {
        postListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentPost = snapshot.getValue(Post.class);
                if (currentPost != null) {
                    postContentTextView.setText(currentPost.getContent());
                    postUserNameTextView.setText(currentPost.getUserName());

                    String stats = currentPost.getUpvotes() + " upvotes Â· " +
                            currentPost.getCommentsCount() + " comments";
                    postStatsTextView.setText(stats);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CommentsActivity.this, "Error loading post", Toast.LENGTH_SHORT).show();
            }
        };

        postRef.addValueEventListener(postListener);
    }

    private void setupCommentButton() {
        sendCommentButton.setOnClickListener(v -> {
            String content = commentEditText.getText().toString().trim();
            if (!content.isEmpty()) {
                createComment(content);
            } else {
                Toast.makeText(this, "Please write a comment", Toast.LENGTH_SHORT).show();
            }
        });

        // Enable multiline with shift+enter, send with enter
        commentEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER) {
                if (!event.isShiftPressed()) {
                    sendCommentButton.performClick();
                    return true;
                }
            }
            return false;
        });
    }

    private void createComment(String content) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        String commentId = commentsRef.push().getKey();
        if (commentId == null) return;

        String userName = user.getDisplayName() != null ? user.getDisplayName() : "Anonymous";
        long timestamp = System.currentTimeMillis();

        Comment comment = new Comment(commentId, postId, user.getUid(), userName, content, timestamp);

        // Disable send button while posting
        sendCommentButton.setEnabled(false);

        commentsRef.child(commentId).setValue(comment)
                .addOnSuccessListener(aVoid -> {
                    commentEditText.setText("");
                    updateCommentCount();
                    updateLastActivity();
                    sendCommentButton.setEnabled(true);

                    // Show feedback
                    Toast.makeText(this, "Comment added!", Toast.LENGTH_SHORT).show();

                    // Scroll to bottom to show new comment
                    if (sortOrder.equals("oldest")) {
                        commentsRecyclerView.smoothScrollToPosition(
                                commentsAdapter.getItemCount());
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to add comment", Toast.LENGTH_SHORT).show();
                    sendCommentButton.setEnabled(true);
                });
    }

    private void updateCommentCount() {
        commentsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int count = (int) snapshot.getChildrenCount();
                postRef.child("commentsCount").setValue(count);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void updateLastActivity() {
        postRef.child("lastActivityTimestamp").setValue(System.currentTimeMillis());
    }

    private void loadComments() {
        progressBar.setVisibility(View.VISIBLE);
        commentsRecyclerView.setVisibility(View.GONE);
        emptyTextView.setVisibility(View.GONE);

        commentsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Comment> comments = new ArrayList<>();

                for (DataSnapshot commentSnapshot : snapshot.getChildren()) {
                    Comment comment = commentSnapshot.getValue(Comment.class);
                    if (comment != null) {
                        comments.add(comment);
                    }
                }

                // Sort based on current sort order
                if (sortOrder.equals("oldest")) {
                    Collections.sort(comments, (c1, c2) ->
                            Long.compare(c1.getTimestamp(), c2.getTimestamp()));
                } else {
                    Collections.sort(comments, (c1, c2) ->
                            Long.compare(c2.getTimestamp(), c1.getTimestamp()));
                }

                progressBar.setVisibility(View.GONE);

                if (comments.isEmpty()) {
                    emptyTextView.setVisibility(View.VISIBLE);
                    emptyTextView.setText("No comments yet. Be the first to comment!");
                    commentsRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyTextView.setVisibility(View.GONE);
                    commentsRecyclerView.setVisibility(View.VISIBLE);
                    commentsAdapter.setComments(comments);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(CommentsActivity.this, "Error loading comments", Toast.LENGTH_SHORT).show();
            }
        };

        commentsRef.addValueEventListener(commentsListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (commentsRef != null && commentsListener != null) {
            commentsRef.removeEventListener(commentsListener);
        }
        if (postRef != null && postListener != null) {
            postRef.removeEventListener(postListener);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
