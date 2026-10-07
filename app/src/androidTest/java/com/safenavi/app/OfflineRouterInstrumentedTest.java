package com.safenavi.app;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.safenavi.app.safety.model.RouteProfile;
import com.safenavi.app.safety.model.TravelMode;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.network.OfflineRouter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OfflineRouterInstrumentedTest {
    @Test public void searchesBundledPlacesWithoutNetwork() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        OfflineRouter router = new OfflineRouter(context); CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<PlaceSearchResult>> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        router.search("Vashi", new OfflineRouter.Callback<List<PlaceSearchResult>>() {
            @Override public void onSuccess(List<PlaceSearchResult> value) { result.set(value); latch.countDown(); }
            @Override public void onError(String message) { error.set(message); latch.countDown(); }
        });
        assertTrue("offline search timed out", latch.await(60, TimeUnit.SECONDS));
        assertNull(error.get());
        assertTrue(result.get() != null && !result.get().isEmpty());
    }

    @Test public void routesAcrossNaviMumbaiWithoutNetwork() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        OfflineRouter router = new OfflineRouter(context); CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<OfflineRouter.Result> result = new AtomicReference<>(); AtomicReference<String> error = new AtomicReference<>();
        router.route(19.0760, 72.9986, 19.0330, 73.0169, TravelMode.DRIVING, RouteProfile.SAFEST,
                new OfflineRouter.Callback<OfflineRouter.Result>() {
                    @Override public void onSuccess(OfflineRouter.Result value) { result.set(value); latch.countDown(); }
                    @Override public void onError(String message) { error.set(message); latch.countDown(); }
                });
        assertTrue("offline route timed out", latch.await(60, TimeUnit.SECONDS));
        assertNull(error.get());
        assertTrue(result.get().route.getGeometry().size() > 2);
        assertTrue(result.get().route.getDistanceMeters() > 1000);
    }
}
