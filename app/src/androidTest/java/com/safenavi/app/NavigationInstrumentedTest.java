package com.safenavi.app;

import static org.junit.Assert.*;
import android.app.Instrumentation;
import android.content.*;
import android.location.Location;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.location.*;
import com.safenavi.app.navigation.*;
import com.safenavi.app.product.ServerConnection;
import com.safenavi.app.safety.model.*;
import com.safenavi.app.safety.network.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.maplibre.android.maps.Style;

/** Opt-in real-provider/cloud rehearsal. Does not write a report, hazard, GPS fix or journey to production. */
@RunWith(AndroidJUnit4.class)
public class NavigationInstrumentedTest {
    private static RouteSafetyResult selected;
    private Context context;
    @Before public void setup() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("navigationRehearsal")));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertEquals("Navigation rehearsal must use the deployed cloud",ServerConnection.CLOUD_ORIGIN,ServerConnection.baseUrl());
        if(selected!=null)return;
        CountDownLatch roadsReady=new CountDownLatch(1);AtomicReference<List<RouteCandidate>> roads=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
        new OpenMapService().routes(19.0637159,73.0002509,19.042644,73.023428,TravelMode.DRIVING,new OpenMapService.ResultCallback<List<RouteCandidate>>(){
            public void onSuccess(List<RouteCandidate> value){roads.set(value);roadsReady.countDown();}
            public void onError(String value){error.set(value);roadsReady.countDown();}
        });
        assertTrue(roadsReady.await(30,TimeUnit.SECONDS));assertNull(error.get());assertNotNull(roads.get());
        CountDownLatch rankedReady=new CountDownLatch(1);AtomicReference<DatasetRouteEvaluation> evaluation=new AtomicReference<>();
        new DatasetRiskService().evaluateRoutes(roads.get(),RouteProfile.BALANCED,"midday",new DatasetRiskService.ResultCallback<DatasetRouteEvaluation>(){
            public void onSuccess(DatasetRouteEvaluation value){evaluation.set(value);rankedReady.countDown();}
            public void onError(String value){error.set(value);rankedReady.countDown();}
        });
        assertTrue(rankedReady.await(125,TimeUnit.SECONDS));assertNull(error.get());assertEquals("trained_ml",evaluation.get().getProvider());
        selected=evaluation.get().getRankedResults().get(0);
        assertTrue("Provider must supply real turns",selected.getRoute().getNavigationSteps().size()>2);
    }
    @Test public void selectedCloudRouteOpensInAppAndSimulationControlsWork() throws Exception {
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(NavigationActivity.class.getName(),null,false);
        NavigationActivity nav=null;
        try(ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(context,MapsActivity.class))) {
            scenario.onActivity(activity->{try{
                set(activity,"displayedRoute",selected);
                ((TextView)activity.findViewById(R.id.sourceInput)).setText("Vashi Railway Station");
                ((TextView)activity.findViewById(R.id.destinationInput)).setText("SIES GST, Nerul");
                call(activity,"showRouteDetailsSheet");
            }catch(Exception error){throw new AssertionError(error);}});
            RouteActionsInstrumentedTest.tap("Start navigation");RouteActionsInstrumentedTest.tap("Demo preview (no GPS)");
            nav=(NavigationActivity)monitor.waitForActivityWithTimeout(20000);assertNotNull("Must stay inside Safe-Navi",nav);
            final NavigationActivity active=nav;
            for(int n=0;n<100;n++) {
                AtomicBoolean ready=new AtomicBoolean();
                instrumentation.runOnMainSync(()->{try{Style style=(Style)get(active,"style");ready.set(style!=null&&style.isFullyLoaded());}catch(Exception error){throw new AssertionError(error);}});
                if(ready.get())break;SystemClock.sleep(200);
            }
            RouteActionsInstrumentedTest.visible("DEMO PREVIEW • SIMULATED LOCATION");
            AtomicReference<NavigationProgress> engine=new AtomicReference<>();
            instrumentation.runOnMainSync(()->{try{
                assertTrue((Boolean)get(active,"demo"));engine.set((NavigationProgress)get(active,"progress"));
                assertEquals(selected.getRoute().getProviderRouteId(),engine.get().route.getProviderRouteId());
                assertEquals(selected.getRoute().getGeometry().size(),engine.get().route.getGeometry().size());
                assertEquals(selected.getRoute().getNavigationSteps().size(),engine.get().route.getNavigationSteps().size());
                assertNotNull(((Style)get(active,"style")).getLayer("navigation-position"));
                org.maplibre.android.style.layers.LineLayer line=((Style)get(active,"style")).getLayerAs("navigation-route-line");
                org.maplibre.android.style.layers.LineLayer casing=((Style)get(active,"style")).getLayerAs("navigation-route-outline");
                assertEquals("Navigation must retain the selected preference palette",com.safenavi.app.safety.presentation.RouteColors.forProfileName(((org.json.JSONObject)get(active,"snapshot")).optString("profile")),line.getLineColorAsInt());
                assertEquals(android.graphics.Color.WHITE,casing.getLineColorAsInt());
                assertEquals(8f,line.getLineWidth().getValue(),.01f);
            }catch(Exception error){throw new AssertionError(error);}});
            SystemClock.sleep(2200);assertTrue(engine.get().progress()>0);
            RouteActionsInstrumentedTest.tap("Pause");double paused=engine.get().progress();SystemClock.sleep(1400);assertEquals(paused,engine.get().progress(),.01);
            RouteActionsInstrumentedTest.tap("Next turn");assertTrue(engine.get().progress()>paused);
            RouteActionsInstrumentedTest.tap("20x speed");RouteActionsInstrumentedTest.visible("60x speed");
            SystemClock.sleep(2500);shot("navigation-demo-turn-1.8.png");
            RouteActionsInstrumentedTest.tap("Overview");SystemClock.sleep(3000);shot("navigation-demo-overview-1.8.png");
            instrumentation.runOnMainSync(()->{try{
                org.maplibre.android.maps.MapLibreMap map=(org.maplibre.android.maps.MapLibreMap)get(active,"map");
                List<HazardLocation.GeoPoint> points=engine.get().route.getGeometry();
                for(HazardLocation.GeoPoint point:Arrays.asList(points.get(0),points.get(points.size()-1))) {
                    android.graphics.PointF pixel=map.getProjection().toScreenLocation(new org.maplibre.android.geometry.LatLng(point.getLatitude(),point.getLongitude()));
                    assertTrue("Overview must show endpoints below the instruction card",pixel.y>=active.findViewById(R.id.navigationTop).getBottom());
                    assertTrue("Overview must show endpoints above the controls",pixel.y<=active.findViewById(R.id.navigationBottom).getTop());
                    assertTrue("Overview must not crop the route sides",pixel.x>=0&&pixel.x<=active.findViewById(R.id.navigationMap).getWidth());
                }
            }catch(Exception error){throw new AssertionError(error);}});
            RouteActionsInstrumentedTest.tap("Resume");SystemClock.sleep(1300);RouteActionsInstrumentedTest.tap("Pause");
            assertTrue(engine.get().progress()>paused);RouteActionsInstrumentedTest.tap("Exit");
        } finally {
            if(nav!=null&&!nav.isFinishing()) {final NavigationActivity active=nav;instrumentation.runOnMainSync(active::finish);}
            instrumentation.removeMonitor(monitor);
        }
    }
    @Test public void emulatedGpsFixAdvancesOnlyWhileNavigationIsForeground() throws Exception {
        // Explicit emulator-only test permission; injected locations below are not field-GPS proof.
        try(android.os.ParcelFileDescriptor ignored=InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .executeShellCommand("pm grant com.safenavi.app android.permission.ACCESS_FINE_LOCATION")) { }
        Intent intent=NavigationRouteStore.intent(context,selected,"Vashi Railway Station","SIES GST, Nerul",false);
        try(ActivityScenario<NavigationActivity> scenario=ActivityScenario.launch(intent)) {
            AtomicReference<NavigationProgress> engine=new AtomicReference<>();AtomicReference<LocationCallback> callback=new AtomicReference<>();
            scenario.onActivity(activity->{try{
                assertFalse((Boolean)get(activity,"demo"));assertEquals(View.GONE,activity.findViewById(R.id.navigationDemoControls).getVisibility());
                engine.set((NavigationProgress)get(activity,"progress"));callback.set((LocationCallback)get(activity,"locationUpdates"));
                HazardLocation.GeoPoint point=selected.getRoute().getGeometry().get(selected.getRoute().getGeometry().size()/3);
                callback.get().onLocationResult(LocationResult.create(Collections.singletonList(location(point))));
            }catch(Exception error){throw new AssertionError(error);}});
            assertTrue(engine.get().progress()>0);
            scenario.moveToState(Lifecycle.State.CREATED);double before=engine.get().progress();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->callback.get().onLocationResult(LocationResult.create(Collections.singletonList(location(
                    selected.getRoute().getGeometry().get(selected.getRoute().getGeometry().size()-1))))));
            assertEquals("Background callbacks must not advance foreground-only navigation",before,engine.get().progress(),.01);
        }
    }
    @Test public void routeSnapshotRejectsPathTraversal() throws Exception {
        try {NavigationRouteStore.read(context,"../safe_navi_session.xml");fail("Invalid path accepted");}
        catch(IOException expected){assertTrue(expected.getMessage().contains("unavailable"));}
    }
    private static Location location(HazardLocation.GeoPoint point) {
        Location location=new Location("test-only");location.setLatitude(point.getLatitude());location.setLongitude(point.getLongitude());location.setAccuracy(5);location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());return location;
    }
    private void shot(String file) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try(FileOutputStream stream=new FileOutputStream(new File(context.getExternalFilesDir(null),file))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();
    }
    private static Object get(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private static void call(Object object,String name)throws Exception{Method method=object.getClass().getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);}
}
