package com.safenavi.app;

import static org.junit.Assert.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.safenavi.app.product.*;
import com.safenavi.app.safety.model.*;
import org.json.*;
import okhttp3.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Explicit opt-in. All data/network calls use the real HTTPS cloud; no localhost fixtures. */
@RunWith(AndroidJUnit4.class)
public class CloudRehearsalInstrumentedTest {
    private Context context; private JSONObject access; private String oldServer; private Map<String,?> oldSession;
    private boolean testSessionCreated;
    @Before public void setup() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("cloudRehearsal")));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        oldServer=ServerConnection.baseUrl();oldSession=context.getSharedPreferences("safe_navi_session",0).getAll();
        ServerConnection.save(context,ServerConnection.CLOUD_ORIGIN);
        try(InputStream stream=new FileInputStream(new File(context.getExternalFilesDir(null),"cloud-rehearsal.private.json"))){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
            while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);
            access=new JSONObject(bytes.toString("UTF-8"));
        }
        JSONObject account=access.getJSONArray("accounts").getJSONObject(0);
        JSONObject value=api("/api/v1/auth/login",new JSONObject().put("email",account.getString("email")).put("password",account.getString("password")));
        JSONObject user=value.getJSONObject("user");
        ProductSession.save(context,value.getString("access_token"),user.getString("id"),user.getString("name"),user.getString("email"),user.getString("role"));
        testSessionCreated=true;
    }
    @After public void restore(){
        if(context==null)return;
        try { if(testSessionCreated&&ProductSession.isSignedIn(context))api("/api/v1/auth/logout",new JSONObject()); }
        catch(Exception ignored) { /* Best effort: never revoke another device's sessions. */ }
        SharedPreferences.Editor editor=context.getSharedPreferences("safe_navi_session",0).edit().clear();
        for(Map.Entry<String,?> entry:oldSession.entrySet()){
            Object v=entry.getValue();String k=entry.getKey();
            if(v instanceof String)editor.putString(k,(String)v);
            else if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);
            else if(v instanceof Long)editor.putLong(k,(Long)v);
            else if(v instanceof Integer)editor.putInt(k,(Integer)v);
        }editor.commit();ServerConnection.save(context,oldServer);
    }
    private JSONObject api(String path,JSONObject data)throws Exception {
        Request.Builder builder=new Request.Builder().url(ServerConnection.CLOUD_ORIGIN+path);
        if(ProductSession.isSignedIn(context))builder.header("Authorization","Bearer "+ProductSession.token(context));
        if(data!=null)builder.post(RequestBody.create(data.toString(),MediaType.parse("application/json")));
        try(Response response=new OkHttpClient.Builder().callTimeout(125,java.util.concurrent.TimeUnit.SECONDS).build().newCall(builder.build()).execute()){
            assertEquals("Cloud response "+path,200,response.code());
            String body=response.body().string();
            return body.startsWith("[")?new JSONObject().put("items",new JSONArray(body)):new JSONObject(body);
        }
    }
    private static Object field(Object object,String key)throws Exception {
        Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(object);
    }
    private static void invoke(Object object,String key)throws Exception {
        Method m=object.getClass().getDeclaredMethod(key);m.setAccessible(true);m.invoke(object);
    }
    private <T extends android.app.Activity> void until(ActivityScenario<T> scenario,Predicate<T> ready,int seconds)throws Exception{
        for(int n=0;n<seconds*4;n++){
            AtomicReference<Boolean> done=new AtomicReference<>(false);
            scenario.onActivity(a->done.set(ready.test(a)));
            if(done.get())return;
            android.os.SystemClock.sleep(250);
        }
        fail("Cloud UI did not reach the expected ready state in "+seconds+" seconds");
    }
    private void shot(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo window=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
        assertNotNull("Screenshot requires an active app window",window);
        assertEquals("An Android/system dialog must not cover presentation evidence",context.getPackageName(),String.valueOf(window.getPackageName()));
        android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        try(FileOutputStream stream=new FileOutputStream(new File(context.getExternalFilesDir(null),name))){
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);
        }bitmap.recycle();
    }
    private static boolean text(View view,String query){
        if(view instanceof TextView&&((TextView)view).getText().toString().contains(query)&&view.isShown())return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            if(text(((ViewGroup)view).getChildAt(i),query))return true;
        return false;
    }
    @Test public void a_sourceAndDestinationGoUseRealSearchAndCloudML()throws Exception{
        try(ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(context,MapsActivity.class))){
            scenario.onActivity(a->{
                assertTrue(a.findViewById(R.id.sourceInput).isShown());
                assertTrue(a.findViewById(R.id.destinationInput).isShown());
                a.findViewById(R.id.sourceInput).performClick();
                try{
                    Object sheet=field(a,"activePlaceSearch");
                    ((EditText)field(sheet,"input")).setText("Thane Railway Station, Kopri");
                    invoke(sheet,"submitBest");
                }catch(Exception e){throw new AssertionError(e);}
            });
            until(scenario,a->{try{return field(a,"selectedSourcePlace")!=null;}catch(Exception e){throw new AssertionError(e);}},40);
            scenario.onActivity(a->{
                assertTrue("Source remains visible after best-match submission",a.findViewById(R.id.sourceInput).isShown());
                assertTrue(a.findViewById(R.id.destinationInput).isShown());
                a.findViewById(R.id.destinationInput).performClick();
                try{
                    Object sheet=field(a,"activePlaceSearch");
                    ((EditText)field(sheet,"input")).setText("CBD Belapur Railway Station");
                    invoke(sheet,"submitBest");
                }catch(Exception e){throw new AssertionError(e);}
            });
            until(scenario,a->{try{
                org.maplibre.android.maps.Style style=(org.maplibre.android.maps.Style)field(a,"mapStyle");
                return field(a,"displayedRoute")!=null&&style!=null&&style.isFullyLoaded()
                    &&style.getLayer("safe-navi-route-options-fastest")!=null;
            }catch(Exception e){throw new AssertionError(e);}},150);
            scenario.onActivity(a->{
                assertTrue(a.findViewById(R.id.sourceInput).isShown());
                assertTrue(a.findViewById(R.id.destinationInput).isShown());
                assertTrue(a.findViewById(R.id.fastestRecommendation).isShown());
                assertTrue(a.findViewById(R.id.balancedRecommendation).isShown());
                assertTrue(a.findViewById(R.id.safestRecommendation).isShown());
                TextView metrics=a.findViewById(R.id.routeDistanceTime);
                assertTrue("Selected distance and ETA must be visible",metrics.isShown());
                assertTrue(metrics.getText().toString().contains("km"));
                String before=metrics.getText().toString();
                a.findViewById(R.id.riskLayerButton).performClick();
                assertEquals("Risk overlay must not overwrite distance",before,metrics.getText().toString());
                a.findViewById(R.id.riskLayerButton).performClick();
                try{assertFalse("Must not silently pass using offline fallback",(Boolean)field(a,"displayedOffline"));}
                catch(Exception e){throw new AssertionError(e);}
                assertTrue(((TextView)a.findViewById(R.id.routeResultRisk)).getText().toString().contains("ML safe-route-linear"));
            });
            until(scenario,a->{try{
                org.maplibre.android.maps.MapLibreMap map=(org.maplibre.android.maps.MapLibreMap)field(a,"map");
                PlaceSearchResult source=(PlaceSearchResult)field(a,"selectedSourcePlace"), destination=(PlaceSearchResult)field(a,"selectedDestinationPlace");
                org.maplibre.android.geometry.LatLngBounds bounds=map.getProjection().getVisibleRegion().latLngBounds;
                return bounds.contains(new org.maplibre.android.geometry.LatLng(source.getLatitude(),source.getLongitude()))
                    &&bounds.contains(new org.maplibre.android.geometry.LatLng(destination.getLatitude(),destination.getLongitude()));
            }catch(Exception e){throw new AssertionError(e);}},20);
            android.os.SystemClock.sleep(2500); // Let native tile/camera frames settle after the full-route fit, not just API completion.
            shot("cloud-map-endpoints-1.7.4.png");
            scenario.onActivity(a->a.findViewById(R.id.findRouteButton).performClick());
            shot("cloud-route-details-1.7.4.png");
        }
    }
    @Test public void b_cloudCommunityAndThreadRenderRetainedDemoActivity()throws Exception{
        JSONObject post=null;
        for(Object value:iter(api("/api/v1/community/posts",null).getJSONArray("items"))){
            JSONObject item=(JSONObject)value;
            if(item.getString("id").equals(access.getJSONObject("records").getString("post_id")))post=item;
        }
        assertNotNull(post); final JSONObject selected=post;
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context,MainActivity.class))){
            AtomicReference<CommunityFragment> fragment=new AtomicReference<>();
            scenario.onActivity(a->{CommunityFragment community=new CommunityFragment();fragment.set(community);
                a.getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer,community).commitNow();});
            until(scenario,a->text(a.getWindow().getDecorView(),"DEMO REHEARSAL"),35);
            shot("cloud-community-1.7.3.png");
            scenario.onActivity(a->fragment.get().onComments(selected));
            until(scenario,a->{try{
                android.app.Dialog sheet=(android.app.Dialog)field(fragment.get(),"discussionDialog");
                return sheet!=null&&sheet.isShowing()&&text(sheet.getWindow().getDecorView(),"DEMO participant")
                    &&text(sheet.getWindow().getDecorView(),"DEMO reply");
            }catch(Exception e){throw new AssertionError(e);}},30);
            shot("cloud-discussion-1.7.3.png");
        }
    }
    @Test public void c_cloudPrivateCaseMessagesRender()throws Exception{
        String id=access.getJSONObject("records").getString("report_id");
        try(ActivityScenario<CaseConversationActivity> scenario=ActivityScenario.launch(new Intent(context,CaseConversationActivity.class).putExtra("report_id",id))){
            until(scenario,a->text(a.getWindow().getDecorView(),"DEMO follow-up"),35);
            shot("cloud-case-chat-1.7.3.png");
        }
    }
    @Test public void d_unresolvedTypedEndpointsDoNotForceASecondSelectionList()throws Exception{
        try(ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(context,MapsActivity.class))){
            scenario.onActivity(a->{
                ((EditText)a.findViewById(R.id.sourceInput)).setText("Thane Railway Station, Kopri");
                ((EditText)a.findViewById(R.id.destinationInput)).setText("CBD Belapur Railway Station");
                a.findViewById(R.id.findRouteButton).performClick();
            });
            until(scenario,a->{try{return field(a,"displayedRoute")!=null;}catch(Exception e){throw new AssertionError(e);}},150);
            scenario.onActivity(a->{
                try {
                    assertNull("Resolver must commit a best match, not open a forced picker",field(a,"activePlaceSearch"));
                    assertNotNull(field(a,"selectedSourcePlace"));assertNotNull(field(a,"selectedDestinationPlace"));
                    assertFalse("Cloud ML required",(Boolean)field(a,"displayedOffline"));
                } catch(Exception e){throw new AssertionError(e);}
                assertTrue(a.findViewById(R.id.sourceInput).isShown());
                assertTrue(a.findViewById(R.id.destinationInput).isShown());
            });
        }
    }
    @Test public void e_privatePresentationAlertsRenderFromTheCloud()throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("presentationSeeded")));
        JSONArray expected=access.getJSONArray("presentation_hazards");
        assertTrue("Original markers and any expansion must be included",expected.length()>=12);
        Set<String> expectedIds=new HashSet<>();
        for(int i=0;i<expected.length();i++)expectedIds.add(expected.getString(i));
        assertEquals("Handoff must not duplicate markers",expected.length(),expectedIds.size());
        JSONObject mapResponse=api("/api/v1/hazards/map",null);
        Set<String> ids=new HashSet<>();int news=0;
        for(Object row:iter(mapResponse.getJSONArray("hazards"))){JSONObject h=(JSONObject)row;ids.add(h.getString("id"));if("NEWS_VERIFIED".equals(h.optString("source")))news++;}
        for(int i=0;i<expected.length();i++)assertTrue("Missing published sample",ids.contains(expected.getString(i)));
        assertTrue("News replay markers must have source records",news>=2);
        try(ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(context,MapsActivity.class))){
            until(scenario,a->{try{
                org.maplibre.android.maps.Style style=(org.maplibre.android.maps.Style)field(a,"mapStyle");
                Map<?,?> loaded=(Map<?,?>)field(a,"liveHazardRecords");
                return style!=null&&style.isFullyLoaded()&&style.getLayer("verified-alert-labels")!=null&&loaded.keySet().containsAll(ids);
            }catch(Exception error){throw new AssertionError(error);}},90);
            scenario.onActivity(a->{try{
                Map<?,?> loaded=(Map<?,?>)field(a,"liveHazardRecords");assertTrue(loaded.keySet().containsAll(ids));
                assertTrue(((TextView)a.findViewById(R.id.areaCrimeButton)).getText().toString().contains(String.valueOf(ids.size())));
                org.maplibre.android.geometry.LatLngBounds.Builder bounds=new org.maplibre.android.geometry.LatLngBounds.Builder();
                for(Object value:loaded.values()){JSONObject h=(JSONObject)value;bounds.include(new org.maplibre.android.geometry.LatLng(h.getDouble("latitude"),h.getDouble("longitude")));}
                float density=a.getResources().getDisplayMetrics().density;
                ((org.maplibre.android.maps.MapLibreMap)field(a,"map")).moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds.build(),(int)(20*density),(int)(190*density),(int)(20*density),(int)(100*density)));
            }catch(Exception error){throw new AssertionError(error);}});
            android.os.SystemClock.sleep(5000);shot("cloud-presentation-alerts.png");
            scenario.onActivity(a->a.findViewById(R.id.areaCrimeButton).performClick());
            android.os.SystemClock.sleep(500);shot("cloud-presentation-alert-list.png");
        }
    }
    @Test public void f_presentationPinsUseCloudRecommendationsAndVisibleColors()throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("presentationSeeded")));
        int expectedRoads=Integer.parseInt(InstrumentationRegistry.getArguments().getString("expectedPresentationRoads","3"));
        try(ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(context,MapsActivity.class))){
            scenario.onActivity(a->{try{
                // Exact previously geocoded pins; real providers still supply all roads and ML scores.
                Method select=MapsActivity.class.getDeclaredMethod("setSelectedPlace",boolean.class,PlaceSearchResult.class);select.setAccessible(true);
                select.invoke(a,true,new PlaceSearchResult("Vashi Railway Station",19.0637159,73.0002509));
                select.invoke(a,false,new PlaceSearchResult("SIES GST, Nerul",19.042644,73.023428));
                a.findViewById(R.id.findRouteButton).performClick();
            }catch(Exception error){throw new AssertionError(error);}});
            until(scenario,a->{try{
                org.maplibre.android.maps.Style style=(org.maplibre.android.maps.Style)field(a,"mapStyle");
                return field(a,"displayedRoute")!=null&&style!=null&&style.isFullyLoaded()
                    &&((TextView)a.findViewById(R.id.routeRiskSummary)).getText().toString().startsWith(expectedRoads+" recommended roads.");
            }catch(Exception error){throw new AssertionError(error);}},150);
            scenario.onActivity(a->{try{
                assertFalse("This must be the live cloud, not the offline graph",(Boolean)field(a,"displayedOffline"));
                assertTrue(((TextView)a.findViewById(R.id.routeResultRisk)).getText().toString().contains("ML safe-route-linear"));
                org.maplibre.android.maps.Style style=(org.maplibre.android.maps.Style)field(a,"mapStyle");
                String[] suffixes={"fastest","balanced","safest"};
                int[] colors={0xFF2563EB,0xFF7C3AED,0xFF00796B};
                int[] buttons={R.id.fastestRecommendation,R.id.balancedRecommendation,R.id.safestRecommendation};
                int drawn=0;
                for(int i=0;i<suffixes.length;i++) {
                    org.maplibre.android.style.layers.LineLayer line=style.getLayerAs("safe-navi-route-options-"+suffixes[i]);
                    org.maplibre.android.style.layers.LineLayer casing=style.getLayerAs("safe-navi-route-options-"+suffixes[i]+"-casing");
                    if(line!=null){drawn++;assertNotNull(casing);
                        assertTrue(line.getLineColorAsInt()==colors[i]||line.getLineColorAsInt()==0xFF526C85);
                        assertEquals(android.graphics.Color.WHITE,casing.getLineColorAsInt());
                        assertEquals(line.getLineWidth().getValue()+4f,casing.getLineWidth().getValue(),.01f);}
                    assertTrue(a.findViewById(buttons[i]).performClick());
                    int selected=0;
                    for(String name:suffixes){org.maplibre.android.style.layers.LineLayer highlighted=style.getLayerAs("safe-navi-route-options-"+name);
                        if(highlighted!=null&&Math.abs(highlighted.getLineWidth().getValue()-8f)<.01f)selected++;}
                    assertEquals("Exactly one physical road is highlighted",1,selected);
                }
                assertEquals(expectedRoads,drawn);
                a.findViewById(R.id.balancedRecommendation).performClick();
                assertTrue(a.findViewById(R.id.fastestRecommendation).isShown());
                assertTrue(a.findViewById(R.id.balancedRecommendation).isShown());
                assertTrue(a.findViewById(R.id.safestRecommendation).isShown());
            }catch(Exception error){throw new AssertionError(error);}});
            android.os.SystemClock.sleep(5000);shot("cloud-presentation-three-routes.png");
            scenario.onActivity(a->a.findViewById(R.id.findRouteButton).performClick());
            android.os.SystemClock.sleep(500);shot("cloud-presentation-route-choices.png");
        }
    }
    @Test public void g_officialCanInspectExplicitFloodPassabilityControls()throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("floodPassability")));
        JSONObject official=access.getJSONArray("accounts").getJSONObject(1);
        api("/api/v1/auth/logout",new JSONObject());
        JSONObject signed=api("/api/v1/auth/login",new JSONObject().put("email",official.getString("email")).put("password",official.getString("password")));
        JSONObject user=signed.getJSONObject("user");assertEquals("government",user.getString("role"));
        ProductSession.save(context,signed.getString("access_token"),user.getString("id"),user.getString("name"),user.getString("email"),user.getString("role"));
        String hazardId=access.getString("presentation_flood_hazard");
        try(ActivityScenario<MapsActivity> scenario=ActivityScenario.launch(new Intent(context,MapsActivity.class))){
            until(scenario,a->{try{return ((Map<?,?>)field(a,"liveHazardRecords")).containsKey(hazardId);}catch(Exception error){throw new AssertionError(error);}},90);
            scenario.onActivity(a->{try{
                JSONObject record=(JSONObject)((Map<?,?>)field(a,"liveHazardRecords")).get(hazardId);
                assertTrue(record.getBoolean("routing_blocked"));assertEquals("IMPASSABLE",record.getString("passability"));
                Method show=MapsActivity.class.getDeclaredMethod("showHazard",String.class);show.setAccessible(true);show.invoke(a,hazardId);
            }catch(Exception error){throw new AssertionError(error);}});
            RouteActionsInstrumentedTest.tap("Update official hazard lifecycle");
            RouteActionsInstrumentedTest.tap("Keep active");
            RouteActionsInstrumentedTest.visible("Impassable (exclude road)");
            shot("cloud-flood-official-controls.png");
            RouteActionsInstrumentedTest.tap("Impassable (exclude road)");
            RouteActionsInstrumentedTest.visible("Not confirmed (exclude road)");
            RouteActionsInstrumentedTest.visible("Inspected: passable with caution");
            RouteActionsInstrumentedTest.tap("Impassable (exclude road)");
            RouteActionsInstrumentedTest.tap("Cancel"); // Read-only UI check: never submit a false inspection.
        }
    }
    @Test public void h_officialPublishedReportsOpenMapAndPendingReportsOpenPublishForm()throws Exception {
        JSONObject official=access.getJSONArray("accounts").getJSONObject(1);
        api("/api/v1/auth/logout",new JSONObject());
        JSONObject signed=api("/api/v1/auth/login",new JSONObject().put("email",official.getString("email")).put("password",official.getString("password")));
        JSONObject user=signed.getJSONObject("user");assertEquals("government",user.getString("role"));
        ProductSession.save(context,signed.getString("access_token"),user.getString("id"),user.getString("name"),user.getString("email"),user.getString("role"));
        JSONArray reports=api("/api/v1/reports",null).getJSONArray("items");
        int hazardCount=api("/api/v1/hazards/map",null).getJSONArray("hazards").length();
        JSONObject published=null,pending=null;
        for(Object item:iter(reports)) {
            JSONObject report=(JSONObject)item;
            if("VERIFIED".equals(report.optString("status"))&&published==null)published=report;
            if("REPORTED".equals(report.optString("status"))&&pending==null)pending=report;
        }
        assertNotNull(published);assertNotNull(pending);
        final JSONObject selected=published,awaiting=pending;
        AtomicReference<Intent> opened=new AtomicReference<>();
        android.app.Instrumentation.ActivityMonitor monitor=new android.app.Instrumentation.ActivityMonitor(){
            @Override public android.app.Instrumentation.ActivityResult onStartActivity(Intent intent){
                if(intent.getComponent()!=null&&MapsActivity.class.getName().equals(intent.getComponent().getClassName())) {
                    opened.set(new Intent(intent));return new android.app.Instrumentation.ActivityResult(0,null);
                } return null;
            }
        };
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor);
        try(ActivityScenario<GovernmentSafetyDashboardActivity> scenario=ActivityScenario.launch(new Intent(context,GovernmentSafetyDashboardActivity.class))){
            until(scenario,a->{try{return field(a,"currentReport")!=null;}catch(Exception error){throw new AssertionError(error);}},40);
            scenario.onActivity(a->{try{
                Field f=a.getClass().getDeclaredField("currentReport");f.setAccessible(true);f.set(a,selected);invoke(a,"renderCurrentReport");
                Button action=a.findViewById(R.id.verifyReportButton);
                assertTrue(action.isEnabled());assertEquals("View published hazard on map",action.getText().toString());action.performClick();
                assertNotNull(opened.get());assertEquals(selected.optString("linked_hazard_id"),opened.get().getStringExtra("focus_hazard_id"));
                f.set(a,awaiting);invoke(a,"renderCurrentReport");
                assertTrue(action.isEnabled());assertEquals("Verify and publish hazard",action.getText().toString());action.performClick();
            }catch(Exception error){throw new AssertionError(error);}});
            RouteActionsInstrumentedTest.visible("Publish verified hazard");
            RouteActionsInstrumentedTest.tap("Cancel");
            scenario.onActivity(a->a.findViewById(R.id.browseGovernmentHazardsButton).performClick());
            RouteActionsInstrumentedTest.visible("Published hazards · "+hazardCount);
            shot("official-hazard-browser-1.8.4.png");
            RouteActionsInstrumentedTest.tap("Close");
        } finally { InstrumentationRegistry.getInstrumentation().removeMonitor(monitor); }
    }
    private static List<Object> iter(JSONArray values)throws JSONException{
        List<Object> result=new ArrayList<>();for(int i=0;i<values.length();i++)result.add(values.get(i));return result;
    }
}
