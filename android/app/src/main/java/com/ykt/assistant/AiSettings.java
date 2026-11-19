package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.util.Base64;
import java.util.function.BooleanSupplier;

/** Only the API Key is encrypted. No key is ever prefilled into a UI or exported. */
final class AiSettings {
    interface SecretCodec {String encrypt(String plain)throws Exception;String decrypt(String encrypted)throws Exception;}
    static final class CipherCodec implements SecretCodec {
        interface KeySource {SecretKey key(boolean create)throws Exception;}
        private final KeySource source;
        CipherCodec(KeySource source){this.source=source;}
        public String encrypt(String plain)throws Exception{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,source.key(true));return Base64.getEncoder().encodeToString(c.getIV())+":"+Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        public String decrypt(String encrypted)throws Exception{String[] parts=encrypted.split(":",-1);if(parts.length!=2)throw new Exception();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,source.key(false),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));return new String(c.doFinal(Base64.getDecoder().decode(parts[1])),java.nio.charset.StandardCharsets.UTF_8);}
    }
    static final class Preview {
        final String endpoint,model;final boolean hasKey,jsonMode,includeImage,autoFill;final long revision;
        Preview(SharedPreferences p){endpoint=p.getString("endpoint","");model=p.getString("model","");hasKey=!p.getString("encrypted_key","").isEmpty();jsonMode=true;includeImage=p.getBoolean("include_image",false);autoFill=p.getBoolean("auto_fill",false);revision=p.getLong("revision",0);}
        boolean configured(){return !endpoint.isEmpty()&&!model.isEmpty();}
        String summary(){if(!configured())return "未配置接口与模型";try{return model+" · "+new AiConfig(endpoint,model,"",jsonMode,includeImage,autoFill).origin();}catch(IllegalArgumentException e){return "配置无效，请重新设置";}}
    }
    private static final Object LOCK=new Object();
    private static final String ALIAS="rain.assistant.ai.key.v1";
    private final SharedPreferences prefs;private final SecretCodec secrets;
    AiSettings(Context context){this(context,new CipherCodec(create->{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);SecretKey key=(SecretKey)store.getKey(ALIAS,null);if(key!=null)return key;if(!create)throw new Exception();KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());return generator.generateKey();}));}
    AiSettings(Context context,SecretCodec secrets){prefs=context.getSharedPreferences("ai_settings",Context.MODE_PRIVATE);this.secrets=secrets;}
    Preview preview(){synchronized(LOCK){return new Preview(prefs);}}
    long revision(){return preview().revision;}
    AiConfig read()throws Exception{synchronized(LOCK){Preview p=new Preview(prefs);String key="",encrypted=prefs.getString("encrypted_key","");try{if(!encrypted.isEmpty())key=secrets.decrypt(encrypted);}catch(Exception e){throw new Exception("本机 AI Key 无法读取，请在 AI 设置中输入新 Key 或清除已保存的 Key");}return new AiConfig(p.endpoint,p.model,key,p.jsonMode,p.includeImage,p.autoFill);}}
    void save(String endpoint,String model,String newKey,boolean clearKey,boolean jsonMode,boolean includeImage,boolean autoFill,BooleanSupplier cancelled)throws Exception{
        synchronized(LOCK){
            if(cancelled.getAsBoolean())return;
            if(clearKey&&newKey!=null&&!newKey.trim().isEmpty())throw new IllegalArgumentException("输入新 Key 和清除旧 Key 请只选择一种操作");
            AiConfig checked=new AiConfig(endpoint,model,newKey,jsonMode,includeImage,autoFill);String encrypted=prefs.getString("encrypted_key","");
            try{if(!checked.key.isEmpty())encrypted=secrets.encrypt(checked.key);else if(clearKey)encrypted="";else if(!encrypted.isEmpty())secrets.decrypt(encrypted);}catch(Exception e){throw new Exception("AI Key 加密保存或读取失败，请重新输入 Key，或清除旧 Key 后重试");}
            if(cancelled.getAsBoolean())return;
            if(!prefs.edit().putString("endpoint",checked.endpoint.toString()).putString("model",checked.model).putString("encrypted_key",encrypted).putBoolean("json_mode",true).putBoolean("include_image",includeImage).putBoolean("auto_fill",autoFill).putLong("revision",prefs.getLong("revision",0)+1).commit())throw new Exception("无法保存 AI 配置，请重试");
        }
    }
    void clear()throws Exception{synchronized(LOCK){long revision=prefs.getLong("revision",0)+1;if(!prefs.edit().clear().putLong("revision",revision).commit())throw new Exception("无法清除 AI 配置，请重试");}}
}
