package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.LongSupplier;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Permanent account/server/lesson-scoped encrypted credentials; logout revokes clients, not files. */
final class LessonCache {
    interface Crypto { byte[] seal(byte[] plain,byte[] aad) throws Exception; byte[] open(byte[] encoded,byte[] aad) throws Exception; }
    private final java.util.concurrent.atomic.AtomicLong generation=new java.util.concurrent.atomic.AtomicLong();
    final File directory;
    private final SharedPreferences prefs;
    private final Crypto crypto;
    private final LongSupplier clock;
    private static final Object lock=new Object();
    LessonCache(Context context) { this(new File(privateRoot(context),"files/classrooms"),context.getSharedPreferences("classroom_cache",0),new DeviceCrypto(),System::currentTimeMillis); }
    LessonCache(File dir,SharedPreferences prefs,Crypto crypto,LongSupplier clock) { directory=dir; this.prefs=prefs; this.crypto=crypto; this.clock=clock; }
    static File privateRoot(Context context) { File external=context.getExternalFilesDir(null); return external==null?context.getFilesDir():external.getParentFile(); }
    private long epoch() { return prefs.getLong("epoch",0); }
    void revoke() { generation.incrementAndGet(); }
    RainApi.LessonStorage session() {
        final long lease=generation.get(),storageEpoch=epoch();
        return new RainApi.LessonStorage() {
            private String scope(String host,String uid,String lesson) { return storageEpoch+"|"+host+"|"+uid+"|"+lesson; }
            private File file(String scope) throws Exception { byte[] hash=MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8)); StringBuilder key=new StringBuilder(); for(byte b:hash) key.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return new File(directory,storageEpoch+"-"+key+".bin"); }
            @Override public JSONObject read(String host,String uid,String lesson) throws Exception {
                synchronized(lock) {
                    if(lease!=generation.get()) return null; String scope=scope(host,uid,lesson); AtomicFile target=new AtomicFile(file(scope));
                    if(!target.getBaseFile().exists()) return null;
                    try {
                        JSONObject data=new JSONObject(new String(crypto.open(target.readFully(),scope.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8));
                        if(!host.equals(data.getString("host"))||!uid.equals(data.getString("uid"))||!lesson.equals(data.getString("lesson"))||data.optString("bearer").isEmpty()||data.optString("lessonToken").isEmpty()) { return null; }
                        return lease==generation.get()?data:null;
                    } catch(Exception e) { return null; }
                }
            }
            @Override public void write(String host,String uid,String lesson,JSONObject credentials) throws Exception {
                synchronized(lock) {
                    if(lease!=generation.get()) return; if(!directory.isDirectory()&&!directory.mkdirs()) throw new java.io.IOException("课堂缓存目录不可用");
                    String scope=scope(host,uid,lesson); JSONObject data=RainApi.object("host",host,"uid",uid,"lesson",lesson,"savedAt",clock.getAsLong(),"bearer",credentials.getString("bearer"),"lessonToken",credentials.getString("lessonToken"));
                    byte[] encrypted=crypto.seal(data.toString().getBytes(StandardCharsets.UTF_8),scope.getBytes(StandardCharsets.UTF_8)); if(lease!=generation.get()) return;
                    AtomicFile target=new AtomicFile(file(scope)); FileOutputStream output=null;
                    try { output=target.startWrite(); output.write(encrypted); if(lease!=generation.get()) { target.failWrite(output); return; } target.finishWrite(output); }
                    catch(Exception e) { if(output!=null) target.failWrite(output); throw e; }
                }
            }
            @Override public void remove(String host,String uid,String lesson) throws Exception { synchronized(lock) { new AtomicFile(file(scope(host,uid,lesson))).delete(); } }
        };
    }
    static final class DeviceCrypto implements Crypto {
        private final String alias;
        DeviceCrypto() { this("rain.assistant.classrooms.v1"); }
        DeviceCrypto(String alias) { this.alias=alias; }
        private SecretKey key() throws Exception {
            KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null); if(store.containsAlias(alias)) return (SecretKey)store.getKey(alias,null);
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore"); generator.init(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); return generator.generateKey();
        }
        public byte[] seal(byte[] plain,byte[] aad) throws Exception { Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key()); cipher.updateAAD(aad); byte[] body=cipher.doFinal(plain),iv=cipher.getIV(); byte[] out=new byte[iv.length+body.length]; System.arraycopy(iv,0,out,0,iv.length); System.arraycopy(body,0,out,iv.length,body.length); return out; }
        public byte[] open(byte[] encoded,byte[] aad) throws Exception { if(encoded.length<28) throw new java.io.IOException("无效课堂缓存"); Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(encoded,0,12))); cipher.updateAAD(aad); return cipher.doFinal(Arrays.copyOfRange(encoded,12,encoded.length)); }
    }
}
