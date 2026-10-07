package com.safenavi.app.product;

import android.content.Context;
import android.content.SharedPreferences;

public final class ProductSession {
    private static final String FILE = "safe_navi_session";
    private ProductSession() { }
    private static SharedPreferences preferences(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public static void save(Context context, String token, String userId, String name, String email, String role) {
        preferences(context).edit().putString("token", token).putString("user_id", userId)
                .putString("name", name).putString("email", email).putString("role", role).apply();
    }
    public static boolean isSignedIn(Context context) { return !token(context).isEmpty(); }
    public static String token(Context context) { return preferences(context).getString("token", ""); }
    public static String name(Context context) { return preferences(context).getString("name", "Safe-Navi user"); }
    public static String email(Context context) { return preferences(context).getString("email", ""); }
    public static String role(Context context) { return preferences(context).getString("role", "citizen"); }
    public static void setActiveJourney(Context context, String journeyId) { preferences(context).edit().putString("active_journey_id", journeyId == null ? "" : journeyId).apply(); }
    public static String activeJourney(Context context) { return preferences(context).getString("active_journey_id", ""); }
    public static void setBackgroundJourneyEnabled(Context context, boolean enabled) { preferences(context).edit().putBoolean("background_journey_enabled", enabled).apply(); }
    public static boolean backgroundJourneyEnabled(Context context) { return preferences(context).getBoolean("background_journey_enabled", false); }
    public static void clear(Context context) { preferences(context).edit().clear().apply(); }
}
