package com.safenavi.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.snackbar.Snackbar;
import com.safenavi.app.product.ProductSession;
import com.safenavi.app.product.ProductApiClient;
import com.safenavi.app.product.SafetySyncWorker;
import com.safenavi.app.product.SafetyCheckInWorker;
import com.safenavi.app.product.OfflineMapManager;
import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsFragment extends Fragment {
    private SharedPreferences preferences;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
                                                  @Nullable ViewGroup container,
                                                  @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        preferences = requireActivity().getSharedPreferences("AppPreferences", 0);
        ((TextView) view.findViewById(R.id.usernameTextView)).setText(ProductSession.name(requireContext()));
        ((TextView) view.findViewById(R.id.emailTextView)).setText(ProductSession.email(requireContext()));
        view.findViewById(R.id.verificationCard).setVisibility(View.GONE);
        view.findViewById(R.id.changePasswordLayout).setVisibility(View.VISIBLE);
        view.findViewById(R.id.deleteAccountLayout).setVisibility(View.VISIBLE);
        view.findViewById(R.id.changePasswordLayout).setOnClickListener(v -> changePassword());
        view.findViewById(R.id.deleteAccountLayout).setOnClickListener(v -> deleteAccount());
        view.findViewById(R.id.emergencyContactsButton).setOnClickListener(v -> openEmergencyContacts());
        view.findViewById(R.id.journeyHistoryButton).setOnClickListener(v -> openJourneyHistory());
        view.findViewById(R.id.languageButton).setOnClickListener(v -> openLanguagePicker());
        view.findViewById(R.id.offlineMapsButton).setOnClickListener(v -> openOfflineMaps());
        view.findViewById(R.id.safetyCheckinsButton).setOnClickListener(v -> openSafetyCheckIns());
        SwitchCompat notifications = view.findViewById(R.id.notificationsSwitch);
        SwitchCompat darkMode = view.findViewById(R.id.darkModeSwitch);
        notifications.setChecked(preferences.getBoolean("notifications_enabled", true));
        notifications.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) return;
            preferences.edit().putBoolean("notifications_enabled", checked).apply();
            if (checked) SafetySyncWorker.schedule(requireContext()); else SafetySyncWorker.cancel(requireContext());
        });
        view.findViewById(R.id.notificationsLayout).setOnClickListener(v -> openSafetyInbox());
        refreshNotificationCount(view.findViewById(R.id.notificationsSubtitle));
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        darkMode.setChecked(nightMode == Configuration.UI_MODE_NIGHT_YES);
        darkMode.setOnCheckedChangeListener((button, checked) -> {
            if (!button.isPressed()) return;
            preferences.edit().putBoolean("dark_mode_enabled", checked).apply();
            AppCompatDelegate.setDefaultNightMode(checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });
        view.findViewById(R.id.logoutLayout).setOnClickListener(v -> logout());
        return view;
    }

    private void openLanguagePicker() {
        String[] labels = {"English", "हिन्दी", "मराठी"};
        String[] tags = {"en", "hi", "mr"};
        String current = preferences.getString("app_language", "en");
        int selected = "hi".equals(current) ? 1 : ("mr".equals(current) ? 2 : 0);
        new AlertDialog.Builder(requireContext()).setTitle(getString(R.string.language))
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    preferences.edit().putString("app_language", tags[which]).apply();
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[which]));
                    dialog.dismiss();
                }).setNegativeButton("Cancel", null).show();
    }

    private void openOfflineMaps() {
        OfflineMapManager.status(requireContext(), new OfflineMapManager.Callback() {
            @Override public void onStatus(String status, boolean downloaded) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> new AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.offline_maps))
                        .setMessage(status + "\n\nDownloaded tiles keep the base map visible without a connection. Live routes, search and new safety updates still require internet. Use Wi-Fi; the area can use significant storage.")
                        .setNegativeButton("Close", null)
                        .setNeutralButton(downloaded ? "Remove" : "Refresh", (d,w) -> {
                            if (downloaded) OfflineMapManager.remove(requireContext(), mapStatusCallback()); else openOfflineMaps();
                        })
                        .setPositiveButton(downloaded ? "Update" : "Download", (d,w) -> {
                            Snackbar.make(requireView(), "Offline map download started. You may keep using Safe-Navi.", Snackbar.LENGTH_LONG).show();
                            OfflineMapManager.download(requireContext(), mapStatusCallback());
                        }).show());
            }
        });
    }

    private OfflineMapManager.Callback mapStatusCallback() {
        return new OfflineMapManager.Callback() {
            private int lastPercent = -10;
            @Override public void onStatus(String message, boolean downloaded) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()); }
            @Override public void onProgress(long completed, long required, long bytes) {
                if (!isAdded() || required <= 0) return;
                int percent = (int) (completed * 100 / required);
                if (percent >= lastPercent + 10) { lastPercent = percent; requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), "Offline map " + percent + "% downloaded", Snackbar.LENGTH_SHORT).show()); }
            }
        };
    }

    private void openSafetyCheckIns() {
        String active = ProductSession.activeJourney(requireContext());
        if (active.isEmpty()) {
            new AlertDialog.Builder(requireContext()).setTitle(getString(R.string.safety_checkins))
                    .setMessage("Start a shared journey from the map first. Check-ins are tied to that consented journey so trusted viewers can see whether a response is overdue.")
                    .setPositiveButton("Close", null).show();
            return;
        }
        String[] labels = {"Every 15 minutes", "Every 30 minutes", "Every hour", "Every 2 hours", "Turn off"};
        Integer[] values = {15, 30, 60, 120, null};
        new AlertDialog.Builder(requireContext()).setTitle(getString(R.string.safety_checkins))
                .setItems(labels, (dialog, which) -> new ProductApiClient(requireContext()).scheduleCheckIn(active, values[which], new ProductApiClient.ObjectCallback() {
                    @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                        if (values[which] == null) SafetyCheckInWorker.cancel(requireContext()); else SafetyCheckInWorker.schedule(requireContext(), values[which]);
                        Snackbar.make(requireView(), values[which] == null ? "Safety check-ins turned off" : "Safety check-ins scheduled", Snackbar.LENGTH_LONG).show();
                    }); }
                    @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()); }
                })).setNegativeButton("Cancel", null).show();
    }

    private void refreshNotificationCount(TextView subtitle) {
        new ProductApiClient(requireContext()).notifications(new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) {
                if (!isAdded()) return;
                int unread = 0; for (int i = 0; i < values.length(); i++) if (values.optJSONObject(i) != null && values.optJSONObject(i).isNull("read_at")) unread++;
                final int count = unread;
                requireActivity().runOnUiThread(() -> subtitle.setText(count == 0 ? "Safety inbox · all caught up" : count + " unread safety update" + (count == 1 ? "" : "s")));
            }
            @Override public void onError(String message) { }
        });
    }

    private void openSafetyInbox() {
        ProductApiClient api = new ProductApiClient(requireContext());
        api.notifications(new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                StringBuilder content = new StringBuilder();
                for (int i = 0; i < Math.min(values.length(), 15); i++) {
                    JSONObject item = values.optJSONObject(i); if (item == null) continue;
                    content.append(item.isNull("read_at") ? "● " : "○ ").append(item.optString("title")).append("\n")
                            .append(item.optString("message")).append("\n").append(item.optString("created_at")).append("\n\n");
                    if (item.isNull("read_at")) api.markNotificationRead(item.optString("id"), new ProductApiClient.ObjectCallback() {
                        @Override public void onSuccess(JSONObject value) { }
                        @Override public void onError(String message) { }
                    });
                }
                new AlertDialog.Builder(requireContext()).setTitle("Safety inbox")
                        .setMessage(content.length() == 0 ? "No safety updates yet." : content.toString().trim())
                        .setPositiveButton("Done", null).show();
            }); }
            @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()); }
        });
    }

    private void logout() {
        new AlertDialog.Builder(requireContext()).setTitle("Sign out")
                .setMessage("Sign out of Safe-Navi on this device?")
                .setNegativeButton("Cancel", null).setPositiveButton("Sign out", (dialog, which) -> {
                    ProductApiClient api = new ProductApiClient(requireContext());
                    ProductApiClient.ObjectCallback finish = new ProductApiClient.ObjectCallback() {
                        private void leave() { if (!isAdded()) return; requireActivity().runOnUiThread(() -> {
                            ProductSession.clear(requireContext());
                            Intent intent = new Intent(requireContext(), LoginTypeActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                        }); }
                        @Override public void onSuccess(JSONObject value) { leave(); }
                        @Override public void onError(String message) { leave(); }
                    };
                    api.logout(finish);
                }).show();
    }

    private void changePassword() {
        LinearLayout form = new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(48, 8, 48, 0);
        EditText current = new EditText(requireContext()); current.setHint("Current password"); current.setInputType(129);
        EditText replacement = new EditText(requireContext()); replacement.setHint("New password (10+ characters)"); replacement.setInputType(129);
        form.addView(current); form.addView(replacement);
        new AlertDialog.Builder(requireContext()).setTitle("Change password").setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Change", (d,w) -> new ProductApiClient(requireContext()).changePassword(
                        current.getText().toString(), replacement.getText().toString(), sessionEndingCallback("Password changed. Sign in again."))).show();
    }

    private void deleteAccount() {
        EditText password = new EditText(requireContext()); password.setHint("Confirm your password"); password.setInputType(129); password.setPadding(48, 8, 48, 0);
        new AlertDialog.Builder(requireContext()).setTitle("Delete citizen account")
                .setMessage("Your identity and active sessions will be removed. Safety records remain anonymized for audit integrity. This cannot be undone.")
                .setView(password).setNegativeButton("Cancel", null).setPositiveButton("Delete permanently", (d,w) ->
                        new ProductApiClient(requireContext()).deleteAccount(password.getText().toString(), sessionEndingCallback("Account deleted."))).show();
    }

    private void openEmergencyContacts() {
        ProductApiClient api = new ProductApiClient(requireContext());
        api.emergencyContacts(new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                String[] labels = new String[values.length()]; for (int i = 0; i < values.length(); i++) {
                    JSONObject item = values.optJSONObject(i); labels[i] = item.optString("name") + " · " + item.optString("relationship") + "\n" + item.optString("phone");
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(requireContext()).setTitle("Emergency contacts")
                        .setNegativeButton("Close", null).setPositiveButton("Add contact", (d,w) -> addEmergencyContact());
                if (labels.length == 0) builder.setMessage("Add a trusted person before starting journey sharing.");
                else builder.setItems(labels, (d, which) -> {
                    JSONObject item = values.optJSONObject(which); if (item == null) return;
                    new AlertDialog.Builder(requireContext()).setTitle(item.optString("name"))
                            .setMessage(item.optString("relationship") + "\n" + item.optString("phone"))
                            .setPositiveButton("Call", (choice, button) -> startActivity(new Intent(Intent.ACTION_DIAL,
                                    android.net.Uri.parse("tel:" + android.net.Uri.encode(item.optString("phone"))))))
                            .setNegativeButton("Delete", (choice, button) -> api.deleteEmergencyContact(item.optString("id"), new ProductApiClient.ObjectCallback() {
                                @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), "Emergency contact deleted", Snackbar.LENGTH_SHORT).show()); }
                                @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()); }
                            })).setNeutralButton("Cancel", null).show();
                });
                builder.show();
            }); }
            @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()); }
        });
    }

    private void addEmergencyContact() {
        LinearLayout form = new LinearLayout(requireContext()); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(48, 8, 48, 0);
        EditText name = new EditText(requireContext()); name.setHint("Name");
        EditText phone = new EditText(requireContext()); phone.setHint("Phone number"); phone.setInputType(3);
        EditText relationship = new EditText(requireContext()); relationship.setHint("Relationship");
        form.addView(name); form.addView(phone); form.addView(relationship);
        new AlertDialog.Builder(requireContext()).setTitle("Add trusted contact").setView(form).setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d,w) -> new ProductApiClient(requireContext()).addEmergencyContact(
                        name.getText().toString(), phone.getText().toString(), relationship.getText().toString(), new ProductApiClient.ObjectCallback() {
                            @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), "Emergency contact saved", Snackbar.LENGTH_SHORT).show()); }
                            @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()); }
                        })).show();
    }

    private void openJourneyHistory() {
        new ProductApiClient(requireContext()).journeys(new ProductApiClient.ArrayCallback() {
            @Override public void onSuccess(JSONArray values) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                StringBuilder history = new StringBuilder(); for (int i = 0; i < Math.min(values.length(), 20); i++) {
                    JSONObject item = values.optJSONObject(i); if (item == null) continue;
                    history.append("• ").append(item.optString("destination_label")).append("\n  ")
                            .append(item.optString("status")).append(" · ").append(item.optString("travel_mode").toLowerCase())
                            .append(" · ").append(item.optString("route_profile").toLowerCase()).append("\n  ")
                            .append(item.optString("route_summary")).append("\n  ").append(item.optString("started_at")).append("\n\n");
                }
                new AlertDialog.Builder(requireContext()).setTitle("Journey history")
                        .setMessage(history.length() == 0 ? "No journeys recorded yet." : history.toString().trim()).setPositiveButton("Close", null).show();
            }); }
            @Override public void onError(String message) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show()); }
        });
    }

    private ProductApiClient.ObjectCallback sessionEndingCallback(String message) {
        return new ProductApiClient.ObjectCallback() {
            @Override public void onSuccess(JSONObject value) { if (isAdded()) requireActivity().runOnUiThread(() -> {
                Snackbar.make(requireView(), message, Snackbar.LENGTH_SHORT).show(); ProductSession.clear(requireContext());
                Intent intent = new Intent(requireContext(), LoginTypeActivity.class); intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); startActivity(intent);
            }); }
            @Override public void onError(String error) { if (isAdded()) requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), error, Snackbar.LENGTH_LONG).show()); }
        };
    }
}
