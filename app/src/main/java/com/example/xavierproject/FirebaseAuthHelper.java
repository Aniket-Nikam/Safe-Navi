package com.example.xavierproject;

import android.util.Log;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class FirebaseAuthHelper {

    private static final String TAG = "FirebaseAuthHelper";

    public interface EmailCallback {
        void onEmailFetched(String email);
        void onEmailNotFound();
    }

    /**
     * Get current user's email from Firebase Authentication
     */
    public static String getCurrentUserEmail() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            return currentUser.getEmail();
        }
        return null;
    }

    /**
     * Get user email by userId from Firebase Authentication
     * Note: This method looks up email from a user's profile in Realtime Database
     * because Firebase Auth doesn't allow querying other users' emails directly
     *
     * Alternative: Store email in Realtime Database when user registers
     */
    public static void getUserEmailByUserId(String userId, EmailCallback callback) {
        // Check if it's the current user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(userId)) {
            String email = currentUser.getEmail();
            if (email != null && !email.isEmpty()) {
                callback.onEmailFetched(email);
                return;
            }
        }

        // For other users, we need to get it from Realtime Database
        // You should store email in the database when user creates a report
        DatabaseReference usersRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
                .getReference("users")
                .child(userId);

        usersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String email = dataSnapshot.child("email").getValue(String.class);
                    if (email != null && !email.isEmpty()) {
                        callback.onEmailFetched(email);
                    } else {
                        Log.w(TAG, "Email not found for user: " + userId);
                        callback.onEmailNotFound();
                    }
                } else {
                    Log.w(TAG, "User data not found for userId: " + userId);
                    callback.onEmailNotFound();
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error fetching user email: " + databaseError.getMessage());
                callback.onEmailNotFound();
            }
        });
    }

    /**
     * Store user email in Realtime Database when they register or first login
     * Call this method in your login/registration flow
     */
    public static void saveUserEmailToDatabase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            String userId = currentUser.getUid();
            String email = currentUser.getEmail();
            String displayName = currentUser.getDisplayName();

            if (email != null) {
                DatabaseReference userRef = FirebaseDatabase.getInstance(BuildConfig.FIREBASE_DATABASE_URL)
                        .getReference("users")
                        .child(userId);

                // Create user profile object
                UserProfile userProfile = new UserProfile();
                userProfile.setEmail(email);
                userProfile.setName(displayName != null ? displayName : "Anonymous");
                userProfile.setUserId(userId);

                userRef.setValue(userProfile)
                        .addOnSuccessListener(aVoid ->
                                Log.d(TAG, "User email saved to database"))
                        .addOnFailureListener(e ->
                                Log.e(TAG, "Failed to save user email: " + e.getMessage()));
            }
        }
    }

    /**
     * Simple user profile class for database storage
     */
    public static class UserProfile {
        private String userId;
        private String email;
        private String name;

        public UserProfile() {
            // Required empty constructor for Firebase
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
