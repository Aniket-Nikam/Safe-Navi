package com.safenavi.app.safety;

import static org.junit.Assert.*;
import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.presentation.RouteColors;
import org.junit.Test;

public class RouteColorsTest {
    @Test public void preferencesUseTheApprovedPalette() {
        assertEquals(0xFF2563EB, RouteColors.forProfile(RouteProfile.FASTEST));
        assertEquals(0xFF7C3AED, RouteColors.forProfile(RouteProfile.BALANCED));
        assertEquals(0xFF00796B, RouteColors.forProfile(RouteProfile.SAFEST));
    }

    @Test public void navigationAndMapUseTheSameTokens() {
        for (RouteProfile profile : RouteProfile.values())
            assertEquals(RouteColors.forProfile(profile), RouteColors.forProfileName(profile.name()));
        assertEquals(RouteColors.BALANCED, RouteColors.forProfileName(null));
        assertEquals(RouteColors.BALANCED, RouteColors.forProfileName("unrecognised"));
    }

    @Test public void routeColorsContrastWithTheirOpaqueWhiteCasing() {
        assertEquals(0xFFFFFFFF, RouteColors.CASING);
        for (RouteProfile profile : RouteProfile.values()) {
            int color = RouteColors.forProfile(profile);
            double luminance = .2126 * linear((color >> 16) & 255)
                    + .7152 * linear((color >> 8) & 255) + .0722 * linear(color & 255);
            assertTrue("Route must remain distinct from its white outline", 1.05 / (luminance + .05) >= 4.5);
        }
    }

    @Test public void selectionHasAVisibleNonColorCue() {
        assertTrue(RouteColors.SELECTED_WIDTH > RouteColors.ALTERNATIVE_WIDTH);
        assertTrue(RouteColors.CASING_EXTRA_WIDTH >= 4f);
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
}
