package com.safenavi.app;

import static org.junit.Assert.*;
import android.content.Intent;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.safenavi.app.safety.model.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.Style;

/** Isolated rendering test. These artificial paths are never written to product data. */
@RunWith(AndroidJUnit4.class)
public class MapPresentationInstrumentedTest {
    @Test public void allPreferencesAndEndpointMarkersRenderAndSelectWithoutClipping() throws Exception {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MapsActivity.class);
        try (ActivityScenario<MapsActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicReference<MapLibreMap> mapRef = new AtomicReference<>();
            for (int i = 0; i < 100 && mapRef.get() == null; i++) {
                scenario.onActivity(activity -> {
                    try { Field field = MapsActivity.class.getDeclaredField("map"); field.setAccessible(true); mapRef.set((MapLibreMap) field.get(activity)); }
                    catch (Exception error) { throw new AssertionError(error); }
                });
                if (mapRef.get() == null) android.os.SystemClock.sleep(100);
            }
            assertNotNull("Map did not initialise", mapRef.get());
            CountDownLatch ready = new CountDownLatch(1);
            scenario.onActivity(activity -> mapRef.get().setStyle(new Style.Builder().fromJson(
                    "{\"version\":8,\"sources\":{},\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"#e9f0ed\"}}]}"), style -> {
                try { Field field = MapsActivity.class.getDeclaredField("mapStyle"); field.setAccessible(true); field.set(activity, style); ready.countDown(); }
                catch (Exception error) { throw new AssertionError(error); }
            }));
            assertTrue("Local test map style did not load", ready.await(20, TimeUnit.SECONDS));
            scenario.onActivity(activity -> {
                try {
                    List<RouteCandidate> roads = new ArrayList<>(); List<RouteSafetyResult> evaluated = new ArrayList<>();
                    double[] minutes = {16.6, 20.2, 22}; double[] risks = {86.54, 53.82, 49.29};
                    for (int i = 0; i < 3; i++) {
                        RouteCandidate route = new RouteCandidate("test-" + i, minutes[i], 6000,
                                Arrays.asList(new HazardLocation.GeoPoint(19.0657, 72.9986),
                                        new HazardLocation.GeoPoint(19.05 + i * .001, 73.01),
                                        new HazardLocation.GeoPoint(19.042644, 73.023428)));
                        roads.add(route); evaluated.add(new RouteSafetyResult(route, RouteProfile.BALANCED, risks[i], 0,
                                Collections.singletonList(new RouteRiskPoint("test-risk", 19.05, 73.01,
                                        "Test estimate", "Synthetic test explanation", 70, false, "test"))));
                    }
                    Method method = MapsActivity.class.getDeclaredMethod("renderRankedRoutes", List.class, List.class,
                            RouteProfile.class, PlaceSearchResult.class, PlaceSearchResult.class, String.class, String.class);
                    method.setAccessible(true); method.invoke(activity, evaluated, roads, RouteProfile.BALANCED,
                            new PlaceSearchResult("Vashi · TEST", 19.0657, 72.9986),
                            new PlaceSearchResult("SIES GST Nerul · TEST", 19.042644, 73.023428), "Test geometry", "Test data");
                    TextView fastest = activity.findViewById(R.id.fastestRecommendation);
                    TextView balanced = activity.findViewById(R.id.balancedRecommendation);
                    TextView safest = activity.findViewById(R.id.safestRecommendation);
                    assertTrue(fastest.getText().toString().contains("16.6 min"));
                    assertTrue(balanced.getText().toString().contains("20.2 min"));
                    assertTrue(safest.getText().toString().contains("22.0 min"));
                    assertTrue(safest.performClick());
                    assertTrue(((TextView) activity.findViewById(R.id.routeResultTitle)).getText().toString().contains("Safest"));
                    assertNotNull(mapRef.get().getStyle().getLayer("endpoint-start-marker"));
                    assertNotNull(mapRef.get().getStyle().getLayer("endpoint-finish-marker"));
                    assertNotNull(mapRef.get().getStyle().getLayer("selected-route-risk-markers"));
                } catch (Exception error) { throw new AssertionError(error); }
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                android.view.View panel = activity.findViewById(R.id.routeControlCard);
                android.view.View search = activity.findViewById(R.id.routeSearchCard);
                assertTrue("Map needs visible space between search and results", panel.getTop() - search.getBottom() > 60);
                for (int id : new int[]{R.id.fastestRecommendation, R.id.balancedRecommendation, R.id.safestRecommendation}) {
                    TextView row = activity.findViewById(id);
                    assertTrue(row.isShown()); assertTrue("Preference text clipped", row.getLayout().getHeight() <= row.getHeight() - row.getPaddingTop() - row.getPaddingBottom());
                }
            });
            screenshot("map-redesign.png");
        }
    }

    @Test public void placeSearchUsesDedicatedSheetWithLocalityRows() throws Exception {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MapsActivity.class);
        try (ActivityScenario<MapsActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicReference<com.safenavi.app.product.PlaceSearchSheet> sheetRef = new AtomicReference<>();
            scenario.onActivity(activity -> {
                com.safenavi.app.product.PlaceSearchSheet sheet = new com.safenavi.app.product.PlaceSearchSheet(
                        activity, new com.safenavi.app.safety.network.OpenMapService(),
                        new com.safenavi.app.safety.network.OfflineRouter(activity), "Destination", "", 19, 73,
                        new com.safenavi.app.product.PlaceSearchSheet.Listener() {
                            public void onPlace(PlaceSearchResult place) { }
                            public void onMapPin() { }
                            public void onDeviceLocation() { }
                        });
                sheet.showResults(Collections.singletonList(new PlaceSearchResult("SIES GST, Nerul, Navi Mumbai", 19.04, 73.02)));
                sheetRef.set(sheet);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                try {
                    Field field = sheetRef.get().getClass().getDeclaredField("dialog"); field.setAccessible(true);
                    android.app.Dialog dialog = (android.app.Dialog) field.get(sheetRef.get());
                    assertTrue(dialog.isShowing());
                    android.view.View root = dialog.getWindow().getDecorView();
                    assertTrue(hasVisibleText(root, "SIES GST"));
                    assertTrue(hasVisibleText(root, "Nerul, Navi Mumbai"));
                } catch (Exception error) { throw new AssertionError(error); }
            });
            screenshot("map-search-redesign.png");
        }
    }
    private static boolean hasVisibleText(android.view.View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView)view).getText()) && view.isShown()) return true;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) if(hasVisibleText(group.getChildAt(i),text)) return true;
        }
        return false;
    }
    private static void screenshot(String name) throws Exception {
        android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        java.io.File output=new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name);
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(output)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream); }
        bitmap.recycle();
    }
}
