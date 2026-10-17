package com.safenavi.app;

import static org.junit.Assert.*;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.safenavi.app.safety.model.*;
import com.safenavi.app.safety.network.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;

/** Explicit opt-in read-only integration check. Never creates product records. */
@RunWith(AndroidJUnit4.class)
public class LiveMapSmokeInstrumentedTest {
    @Test public void realSearchRoutesAndCloudModelRender() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("liveProviders")));
        try (ActivityScenario<MapsActivity> scenario = ActivityScenario.launch(new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(), MapsActivity.class))) {
            AtomicReference<OpenMapService> provider=new AtomicReference<>();
            scenario.onActivity(activity -> {
                try { Field field=MapsActivity.class.getDeclaredField("openMapService"); field.setAccessible(true); provider.set((OpenMapService)field.get(activity)); }
                catch(Exception e) { throw new AssertionError(e); }
            });
            CountDownLatch searchReady=new CountDownLatch(1); AtomicReference<List<PlaceSearchResult>> places=new AtomicReference<>();
            AtomicReference<String> error=new AtomicReference<>();
            scenario.onActivity(activity -> provider.get().suggest("SIES Nerul",19.043,73.023,new OpenMapService.ResultCallback<List<PlaceSearchResult>>() {
                public void onSuccess(List<PlaceSearchResult> value) { places.set(value); searchReady.countDown(); }
                public void onError(String message) { error.set(message); searchReady.countDown(); }
            }));
            assertTrue(searchReady.await(30,TimeUnit.SECONDS)); assertNull(error.get()); assertFalse(places.get().isEmpty());
            PlaceSearchResult destination=places.get().get(0);
            assertTrue(destination.getDisplayName(),destination.getDisplayName().toLowerCase(Locale.ROOT).contains("sies"));
            PlaceSearchResult source=new PlaceSearchResult("Vashi Railway Station, Navi Mumbai",19.0657,72.9986);
            CountDownLatch routesReady=new CountDownLatch(1); AtomicReference<List<RouteCandidate>> roads=new AtomicReference<>();
            provider.get().routes(source.getLatitude(),source.getLongitude(),destination.getLatitude(),destination.getLongitude(),TravelMode.DRIVING,
                    new OpenMapService.ResultCallback<List<RouteCandidate>>() {
                        public void onSuccess(List<RouteCandidate> value) { roads.set(value); routesReady.countDown(); }
                        public void onError(String message) { error.set(message); routesReady.countDown(); }
                    });
            assertTrue(routesReady.await(30,TimeUnit.SECONDS)); assertNull(error.get()); assertFalse(roads.get().isEmpty());
            CountDownLatch riskReady=new CountDownLatch(1); AtomicReference<DatasetRouteEvaluation> evaluation=new AtomicReference<>();
            new DatasetRiskService().evaluateRoutes(roads.get(),RouteProfile.BALANCED,"morning_peak",new DatasetRiskService.ResultCallback<DatasetRouteEvaluation>() {
                public void onSuccess(DatasetRouteEvaluation value) { evaluation.set(value); riskReady.countDown(); }
                public void onError(String message) { error.set(message); riskReady.countDown(); }
            });
            assertTrue(riskReady.await(125,TimeUnit.SECONDS)); assertNull(error.get()); assertEquals("trained_ml",evaluation.get().getProvider());
            for(int i=0;i<120;i++) {
                AtomicReference<Boolean> ready=new AtomicReference<>(false);
                scenario.onActivity(activity -> { try { Field f=MapsActivity.class.getDeclaredField("mapStyle"); f.setAccessible(true); ready.set(f.get(activity)!=null); } catch(Exception e) { throw new AssertionError(e); } });
                if(ready.get()) break;
                android.os.SystemClock.sleep(250);
            }
            scenario.onActivity(activity -> { try {
                Method render=MapsActivity.class.getDeclaredMethod("renderRankedRoutes",List.class,List.class,RouteProfile.class,PlaceSearchResult.class,PlaceSearchResult.class,String.class,String.class);
                render.setAccessible(true); render.invoke(activity,evaluation.get().getRankedResults(),roads.get(),RouteProfile.BALANCED,source,destination,"OpenStreetMap / Valhalla","Trained ML · live smoke check");
            } catch(Exception e) { throw new AssertionError(e); } });
            android.os.SystemClock.sleep(5000); // Let the real map renderer complete its camera/tile frame.
            android.graphics.Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            java.io.File file=new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"map-live-1.2.png");
            try(java.io.FileOutputStream stream=new java.io.FileOutputStream(file)) { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream); }
            image.recycle();
        }
    }
}
