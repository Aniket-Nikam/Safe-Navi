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
    @Test public void lateHazardResponseAfterLeavingMapIsIgnored() throws Exception {
        AtomicReference<MapsActivity> closedMap = new AtomicReference<>();
        try(ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),MapsActivity.class))) {
            scenario.onActivity(closedMap::set);
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                assertTrue(closedMap.get().isDestroyed());
                Method apply=MapsActivity.class.getDeclaredMethod("applyHazardResponse",org.json.JSONObject.class,boolean.class);
                apply.setAccessible(true);
                apply.invoke(closedMap.get(),new org.json.JSONObject(),false);
            } catch(Exception error) { throw new AssertionError(error); }
        });
    }
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
                    "{\"version\":8,\"glyphs\":\"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf\",\"sources\":{},\"layers\":[{\"id\":\"background\",\"type\":\"background\",\"paint\":{\"background-color\":\"#e9f0ed\"}}]}"), style -> {
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
                    assertTrue(fastest.getText().toString().contains("17 min"));
                    assertTrue(balanced.getText().toString().contains("20 min"));
                    assertTrue(safest.getText().toString().contains("22 min"));
                    String[] names={"fastest","balanced","safest"};
                    int[] colors={android.graphics.Color.parseColor("#E53935"),android.graphics.Color.parseColor("#F4C430"),android.graphics.Color.parseColor("#168A4A")};
                    Field layerField=MapsActivity.class.getDeclaredField("ROUTES_LAYER");layerField.setAccessible(true);
                    String layerPrefix=(String)layerField.get(null);
                    for(int i=0;i<3;i++){
                        org.maplibre.android.style.layers.LineLayer layer=mapRef.get().getStyle().getLayerAs(layerPrefix+"-"+names[i]);
                        assertNotNull(layer);assertEquals(colors[i],layer.getLineColorAsInt());
                        Float offset=layer.getLineOffset().getValue();
                        // Unset is MapLibre's zero-offset default; explicit zero is also valid.
                        if(offset!=null) assertEquals("Real roads must not be shifted into artificial parallel paths",0f,offset,.01f);
                    }
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
                    assertTrue("Three compact choices must be visible together",row.isShown());
                }
                assertTrue("Both endpoints must remain editable after comparison",activity.findViewById(R.id.endpointEditor).isShown());
                assertFalse(activity.findViewById(R.id.safetyScoreCard).isShown());
                assertEquals("none",mapRef.get().getStyle().getLayer("selected-route-risk-markers").getVisibility().getValue());
            });
            android.os.SystemClock.sleep(2500); // Wait for the native map frame and camera animation, not only UI idle.
            screenshot("map-three-routes-1.5.png");
            scenario.onActivity(activity -> activity.findViewById(R.id.findRouteButton).performClick());
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                try {
                    Field field=MapsActivity.class.getDeclaredField("activeMapSheet"); field.setAccessible(true);
                    android.app.Dialog sheet=(android.app.Dialog)field.get(activity);
                    assertNotNull(sheet); assertTrue(hasVisibleText(sheet.getWindow().getDecorView(),"Share route"));
                } catch(Exception e) { throw new AssertionError(e); }
            });
            screenshot("map-details-1.5.png");
            scenario.onActivity(activity -> {
                try {
                    Field sheetField=MapsActivity.class.getDeclaredField("activeMapSheet");sheetField.setAccessible(true);
                    ((android.app.Dialog)sheetField.get(activity)).dismiss();
                    RouteCandidate road=new RouteCandidate("shared-test",18,6000,Arrays.asList(
                        new HazardLocation.GeoPoint(19.0657,72.9986),new HazardLocation.GeoPoint(19.05,73.01),new HazardLocation.GeoPoint(19.042644,73.023428)));
                    RouteSafetyResult result=new RouteSafetyResult(road,RouteProfile.BALANCED,51,0,Collections.emptyList());
                    Method render=MapsActivity.class.getDeclaredMethod("renderRankedRoutes",List.class,List.class,RouteProfile.class,PlaceSearchResult.class,PlaceSearchResult.class,String.class,String.class);
                    render.setAccessible(true);render.invoke(activity,Collections.singletonList(result),Collections.singletonList(road),RouteProfile.BALANCED,
                        new PlaceSearchResult("A · TEST",19.0657,72.9986),new PlaceSearchResult("B · TEST",19.042644,73.023428),"Test geometry","Test only");
                    org.maplibre.android.style.layers.LineLayer shared=mapRef.get().getStyle().getLayerAs("safe-navi-route-options-fastest");
                    assertNotNull("The shared road must be rendered once",shared);
                    assertEquals(android.graphics.Color.parseColor("#526C85"),shared.getLineColorAsInt());
                    for(String name:new String[]{"balanced","safest"}){
                        assertNull("Do not duplicate identical geometry",mapRef.get().getStyle().getLayer("safe-navi-route-options-"+name));
                        assertNull(mapRef.get().getStyle().getSource("safe-navi-routes-"+name));
                    }
                    assertEquals("All three recommend the same road",((TextView)activity.findViewById(R.id.routeStatusText)).getText().toString());
                    assertEquals("6.0 km · 18 min",((TextView)activity.findViewById(R.id.routeDistanceTime)).getText().toString());
                    activity.findViewById(R.id.riskLayerButton).performClick();
                    assertEquals("Distance must remain visible with the risk overlay",android.view.View.VISIBLE,activity.findViewById(R.id.routeDistanceTime).getVisibility());
                    // Local-only record: no network publication and no fabricated production crime.
                    org.json.JSONObject record=new org.json.JSONObject().put("id","test-crime").put("title","TEST robbery report").put("category_key","CRIME").put("reason","Test only").put("severity","HIGH").put("status","ACTIVE").put("latitude",19.05).put("longitude",73.01).put("crime_types",new org.json.JSONArray().put("Robbery / snatching"));
                    Method apply=MapsActivity.class.getDeclaredMethod("applyHazardResponse",org.json.JSONObject.class,boolean.class);apply.setAccessible(true);
                    apply.invoke(activity,new org.json.JSONObject().put("hazards",new org.json.JSONArray().put(record)),true);
                    org.maplibre.android.style.layers.SymbolLayer labels=mapRef.get().getStyle().getLayerAs("verified-alert-labels");
                    assertNotNull(labels);assertEquals("Noto Sans Regular",labels.getTextFont().getValue()[0]);
                    assertEquals("Crime & alerts · 1",((TextView)activity.findViewById(R.id.areaCrimeButton)).getText().toString());
                }catch(Exception e){throw new AssertionError(e);}
            });
            android.os.SystemClock.sleep(2500);
            screenshot("map-shared-road-1.5.png");
        }
    }

    @Test public void placeSearchUsesDedicatedSheetWithLocalityRows() throws Exception {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), MapsActivity.class);
        try (ActivityScenario<MapsActivity> scenario = ActivityScenario.launch(intent)) {
            AtomicReference<com.safenavi.app.product.PlaceSearchSheet> sheetRef = new AtomicReference<>();
            scenario.onActivity(activity -> {
                com.safenavi.app.product.PlaceSearchSheet sheet = new com.safenavi.app.product.PlaceSearchSheet(
                        activity, new com.safenavi.app.safety.network.OpenMapService(),
                        new com.safenavi.app.safety.network.OfflineRouter(activity), "Destination", "SIES", 19, 73,
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
    @Test public void goUsesRelevantCampusWithoutTappingAResult() throws Exception {
        try(ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),MapsActivity.class))) {
            AtomicReference<PlaceSearchResult> selected=new AtomicReference<>();
            scenario.onActivity(activity -> {
                com.safenavi.app.product.PlaceSearchSheet sheet=new com.safenavi.app.product.PlaceSearchSheet(activity,new com.safenavi.app.safety.network.OpenMapService(),new com.safenavi.app.safety.network.OfflineRouter(activity),"Destination","SIES",19.04,73.02,new com.safenavi.app.product.PlaceSearchSheet.Listener() {
                    public void onPlace(PlaceSearchResult place) { selected.set(place); }
                    public void onMapPin() { }
                    public void onDeviceLocation() { }
                });
                sheet.showResults(Arrays.asList(new PlaceSearchResult("SIES College Parking, Nerul",19.04,73.02),new PlaceSearchResult("SIES College, Nerul",19.042,73.023)));
                try { Method go=sheet.getClass().getDeclaredMethod("submitBest"); go.setAccessible(true); go.invoke(sheet); }
                catch(Exception e) { throw new AssertionError(e); }
            });
            assertNotNull(selected.get()); assertEquals("SIES College",selected.get().getPrimaryText());
        }
    }
    private static void screenshot(String name) throws Exception {
        android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        java.io.File output=new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),name);
        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(output)) { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream); }
        bitmap.recycle();
    }
}
