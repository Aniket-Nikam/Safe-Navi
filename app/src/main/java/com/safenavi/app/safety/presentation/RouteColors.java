package com.safenavi.app.safety.presentation;

import com.safenavi.app.safety.model.RouteProfile;

/** Shared presentation tokens only; never used to score or choose a road. */
public final class RouteColors {
    public static final int FASTEST = 0xFF2563EB;
    public static final int BALANCED = 0xFF7C3AED;
    public static final int SAFEST = 0xFF00796B;
    public static final int SHARED = 0xFF526C85;
    public static final int CASING = 0xFFFFFFFF;
    public static final float SELECTED_WIDTH = 8f;
    public static final float ALTERNATIVE_WIDTH = 5f;
    public static final float CASING_EXTRA_WIDTH = 4f;

    private RouteColors() { }

    public static int forProfile(RouteProfile profile) {
        if (profile == RouteProfile.FASTEST) return FASTEST;
        if (profile == RouteProfile.SAFEST) return SAFEST;
        return BALANCED;
    }

    public static int forProfileName(String name) {
        return forProfile("FASTEST".equals(name) ? RouteProfile.FASTEST
                : "SAFEST".equals(name) ? RouteProfile.SAFEST : RouteProfile.BALANCED);
    }
}
