package com.safenavi.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class CommunityPostAdapter extends RecyclerView.Adapter<CommunityPostAdapter.PostHolder> {
    public interface Actions { void onVote(JSONObject post); void onComments(JSONObject post); }
    private final List<JSONObject> posts = new ArrayList<>();
    private final Actions actions;

    public CommunityPostAdapter(Actions actions) { this.actions = actions; }

    public void replace(JSONArray values) {
        posts.clear();
        for (int i = 0; i < values.length(); i++) {
            JSONObject post = values.optJSONObject(i);
            if (post != null) posts.add(post);
        }
        notifyDataSetChanged();
    }

    @NonNull @Override public PostHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new PostHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_community_post, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull PostHolder holder, int position) {
        JSONObject post = posts.get(position);
        holder.author.setText(post.optString("author_name", "Safe-Navi member"));
        holder.category.setText(post.optString("category", "general").replace('_', ' ').toUpperCase(Locale.US));
        holder.content.setText(post.optString("content"));
        String evidenceId = post.optString("evidence_id");
        holder.image.setTag(evidenceId);
        holder.image.setImageDrawable(null);
        holder.image.setVisibility(evidenceId.isEmpty() ? View.GONE : View.VISIBLE);
        if (!evidenceId.isEmpty()) new com.safenavi.app.product.ProductApiClient(holder.image.getContext())
                .downloadEvidence(evidenceId, new com.safenavi.app.product.ProductApiClient.BytesCallback() {
                    @Override public void onSuccess(byte[] value, String mediaType) { holder.image.post(() -> {
                        if (evidenceId.equals(holder.image.getTag())) holder.image.setImageBitmap(BitmapFactory.decodeByteArray(value, 0, value.length));
                    }); }
                    @Override public void onError(String message) { holder.image.post(() -> {
                        if (evidenceId.equals(holder.image.getTag())) holder.image.setVisibility(View.GONE);
                    }); }
                });
        holder.time.setText(relativeTime(post.optString("created_at")));
        boolean voted = post.optInt("upvoted") == 1 || post.optBoolean("upvoted");
        holder.vote.setText((voted ? "Helpful · " : "Helpful · ") + post.optInt("upvotes"));
        holder.vote.setIconResource(voted ? R.drawable.ic_upvote_filled : R.drawable.ic_upvote);
        holder.comments.setText("Discuss · " + post.optInt("comments_count"));
        holder.vote.setOnClickListener(v -> actions.onVote(post));
        holder.comments.setOnClickListener(v -> actions.onComments(post));
    }

    @Override public int getItemCount() { return posts.size(); }

    private static String relativeTime(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) return "Just now";
        return timestamp.substring(0, 10) + " · community supplied";
    }

    static class PostHolder extends RecyclerView.ViewHolder {
        final TextView author, category, content, time;
        final ImageView image;
        final MaterialButton vote, comments;
        PostHolder(View view) {
            super(view);
            author = view.findViewById(R.id.communityPostAuthor);
            category = view.findViewById(R.id.communityPostCategory);
            content = view.findViewById(R.id.communityPostContent);
            image = view.findViewById(R.id.communityPostImage);
            time = view.findViewById(R.id.communityPostTime);
            vote = view.findViewById(R.id.communityVoteButton);
            comments = view.findViewById(R.id.communityCommentsButton);
        }
    }
}
