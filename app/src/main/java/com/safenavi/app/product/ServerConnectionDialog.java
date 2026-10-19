package com.safenavi.app.product;

import android.app.Activity;
import android.content.Intent;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.safenavi.app.LoginTypeActivity;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

public final class ServerConnectionDialog {
    private ServerConnectionDialog() { }
    public static void show(Activity activity) {
        show(activity, ServerConnection.baseUrl());
    }
    public static void show(Activity activity, String initialAddress) {
        EditText input=new EditText(activity); input.setText(initialAddress); input.setTextSize(14);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(40,20,40,20);
        AlertDialog dialog=new MaterialAlertDialogBuilder(activity).setTitle("Server connection")
                .setMessage("Enter your hosted HTTPS address, or the laptop address shown by Start Safe-Navi. Only laptop hosting requires the same network. A sleeping free cloud server may take about a minute to wake. Changing the address signs you out.")
                .setView(input).setNeutralButton("Use cloud", null).setNegativeButton("Cancel",null).setPositiveButton("Test and save",null).create();
        dialog.setOnShowListener(shown -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                input.setText(ServerConnection.CLOUD_ORIGIN);
                input.setError(null);
            });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            final String address;
            try { address=ServerConnection.normalize(input.getText().toString()); }
            catch(IllegalArgumentException error){input.setError(error.getMessage());return;}
            if (ServerConnection.requiresCleanQueue(ServerConnection.baseUrl(), address)
                    && OfflineReportQueue.entriesForCurrentServer(activity).length() > 0) {
                input.setError("Upload pending offline reports to the current server before switching cloud servers. Your reports and photographs have not been deleted.");
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Connecting…");
            new Thread(() -> {
                String error=null;
                try {
                    OkHttpClient client=new OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
                            .readTimeout(90,TimeUnit.SECONDS).callTimeout(120,TimeUnit.SECONDS).followRedirects(false).build();
                    try(Response response=client.newCall(new Request.Builder().url(address+"/health").build()).execute()) {
                        if(!response.isSuccessful() || response.body()==null) throw new IllegalStateException("Server health check failed");
                        JSONObject health=new JSONObject(response.body().string());
                        if(!"ok".equals(health.optString("status")) || !health.has("risk_provider")) throw new IllegalStateException("This address is not a healthy Safe-Navi server");
                    }
                } catch(Exception problem){error="Cannot reach Safe-Navi. Check the HTTPS address and cloud service status; for laptop hosting, check the launcher, Wi-Fi and firewall.";}
                final String message=error;
                activity.runOnUiThread(() -> {
                    if(activity.isFinishing() || activity.isDestroyed() || !dialog.isShowing()) return;
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText("Test and save");
                    if(message!=null){input.setError(message);return;}
                    boolean changed=!ServerConnection.baseUrl().equals(address);
                    if(changed){
                        JourneyTrackingService.stop(activity);ProductSession.clear(activity);
                        com.safenavi.app.safety.network.OfflineHazardCache.clear(activity);
                        activity.getSharedPreferences("safe_navi_background_alerts",0).edit().clear().apply();
                    }
                    ServerConnection.save(activity,address);dialog.dismiss();
                    Toast.makeText(activity,"Safe-Navi server connected",Toast.LENGTH_LONG).show();
                    if(changed) activity.startActivity(new Intent(activity,LoginTypeActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                });
            },"safenavi-connection-check").start();
        }); });
        dialog.show();
    }
}
