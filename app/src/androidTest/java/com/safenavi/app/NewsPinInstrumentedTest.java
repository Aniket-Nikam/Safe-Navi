package com.safenavi.app;

import static org.junit.Assert.*;
import android.content.*;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.safenavi.app.product.*;
import com.safenavi.app.safety.model.PlaceSearchResult;
import com.safenavi.app.safety.network.OpenMapService;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.maplibre.android.maps.*;

/** Opt-in localhost publication checks. No live accounts, real crimes or cloud hazards are written. */
@RunWith(AndroidJUnit4.class)
public class NewsPinInstrumentedTest {
    private Context context;
    private String oldServer, signalId;
    private Map<String,?> oldSession;
    private static final PlaceSearchResult JUHU = new PlaceSearchResult("Juhu, Mumbai, India",19.107281,72.830032);

    @Before public void setup() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("localWorkflow")));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        oldServer=ServerConnection.baseUrl();oldSession=context.getSharedPreferences("safe_navi_session",0).getAll();
        ServerConnection.save(context,"http://127.0.0.1:8017");
        JSONObject auth=request("/api/v1/auth/login",new JSONObject().put("email","official@ui.test").put("password","ui-workflow-password"));
        JSONObject user=auth.getJSONObject("user");
        ProductSession.save(context,auth.getString("access_token"),user.getString("id"),user.getString("name"),user.getString("email"),user.getString("role"));
        signalId=request("/api/v1/government/news-signals",null).getJSONArray("items").getJSONObject(0).getString("id");
    }
    @After public void cleanup() {
        if(context==null)return;
        ServerConnection.save(context,oldServer);
        SharedPreferences.Editor editor=context.getSharedPreferences("safe_navi_session",0).edit().clear();
        for(Map.Entry<String,?> entry:oldSession.entrySet()) {
            Object v=entry.getValue();String k=entry.getKey();
            if(v instanceof String)editor.putString(k,(String)v);else if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);
            else if(v instanceof Long)editor.putLong(k,(Long)v);else if(v instanceof Integer)editor.putInt(k,(Integer)v);
        }
        editor.commit();
    }
    private JSONObject request(String path,JSONObject payload) throws Exception {
        Request.Builder request=new Request.Builder().url("http://127.0.0.1:8017"+path).header("Authorization","Bearer "+ProductSession.token(context));
        if(payload!=null)request.post(RequestBody.create(payload.toString(),MediaType.parse("application/json")));
        try(Response response=new OkHttpClient().newCall(request.build()).execute()) {
            assertTrue("Local fixture request failed: "+response.code(),response.isSuccessful());
            String body=response.body().string();return body.startsWith("[")?new JSONObject().put("items",new JSONArray(body)):new JSONObject(body);
        }
    }
    private ActivityScenario<NewsWorkbenchActivity> open(FakePlaces provider,String headline,String hint) {
        ActivityScenario<NewsWorkbenchActivity> scenario=ActivityScenario.launch(new Intent(context,NewsWorkbenchActivity.class));
        scenario.onActivity(activity->{try {
            Field field=NewsWorkbenchActivity.class.getDeclaredField("newsPlaces");field.setAccessible(true);field.set(activity,provider);
            Method method=NewsWorkbenchActivity.class.getDeclaredMethod("publish",JSONObject.class);method.setAccessible(true);
            method.invoke(activity,new JSONObject().put("id",signalId).put("headline",headline).put("location_text",hint));
        } catch(Exception error){throw new AssertionError(error);}});
        return scenario;
    }
    private static AlertDialog dialog(NewsWorkbenchActivity activity) {
        try {Field field=NewsWorkbenchActivity.class.getDeclaredField("publicationDialog");field.setAccessible(true);return (AlertDialog)field.get(activity);}
        catch(Exception error){throw new AssertionError(error);}
    }
    private static <T extends View> T tagged(NewsWorkbenchActivity activity,String tag) { return dialog(activity).getWindow().getDecorView().findViewWithTag(tag); }
    private static MapLibreMap awaitMap(ActivityScenario<NewsWorkbenchActivity> scenario,boolean pin) {
        AtomicReference<MapLibreMap> ready=new AtomicReference<>();
        for(int i=0;i<150;i++) {
            scenario.onActivity(activity->{MapView view=tagged(activity,"news-location-map");view.getMapAsync(map->{
                if(map.getStyle()!=null&&map.getStyle().isFullyLoaded()&&(!pin||map.getStyle().getSource("news-location")!=null))ready.set(map);
            });});
            if(ready.get()!=null)return ready.get();SystemClock.sleep(200);
        }
        throw new AssertionError("Preview map/pin failed to become ready");
    }
    @Test public void automaticAreaPinSurvivesMapLoadAndPublishesCorrectCoordinates() throws Exception {
        try(ActivityScenario<NewsWorkbenchActivity> scenario=open(new FakePlaces(false),"UI TEST ONLY: Juhu incident","Mumbai")) {
            awaitMap(scenario,true);
            scenario.onActivity(activity->{
                assertEquals(JUHU.getDisplayName(),((EditText)tagged(activity,"news-address")).getText().toString());
                assertNull(((EditText)tagged(activity,"news-address")).getError());
                assertTrue(((TextView)tagged(activity,"news-location-status")).getText().toString().startsWith("Suggested area"));
            });
            // Style/source readiness precedes asynchronous road-tile rendering.
            SystemClock.sleep(5000);
            shot("news-auto-pin-1.8.1.png");
            scenario.onActivity(activity->{
                ((EditText)tagged(activity,"news-verification-note")).setText("Isolated UI test verification, not a real incident.");
                ((CheckBox)tagged(activity,"news-confirm-current")).setChecked(true);
            });
            RouteActionsInstrumentedTest.tap("Publish");
            RouteActionsInstrumentedTest.visible("Hazard published to backend");
            JSONObject item=request("/api/v1/hazards/map",null).getJSONArray("hazards").getJSONObject(0);
            assertEquals(JUHU.getLatitude(),item.getDouble("latitude"),.0000001);
            assertEquals(JUHU.getLongitude(),item.getDouble("longitude"),.0000001);
            assertEquals(JUHU.getDisplayName(),item.getString("address"));
            RouteActionsInstrumentedTest.tap("Stay here");
        }
    }
    @Test public void manualMapTapClearsOldErrorAndCannotBeOverwrittenByLateSuggestion() throws Exception {
        FakePlaces provider=new FakePlaces(true);
        try(ActivityScenario<NewsWorkbenchActivity> scenario=open(provider,"UI TEST ONLY: Juhu incident","Mumbai")) {
            awaitMap(scenario,false);RouteActionsInstrumentedTest.tap("Publish");
            Rect target=new Rect();
            scenario.onActivity(activity->{
                assertNotNull(((EditText)tagged(activity,"news-address")).getError());
                assertTrue(tagged(activity,"news-location-map").getGlobalVisibleRect(target));
            });
            // A real touch in MapLibre, not invoking the selection callback through reflection.
            tap(target.exactCenterX(),target.exactCenterY());
            AtomicReference<String> manual=new AtomicReference<>();
            for(int i=0;i<40;i++) {
                scenario.onActivity(activity->{String value=((EditText)tagged(activity,"news-address")).getText().toString();if(value.startsWith("Road at"))manual.set(value);});
                if(manual.get()!=null)break;SystemClock.sleep(100);
            }
            assertNotNull("Map tap must select a pin",manual.get());
            scenario.onActivity(activity->{
                provider.pending.onSuccess(Collections.singletonList(JUHU));
                assertEquals(manual.get(),((EditText)tagged(activity,"news-address")).getText().toString());
                assertNull("Selecting a road must clear the earlier pin error",((EditText)tagged(activity,"news-address")).getError());
                dialog(activity).getButton(-1).performClick();
                assertNull(((EditText)tagged(activity,"news-address")).getError());
                assertNotNull("Next validation should concern verification, not the existing pin",((EditText)tagged(activity,"news-verification-note")).getError());
            });
        }
    }
    @Test public void unsupportedOrCityOnlyNewsDoesNotInventCoordinatesAndInvalidRadiusIsBlocked() throws Exception {
        FakePlaces provider=new FakePlaces(false);
        try(ActivityScenario<NewsWorkbenchActivity> scenario=open(provider,"UI TEST ONLY: Mumbai update","Mumbai")) {
            scenario.onActivity(activity->{
                assertEquals(0,provider.requests);
                assertEquals("",((EditText)tagged(activity,"news-address")).getText().toString());
                assertTrue(((TextView)tagged(activity,"news-location-status")).getText().toString().contains("no single supported area"));
            });
        }
        try(ActivityScenario<NewsWorkbenchActivity> scenario=open(new FakePlaces(false),"UI TEST ONLY: Juhu incident","Mumbai")) {
            scenario.onActivity(activity->{
                ((EditText)tagged(activity,"news-verification-note")).setText("Isolated UI fixture, not a live verification.");
                ((EditText)tagged(activity,"news-radius")).setText("999");
                ((CheckBox)tagged(activity,"news-confirm-current")).setChecked(true);
                dialog(activity).getButton(-1).performClick();
                assertNotNull(((EditText)tagged(activity,"news-radius")).getError());
                assertTrue(dialog(activity).isShowing());
            });
        }
    }
    @Test public void loginHasNoServerUrlButKeepsConnectionSettings() throws Exception {
        ProductSession.clear(context);
        try(ActivityScenario<LoginActivity> scenario=ActivityScenario.launch(new Intent(context,LoginActivity.class).putExtra("LOGIN_TYPE","citizen"))) {
            scenario.onActivity(activity->{
                assertEquals(View.GONE,activity.findViewById(R.id.loginServerAddress).getVisibility());
                assertEquals("",((TextView)activity.findViewById(R.id.loginServerAddress)).getText().toString());
                assertTrue(activity.findViewById(R.id.loginServerConnectionButton).isShown());
            });
            shot("login-clean-1.8.1.png");
            RouteActionsInstrumentedTest.tap("Server connection");
            RouteActionsInstrumentedTest.visible("Server connection");
        }
    }
    private static void tap(float x,float y) {
        long time=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(time,time+80,MotionEvent.ACTION_UP,x,y,0);
        try {InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(down,true);InstrumentationRegistry.getInstrumentation().getUiAutomation().injectInputEvent(up,true);}
        finally {down.recycle();up.recycle();}
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
    }
    private void shot(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(300);
        android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private static class FakePlaces extends OpenMapService {
        final boolean defer;int requests;ResultCallback<List<PlaceSearchResult>> pending;
        FakePlaces(boolean defer){this.defer=defer;}
        @Override public Call suggest(String query,double lat,double lon,ResultCallback<List<PlaceSearchResult>> callback) {
            requests++;pending=callback;if(!defer)callback.onSuccess(Collections.singletonList(JUHU));return null;
        }
    }
}
