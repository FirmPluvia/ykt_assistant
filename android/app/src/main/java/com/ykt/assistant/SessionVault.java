package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Plain private Cookie storage with revocable leases. AES is used only to migrate older sessions. */
final class SessionVault {
    interface LegacyDecoder { String decode(String value) throws Exception; }
    private final LegacyDecoder legacyDecoder;
    private final SharedPreferences prefs;
    private final AtomicLong generation=new AtomicLong();
    private final Object ioLock=new Object();
    private static final String LEGACY_ALIAS="rain.assistant.session.v2";
    SessionVault(Context context) { this(context,null); }
    SessionVault(Context context,LegacyDecoder decoder) { prefs=context.getSharedPreferences("native_session",Context.MODE_PRIVATE); legacyDecoder=decoder==null?this::decodeLegacy:decoder; }
    RainApi.CookieStorage session() {
        final long lease=generation.get();
        return new RainApi.CookieStorage() {
            @Override public String read() throws Exception {
                if(lease!=generation.get()||prefs.getBoolean("signed_out",false)) return "[]";
                synchronized(ioLock) {
                    if(lease!=generation.get()||prefs.getBoolean("signed_out",false)) return "[]";
                    String result=prefs.getString("cookies",null);
                    if(result==null) {
                        String legacy=prefs.getString("encrypted",null); if(legacy==null) return "[]";
                        result=legacyDecoder.decode(legacy);
                        if(lease!=generation.get()||prefs.getBoolean("signed_out",false)) return "[]";
                        if(!prefs.edit().putString("cookies",result).remove("encrypted").commit()) throw new Exception("本机会话迁移失败，请重新登录");
                        if(lease!=generation.get()) { prefs.edit().remove("cookies").apply(); return "[]"; }
                    }
                    return result;
                }
            }
            @Override public void write(String cookies) throws Exception {
                synchronized(ioLock) {
                    if(lease!=generation.get()) return;
                    if(!prefs.edit().putString("cookies",cookies).remove("encrypted").commit()) throw new Exception("无法保存本机会话");
                    if(lease!=generation.get()) prefs.edit().remove("cookies").apply();
                }
            }
            @Override public void authenticated() throws Exception {
                if(lease!=generation.get()) throw new Exception("账号会话已切换，请重新登录");
                prefs.edit().putBoolean("signed_out",false).apply();
            }
        };
    }
    void clear() {
        generation.incrementAndGet();
        prefs.edit().remove("cookies").remove("encrypted").putBoolean("signed_out",true).apply();
    }
    private String decodeLegacy(String raw) throws Exception {
        String[] parts=raw.split(":"); if(parts.length!=2) throw new Exception("本机会话数据损坏，请重新登录");
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        SecretKey key=(SecretKey)store.getKey(LEGACY_ALIAS,null); if(key==null) throw new Exception("旧会话密钥不可用，请重新登录");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
}
