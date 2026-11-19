package com.ykt.assistant;
import android.content.Context;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class AiSettingsTest {
    Context context;AiSettings settings;
    @Before public void setup()throws Exception{context=RuntimeEnvironment.getApplication();context.getSharedPreferences("ai_settings",0).edit().clear().commit();javax.crypto.KeyGenerator g=javax.crypto.KeyGenerator.getInstance("AES");g.init(128);javax.crypto.SecretKey key=g.generateKey();settings=new AiSettings(context,new AiSettings.CipherCodec(create->key));}
    void save(String key,boolean clear)throws Exception{settings.save("https://ai.example/v1","model",key,clear,true,false,false,()->false);}
    @Test public void keyIsEncryptedAndBlankSaveRetainsIt()throws Exception{save("very-private-key",false);String disk=context.getSharedPreferences("ai_settings",0).getAll().toString();assertFalse(disk.contains("very-private-key"));assertTrue(settings.preview().hasKey);assertEquals("very-private-key",settings.read().key);long revision=settings.revision();save("",false);assertEquals("very-private-key",settings.read().key);assertTrue(settings.revision()>revision);}
    @Test public void explicitKeyClearAndConfigurationClearHaveSeparateEffects()throws Exception{save("private-key",false);save("",true);assertEquals("",settings.read().key);assertTrue(settings.preview().configured());long revision=settings.revision();settings.clear();assertFalse(settings.preview().configured());assertFalse(settings.preview().hasKey);assertTrue(settings.revision()>revision);}
    @Test public void tamperedCipherDoesNotBecomePlaintextOrSilentlyAnonymous()throws Exception{save("private-key",false);context.getSharedPreferences("ai_settings",0).edit().putString("encrypted_key","broken").commit();try{settings.read();fail();}catch(Exception e){assertTrue(e.getMessage().contains("Key"));assertFalse(e.getMessage().contains("private-key"));}save("",true);assertEquals("",settings.read().key);}
    @Test public void cancellationAndInvalidConfigurationDoNotOverwriteAcceptedSettings()throws Exception{save("private-key",false);long revision=settings.revision();settings.save("https://other.example","new","other-key",false,false,true,true,()->true);assertEquals(revision,settings.revision());assertEquals("private-key",settings.read().key);try{settings.save("http://other.example","new","x",false,false,false,false,()->false);fail();}catch(IllegalArgumentException expected){}assertEquals(revision,settings.revision());}
}
