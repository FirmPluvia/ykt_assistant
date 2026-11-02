package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Encrypted account-scoped activity snapshots. Identical content does not rewrite the file. */
final class FeedStore {
    private static final Object LOCK=new Object();
    private static final int LIMIT=16*1024*1024;
    final File directory;
    private final SharedPreferences prefs;
    private final LessonCache.Crypto crypto;
    FeedStore(Context context) { this(new File(LessonCache.privateRoot(context),"files/activities"),context.getSharedPreferences("activity_cache",0),new LessonCache.DeviceCrypto("rain.assistant.activities.v1")); }
    FeedStore(File dir,SharedPreferences prefs,LessonCache.Crypto crypto) { directory=dir; this.prefs=prefs; this.crypto=crypto; }
    private long epoch() { return prefs.getLong("epoch",0); }
    void revoke() { prefs.edit().putLong("epoch",epoch()+1).apply(); }
    void purgeRevoked() { synchronized(LOCK) { File[] files=directory.listFiles(); if(files!=null) for(File file:files) if(!file.getName().startsWith(epoch()+"-")) file.delete(); } }
    Session session(String host,String uid) { return new Session(epoch(),host,uid); }
    final class Session {
        private final long lease;
        private final String scope;
        private String last;
        Session(long lease,String host,String uid) { this.lease=lease; scope=lease+"|"+host+"|"+uid; }
        private AtomicFile file() throws Exception { byte[] hash=MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)); StringBuilder name=new StringBuilder(); for(byte b:hash) name.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return new AtomicFile(new File(directory,lease+"-"+name+".bin")); }
        JSONObject read() {
            synchronized(LOCK) {
                if(lease!=epoch()) return null;
                try {
                    AtomicFile file=file(); if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists()) { last=null; return null; }
                    if(file.getBaseFile().length()>LIMIT+1024) { file.delete(); return null; }
                    JSONObject body=new JSONObject(new String(crypto.open(file.readFully(),scope.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8));
                    if(body.getInt("version")!=1) return null;
                    if(lease!=epoch()) return null; last=canonical(body); return body;
                } catch(Exception e) { try { file().delete(); } catch(Exception ignored) {} last=null; return null; }
            }
        }
        boolean write(JSONObject body) throws Exception {
            synchronized(LOCK) {
                if(lease!=epoch()) return false;
                String next=canonical(body); if(last==null) read(); if(next.equals(last)) return false;
                byte[] plain=next.getBytes(StandardCharsets.UTF_8); if(plain.length>LIMIT) throw new java.io.IOException("动态缓存超过大小限制");
                byte[] data=crypto.seal(plain,scope.getBytes(StandardCharsets.UTF_8)); if(lease!=epoch()) return false;
                if(!directory.isDirectory()&&!directory.mkdirs()) throw new java.io.IOException("动态缓存目录不可用");
                AtomicFile file=file(); FileOutputStream out=null;
                try { out=file.startWrite(); out.write(data); file.finishWrite(out); if(lease!=epoch()) { file.delete(); return false; } last=next; return true; }
                catch(Exception e) { if(out!=null) file.failWrite(out); throw e; }
            }
        }
    }
    static String canonical(Object value) throws Exception {
        if(value==null||value==JSONObject.NULL) return "null";
        if(value instanceof JSONObject) { JSONObject object=(JSONObject)value; List<String> keys=new ArrayList<>(); java.util.Iterator<String> it=object.keys(); while(it.hasNext()) keys.add(it.next()); Collections.sort(keys); StringBuilder out=new StringBuilder("{"); for(String key:keys) { if(out.length()>1) out.append(','); out.append(JSONObject.quote(key)).append(':').append(canonical(object.get(key))); } return out.append('}').toString(); }
        if(value instanceof JSONArray) { JSONArray array=(JSONArray)value; StringBuilder out=new StringBuilder("["); for(int i=0;i<array.length();i++) { if(i>0) out.append(','); out.append(canonical(array.get(i))); } return out.append(']').toString(); }
        if(value instanceof String) return JSONObject.quote((String)value);
        return String.valueOf(value);
    }
}
