package com.example.xavierproject;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

public class CloudinaryHelper {

    private static final String TAG = "CloudinaryHelper";
    private static final String CLOUD_NAME = BuildConfig.CLOUD_NAME;
    private static final String UPLOAD_PRESET = BuildConfig.UPLOAD_PRESET;
    private static final String UPLOAD_URL = "https://api.cloudinary.com/v1_1/" + CLOUD_NAME + "/image/upload";

    private static OkHttpClient client;
    private static Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Initialize OkHttp client with optimized settings
     */
    private static OkHttpClient getClient() {
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return client;
    }

    /**
     * Upload image to Cloudinary using unsigned upload with OkHttp
     * @param context Application context
     * @param imageUri URI of the image to upload
     * @param callback Callback to handle upload result
     */
    public static void uploadImage(Context context, Uri imageUri, CloudinaryUploadCallback callback) {
        uploadImage(context, imageUri, UPLOAD_PRESET, callback);
    }

    /**
     * Upload image to Cloudinary with custom preset
     * @param context Application context
     * @param imageUri URI of the image to upload
     * @param uploadPreset Your unsigned upload preset name
     * @param callback Callback to handle upload result
     */
    public static void uploadImage(Context context, Uri imageUri, String uploadPreset, CloudinaryUploadCallback callback) {
        if (imageUri == null) {
            runOnMainThread(() -> callback.onError("Image URI is null"));
            return;
        }

        runOnMainThread(() -> callback.onStart());

        new Thread(() -> {
            File imageFile = null;
            try {
                // Convert URI to File
                imageFile = getFileFromUri(context, imageUri);
                if (imageFile == null) {
                    final String error = "Failed to process image file";
                    runOnMainThread(() -> callback.onError(error));
                    return;
                }

                // Get MIME type and extension
                String mimeType = context.getContentResolver().getType(imageUri);
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
                if (extension == null) {
                    extension = "jpg";
                }

                // Determine media type
                MediaType mediaType = MediaType.parse(mimeType != null ? mimeType : "image/*");

                // Build multipart request
                final File finalImageFile = imageFile;
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "image." + extension,
                                RequestBody.create(finalImageFile, mediaType))
                        .addFormDataPart("upload_preset", uploadPreset)
                        .addFormDataPart("folder", "XavierProject/reports")
                        .addFormDataPart("resource_type", "image")
                        .build();

                Request request = new Request.Builder()
                        .url(UPLOAD_URL)
                        .post(requestBody)
                        .build();

                Log.d(TAG, "Starting upload to Cloudinary...");
                Log.d(TAG, "File size: " + finalImageFile.length() + " bytes");
                runOnMainThread(() -> callback.onProgress(10));

                // Execute upload
                final File tempFile = imageFile;
                getClient().newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        Log.e(TAG, "Upload failed: " + e.getMessage());
                        final String errorMsg = "Network error: " + e.getMessage();
                        runOnMainThread(() -> callback.onError(errorMsg));
                        cleanupTempFile(tempFile);
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) {
                        try {
                            runOnMainThread(() -> callback.onProgress(70));

                            if (response.isSuccessful() && response.body() != null) {
                                String responseData = response.body().string();
                                Log.d(TAG, "Upload response: " + responseData);

                                runOnMainThread(() -> callback.onProgress(90));

                                // Parse JSON response
                                JSONObject jsonObject = new JSONObject(responseData);
                                String imageUrl = jsonObject.getString("secure_url");
                                String publicId = jsonObject.getString("public_id");

                                // Get additional metadata
                                int width = jsonObject.optInt("width", 0);
                                int height = jsonObject.optInt("height", 0);
                                long bytes = jsonObject.optLong("bytes", 0);

                                Log.d(TAG, "Upload successful!");
                                Log.d(TAG, "Image URL: " + imageUrl);
                                Log.d(TAG, "Public ID: " + publicId);
                                Log.d(TAG, "Dimensions: " + width + "x" + height);
                                Log.d(TAG, "Size: " + bytes + " bytes");

                                runOnMainThread(() -> {
                                    callback.onProgress(100);
                                    callback.onSuccess(imageUrl, publicId);
                                });
                            } else {
                                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                                Log.e(TAG, "Upload failed with code: " + response.code());
                                Log.e(TAG, "Error body: " + errorBody);

                                final String errorMsg = parseErrorMessage(response.code(), errorBody);
                                runOnMainThread(() -> callback.onError(errorMsg));
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing response: " + e.getMessage());
                            final String errorMsg = "Error processing response: " + e.getMessage();
                            runOnMainThread(() -> callback.onError(errorMsg));
                        } finally {
                            response.close();
                            cleanupTempFile(tempFile);
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Upload preparation failed: " + e.getMessage());
                e.printStackTrace();
                final String errorMsg = "Failed to prepare upload: " + e.getMessage();
                final File finalFile = imageFile;
                runOnMainThread(() -> callback.onError(errorMsg));
                cleanupTempFile(finalFile);
            }
        }).start();
    }

    /**
     * Parse error message from response
     */
    private static String parseErrorMessage(int code, String errorBody) {
        try {
            JSONObject errorJson = new JSONObject(errorBody);
            if (errorJson.has("error")) {
                JSONObject error = errorJson.getJSONObject("error");
                String message = error.optString("message", "Unknown error");
                return "Upload failed: " + message;
            }
        } catch (Exception e) {
            // Ignore JSON parsing errors
        }

        switch (code) {
            case 400:
                return "Invalid upload request. Check your upload preset.";
            case 401:
                return "Authentication failed. Check your Cloudinary credentials.";
            case 403:
                return "Access denied. Verify your upload preset permissions.";
            case 420:
                return "Rate limit exceeded. Please try again later.";
            case 500:
                return "Cloudinary server error. Please try again.";
            default:
                return "Upload failed with code: " + code;
        }
    }

    /**
     * Convert URI to File with improved error handling
     */
    private static File getFileFromUri(Context context, Uri uri) {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream from URI");
                return null;
            }

            // Create temp file with timestamp
            String fileName = "temp_upload_" + System.currentTimeMillis() + ".jpg";
            File tempFile = new File(context.getCacheDir(), fileName);
            outputStream = new FileOutputStream(tempFile);

            byte[] buffer = new byte[8192]; // Increased buffer size
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            outputStream.flush();

            Log.d(TAG, "Temp file created: " + tempFile.getAbsolutePath());
            Log.d(TAG, "File size: " + totalBytes + " bytes");

            return tempFile;
        } catch (Exception e) {
            Log.e(TAG, "Error creating file from URI: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing streams: " + e.getMessage());
            }
        }
    }

    /**
     * Clean up temporary file
     */
    private static void cleanupTempFile(File file) {
        if (file != null && file.exists()) {
            if (file.delete()) {
                Log.d(TAG, "Temp file deleted: " + file.getName());
            } else {
                Log.w(TAG, "Failed to delete temp file: " + file.getName());
            }
        }
    }

    /**
     * Run callback on main thread
     */
    private static void runOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mainHandler.post(runnable);
        }
    }

    /**
     * Get optimized image URL with transformations
     */
    public static String getOptimizedImageUrl(String publicId, int width, int height) {
        return String.format(
                "https://res.cloudinary.com/%s/image/upload/w_%d,h_%d,c_fill,q_auto,f_auto/%s",
                CLOUD_NAME, width, height, publicId
        );
    }

    /**
     * Get thumbnail URL
     */
    public static String getThumbnailUrl(String publicId) {
        return getOptimizedImageUrl(publicId, 200, 200);
    }

    /**
     * Get responsive image URL
     */
    public static String getResponsiveImageUrl(String publicId, int width) {
        return String.format(
                "https://res.cloudinary.com/%s/image/upload/w_%d,c_scale,q_auto,f_auto/%s",
                CLOUD_NAME, width, publicId
        );
    }

    /**
     * Get original image URL from public ID
     */
    public static String getImageUrl(String publicId) {
        return String.format(
                "https://res.cloudinary.com/%s/image/upload/%s",
                CLOUD_NAME, publicId
        );
    }

    /**
     * Get image URL with quality optimization
     */
    public static String getImageUrlWithQuality(String publicId, int quality) {
        return String.format(
                "https://res.cloudinary.com/%s/image/upload/q_%d/%s",
                CLOUD_NAME, quality, publicId
        );
    }

    /**
     * Callback interface for upload operations
     */
    public interface CloudinaryUploadCallback {
        void onStart();
        void onProgress(int progress);
        void onSuccess(String imageUrl, String publicId);
        void onError(String error);
    }
}
