package com.safenavi.app.product;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Private, no-backup drafts, separated by account, backend and case. Not end-to-end encryption. */
public final class ScopedDraftStore {
    private ScopedDraftStore() { }
    private static File file(Context context,String name) throws Exception {
        String key=ServerConnection.baseUrl()+"|"+ProductSession.email(context)+"|"+name;
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder hash=new StringBuilder(); for(byte b:digest) hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        File dir=new File(context.getNoBackupFilesDir(),"drafts"); if(!dir.exists()&&!dir.mkdirs()) throw new IOException("Cannot save draft");
        return new File(dir,hash+".json");
    }
    public static synchronized JSONObject load(Context context,String name) {
        try { return new JSONObject(new String(new AtomicFile(file(context,name)).readFully(),StandardCharsets.UTF_8)); }
        catch(Exception error) { return new JSONObject(); }
    }
    public static synchronized boolean save(Context context,String name,JSONObject value) {
        AtomicFile target; FileOutputStream stream=null;
        try { target=new AtomicFile(file(context,name)); stream=target.startWrite();
            try { stream.write(value.toString().getBytes(StandardCharsets.UTF_8)); target.finishWrite(stream); return true; }
            catch(Exception error) { target.failWrite(stream); return false; }
        } catch(Exception error) { return false; }
    }
    public static synchronized void clear(Context context,String name) { try { new AtomicFile(file(context,name)).delete(); } catch(Exception ignored) { } }
    public static synchronized void appendEvidence(Context context,String name,String id) {
        JSONObject draft=load(context,name);org.json.JSONArray files=draft.optJSONArray("evidence");if(files==null)files=new org.json.JSONArray();
        for(int i=0;i<files.length();i++)if(id.equals(files.optString(i)))return;
        files.put(id);try{draft.put("evidence",files);save(context,name,draft);}catch(Exception ignored){}
    }
}
