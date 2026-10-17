package com.safenavi.app.product;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.safenavi.app.R;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.network.OpenMapService;
import com.safenavi.app.safety.network.OfflineRouter;
import com.safenavi.app.safety.service.PlaceRelevance;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Call;

/** Cancellable, debounced Photon suggestions with an explicit Nominatim search action. */
public final class PlaceSearchSheet {
    public interface Listener {
        void onPlace(PlaceSearchResult place);
        void onMapPin();
        void onDeviceLocation();
    }
    private final Activity activity;
    private final OpenMapService provider;
    private final OfflineRouter offline;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BottomSheetDialog dialog;
    private final TextInputEditText input;
    private final TextView status;
    private final Results adapter = new Results();
    private final double latitude, longitude;
    private Runnable pending;
    private Call suggestion;
    private volatile int generation;
    private long lastRequest;

    public PlaceSearchSheet(Activity activity, OpenMapService provider, OfflineRouter offline,
                            String title, String initial, double latitude, double longitude,
                            Listener listener) {
        this.activity = activity; this.provider = provider; this.offline = offline;
        this.latitude = latitude; this.longitude = longitude; this.listener = listener;
        dialog = new BottomSheetDialog(activity);
        LinearLayout root = new LinearLayout(activity); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(14), dp(18), dp(12));
        root.setBackgroundColor(color(R.color.surface));
        LinearLayout heading = new LinearLayout(activity); heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView label = label(title, 20, true); heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton close = button("Close"); close.setOnClickListener(v -> dialog.dismiss());
        heading.addView(close, new LinearLayout.LayoutParams(-2, dp(48))); root.addView(heading);
        TextInputLayout field = new TextInputLayout(activity); field.setHint("Search a place or address");
        field.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        input = new TextInputEditText(activity); input.setTextSize(16); input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        field.addView(input, new LinearLayout.LayoutParams(-1, dp(60))); root.addView(field);
        LinearLayout actions = new LinearLayout(activity);
        MaterialButton locate = button("My location"), pin = button("Choose on map"), search = button("Go");
        locate.setOnClickListener(v -> { dialog.dismiss(); listener.onDeviceLocation(); });
        pin.setOnClickListener(v -> { dialog.dismiss(); listener.onMapPin(); });
        search.setOnClickListener(v -> submitBest());
        for (MaterialButton action : new MaterialButton[]{locate, pin, search})
            actions.addView(action, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(actions);
        status = label("Mumbai & Navi Mumbai · type at least 3 characters", 12, false);
        status.setPadding(0, dp(8), 0, dp(8)); root.addView(status);
        RecyclerView results = new RecyclerView(activity); results.setLayoutManager(new LinearLayoutManager(activity));
        results.setAdapter(adapter); root.addView(results, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView attribution = label("Places: OpenStreetMap · Photon / Nominatim. Coverage may be incomplete.", 11, false);
        root.addView(attribution);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, Math.round(activity.getResources().getDisplayMetrics().heightPixels * .88f)));
        dialog.setContentView(root);
        if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                invalidate(); adapter.set(new ArrayList<>());
                String query = s.toString().trim();
                if (query.length() < 3) { status.setText("Type at least 3 characters, or choose a point on the map."); return; }
                status.setText("Searching nearby places…");
                pending = () -> search(false);
                handler.postDelayed(pending, Math.max(900, 1100 - (android.os.SystemClock.elapsedRealtime() - lastRequest)));
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnEditorActionListener((v, action, event) -> {
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) { submitBest(); return true; }
            return false;
        });
        dialog.setOnDismissListener(d -> {
            invalidate();
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
        });
        dialog.setOnShowListener(d -> {
            dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
            dialog.getBehavior().setSkipCollapsed(true);
            input.requestFocus();
        });
        input.setText("My location".equalsIgnoreCase(initial.trim()) ? "" : initial);
    }

    public void show() { dialog.show(); }
    public void showResults(List<PlaceSearchResult> places) {
        invalidate(); adapter.set(ranked(places));
        status.setText("Go uses the best match. Or tap another place below."); dialog.show();
    }
    private void invalidate() {
        generation++;
        if (pending != null) handler.removeCallbacks(pending);
        if (suggestion != null) suggestion.cancel();
        suggestion = null;
    }
    private void search(boolean explicit) {
        if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()) return;
        invalidate(); String query = input.getText() == null ? "" : input.getText().toString().trim();
        if (query.length() < 3) { status.setText("Enter at least 3 characters."); return; }
        int request = generation; lastRequest = android.os.SystemClock.elapsedRealtime();
        status.setText(explicit ? "Searching addresses…" : "Searching nearby places…");
        OpenMapService.ResultCallback<List<PlaceSearchResult>> callback = new OpenMapService.ResultCallback<List<PlaceSearchResult>>() {
            @Override public void onSuccess(List<PlaceSearchResult> places) { activity.runOnUiThread(() -> {
                if (!active(request)) return;
                List<PlaceSearchResult> matches=ranked(places); adapter.set(matches);
                if(explicit && !matches.isEmpty()) { choose(matches.get(0)); return; }
                status.setText(places.isEmpty() ? "No match. Try the full name and locality, tap Search, or choose on map."
                        : "Go uses the best match. Or tap another place below.");
            }); }
            @Override public void onError(String message) {
                if (!active(request)) return;
                offline.search(query, new OfflineRouter.Callback<List<PlaceSearchResult>>() {
                    @Override public void onSuccess(List<PlaceSearchResult> places) { activity.runOnUiThread(() -> {
                        if (!active(request)) return; List<PlaceSearchResult> matches=ranked(places); adapter.set(matches);
                        if(explicit && !matches.isEmpty()) { choose(matches.get(0)); return; }
                        status.setText("Offline graph matches · limited Navi Mumbai coverage");
                    }); }
                    @Override public void onError(String ignored) { activity.runOnUiThread(() -> {
                        if (active(request)) status.setText("Search provider unavailable. Retry Search or choose the exact point on the map.");
                    }); }
                });
            }
        };
        if (explicit) provider.search(query, callback);
        else suggestion = provider.suggest(query, latitude, longitude, callback);
    }
    private List<PlaceSearchResult> ranked(List<PlaceSearchResult> places) {
        return PlaceRelevance.rank(input.getText()==null?"":input.getText().toString(),OpenMapService.uniquePlaces(places),latitude,longitude);
    }
    private void choose(PlaceSearchResult place) { dialog.dismiss(); listener.onPlace(place); }
    private void submitBest() {
        String query=input.getText()==null?"":input.getText().toString().trim();
        if("my location".equalsIgnoreCase(query) || "current location".equalsIgnoreCase(query)) {
            dialog.dismiss(); listener.onDeviceLocation(); return;
        }
        if(!adapter.places.isEmpty()) { choose(adapter.places.get(0)); return; }
        search(true);
    }
    private boolean active(int request) { return request == generation && dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed(); }
    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }
    private int color(int value) { return ContextCompat.getColor(activity, value); }
    private TextView label(String text, int size, boolean bold) {
        TextView label = new TextView(activity); label.setText(text); label.setTextSize(size);
        label.setTextColor(color(bold ? R.color.text_primary : R.color.text_secondary));
        if (bold) label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        return label;
    }
    private MaterialButton button(String text) {
        MaterialButton button = new MaterialButton(activity, null, com.google.android.material.R.attr.borderlessButtonStyle);
        button.setText(text); button.setTextSize(12); button.setLetterSpacing(0); button.setAllCaps(false);
        button.setPadding(dp(5), 0, dp(5), 0); button.setMinWidth(0); button.setMinimumWidth(0);
        return button;
    }
    private final class Results extends RecyclerView.Adapter<ResultHolder> {
        private final List<PlaceSearchResult> places = new ArrayList<>();
        void set(List<PlaceSearchResult> items) { places.clear(); places.addAll(items); notifyDataSetChanged(); }
        @NonNull @Override public ResultHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(activity); row.setOrientation(LinearLayout.VERTICAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            row.setPadding(dp(12), dp(12), dp(12), dp(12)); row.setMinimumHeight(dp(72));
            TextView name = label("", 16, true), address = label("", 13, false);
            name.setMaxLines(2); address.setMaxLines(2); address.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(name); row.addView(address);
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            return new ResultHolder(row, name, address);
        }
        @Override public void onBindViewHolder(@NonNull ResultHolder holder, int position) {
            PlaceSearchResult place = places.get(position);
            holder.name.setText(place.getPrimaryText()); holder.address.setText(place.getSecondaryText());
            holder.itemView.setContentDescription(place.getDisplayName());
            holder.itemView.setOnClickListener(v -> { dialog.dismiss(); listener.onPlace(place); });
        }
        @Override public int getItemCount() { return places.size(); }
    }
    private static final class ResultHolder extends RecyclerView.ViewHolder {
        final TextView name, address;
        ResultHolder(View view, TextView name, TextView address) { super(view); this.name = name; this.address = address; }
    }
}
