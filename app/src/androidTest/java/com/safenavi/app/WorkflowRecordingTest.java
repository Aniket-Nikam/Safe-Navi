package com.safenavi.app;

import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.safenavi.app.product.*;
import com.safenavi.app.safety.model.*;
import com.safenavi.app.safety.network.*;
import okhttp3.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.maplibre.android.maps.*;

/** Explicit local recording only. Captures the installed production UI using demo accounts. */
@RunWith(AndroidJUnit4.class) @FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class WorkflowRecordingTest {
    Context context; final String base="http://127.0.0.1:8017";
    static boolean offlineReady; static String offlineStatus="";
    @Before public void setup() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("recording")));
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ServerConnection.save(context,base);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));
        login("citizen");
    }
    void login(String who)throws Exception{
        JSONObject value=req("POST","/api/v1/auth/login",new JSONObject().put("email",who+"@demo.test").put("password","demo-workflow-password"));
        JSONObject user=value.getJSONObject("user"); ProductSession.save(context,value.getString("access_token"),user.getString("id"),user.getString("name"),user.getString("email"),user.getString("role"));
    }
    JSONObject req(String method,String path,JSONObject body)throws Exception{
        Request.Builder b=new Request.Builder().url(base+path).header("Authorization","Bearer "+ProductSession.token(context));
        if(body!=null)b.method(method,RequestBody.create(body.toString(),MediaType.parse("application/json")));
        try(Response r=new OkHttpClient.Builder().readTimeout(90,TimeUnit.SECONDS).build().newCall(b.build()).execute()){
            String data=r.body().string(); assertTrue(r.code()+" "+data,r.isSuccessful());
            return data.startsWith("[")?new JSONObject().put("items",new JSONArray(data)):new JSONObject(data);
        }
    }
    Object field(Object obj,String name)throws Exception{Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    void call(Object obj,String name)throws Exception{Method m=obj.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(obj);}
    void callJson(Object obj,String name,JSONObject data)throws Exception{Method m=obj.getClass().getDeclaredMethod(name,JSONObject.class);m.setAccessible(true);m.invoke(obj,data);}
    interface Run<A>{void run(A a)throws Exception;}
    <A extends Activity>void ui(ActivityScenario<A> s,Run<A> fn){s.onActivity(a->{try{fn.run(a);}catch(Exception e){throw new AssertionError(e);}});}
    void waitFor(java.util.function.BooleanSupplier condition,int seconds,String message)throws Exception{
        long until=System.currentTimeMillis()+seconds*1000L;while(System.currentTimeMillis()<until){if(condition.getAsBoolean())return;Thread.sleep(250);}fail(message+"; visible: "+visible());
    }
    String visible(){StringBuilder out=new StringBuilder();collect(InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow(),out);return out.toString();}
    void collect(AccessibilityNodeInfo node,StringBuilder out){if(node==null)return;if(node.getText()!=null)out.append(node.getText()).append("\n");for(int i=0;i<node.getChildCount();i++)collect(node.getChild(i),out);}
    boolean tapNode(AccessibilityNodeInfo node,String label){if(node==null)return false;
        if(node.getText()!=null&&node.getText().toString().equals(label)){AccessibilityNodeInfo target=node;while(target!=null){if(target.isClickable())return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);target=target.getParent();}}
        for(int i=0;i<node.getChildCount();i++)if(tapNode(node.getChild(i),label))return true;return false;
    }
    void tap(String label)throws Exception{waitFor(()->tapNode(InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow(),label),15,"Cannot tap "+label);Thread.sleep(700);}
    void back()throws Exception{InstrumentationRegistry.getInstrumentation().getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);Thread.sleep(650);}
    boolean fillNode(AccessibilityNodeInfo node,String hint,String value){if(node==null)return false;String label=String.valueOf(node.getText())+" "+String.valueOf(node.getHintText());if(node.isEditable()&&label.contains(hint)){Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);}for(int i=0;i<node.getChildCount();i++)if(fillNode(node.getChild(i),hint,value))return true;return false;}
    void fill(String hint,String value)throws Exception{waitFor(()->fillNode(InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow(),hint,value),10,"Cannot fill "+hint);}
    View mapView(View root){if(root instanceof MapView)return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View result=mapView(((ViewGroup)root).getChildAt(i));if(result!=null)return result;}return null;}
    void shot(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(1800);
        waitFor(()->!visible().matches("(?s).*(Connecting securely|Refreshing news review queue|Navi is thinking|Thinking with Safe-Navi|Checking government-verified active|Securing evidence on this device|Accepted by server. Refreshing).*"),90,"Busy capture "+name);
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        File dir=new File(context.getExternalFilesDir(null),"workflow17");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        android.util.Log.i("SafeNaviRecording","CAPTURED "+name);
    }
    void scrollTo(View target){android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);target.requestRectangleOnScreen(rect,true);}
    void fragment(ActivityScenario<MainActivity> scenario,Fragment fragment){ui(scenario,a->a.getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer,fragment).commitNow());}
    Uri demoPhoto()throws Exception{
        JSONArray files=req("GET","/api/v1/reports",null).getJSONArray("items");String id=files.getJSONObject(0).getString("id");
        JSONArray evidence=req("GET","/api/v1/reports/"+id,null).getJSONArray("evidence");
        if(evidence.length()==0){for(int i=0;i<files.length();i++){evidence=req("GET","/api/v1/reports/"+files.getJSONObject(i).getString("id"),null).getJSONArray("evidence");if(evidence.length()>0)break;}}
        String eid=evidence.getJSONObject(0).getString("id"); byte[] bytes;
        try(Response response=new OkHttpClient().newCall(new Request.Builder().url(base+"/api/v1/evidence/"+eid).header("Authorization","Bearer "+ProductSession.token(context)).build()).execute()){assertTrue(response.isSuccessful());bytes=response.body().bytes();}
        ContentValues values=new ContentValues();values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"Demo evidence.jpg");values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        Uri uri=context.getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        try(OutputStream out=context.getContentResolver().openOutputStream(uri)){out.write(bytes);}return uri;
    }
    String caseId()throws Exception{JSONArray cases=req("GET","/api/v1/reports",null).getJSONArray("items");for(int i=0;i<cases.length();i++)if(cases.getJSONObject(i).optString("title").contains("road obstruction"))return cases.getJSONObject(i).getString("id");return cases.getJSONObject(0).getString("id");}

    @Test public void a_identityHomeAndReport()throws Exception{
        try(ActivityScenario<LoginTypeActivity>s=ActivityScenario.launch(LoginTypeActivity.class)){shot("01-roles");}
        try(ActivityScenario<LoginActivity>s=ActivityScenario.launch(new Intent(context,LoginActivity.class).putExtra("LOGIN_TYPE","citizen"))){ui(s,a->{((EditText)a.findViewById(R.id.emailEditText)).setText("citizen@demo.test");((EditText)a.findViewById(R.id.passwordEditText)).setText("demo-workflow-password");});shot("02-login");}
        try(ActivityScenario<SignUpActivity>s=ActivityScenario.launch(SignUpActivity.class)){shot("03-registration");}
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            Thread.sleep(2000);shot("04-home");
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->OfflineMapManager.download(context,new OfflineMapManager.Callback(){public void onStatus(String value,boolean ready){offlineStatus=value;offlineReady=ready;android.util.Log.i("SafeNaviRecording","OFFLINE "+value);}public void onProgress(long done,long all,long bytes){}}));
            ReportFragment report=new ReportFragment();fragment(s,report);Uri photo=demoPhoto();Thread.sleep(5000);
            ui(s,a->{((EditText)a.findViewById(R.id.editTextTitle)).setText("Demo: damaged streetlight near station");((AutoCompleteTextView)a.findViewById(R.id.autoCompleteCategory)).setText("Poor lighting",false);((EditText)a.findViewById(R.id.editTextDescription)).setText("The streetlight is flickering on the station approach. Please inspect the pole and wiring.");Method m=ReportFragment.class.getDeclaredMethod("uploadEvidence",Uri.class);m.setAccessible(true);m.invoke(report,photo);});
            Thread.sleep(1500);ui(s,a->{Method pin=ReportFragment.class.getDeclaredMethod("selectLocation",double.class,double.class);pin.setAccessible(true);pin.invoke(report,19.066,72.9986);});Thread.sleep(5000);shot("05-report-form");
            ui(s,a->scrollTo(a.findViewById(R.id.buttonSubmitReport)));Thread.sleep(2000);shot("06-report-location-evidence");
            ui(s,a->a.findViewById(R.id.buttonSubmitReport).performClick());shot("07-review-before-send");tap("Send report");
            Thread.sleep(2000);if(visible().contains("Submit new anyway"))tap("Submit new anyway");
            waitFor(()->visible().contains("Report secured for upload"),30,"Report not secured");shot("08-offline-evidence-storage");tap("Done");
            waitFor(()->OfflineReportQueue.pending(context).length()==0,60,"Automatic report upload did not finish");
            ui(s,a->call(report,"showHistory"));waitFor(()->visible().contains("received by server"),15,"History not ready");shot("09-report-history");back();
            JSONObject detail=req("GET","/api/v1/reports/"+caseId(),null);ui(s,a->ReportHistorySheet.detail(a,detail,()->{}));shot("10-case-timeline");back();
        }
    }

    @Test public void b_conversationsAndAssistant()throws Exception{
        String id=caseId();try(ActivityScenario<CaseConversationActivity>s=ActivityScenario.launch(new Intent(context,CaseConversationActivity.class).putExtra("report_id",id))){waitFor(()->visible().contains("Connected. Updates"),20,"Case chat not connected");shot("11-case-conversation");ui(s,a->((EditText)field(a,"composer")).setText("Thank you. Please notify me when the road is clear."));ui(s,a->((View)field(a,"send")).performClick());Thread.sleep(2000);shot("12-case-message-sent");}
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            CommunityFragment community=new CommunityFragment();fragment(s,community);waitFor(()->visible().contains("Demo update"),20,"Community feed missing");shot("13-community-feed");
            JSONObject post=req("GET","/api/v1/community/posts",null).getJSONArray("items").getJSONObject(0);
            ui(s,a->community.onComments(post));waitFor(()->visible().contains("Discussion"),15,"Discussion missing");shot("14-threaded-comments");back();
            ChatbotFragment assistant=new ChatbotFragment();fragment(s,assistant);ui(s,a->((EditText)a.findViewById(R.id.msgInput)).setText("What should I do if a road is flooded? Give a brief practical answer."));ui(s,a->a.findViewById(R.id.sendBtn).performClick());
            Thread.sleep(1000);waitFor(()->{try{return !visible().contains("Thinking with")&&!visible().contains("Navi is thinking");}catch(Exception e){return false;}},100,"Assistant still loading");shot("15-navi-guidance");
        }
    }

    @Test public void c_officialEvidenceAndNews()throws Exception{
        login("official");String id=caseId(); final JSONObject caseDetail=req("GET","/api/v1/reports/"+id,null);
        try(ActivityScenario<GovernmentSafetyDashboardActivity>s=ActivityScenario.launch(GovernmentSafetyDashboardActivity.class)){
            Thread.sleep(2000);ui(s,a->{Field f=a.getClass().getDeclaredField("currentReport");f.setAccessible(true);f.set(a,caseDetail);call(a,"renderCurrentReport");});shot("16-official-dashboard");
            ui(s,a->call(a,"showAssignmentDialog"));shot("17-official-assignment");back();
            ui(s,a->call(a,"showOperationalDetail"));waitFor(()->visible().contains("Operational record"),20,"Audit missing");shot("18-official-audit");back();
            ui(s,a->call(a,"showVerificationDialog"));shot("19-official-verification");back();
            ui(s,a->a.findViewById(R.id.viewReportEvidenceButton).performClick());waitFor(()->visible().contains("Demonstration evidence.jpg"),15,"Evidence picker missing");tap("Demonstration evidence.jpg · image/jpeg");Thread.sleep(1500);shot("20-evidence-photo");back();
        }
        try(ActivityScenario<NewsWorkbenchActivity>s=ActivityScenario.launch(NewsWorkbenchActivity.class)){
            waitFor(()->visible().contains("latest signals."),25,"News queue loading");shot("21-news-queue");
            JSONObject signal=req("GET","/api/v1/government/news-signals",null).getJSONArray("items").getJSONObject(0);
            ui(s,a->callJson(a,"review",signal));shot("22-news-source-review");back();
            ui(s,a->callJson(a,"publish",signal));Thread.sleep(6000);shot("23-news-pin-verification");back();
        }
    }

    @Test public void d_admin()throws Exception{
        login("admin");try(ActivityScenario<GovernmentSafetyDashboardActivity>s=ActivityScenario.launch(GovernmentSafetyDashboardActivity.class)){
            Thread.sleep(2000);shot("24-admin-controls");
            ui(s,a->call(a,"showAdminAccounts"));waitFor(()->visible().contains("Manage accounts"),15,"Accounts unavailable");shot("25-admin-accounts");back();
            JSONArray users=req("GET","/api/v1/admin/accounts",null).getJSONArray("items");JSONObject person=null;for(int i=0;i<users.length();i++)if(users.getJSONObject(i).optString("role").equals("citizen")){person=users.getJSONObject(i);break;}final JSONObject selected=person;
            ui(s,a->callJson(a,"showAdminAccountActions",selected));shot("26-admin-account-actions");back();
            ui(s,a->call(a,"showAdminAppSettings"));waitFor(()->visible().contains("Allow new citizen"),15,"Settings unavailable");shot("27-admin-app-settings");back();
        }
        try(ActivityScenario<NewsWorkbenchActivity>s=ActivityScenario.launch(NewsWorkbenchActivity.class)){waitFor(()->visible().contains("latest signals."),20,"News loading");ui(s,a->call(a,"schedule"));waitFor(()->visible().contains("News check interval"),20,"Schedule loading");shot("28-news-schedule");back();}
    }

    @Test public void e_realMapAndRoutes()throws Exception{
        try(ActivityScenario<MapsActivity>s=ActivityScenario.launch(MapsActivity.class)){
            waitMap(s);
            AtomicReference<OpenMapService> service=new AtomicReference<>();ui(s,a->service.set((OpenMapService)field(a,"openMapService")));
            CountDownLatch placesDone=new CountDownLatch(1);AtomicReference<List<PlaceSearchResult>> places=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
            service.get().suggest("SIES GST Nerul",19.04,73.02,new OpenMapService.ResultCallback<List<PlaceSearchResult>>(){public void onSuccess(List<PlaceSearchResult>p){places.set(p);placesDone.countDown();}public void onError(String m){error.set(m);placesDone.countDown();}});
            assertTrue(placesDone.await(45,TimeUnit.SECONDS));assertNull(error.get());assertFalse(places.get().isEmpty());
            ui(s,a->{PlaceSearchSheet sheet=new PlaceSearchSheet(a,service.get(),new OfflineRouter(a),"Destination","SIES GST Nerul",19.04,73.02,new PlaceSearchSheet.Listener(){public void onPlace(PlaceSearchResult p){}public void onMapPin(){}public void onDeviceLocation(){}});sheet.showResults(places.get());});shot("29-place-search");tap("Close");
            PlaceSearchResult start=new PlaceSearchResult("Vashi Railway Station",19.0632,72.9988),end=new PlaceSearchResult("SIES GST, Nerul",19.042644,73.023428);
            CountDownLatch roadsDone=new CountDownLatch(1);AtomicReference<List<RouteCandidate>> roads=new AtomicReference<>();
            service.get().routes(start.getLatitude(),start.getLongitude(),end.getLatitude(),end.getLongitude(),TravelMode.DRIVING,new OpenMapService.ResultCallback<List<RouteCandidate>>(){public void onSuccess(List<RouteCandidate>p){roads.set(p);roadsDone.countDown();}public void onError(String m){error.set(m);roadsDone.countDown();}});
            assertTrue(roadsDone.await(60,TimeUnit.SECONDS));assertNull(error.get());assertFalse(roads.get().isEmpty());
            CountDownLatch riskDone=new CountDownLatch(1);AtomicReference<DatasetRouteEvaluation> risk=new AtomicReference<>();
            new DatasetRiskService().evaluateRoutes(roads.get(),RouteProfile.BALANCED,"morning_peak",new DatasetRiskService.ResultCallback<DatasetRouteEvaluation>(){public void onSuccess(DatasetRouteEvaluation p){risk.set(p);riskDone.countDown();}public void onError(String m){error.set(m);riskDone.countDown();}});
            assertTrue(riskDone.await(120,TimeUnit.SECONDS));assertNull(error.get());assertFalse(risk.get().getRankedResults().isEmpty());
            ui(s,a->{Method render=MapsActivity.class.getDeclaredMethod("renderRankedRoutes",List.class,List.class,RouteProfile.class,PlaceSearchResult.class,PlaceSearchResult.class,String.class,String.class);render.setAccessible(true);render.invoke(a,risk.get().getRankedResults(),roads.get(),RouteProfile.BALANCED,start,end,"OpenStreetMap / Valhalla","Trained ML");});Thread.sleep(6500);waitFor(()->visible().contains("Fastest")&&!visible().contains("Search a place or address"),15,"Search must close before route capture");shot("30-three-real-routes");
            if(req("GET","/api/v1/hazards/map",null).getJSONArray("hazards").length()>1)shot("53-news-route-impact");
            ui(s,a->call(a,"showRouteDetailsSheet"));shot("31-route-reasoning");
            if(req("GET","/api/v1/hazards/map",null).getJSONArray("hazards").length()>1)shot("54-news-risk-proof");
            back();
            ui(s,a->{View button=a.findViewById(R.id.riskLayerButton);button.performClick();Object sheet=field(a,"activeMapSheet");if(sheet instanceof Dialog)((Dialog)sheet).dismiss();});Thread.sleep(2000);shot("32-full-route-risk");
            ui(s,a->call(a,"showAlertsSheet"));shot("33-verified-crime-alerts");back();
            ui(s,a->call(a,"showEmergencyTools"));shot("34-emergency-toolkit");back();
            ui(s,a->call(a,"openTrustedContacts"));waitFor(()->visible().contains("Call a trusted contact"),15,"Contacts missing");shot("35-sos-contact");back();
            ui(s,a->call(a,"showSavedPlaces"));Thread.sleep(1000);shot("36-saved-places");back();
            ui(s,a->call(a,"showTravelModePicker"));shot("46-travel-modes");back();
            ui(s,a->call(a,"showHazardFilters"));shot("47-map-layers");back();
            ui(s,a->call(a,"shareJourneyLink"));Thread.sleep(1600);shot("48-journey-sharing");back();
        }
    }
    void waitMap(ActivityScenario<MapsActivity>s)throws Exception{
        long until=System.currentTimeMillis()+90000;boolean ready=false;while(System.currentTimeMillis()<until){final boolean[] value={false};ui(s,a->value[0]=field(a,"mapStyle")!=null);if(value[0]){ready=true;break;}Thread.sleep(300);}assertTrue("Real map style failed to load",ready);Thread.sleep(7000);
    }

    @Test public void f_profileJourneyAndOffline()throws Exception{
        String journey=req("GET","/api/v1/journeys",null).getJSONArray("items").getJSONObject(0).getString("id");ProductSession.setActiveJourney(context,journey);
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            SettingsFragment settings=new SettingsFragment();fragment(s,settings);Thread.sleep(2000);shot("37-profile");
            ui(s,a->call(settings,"openEmergencyContacts"));waitFor(()->visible().contains("Demo trusted contact"),15,"Contacts not ready");shot("38-emergency-contacts");back();
            ui(s,a->call(settings,"openJourneyHistory"));Thread.sleep(1200);shot("39-shared-journey");back();
            ui(s,a->call(settings,"openSafetyCheckIns"));shot("40-checkin-schedule");tap("Every 15 minutes");Thread.sleep(1800);
            ui(s,a->call(settings,"openLanguagePicker"));shot("41-languages");back();
            ui(s,a->call(settings,"openSafetyInbox"));Thread.sleep(1200);shot("42-notifications");back();
            ui(s,a->call(settings,"changePassword"));shot("43-account-security");back();
            ui(s,a->call(settings,"openOfflineMaps"));Thread.sleep(1500);shot("44-offline-map-management");back();
            ui(s,a->ServerConnectionDialog.show(a));shot("45-server-settings");back();
        }
        android.util.Log.i("SafeNaviRecording","OFFLINE_FINAL "+offlineReady+" "+offlineStatus);
    }

    @Test public void g_polishedFinalStates()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){Thread.sleep(6500);shot("04-home");}
        try(ActivityScenario<SignUpActivity>s=ActivityScenario.launch(SignUpActivity.class)){
            fill("Username","Demo citizen");fill("Email","new.citizen@demo.test");fill("Confirm Password","example-password");fill("Password","example-password");shot("03-registration");
        }
        login("official");String id=caseId();final JSONObject detail=req("GET","/api/v1/reports/"+id,null);
        if(!"VERIFIED".equals(detail.optString("status")))try(ActivityScenario<GovernmentSafetyDashboardActivity>s=ActivityScenario.launch(GovernmentSafetyDashboardActivity.class)){
            Thread.sleep(1800);ui(s,a->{Field f=a.getClass().getDeclaredField("currentReport");f.setAccessible(true);f.set(a,detail);call(a,"renderCurrentReport");call(a,"showVerificationDialog");});
            fill("Verification reason","Demo field review: one lane is obstructed. Use the open lane carefully and follow the response team.");shot("19-official-verification");tap("Publish");Thread.sleep(1500);
        }
        JSONObject verified=req("GET","/api/v1/reports/"+id,null);assertEquals("VERIFIED",verified.optString("status"));
        try(ActivityScenario<GovernmentSafetyDashboardActivity>s=ActivityScenario.launch(GovernmentSafetyDashboardActivity.class)){Thread.sleep(1500);ui(s,a->ReportHistorySheet.detail(a,verified,()->{}));shot("49-verified-case");tap("Close");}
        try(ActivityScenario<NewsWorkbenchActivity>s=ActivityScenario.launch(NewsWorkbenchActivity.class)){
            waitFor(()->visible().contains("latest signals."),20,"News loading");JSONArray signals=req("GET","/api/v1/government/news-signals",null).getJSONArray("items");JSONObject best=signals.getJSONObject(0);for(int i=0;i<signals.length();i++){JSONObject item=signals.getJSONObject(i);if(!"OTHER".equals(item.optString("category"))&&!item.isNull("location_text")){best=item;break;}}final JSONObject story=best;
            ui(s,a->callJson(a,"review",story));shot("22-news-source-review");tap("Close");
            ui(s,a->callJson(a,"publish",story));Thread.sleep(6000);final int[] point=new int[2];
            ui(s,a->{Dialog dialog=(Dialog)field(a,"publicationDialog");View preview=mapView(dialog.getWindow().getDecorView());assertNotNull(preview);int[] xy=new int[2];preview.getLocationOnScreen(xy);point[0]=xy[0]+preview.getWidth()*3/5;point[1]=xy[1]+preview.getHeight()/2;});
            long now=SystemClock.uptimeMillis();android.app.UiAutomation auto=InstrumentationRegistry.getInstrumentation().getUiAutomation();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,point[0],point[1],0),up=android.view.MotionEvent.obtain(now,now+80,1,point[0],point[1],0);auto.injectInputEvent(down,true);auto.injectInputEvent(up,true);down.recycle();up.recycle();Thread.sleep(1200);
            fill("Affected road","Demonstration road pin - verify against the article before publication");fill("How did you verify","Recording demonstration only. An authorised official must confirm the real incident before publishing.");shot("23-news-pin-verification");tap("Cancel");
        }
        login("citizen");try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            SettingsFragment settings=new SettingsFragment();fragment(s,settings);ui(s,a->call(settings,"deleteAccount"));shot("50-delete-account");tap("Cancel");
        }
    }
    @Test public void h_readableFormValues()throws Exception{
        login("official");final JSONObject detail=req("GET","/api/v1/reports/"+caseId(),null);
        try(ActivityScenario<GovernmentSafetyDashboardActivity>s=ActivityScenario.launch(GovernmentSafetyDashboardActivity.class)){
            Thread.sleep(1500);ui(s,a->{Field f=a.getClass().getDeclaredField("currentReport");f.setAccessible(true);f.set(a,detail);call(a,"renderCurrentReport");call(a,"showVerificationDialog");});
            fill("No-go radius","30");fill("Verification reason","Demo: lane obstructed. Field team present. Proceed carefully.");shot("19-official-verification");tap("Cancel");
        }
        try(ActivityScenario<NewsWorkbenchActivity>s=ActivityScenario.launch(NewsWorkbenchActivity.class)){
            waitFor(()->visible().contains("latest signals."),20,"News loading");JSONObject signal=req("GET","/api/v1/government/news-signals",null).getJSONArray("items").getJSONObject(0);
            ui(s,a->callJson(a,"publish",signal));Thread.sleep(5500);final int[] xy=new int[2];ui(s,a->{Dialog d=(Dialog)field(a,"publicationDialog");View map=mapView(d.getWindow().getDecorView());map.getLocationOnScreen(xy);xy[0]+=map.getWidth()*3/5;xy[1]+=map.getHeight()/2;});
            long t=SystemClock.uptimeMillis();android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();MotionEvent a=MotionEvent.obtain(t,t,0,xy[0],xy[1],0),b=MotionEvent.obtain(t,t+90,1,xy[0],xy[1],0);automation.injectInputEvent(a,true);automation.injectInputEvent(b,true);a.recycle();b.recycle();Thread.sleep(1500);
            fill("Affected road","Demo road pin");fill("How did you verify","Demo only: confirm the current incident before publishing.");shot("23-news-pin-verification");tap("Cancel");
        }
    }
    double distanceToRoad(HazardLocation.GeoPoint p,RouteCandidate route){
        double best=Double.MAX_VALUE,scale=111320,cos=Math.cos(Math.toRadians(p.getLatitude()));
        List<HazardLocation.GeoPoint> shape=route.getGeometry();
        for(int i=1;i<shape.size();i++){
            double ax=(shape.get(i-1).getLongitude()-p.getLongitude())*scale*cos,ay=(shape.get(i-1).getLatitude()-p.getLatitude())*scale;
            double bx=(shape.get(i).getLongitude()-p.getLongitude())*scale*cos,by=(shape.get(i).getLatitude()-p.getLatitude())*scale;
            double dx=bx-ax,dy=by-ay,t=Math.max(0,Math.min(1,-(ax*dx+ay*dy)/Math.max(.001,dx*dx+dy*dy)));
            best=Math.min(best,Math.hypot(ax+t*dx,ay+t*dy));
        }return best;
    }
    @Test public void i_newsChangesRealRouteEvaluation()throws Exception{
        OpenMapService service=new OpenMapService();AtomicReference<List<RouteCandidate>> roads=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
        CountDownLatch done=new CountDownLatch(1);PlaceSearchResult start=new PlaceSearchResult("Vashi Railway Station",19.0632,72.9988),end=new PlaceSearchResult("SIES GST, Nerul",19.042644,73.023428);
        service.routes(start.getLatitude(),start.getLongitude(),end.getLatitude(),end.getLongitude(),TravelMode.DRIVING,new OpenMapService.ResultCallback<List<RouteCandidate>>(){
            public void onSuccess(List<RouteCandidate> values){roads.set(values);done.countDown();}public void onError(String m){error.set(m);done.countDown();}});
        assertTrue(done.await(70,TimeUnit.SECONDS));assertNull(error.get());assertTrue("Need real alternative roads for the demonstration",roads.get().size()>1);
        JSONObject payload=DatasetRiskService.buildRoutePayload(roads.get(),RouteProfile.BALANCED,"morning_peak");
        JSONObject before=req("POST","/api/v1/risk/route",payload);assertEquals("trained_ml",before.getString("risk_provider"));
        String quick=before.getJSONObject("recommendations").getJSONObject("fastest").getString("route_id");
        RouteCandidate fastest=null;for(RouteCandidate r:roads.get())if(r.getProviderRouteId().equals(quick))fastest=r;assertNotNull(fastest);
        HazardLocation.GeoPoint chosen=null;double separation=0;
        List<HazardLocation.GeoPoint> shape=fastest.getGeometry();
        for(int i=1;i<shape.size()-1;i++)for(RouteCandidate other:roads.get())if(other!=fastest){
            double gap=distanceToRoad(shape.get(i),other);if(gap>separation){separation=gap;chosen=shape.get(i);}
        }
        assertTrue("Real roads need a separated section",separation>150);final HazardLocation.GeoPoint pin=chosen;
        login("official");JSONArray signals=req("GET","/api/v1/government/news-signals",null).getJSONArray("items");
        JSONObject found=null;for(int i=0;i<signals.length();i++)if(signals.getJSONObject(i).optString("source_url").equals("https://example.com/safenavi-route-demo"))found=signals.getJSONObject(i);
        assertNotNull(found);final JSONObject story=found;
        try(ActivityScenario<NewsWorkbenchActivity>s=ActivityScenario.launch(NewsWorkbenchActivity.class)){
            waitFor(()->visible().contains("latest signals."),25,"News queue loading");shot("21-news-queue");
            ui(s,a->callJson(a,"review",story));shot("22-news-source-review");tap("Close");
            ui(s,a->callJson(a,"publish",story));Thread.sleep(5000);
            ui(s,a->{Dialog d=(Dialog)field(a,"publicationDialog");MapView view=(MapView)mapView(d.getWindow().getDecorView());
                view.getMapAsync(map->map.moveCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(new org.maplibre.android.geometry.LatLng(pin.getLatitude(),pin.getLongitude()),16)));});
            Thread.sleep(3500);final int[] xy=new int[2];
            ui(s,a->{Dialog d=(Dialog)field(a,"publicationDialog");View view=mapView(d.getWindow().getDecorView());view.getLocationOnScreen(xy);xy[0]+=view.getWidth()/2;xy[1]+=view.getHeight()/2;});
            long t=SystemClock.uptimeMillis();android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
            MotionEvent down=MotionEvent.obtain(t,t,0,xy[0],xy[1],0),up=MotionEvent.obtain(t,t+90,1,xy[0],xy[1],0);automation.injectInputEvent(down,true);automation.injectInputEvent(up,true);down.recycle();up.recycle();Thread.sleep(800);
            fill("Affected road","Demo route test road");fill("How did you verify","Fictional demo incident: show verified news changing route risk.");
            ui(s,a->{Dialog d=(Dialog)field(a,"publicationDialog");setChecked(d.getWindow().getDecorView());});
            shot("23-news-pin-verification");tap("Publish");waitFor(()->visible().contains("Hazard published to backend"),20,"Publication acknowledgement missing");shot("51-news-published");tap("Stay here");
            JSONArray updated=req("GET","/api/v1/government/news-signals",null).getJSONArray("items");JSONObject published=null;
            for(int i=0;i<updated.length();i++)if(updated.getJSONObject(i).optString("id").equals(story.optString("id")))published=updated.getJSONObject(i);
            assertNotNull(published);assertEquals("INCREASES_NEARBY_ROUTE_RISK",published.getString("routing_effect"));final JSONObject live=published;
            ui(s,a->callJson(a,"review",live));shot("52-news-routing-live");tap("Close");
        }
        login("citizen");JSONObject after=req("POST","/api/v1/risk/route",payload);
        double previous=0,current=0;JSONArray a=before.getJSONArray("results"),b=after.getJSONArray("results");
        for(int i=0;i<a.length();i++)if(a.getJSONObject(i).getString("route_id").equals(quick))previous=a.getJSONObject(i).getDouble("risk_exposure");
        for(int i=0;i<b.length();i++)if(b.getJSONObject(i).getString("route_id").equals(quick))current=b.getJSONObject(i).getDouble("risk_exposure");
        assertTrue("Published news must increase exposure",current>previous);
        assertNotEquals("Safest should avoid the demonstrated high-exposure road",quick,after.getJSONObject("recommendations").getJSONObject("safest").getString("route_id"));
        DatasetRouteEvaluation evaluated=DatasetRiskService.parseRouteEvaluation(after.toString(),roads.get(),RouteProfile.BALANCED);
        try(ActivityScenario<MapsActivity>s=ActivityScenario.launch(MapsActivity.class)){
            waitMap(s);ui(s,activity->{Method render=MapsActivity.class.getDeclaredMethod("renderRankedRoutes",List.class,List.class,RouteProfile.class,PlaceSearchResult.class,PlaceSearchResult.class,String.class,String.class);render.setAccessible(true);render.invoke(activity,evaluated.getRankedResults(),roads.get(),RouteProfile.BALANCED,start,end,"OpenStreetMap / Valhalla","Trained ML + verified demo news");});
            Thread.sleep(5000);shot("53-news-route-impact");
            ui(s,activity->call(activity,"showRouteDetailsSheet"));shot("54-news-risk-proof");back();
        }
        android.util.Log.i("SafeNaviRecording","NEWS_RISK_BEFORE="+previous+" AFTER="+current+" "+after.getJSONObject("recommendations"));
    }
    void setChecked(View view){
        if(view instanceof CheckBox)((CheckBox)view).setChecked(true);
        if(view instanceof Spinner){Spinner spinner=(Spinner)view;String first=spinner.getAdapter().getItem(0).toString();if(first.equals("LOW"))spinner.setSelection(2);if(first.equals("CAUTION"))spinner.setSelection(1);}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)setChecked(((ViewGroup)view).getChildAt(i));
    }
}
