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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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

public class DiscussionActivity extends AppCompatActivity {

    private RecyclerView postsRecyclerView;
    private PostsAdapter postsAdapter;
    private ProgressBar progressBar;
    private TextView emptyTextView;
    private EditText newPostEditText;
    private ImageButton sendPostButton;
    private ChipGroup filterChipGroup;
    private SearchView searchView;

    private DatabaseReference postsRef;
    private FirebaseAuth mAuth;
    private ValueEventListener postsListener;

    private List<Post> allPosts = new ArrayList<>();
    private String currentFilter = "all";
    private String currentSort = "newest";
    private String searchQuery = "";
    private String selectedCategory = "general";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discussion);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Community Discussion");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mAuth = FirebaseAuth.getInstance();
        postsRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL).getReference("posts");

        initializeViews();
        setupFilterChips();
        loadPosts();
        setupPostButton();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        try {
            getMenuInflater().inflate(R.menu.menu_discussion, menu);

            MenuItem searchItem = menu.findItem(R.id.action_search);
            searchView = (SearchView) searchItem.getActionView();
            searchView.setQueryHint("Search posts...");

            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    searchQuery = query.toLowerCase();
                    filterAndSortPosts();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    searchQuery = newText.toLowerCase();
                    filterAndSortPosts();
                    return true;
                }
            });
        } catch (Exception e) {
            // Menu file not found - search feature disabled
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.action_my_posts) {
            currentFilter = "my_posts";
            filterAndSortPosts();
            return true;
        } else if (id == R.id.action_bookmarked) {
            currentFilter = "bookmarked";
            filterAndSortPosts();
            return true;
        } else if (id == R.id.action_refresh) {
            loadPosts();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeViews() {
        postsRecyclerView = findViewById(R.id.postsRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyTextView = findViewById(R.id.emptyTextView);
        newPostEditText = findViewById(R.id.newPostEditText);
        sendPostButton = findViewById(R.id.sendPostButton);
        filterChipGroup = findViewById(R.id.filterChipGroup);

        postsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        postsAdapter = new PostsAdapter(this, mAuth.getCurrentUser());
        postsRecyclerView.setAdapter(postsAdapter);
    }

    private void setupFilterChips() {
        String[] categories = {"All", "General", "Question", "Discussion", "Help", "Announcement"};

        for (String category : categories) {
            Chip chip = new Chip(this);
            chip.setText(category);
            chip.setCheckable(true);
            chip.setChipBackgroundColorResource(R.color.chip_background);
            chip.setTextColor(getColor(R.color.text_primary));

            if (category.equals("All")) {
                chip.setChecked(true);
            }

            chip.setOnClickListener(v -> {
                if (category.equals("All")) {
                    currentFilter = "all";
                } else {
                    currentFilter = "category";
                    selectedCategory = category.toLowerCase();
                }
                filterAndSortPosts();
            });

            filterChipGroup.addView(chip);
        }
    }

    private void showSortDialog() {
        String[] sortOptions = {"Newest First", "Most Popular", "Most Discussed", "Recently Active"};
        int currentSelection = 0;

        switch (currentSort) {
            case "newest": currentSelection = 0; break;
            case "popular": currentSelection = 1; break;
            case "discussed": currentSelection = 2; break;
            case "active": currentSelection = 3; break;
        }

        new AlertDialog.Builder(this)
                .setTitle("Sort Posts")
                .setSingleChoiceItems(sortOptions, currentSelection, (dialog, which) -> {
                    switch (which) {
                        case 0: currentSort = "newest"; break;
                        case 1: currentSort = "popular"; break;
                        case 2: currentSort = "discussed"; break;
                        case 3: currentSort = "active"; break;
                    }
                    filterAndSortPosts();
                    dialog.dismiss();
                })
                .show();
    }

    private void setupPostButton() {
        sendPostButton.setOnClickListener(v -> showCreatePostDialog());
    }

    private void showCreatePostDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_create_post, null);
        builder.setView(dialogView);

        EditText contentEditText = dialogView.findViewById(R.id.postContentEditText);
        ChipGroup categoryChipGroup = dialogView.findViewById(R.id.categoryChipGroup);

        // Pre-fill if there's text in the quick input
        String quickText = newPostEditText.getText().toString().trim();
        if (!quickText.isEmpty()) {
            contentEditText.setText(quickText);
            contentEditText.setSelection(quickText.length());
        }

        AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.postButton).setOnClickListener(v -> {
            String content = contentEditText.getText().toString().trim();
            if (content.isEmpty()) {
                Toast.makeText(this, "Please write something", Toast.LENGTH_SHORT).show();
                return;
            }

            // Get selected category
            int selectedId = categoryChipGroup.getCheckedChipId();
            String category = "general";
            if (selectedId != -1) {
                Chip selectedChip = dialogView.findViewById(selectedId);
                category = selectedChip.getText().toString().toLowerCase();
            }

            createPost(content, category);
            newPostEditText.setText("");
            dialog.dismiss();
        });

        dialog.show();
    }

    private void createPost(String content, String category) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            return;
        }

        String postId = postsRef.push().getKey();
        if (postId == null) return;

        String userName = user.getDisplayName() != null ? user.getDisplayName() : "Anonymous";
        long timestamp = System.currentTimeMillis();

        Post post = new Post(postId, user.getUid(), userName, content, timestamp);
        post.setCategory(category);

        postsRef.child(postId).setValue(post)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Post created!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to create post", Toast.LENGTH_SHORT).show();
                });
    }

    private void loadPosts() {
        progressBar.setVisibility(View.VISIBLE);
        postsRecyclerView.setVisibility(View.GONE);
        emptyTextView.setVisibility(View.GONE);

        postsListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allPosts.clear();

                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    Post post = postSnapshot.getValue(Post.class);
                    if (post != null) {
                        allPosts.add(post);
                    }
                }

                filterAndSortPosts();
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(DiscussionActivity.this, "Error loading posts", Toast.LENGTH_SHORT).show();
            }
        };

        postsRef.addValueEventListener(postsListener);
    }

    private void filterAndSortPosts() {
        List<Post> filteredPosts = new ArrayList<>();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        for (Post post : allPosts) {
            boolean matches = true;

            // Apply search filter
            if (!searchQuery.isEmpty()) {
                matches = post.getContent().toLowerCase().contains(searchQuery) ||
                        post.getUserName().toLowerCase().contains(searchQuery);
            }

            // Apply category filter
            if (matches && currentFilter.equals("category")) {
                matches = post.getCategory().equals(selectedCategory);
            }

            // Apply my posts filter
            if (matches && currentFilter.equals("my_posts") && currentUser != null) {
                matches = post.getUserId().equals(currentUser.getUid());
            }

            // Apply bookmarked filter
            if (matches && currentFilter.equals("bookmarked") && currentUser != null) {
                matches = post.getBookmarkedBy() != null &&
                        post.getBookmarkedBy().containsKey(currentUser.getUid());
            }

            if (matches) {
                filteredPosts.add(post);
            }
        }

        // Apply sorting
        switch (currentSort) {
            case "newest":
                Collections.sort(filteredPosts, (p1, p2) ->
                        Long.compare(p2.getTimestamp(), p1.getTimestamp()));
                break;
            case "popular":
                Collections.sort(filteredPosts, (p1, p2) ->
                        Integer.compare(p2.getUpvotes(), p1.getUpvotes()));
                break;
            case "discussed":
                Collections.sort(filteredPosts, (p1, p2) ->
                        Integer.compare(p2.getCommentsCount(), p1.getCommentsCount()));
                break;
            case "active":
                Collections.sort(filteredPosts, (p1, p2) ->
                        Long.compare(p2.getLastActivityTimestamp(), p1.getLastActivityTimestamp()));
                break;
        }

        // Sort pinned posts to top
        Collections.sort(filteredPosts, (p1, p2) ->
                Boolean.compare(p2.isPinned(), p1.isPinned()));

        if (filteredPosts.isEmpty()) {
            emptyTextView.setVisibility(View.VISIBLE);
            postsRecyclerView.setVisibility(View.GONE);

            if (!searchQuery.isEmpty()) {
                emptyTextView.setText("No posts found matching \"" + searchQuery + "\"");
            } else {
                emptyTextView.setText("No posts yet. Be the first to start a discussion!");
            }
        } else {
            emptyTextView.setVisibility(View.GONE);
            postsRecyclerView.setVisibility(View.VISIBLE);
            postsAdapter.setPosts(filteredPosts);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (postsRef != null && postsListener != null) {
            postsRef.removeEventListener(postsListener);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
