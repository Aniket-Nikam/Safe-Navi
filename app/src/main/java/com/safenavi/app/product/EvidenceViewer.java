package com.safenavi.app.product;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.Toast;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

public final class EvidenceViewer {
    private EvidenceViewer() { }
    public static void show(Activity activity, JSONArray evidence) {
        if (activity.isFinishing() || activity.isDestroyed() || evidence == null || evidence.length() == 0) return;
        String[] labels = new String[evidence.length()];
        for (int i = 0; i < labels.length; i++) {
            JSONObject item = evidence.optJSONObject(i);
            labels[i] = JsonDisplay.text(item, "original_name", "Evidence " + (i + 1))
                    + " · " + JsonDisplay.text(item, "media_type", "File");
        }
        new MaterialAlertDialogBuilder(activity).setTitle("Uploaded evidence").setItems(labels, (dialog, which) -> {
            JSONObject item = evidence.optJSONObject(which);
            if (item == null) return;
            if (!item.optString("media_type").startsWith("image/")) {
                new MaterialAlertDialogBuilder(activity).setTitle("Video evidence stored")
                        .setMessage("This file is stored securely. In-app preview currently supports photographs.")
                        .setPositiveButton("Close", null).show();
                return;
            }
            Toast.makeText(activity, "Loading secure photograph…", Toast.LENGTH_SHORT).show();
            new ProductApiClient(activity).downloadEvidence(item.optString("id"), new ProductApiClient.BytesCallback() {
                @Override public void onSuccess(byte[] bytes, String mediaType) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    options.inSampleSize = 1;
                    while (options.outWidth / options.inSampleSize > 1600 || options.outHeight / options.inSampleSize > 1600)
                        options.inSampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        if (bitmap == null) { Toast.makeText(activity, "The photograph could not be decoded.", Toast.LENGTH_LONG).show(); return; }
                        ImageView image = new ImageView(activity);
                        image.setAdjustViewBounds(true);
                        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        image.setImageBitmap(bitmap);
                        int height = Math.round(320 * activity.getResources().getDisplayMetrics().density);
                        image.setMaxHeight(height);
                        new MaterialAlertDialogBuilder(activity)
                                .setTitle(JsonDisplay.text(item, "original_name", "Evidence photograph"))
                                .setView(image).setPositiveButton("Close", null).show();
                    });
                }
                @Override public void onError(String message) { activity.runOnUiThread(() -> {
                    if (!activity.isFinishing() && !activity.isDestroyed()) Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                }); }
            });
        }).setNegativeButton("Close", null).show();
    }
}
