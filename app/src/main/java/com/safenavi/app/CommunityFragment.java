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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.view.WindowManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.safenavi.app.product.JsonDisplay;
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
    private BottomSheetDialog discussionDialog;
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
        if (discussionDialog != null) discussionDialog.dismiss();
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext()); discussionDialog = sheet;
        LinearLayout panel = new LinearLayout(requireContext()); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(18), dp(12), dp(18), dp(16));
        TextView heading = threadText("Discussion · " + values.length(), 19); heading.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(heading);
        TextView context = threadText(post.optString("content"), 13); context.setMaxLines(3); panel.addView(context);
        LinearLayout controls = new LinearLayout(requireContext());
        MaterialButton refresh = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        refresh.setText("Refresh"); refresh.setTextSize(12); refresh.setLetterSpacing(0);
        MaterialButton close = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        close.setText("Close"); close.setTextSize(12); close.setLetterSpacing(0); close.setOnClickListener(v -> sheet.dismiss());
        controls.addView(refresh, new LinearLayout.LayoutParams(0, dp(48), 1)); controls.addView(close, new LinearLayout.LayoutParams(0, dp(48), 1)); panel.addView(controls);
        ScrollView scroll = new ScrollView(requireContext()); LinearLayout list = new LinearLayout(requireContext()); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        panel.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        String[] parentId = {null};
        TextView replying = threadText("Adding a comment · tap Reply under a comment to respond", 12); panel.addView(replying);
        MaterialButton cancelReply = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancelReply.setText("Cancel reply"); cancelReply.setTextSize(12); cancelReply.setLetterSpacing(0); cancelReply.setVisibility(View.GONE); panel.addView(cancelReply);
        EditText reply = new EditText(requireContext()); reply.setHint("Add a constructive comment"); reply.setMinLines(1); reply.setMaxLines(4); reply.setTextSize(14); reply.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reply.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)}); panel.addView(reply);
        MaterialButton send = new MaterialButton(requireContext()); send.setText("Post comment"); send.setTextSize(13); send.setLetterSpacing(0); panel.addView(send);
        cancelReply.setOnClickListener(v -> { parentId[0] = null; replying.setText("Adding a comment"); send.setText("Post comment"); cancelReply.setVisibility(View.GONE); });
        java.util.function.Consumer<JSONObject> selectReply = comment -> {
            parentId[0] = comment.optString("id"); replying.setText("Replying to " + comment.optString("author_name"));
            cancelReply.setVisibility(View.VISIBLE); send.setText("Post reply"); reply.requestFocus();
            ((android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(reply, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        };
        renderDiscussion(list, values, selectReply);
        Runnable reload = () -> api.communityComments(post.optString("id"), new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray comments) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                if (!sheet.isShowing()) return;
                heading.setText("Discussion · " + comments.length()); renderDiscussion(list, comments, selectReply);
            }); }
            @Override public void onError(String message) { showError(message); }
        });
        refresh.setOnClickListener(v -> reload.run());
        send.setOnClickListener(v -> {
            String content = reply.getText().toString().trim();
            if (content.length() < 2) { reply.setError("Write at least two characters"); return; }
            send.setEnabled(false);
            api.createCommunityComment(post.optString("id"), content, parentId[0], new ProductApiClient.ObjectCallback() {
                @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                    send.setEnabled(true); reply.setText(""); parentId[0] = null; replying.setText("Adding a comment"); cancelReply.setVisibility(View.GONE); send.setText("Post comment");
                    reload.run(); load();
                }); }
                @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                    send.setEnabled(true); Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                }); }
            });
        });
        int height = getResources().getDisplayMetrics().heightPixels * 85 / 100;
        sheet.setContentView(panel, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        if (sheet.getWindow() != null) sheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        sheet.show(); sheet.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView threadText(String value, int size) {
        TextView view = new TextView(requireContext()); view.setText(value); view.setTextSize(size);
        view.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_primary));
        view.setPadding(0, dp(4), 0, dp(4)); return view;
    }
    private void renderDiscussion(LinearLayout list, JSONArray values, java.util.function.Consumer<JSONObject> reply) {
        list.removeAllViews();
        java.util.Map<String, java.util.List<JSONObject>> children = new java.util.LinkedHashMap<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i); if (item == null) continue;
            String parent = JsonDisplay.text(item, "parent_id", "");
            children.computeIfAbsent(parent, key -> new java.util.ArrayList<>()).add(item);
        }
        if (values.length() == 0) list.addView(threadText("No comments yet. Start the discussion.", 14));
        appendDiscussion(list, children, "", 0, reply);
    }
    private void appendDiscussion(LinearLayout list, java.util.Map<String, java.util.List<JSONObject>> children,
                                  String parent, int depth, java.util.function.Consumer<JSONObject> reply) {
        java.util.List<JSONObject> comments = children.get(parent); if (comments == null || depth > 30) return;
        for (JSONObject comment : comments) {
            LinearLayout card = new LinearLayout(requireContext()); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(Math.min(depth, 2) * 16), dp(8), dp(4), dp(8));
            TextView author = threadText((depth > 0 ? "↳ " : "") + comment.optString("author_name", "Member") + " · " + commentTime(comment.optString("created_at")), 12);
            author.setTypeface(null, android.graphics.Typeface.BOLD); card.addView(author);
            card.addView(threadText(comment.optString("content"), 15));
            TextView action = threadText("Reply", 13); action.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary)); action.setMinHeight(dp(40));
            action.setOnClickListener(v -> reply.accept(comment)); card.addView(action); list.addView(card);
            View divider = new View(requireContext()); divider.setBackgroundColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.divider)); list.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            appendDiscussion(list, children, comment.optString("id"), depth + 1, reply);
        }
    }
    private String commentTime(String value) {
        try {
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US); format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return android.text.format.DateUtils.getRelativeTimeSpanString(format.parse(value.substring(0, 19)).getTime(), System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString();
        } catch (Exception error) { return value; }
    }
    @Override public void onDestroyView() {
        if (discussionDialog != null) { discussionDialog.dismiss(); discussionDialog = null; }
        super.onDestroyView();
    }

    private void setBusy(boolean busy) { if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE); }
    private void showError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> { setBusy(false); Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }); }
}
