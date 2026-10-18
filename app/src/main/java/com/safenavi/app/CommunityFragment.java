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
    private View root;
    private String identity, origin;
    private boolean publishing, uploading;
    private Runnable saveReply;
    private Runnable discussionPoll;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private boolean alive() { return isAdded() && root != null && identity.equals(com.safenavi.app.product.ProductSession.token(requireContext())) && origin.equals(com.safenavi.app.product.ServerConnection.baseUrl()); }
    private void saveDraft() {
        if (!alive()) return;
        try {
            JSONObject draft = new JSONObject().put("content", composer.getText().toString()).put("category", category.getText().toString()).put("photo", photoEvidenceId);
            com.safenavi.app.product.ScopedDraftStore.save(requireContext(), "community", draft);
        } catch (Exception ignored) { }
    }
    private void updateSendState() {
        if(root==null)return;
        root.findViewById(R.id.communityPublish).setEnabled(!publishing && !uploading);
        root.findViewById(R.id.communityAddPhoto).setEnabled(!publishing && !uploading);
        composer.setEnabled(!publishing); category.setEnabled(!publishing);
    }
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
        root = view;
        identity = com.safenavi.app.product.ProductSession.token(requireContext());
        origin = com.safenavi.app.product.ServerConnection.baseUrl();
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
        JSONObject draft = com.safenavi.app.product.ScopedDraftStore.load(requireContext(), "community");
        composer.setText(draft.optString("content"));
        category.setText(draft.optString("category", "General"), false);
        photoEvidenceId = JsonDisplay.text(draft, "photo", null);
        if (photoEvidenceId != null) photoStatus.setText("Photograph attached · saved with your draft");
        View card=view.findViewById(R.id.communityComposerCard);
        MaterialButton toggle=view.findViewById(R.id.communityComposeToggle);
        if(composer.length()>0 || photoEvidenceId!=null){card.setVisibility(View.VISIBLE);toggle.setText("Close composer");}
        toggle.setOnClickListener(v->{boolean open=card.getVisibility()!=View.VISIBLE;card.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"Close composer":"Share an update");saveDraft();});
        composer.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){handler.removeCallbacks(draftSave);handler.postDelayed(draftSave,400);}
            public void afterTextChanged(android.text.Editable e){}
        });
        load();
        return view;
    }

    private final Runnable draftSave = this::saveDraft;
    // A timeout may occur after the server committed. Reuse the same request ID on retry.
    private String submissionId(String key, String payload) {
        JSONObject previous=com.safenavi.app.product.ScopedDraftStore.load(requireContext(),key);
        if(payload.equals(previous.optString("payload")) && !previous.optString("id").isEmpty())return previous.optString("id");
        String id=java.util.UUID.randomUUID().toString();
        try { com.safenavi.app.product.ScopedDraftStore.save(requireContext(),key,new JSONObject().put("payload",payload).put("id",id)); } catch(Exception ignored){}
        return id;
    }

    private void publish() {
        if (publishing || uploading || !alive()) return;
        String content = composer.getText() == null ? "" : composer.getText().toString().trim();
        if (content.length() < 3) { composer.setError("Share a little more detail"); return; }
        String key = category.getText().toString().trim().toLowerCase().replace(' ', '_');
        final View requestView=root;
        saveDraft(); publishing=true; updateSendState(); setBusy(true);
        api.createCommunityPost(content, key, photoEvidenceId, submissionId("community-send",content+"|"+key+"|"+photoEvidenceId), new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (alive()) requireActivity().runOnUiThread(() -> { if(!alive()||root!=requestView)return;
                com.safenavi.app.product.ScopedDraftStore.clear(requireContext(),"community-send");
                publishing=false; updateSendState(); composer.setText(""); photoEvidenceId = null; saveDraft();
                root.findViewById(R.id.communityComposerCard).setVisibility(View.GONE);
                ((MaterialButton)root.findViewById(R.id.communityComposeToggle)).setText("Share an update");
                photoStatus.setText("Optional · JPEG, PNG or WebP · authenticated members only");
                Toast.makeText(requireContext(), "Posted to the community", Toast.LENGTH_SHORT).show(); load();
            }); }
            @Override public void onError(String message) { if(alive())requireActivity().runOnUiThread(()->{if(!alive()||root!=requestView)return;publishing=false;updateSendState();showError(message);}); }
        });
    }

    private void uploadCommunityPhoto(Uri uri) {
        if (uri == null || !alive() || publishing || uploading) return;
        final View requestView=root;
        uploading=true; updateSendState();
        photoStatus.setText("Uploading photograph securely…");
        api.uploadEvidence(uri, new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (alive()) requireActivity().runOnUiThread(() -> { if(!alive()||root!=requestView)return;
                uploading=false; updateSendState(); photoEvidenceId = value.optString("id"); saveDraft();
                photoStatus.setText("Photograph ready · visible only to signed-in members");
            }); }
            @Override public void onError(String message) { if(alive())requireActivity().runOnUiThread(()->{if(!alive()||root!=requestView)return;uploading=false;updateSendState();photoStatus.setText("Photo upload failed. Tap Attach to retry.");showError(message);}); }
        });
    }

    private void load() {
        setBusy(true);
        api.communityPosts(null, new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray value) { if (alive()) requireActivity().runOnUiThread(() -> { if(!alive())return; adapter.replace(value); empty.setText("No posts yet. Start a local conversation."); empty.setVisibility(value.length() == 0 ? View.VISIBLE : View.GONE); setBusy(false); }); }
            @Override public void onError(String message) { showError(message); }
        });
    }

    @Override public void onVote(JSONObject post) {
        api.toggleCommunityVote(post.optString("id"), new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (alive()) requireActivity().runOnUiThread(() -> {if(alive())load();}); }
            @Override public void onError(String message) { showError(message); }
        });
    }

    @Override public void onComments(JSONObject post) {
        api.communityComments(post.optString("id"), new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { if (alive()) requireActivity().runOnUiThread(() -> {if(alive())showComments(post, values);}); }
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
        final View requestView=root;
        String[] parentId = {null};
        TextView replying = threadText("Adding a comment · tap Reply under a comment to respond", 12); panel.addView(replying);
        MaterialButton cancelReply = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cancelReply.setText("Cancel reply"); cancelReply.setTextSize(12); cancelReply.setLetterSpacing(0); cancelReply.setVisibility(View.GONE); panel.addView(cancelReply);
        EditText reply = new EditText(requireContext()); reply.setHint("Add a constructive comment"); reply.setMinLines(1); reply.setMaxLines(4); reply.setTextSize(14); reply.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reply.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1000)}); panel.addView(reply);
        String draftKey="comment:"+post.optString("id");
        JSONObject saved=com.safenavi.app.product.ScopedDraftStore.load(requireContext(),draftKey);
        reply.setText(saved.optString("content")); parentId[0]=JsonDisplay.text(saved,"parent",null);
        if(parentId[0]!=null){replying.setText("Replying to a previous comment");cancelReply.setVisibility(View.VISIBLE);}
        saveReply=()->{if(!alive())return;try{com.safenavi.app.product.ScopedDraftStore.save(requireContext(),draftKey,new JSONObject().put("content",reply.getText().toString()).put("parent",parentId[0]));}catch(Exception ignored){}};
        sheet.setOnDismissListener(d->{if(saveReply!=null)saveReply.run();saveReply=null;if(discussionPoll!=null)handler.removeCallbacks(discussionPoll);discussionPoll=null;});
        MaterialButton send = new MaterialButton(requireContext()); send.setText("Post comment"); send.setTextSize(13); send.setLetterSpacing(0); panel.addView(send);
        cancelReply.setOnClickListener(v -> { parentId[0] = null; replying.setText("Adding a comment"); send.setText("Post comment"); cancelReply.setVisibility(View.GONE); });
        java.util.function.Consumer<JSONObject> selectReply = comment -> {
            if(!send.isEnabled())return;
            parentId[0] = comment.optString("id"); replying.setText("Replying to " + comment.optString("author_name"));
            cancelReply.setVisibility(View.VISIBLE); send.setText("Post reply"); reply.requestFocus();
            ((android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                    .showSoftInput(reply, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        };
        renderDiscussion(list, values, selectReply);
        boolean[] refreshing={false};
        Runnable reload = () -> { if(refreshing[0])return;refreshing[0]=true;api.communityComments(post.optString("id"), new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray comments) { if (alive()) requireActivity().runOnUiThread(() -> { if(!alive())return;
                refreshing[0]=false; if (!sheet.isShowing()) return;
                heading.setText("Discussion · " + comments.length()); renderDiscussion(list, comments, selectReply);
            }); }
            @Override public void onError(String message) { if(alive())requireActivity().runOnUiThread(()->{refreshing[0]=false;if(alive()&&sheet.isShowing())heading.setText("Discussion · reconnecting");}); }
        }); };
        refresh.setOnClickListener(v -> reload.run());
        discussionPoll=new Runnable(){public void run(){if(!alive()||!sheet.isShowing()||!isResumed())return;reload.run();handler.postDelayed(this,5000);}};
        handler.postDelayed(discussionPoll,5000);
        send.setOnClickListener(v -> {
            String content = reply.getText().toString().trim();
            if (content.length() < 2) { reply.setError("Write at least two characters"); return; }
            if(saveReply!=null)saveReply.run();
            send.setEnabled(false);
            reply.setEnabled(false); cancelReply.setEnabled(false);
            api.createCommunityComment(post.optString("id"), content, parentId[0], submissionId(draftKey+":send",content+"|"+parentId[0]), new ProductApiClient.ObjectCallback() {
                @Override public void onSuccess(JSONObject value) { if (alive()) requireActivity().runOnUiThread(() -> { if(!alive()||root!=requestView)return;
                    com.safenavi.app.product.ScopedDraftStore.clear(requireContext(),draftKey+":send");
                    reply.setEnabled(true); cancelReply.setEnabled(true); send.setEnabled(true); reply.setText(""); parentId[0] = null; replying.setText("Adding a comment"); cancelReply.setVisibility(View.GONE); send.setText("Post comment");
                    com.safenavi.app.product.ScopedDraftStore.clear(requireContext(),draftKey); if(sheet.isShowing())reload.run(); load();
                }); }
                @Override public void onError(String message) { if (alive()) requireActivity().runOnUiThread(() -> { if(!alive())return;
                    reply.setEnabled(true); cancelReply.setEnabled(true); send.setEnabled(true); Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
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
    @Override public void onResume() {super.onResume();if(discussionPoll!=null){handler.removeCallbacks(discussionPoll);handler.postDelayed(discussionPoll,5000);}}
    @Override public void onPause() { saveDraft(); if(saveReply!=null)saveReply.run(); if(discussionPoll!=null)handler.removeCallbacks(discussionPoll); super.onPause(); }
    @Override public void onDestroyView() {
        saveDraft(); handler.removeCallbacks(draftSave);
        if(discussionPoll!=null)handler.removeCallbacks(discussionPoll);discussionPoll=null;
        if (discussionDialog != null) { discussionDialog.dismiss(); discussionDialog = null; }
        root=null; super.onDestroyView();
    }

    private void setBusy(boolean busy) { if (progress != null) progress.setVisibility(busy ? View.VISIBLE : View.GONE); }
    private void showError(String message) { if (alive()) requireActivity().runOnUiThread(() -> { if(!alive())return; setBusy(false); empty.setText(message + "\nTap Refresh to try again. Your draft is kept."); empty.setVisibility(View.VISIBLE); Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }); }
}
