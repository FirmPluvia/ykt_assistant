package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypted cookies with revocable leases: stale clients can never reactivate a signed-out account. */
final class SessionVault {
    private final SharedPreferences prefs;
    private final AtomicLong generation=new AtomicLong();
    private final Object ioLock=new Object();
    private static final String ALIAS="rain.assistant.session.v2";
    SessionVault(Context context) { prefs=context.getSharedPreferences("native_session",Context.MODE_PRIVATE); }
    RainApi.CookieStorage session() {
        final long lease=generation.get();
        return new RainApi.CookieStorage() {
            @Override public String read() throws Exception {
                if(lease!=generation.get()||prefs.getBoolean("signed_out",false)) return "[]";
                synchronized(ioLock) {
                    if(lease!=generation.get()||prefs.getBoolean("signed_out",false)) return "[]";
                    String raw=prefs.getString("encrypted",null); if(raw==null) return "[]";
                    String[] parts=raw.split(":"); if(parts.length!=2) throw new Exception("本机会话数据损坏，请重新扫码登录");
                    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
                    String result=new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
                    return lease==generation.get()&&!prefs.getBoolean("signed_out",false)?result:"[]";
                }
            }
            @Override public void write(String cookies) throws Exception {
                synchronized(ioLock) {
                    if(lease!=generation.get()) return;
                    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
                    String value=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(cookies.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
                    if(lease!=generation.get()) return;
                    if(!prefs.edit().putString("encrypted",value).commit()) throw new Exception("无法保存本机会话");
                    // A sign-out may have occurred during the disk commit. New writes use the same ioLock.
                    if(lease!=generation.get()) prefs.edit().remove("encrypted").apply();
                }
            }
            @Override public void authenticated() throws Exception {
                if(lease!=generation.get()) throw new Exception("账号会话已切换，请重新扫码登录");
                // Called only after the Activity accepts the login result on the UI thread.
                prefs.edit().putBoolean("signed_out",false).apply();
            }
        };
    }
    void clear() {
        generation.incrementAndGet();
        // apply updates the in-process view immediately; never wait on encryption, disk or the network here.
        prefs.edit().remove("encrypted").putBoolean("signed_out",true).apply();
    }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
