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
import com.safenavi.app.safety.network.OfflineHazardCache;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.After;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OfflineRouterInstrumentedTest {
    private JSONObject previousHazards;
    @Before public void isolateHazardCache() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        previousHazards=OfflineHazardCache.load(context);
        OfflineHazardCache.save(context,new JSONObject().put("hazards",new JSONArray()));
    }
    @After public void restoreHazardCache() {
        OfflineHazardCache.save(InstrumentationRegistry.getInstrumentation().getTargetContext(),previousHazards);
    }
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

    @Test public void allTravelModesAndProfilesRouteWithinBudget() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        OfflineRouter router=new OfflineRouter(context);
        for(TravelMode mode:TravelMode.values()) for(RouteProfile profile:RouteProfile.values()) {
            CountDownLatch latch=new CountDownLatch(1); AtomicReference<String> error=new AtomicReference<>();
            AtomicReference<OfflineRouter.Result> result=new AtomicReference<>();
            long started=android.os.SystemClock.elapsedRealtime();
            router.route(19.0760,72.9986,19.0330,73.0169,mode,profile,new OfflineRouter.Callback<OfflineRouter.Result>() {
                @Override public void onSuccess(OfflineRouter.Result value){result.set(value);latch.countDown();}
                @Override public void onError(String message){error.set(message);latch.countDown();}
            });
            assertTrue(mode+" "+profile+" timed out",latch.await(60,TimeUnit.SECONDS));
            assertNull(mode+" "+profile+": "+error.get(),error.get());
            assertTrue(result.get().route.getGeometry().size()>2);
            assertTrue("Offline routing exceeded budget",android.os.SystemClock.elapsedRealtime()-started<20_000);
        }
    }

    @Test public void polygonInteriorDestinationIsBlockedOffline() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONArray polygon=new JSONArray();
        for(double[] point:new double[][]{{19.02,73},{19.02,73.03},{19.045,73.03},{19.045,73}})
            polygon.put(new JSONObject().put("latitude",point[0]).put("longitude",point[1]));
        JSONObject closure=new JSONObject().put("latitude",19.033).put("longitude",73.0169).put("status","ACTIVE")
                .put("road_status","CLOSED").put("severity","HIGH").put("geometry_type","POLYGON")
                .put("geometry_coordinates",polygon).put("no_go_radius_m",20);
        OfflineHazardCache.save(context,new JSONObject().put("hazards",new JSONArray().put(closure)));
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> error=new AtomicReference<>();
        new OfflineRouter(context).route(19.076,72.9986,19.033,73.0169,TravelMode.DRIVING,RouteProfile.FASTEST,new OfflineRouter.Callback<OfflineRouter.Result>() {
            @Override public void onSuccess(OfflineRouter.Result value){latch.countDown();}
            @Override public void onError(String message){error.set(message);latch.countDown();}
        });
        assertTrue(latch.await(60,TimeUnit.SECONDS));assertTrue("Interior of closed polygon must not be routable",error.get()!=null);
    }

    @Test public void reroutedRoadEdgesNeverCrossCachedPointClosure() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        OfflineRouter router=new OfflineRouter(context);
        OfflineRouter.Result baseline=awaitRoute(router);
        com.safenavi.app.safety.model.HazardLocation.GeoPoint point=baseline.route.getGeometry().get(baseline.route.getGeometry().size()/2);
        JSONObject h=new JSONObject().put("status","ACTIVE").put("severity","HIGH").put("road_status","CLOSED")
                .put("latitude",point.getLatitude()).put("longitude",point.getLongitude()).put("no_go_radius_m",40);
        OfflineHazardCache.save(context,new JSONObject().put("hazards",new JSONArray().put(h)));
        OfflineRouter.Result rerouted=awaitRoute(router);
        java.util.List<com.safenavi.app.safety.model.HazardLocation.GeoPoint> geometry=rerouted.route.getGeometry();
        OfflineHazardCache.Snapshot snapshot=OfflineHazardCache.snapshot(context);
        for(int i=1;i<geometry.size();i++) {
            com.safenavi.app.safety.model.HazardLocation.GeoPoint a=geometry.get(i-1),b=geometry.get(i);
            assertTrue("Rerouted edge crosses an official closure",!snapshot.blocksEdge(a.getLatitude(),a.getLongitude(),b.getLatitude(),b.getLongitude()));
        }
    }
    private OfflineRouter.Result awaitRoute(OfflineRouter router) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);AtomicReference<OfflineRouter.Result> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
        router.route(19.076,72.9986,19.033,73.0169,TravelMode.DRIVING,RouteProfile.SAFEST,new OfflineRouter.Callback<OfflineRouter.Result>() {
            @Override public void onSuccess(OfflineRouter.Result value){result.set(value);latch.countDown();}
            @Override public void onError(String message){error.set(message);latch.countDown();}
        });
        assertTrue(latch.await(60,TimeUnit.SECONDS));assertNull(error.get());return result.get();
    }
}
