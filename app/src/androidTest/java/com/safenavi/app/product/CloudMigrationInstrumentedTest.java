package com.safenavi.app.product;

import static org.junit.Assert.*;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CloudMigrationInstrumentedTest {
    Context context;String origin;
    Map<String,Map<String,?>> backups=new HashMap<>();
    String[] files={"safe_navi_server","safe_navi_session","safe_navi_offline_reports",
            "safe_navi_background_alerts","safe_navi_verified_hazard_cache"};
    @Before public void setup(){
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();origin=ServerConnection.baseUrl();
        for(String file:files){SharedPreferences p=context.getSharedPreferences(file,0);backups.put(file,p.getAll());p.edit().clear().commit();}
    }
    @After public void restore(){
        for(String file:files){
            SharedPreferences.Editor editor=context.getSharedPreferences(file,0).edit().clear();
            for(Map.Entry<String,?> entry:backups.get(file).entrySet()){
                Object v=entry.getValue();String k=entry.getKey();
                if(v instanceof String)editor.putString(k,(String)v);
                else if(v instanceof Boolean)editor.putBoolean(k,(Boolean)v);
                else if(v instanceof Integer)editor.putInt(k,(Integer)v);
                else if(v instanceof Long)editor.putLong(k,(Long)v);
                else if(v instanceof Float)editor.putFloat(k,(Float)v);
            }editor.commit();
        }
        ServerConnection.save(context,origin);
    }
    @Test public void laptopUpgradeSwitchesAutomaticallyWithoutUploadingOldReports()throws Exception{
        String laptop="http://192.168.29.221:8000";
        ServerConnection.save(context,laptop);
        ProductSession.save(context,"laptop-token","local-user","Test user","migration@example.test","citizen");
        JSONObject report=new JSONObject().put("user_id","migration@example.test")
            .put("payload",new JSONObject().put("client_request_id","migration-test"))
            .put("evidence_local_ids",new JSONArray().put("retained-photo"));
        context.getSharedPreferences("safe_navi_offline_reports",0).edit().putString("queue",new JSONArray().put(report).toString()).commit();
        ScopedDraftStore.save(context,"migration-test",new JSONObject().put("title","Retain this local draft"));
        ServerConnection.initialize(context);
        assertEquals(ServerConnection.CLOUD_ORIGIN,ServerConnection.baseUrl());
        assertFalse(ProductSession.isSignedIn(context));
        assertEquals("migration@example.test",ProductSession.email(context));
        assertEquals(0,OfflineReportQueue.pending(context).length());
        assertEquals(laptop,OfflineReportQueue.allEntries(context).getJSONObject(0).getString("origin"));
        assertEquals("retained-photo",OfflineReportQueue.allEntries(context).getJSONObject(0).getJSONArray("evidence_local_ids").getString(0));
        ProductSession.save(context,"cloud-token","cloud-user","Test user","migration@example.test","citizen");
        assertEquals(0,OfflineReportQueue.pending(context).length());
        // A later explicit local override remains available for recovering old local data.
        ServerConnection.save(context,laptop);ServerConnection.initialize(context);
        assertEquals(laptop,ServerConnection.baseUrl());
        assertEquals(1,OfflineReportQueue.pending(context).length());
        assertEquals("Retain this local draft",ScopedDraftStore.load(context,"migration-test").getString("title"));
        ScopedDraftStore.clear(context,"migration-test");
    }
    @Test public void existingCloudSessionIsNotSignedOut(){
        ServerConnection.save(context,ServerConnection.CLOUD_ORIGIN);
        ProductSession.save(context,"cloud-token","cloud-user","Test user","cloud@example.test","citizen");
        ServerConnection.initialize(context);
        assertEquals(ServerConnection.CLOUD_ORIGIN,ServerConnection.baseUrl());
        assertEquals("cloud-token",ProductSession.token(context));
        assertTrue(ProductSession.isSignedIn(context));
    }
    @Test public void newInstallSelectsCloud(){
        ServerConnection.initialize(context);
        assertEquals(ServerConnection.CLOUD_ORIGIN,ServerConnection.baseUrl());
    }
}
