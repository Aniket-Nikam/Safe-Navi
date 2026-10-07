package com.safenavi.app;

import android.os.Bundle;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.safenavi.app.product.ProductApiClient;
import org.json.JSONArray;
import org.json.JSONObject;

public class CommunityFragment extends Fragment implements CommunityPostAdapter.Actions {
    private ProductApiClient api;
    private CommunityPostAdapter adapter;
    private TextInputEditText composer;
    private AutoCompleteTextView category;
    private ProgressBar progress;
    private TextView empty;
    private TextView photoStatus;
    private String photoEvidenceId;
    private final ActivityResultLauncher<String[]> photoPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::uploadCommunityPhoto);

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                  @Nullable ViewGroup container,
                                                  @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_community, container, false);
        api = new ProductApiClient(requireContext());
        composer = view.findViewById(R.id.communityComposer);
        category = view.findViewById(R.id.communityCategory);
        progress = view.findViewById(R.id.communityProgress);
        empty = view.findViewById(R.id.communityEmpty);
        photoStatus = view.findViewById(R.id.communityPhotoStatus);
        String[] labels = {"General", "Question", "Help", "Update", "Neighbourhood"};
        category.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, labels));
        category.setText(labels[0], false);
        RecyclerView list = view.findViewById(R.id.communityList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setNestedScrollingEnabled(false);
        adapter = new CommunityPostAdapter(this);
        list.setAdapter(adapter);
        view.findViewById(R.id.communityPublish).setOnClickListener(v -> publish());
        view.findViewById(R.id.communityAddPhoto).setOnClickListener(v ->
                photoPicker.launch(new String[]{"image/jpeg", "image/png", "image/webp"}));
        view.findViewById(R.id.communityRefresh).setOnClickListener(v -> load());
        view.findViewById(R.id.communityAskAi).setOnClickListener(v -> ((MainActivity) requireActivity()).openAssistant());
        load();
        return view;
    }

    private void publish() {
        String content = composer.getText() == null ? "" : composer.getText().toString().trim();
        if (content.length() < 3) { composer.setError("Share a little more detail"); return; }
        String key = category.getText().toString().trim().toLowerCase().replace(' ', '_');
        setBusy(true);
        api.createCommunityPost(content, key, photoEvidenceId, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                composer.setText(""); photoEvidenceId = null;
                photoStatus.setText("Optional · JPEG, PNG or WebP · authenticated members only");
                Toast.makeText(requireContext(), "Posted to the community", Toast.LENGTH_SHORT).show(); load();
            }); }
            @Override public void onError(String message) { showError(message); }
        });
    }

    private void uploadCommunityPhoto(Uri uri) {
        if (uri == null) return;
        photoStatus.setText("Uploading photograph securely…");
        api.uploadEvidence(uri, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                photoEvidenceId = value.optString("id");
                photoStatus.setText("Photograph ready · visible only to signed-in members");
            }); }
            @Override public void onError(String message) { showError(message); }
        });
    }

    private void load() {
        setBusy(true);
        api.communityPosts(null, new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray value) { if (isAdded()) requireActivity().runOnUiThread(() -> { adapter.replace(value); empty.setVisibility(value.length() == 0 ? View.VISIBLE : View.GONE); setBusy(false); }); }
            @Override public void onError(String message) { showError(message); }
        });
    }

    @Override public void onVote(JSONObject post) {
        api.toggleCommunityVote(post.optString("id"), new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> load()); }
            @Override public void onError(String message) { showError(message); }
        });
    }

    @Override public void onComments(JSONObject post) {
        api.communityComments(post.optString("id"), new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { if (isAdded()) requireActivity().runOnUiThread(() -> showComments(post, values)); }
            @Override public void onError(String message) { showError(message); }
        });
    }

    private void showComments(JSONObject post, JSONArray values) {
        StringBuilder thread = new StringBuilder();
        for (int i = 0; i < values.length(); i++) {
            JSONObject comment = values.optJSONObject(i);
            if (comment != null) thread.append(comment.optString("author_name", "Member")).append("\n")
                    .append(comment.optString("content")).append("\n\n");
        }
        EditText reply = new EditText(requireContext());
        reply.setHint("Write a constructive reply"); reply.setMinLines(2); reply.setPadding(32, 18, 32, 18);
        String heading = values.length() == 0 ? "No replies yet. Start the conversation." : thread.toString().trim();
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Community discussion").setMessage(heading).setView(reply)
                .setNegativeButton("Close", null).setPositiveButton("Reply", (dialog, which) -> {
                    String content = reply.getText().toString().trim();
                    if (content.length() < 2) return;
                    api.createCommunityComment(post.optString("id"), content, new ProductApiClient.ObjectCallback() {
                        @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> { Toast.makeText(requireContext(), "Reply added", Toast.LENGTH_SHORT).show(); load(); }); }
                        @Override public void onError(String message) { showError(message); }
                    });
                }).show();
    }

    private void setBusy(boolean busy) { if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE); }
    private void showError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> { setBusy(false); Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }); }
}
