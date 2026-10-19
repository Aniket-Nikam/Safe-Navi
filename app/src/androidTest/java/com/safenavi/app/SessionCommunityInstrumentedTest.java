package com.safenavi.app;

import static org.junit.Assert.*;
import android.content.*;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.safenavi.app.product.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@RunWith(AndroidJUnit4.class)
public class SessionCommunityInstrumentedTest {
    private Context context; private String origin; private Map<String,?> session;
    @Before public void setup() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        origin=ServerConnection.baseUrl();session=context.getSharedPreferences("safe_navi_session",0).getAll();
        ServerConnection.save(context,"http://127.0.0.1:8017");
        ProductSession.save(context,"test-token","ui-user","Test user","session@ui.test","citizen");
    }
    @After public void cleanup() {
        ScopedDraftStore.clear(context,"community");
        SharedPreferences.Editor editor=context.getSharedPreferences("safe_navi_session",0).edit().clear();
        for(Map.Entry<String,?> entry:session.entrySet()){
            Object v=entry.getValue();if(v instanceof String)editor.putString(entry.getKey(),(String)v);else if(v instanceof Boolean)editor.putBoolean(entry.getKey(),(Boolean)v);
            else if(v instanceof Long)editor.putLong(entry.getKey(),(Long)v);else if(v instanceof Integer)editor.putInt(entry.getKey(),(Integer)v);
        } editor.commit();ServerConnection.save(context,origin);
    }
    @Test public void oldResponsesCannotInvalidateANewLoginOrDifferentServer() {
        assertFalse(ProductSession.reject(context,"old-token",ServerConnection.baseUrl()));
        assertFalse(ProductSession.reject(context,"test-token","http://127.0.0.1:9999"));
        assertTrue(ProductSession.isSignedIn(context));
        assertTrue(ProductSession.reject(context,"test-token",ServerConnection.baseUrl()));
        assertFalse("Repeated failures must not start another recovery",ProductSession.reject(context,"test-token",ServerConnection.baseUrl()));
        assertFalse(ProductSession.isSignedIn(context));
        assertEquals("session@ui.test",ProductSession.email(context));
        ProductSession.save(context,"new-token","ui-user","Test user","session@ui.test","citizen");
        assertFalse(ProductSession.reject(context,"test-token",ServerConnection.baseUrl()));
        assertTrue(ProductSession.isSignedIn(context));
    }
    @Test public void onlyUnauthorizedResponseRequiresSignInNotServerFailure() throws Exception {
        respond(503);assertTrue(ProductSession.isSignedIn(context));
        respond(401);assertTrue(ProductSession.needsSignIn(context));
    }
    @Test public void rejectedLoginExplainsCredentialsWithoutInvalidatingSession() throws Exception {
        String error=respond(401,true);
        assertTrue(error.contains("Email or password was not accepted"));
        assertFalse(error.contains("sign in again"));
        assertTrue(ProductSession.isSignedIn(context));
    }
    @Test public void loginFieldsRemainReadableInBothThemes() throws Exception {
        int previous=androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode();
        try {
            for(boolean dark:new boolean[]{false,true}) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                        dark?androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES:androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));
                try(ActivityScenario<LoginActivity> scenario=ActivityScenario.launch(new Intent(context,LoginActivity.class).putExtra("LOGIN_TYPE","citizen"))) {
                    scenario.onActivity(a->{
                        EditText email=a.findViewById(R.id.emailEditText), password=a.findViewById(R.id.passwordEditText);
                        email.setText("citizen@example.test"); password.setText("example-password");
                        assertTrue(email.isSingleLine()); assertTrue(password.isSingleLine());
                        assertTrue(email.getWidth()>0); assertTrue(password.getWidth()>0);
                        // Material owns the single field background; no separate EditText underline.
                        assertTrue(email.getBackground() instanceof com.google.android.material.shape.MaterialShapeDrawable);
                        assertTrue(password.getBackground() instanceof com.google.android.material.shape.MaterialShapeDrawable);
                        assertTrue(a.findViewById(R.id.loginServerAddress).isShown());
                    });
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                    android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                    try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"login-1.6-"+(dark?"dark":"light")+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(previous));
        }
    }
    private String respond(int status) throws Exception { return respond(status,false); }
    private String respond(int status,boolean login) throws Exception {
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            ServerConnection.save(context,"http://127.0.0.1:"+listener.getLocalPort());
            Thread server=new Thread(()->{try(Socket socket=listener.accept()){
                BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
                byte[] body="{\"detail\":\"Test response\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                OutputStream out=socket.getOutputStream();out.write(("HTTP/1.1 "+status+" Test\r\nContent-Type: application/json\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));out.write(body);out.flush();
            }catch(Exception e){throw new RuntimeException(e);}});server.setDaemon(true);server.start();
            CountDownLatch done=new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<String> error=new java.util.concurrent.atomic.AtomicReference<>("");
            ProductApiClient.ObjectCallback callback=new ProductApiClient.ObjectCallback(){public void onSuccess(JSONObject value){done.countDown();}public void onError(String message){error.set(message);done.countDown();}};
            if(login)new ProductApiClient(context).login("person@example.test","incorrect-password",callback);
            else new ProductApiClient(context).validateSession(callback);
            assertTrue(done.await(10,TimeUnit.SECONDS));server.join(2000);
            return error.get();
        }
    }
    @Test public void communityComposerIsCollapsedAndDraftSurvivesRecreation() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("localWorkflow")));
        okhttp3.Request request=new okhttp3.Request.Builder().url("http://127.0.0.1:8017/api/v1/auth/login")
            .post(okhttp3.RequestBody.create("{\"email\":\"citizen@ui.test\",\"password\":\"ui-workflow-password\"}",okhttp3.MediaType.parse("application/json"))).build();
        try(okhttp3.Response response=new okhttp3.OkHttpClient().newCall(request).execute()){
            assertTrue(response.isSuccessful());JSONObject value=new JSONObject(response.body().string()),user=value.getJSONObject("user");
            ProductSession.save(context,value.getString("access_token"),user.getString("id"),user.getString("name"),user.getString("email"),user.getString("role"));
        }
        ScopedDraftStore.clear(context,"community");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(new Intent(context,MainActivity.class))){
            scenario.onActivity(a->{a.getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer,new CommunityFragment()).commitNow();
                assertFalse(a.findViewById(R.id.communityComposerCard).isShown());
                a.findViewById(R.id.communityComposeToggle).performClick();
                ((EditText)a.findViewById(R.id.communityComposer)).setText("Test draft survives reconnect");
            });
            scenario.recreate();
            scenario.onActivity(a->{if(a.findViewById(R.id.communityComposer)==null)a.getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer,new CommunityFragment()).commitNow();
                assertEquals("Test draft survives reconnect",((EditText)a.findViewById(R.id.communityComposer)).getText().toString());
                assertTrue(a.findViewById(R.id.communityComposerCard).isShown());
            });
            android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"community-draft-1.5.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        }
        ScopedDraftStore.clear(context,"community");
    }
}
