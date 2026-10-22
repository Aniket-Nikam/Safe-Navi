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
    }
    @After public void restore(){
        if(context==null)return;
        try { if(ProductSession.isSignedIn(context))api("/api/v1/auth/logout",new JSONObject()); }
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
        assertEquals(12,expected.length());
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
    @Test public void f_presentationPinsProduceThreeCloudRecommendationsInTheApp()throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("presentationSeeded")));
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
                    &&((TextView)a.findViewById(R.id.routeRiskSummary)).getText().toString().startsWith("3 recommended roads.");
            }catch(Exception error){throw new AssertionError(error);}},150);
            scenario.onActivity(a->{try{
                assertFalse("This must be the live cloud, not the offline graph",(Boolean)field(a,"displayedOffline"));
                assertTrue(((TextView)a.findViewById(R.id.routeResultRisk)).getText().toString().contains("ML safe-route-linear"));
                org.maplibre.android.maps.Style style=(org.maplibre.android.maps.Style)field(a,"mapStyle");
                for(String suffix:new String[]{"fastest","balanced","safest"})assertNotNull(style.getLayer("safe-navi-route-options-"+suffix));
                assertTrue(a.findViewById(R.id.fastestRecommendation).isShown());
                assertTrue(a.findViewById(R.id.balancedRecommendation).isShown());
                assertTrue(a.findViewById(R.id.safestRecommendation).isShown());
            }catch(Exception error){throw new AssertionError(error);}});
            android.os.SystemClock.sleep(5000);shot("cloud-presentation-three-routes.png");
            scenario.onActivity(a->a.findViewById(R.id.findRouteButton).performClick());
            android.os.SystemClock.sleep(500);shot("cloud-presentation-route-choices.png");
        }
    }
    private static List<Object> iter(JSONArray values)throws JSONException{
        List<Object> result=new ArrayList<>();for(int i=0;i<values.length();i++)result.add(values.get(i));return result;
    }
}
