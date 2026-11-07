package com.ykt.assistant;

import android.app.Dialog;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import okhttp3.OkHttpClient;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class UpdateFlowTest {
    private static HttpUrl endpoint;
    public static class TestActivity extends MainActivity { @Override protected UpdateChecker createUpdateChecker() { return new UpdateChecker(endpoint,new OkHttpClient()); } }
    private MockWebServer server; private ActivityController<TestActivity> controller; private TestActivity activity;
    @Before public void setup() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("native_session",0).edit().clear().commit(); RuntimeEnvironment.getApplication().getSharedPreferences("native_preferences",0).edit().clear().commit(); server=new MockWebServer(); server.start(); endpoint=server.url("/note?sev=j1");
    }
    @After public void cleanup() throws Exception { if(controller!=null) controller.pause().stop().destroy(); ShadowLooper.idleMainLooper(); server.close(); }
    private String note(int code,boolean force) throws Exception { JSONObject data=new JSONObject().put("schemaVersion",1).put("versionCode",code).put("versionName","3.2").put("forceUpdate",force).put("downloadUrl","").put("githubUrl",""); return new JSONObject().put("content","<p>［"+Base64.getEncoder().encodeToString(data.toString().getBytes(StandardCharsets.UTF_8))+"］</p>").toString(); }
    private Object get(String name) throws Exception { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void start() { controller=Robolectric.buildActivity(TestActivity.class).setup(); activity=controller.get(); }
    private void waitForUpdate() throws Exception { for(int n=0;n<200;n++) { ShadowLooper.idleMainLooper(); if(!(Boolean)get("checkingUpdate")) return; Thread.sleep(10); } fail("update timeout"); }
    private View label(View view,String text) { if(view instanceof TextView&&text.equals(((TextView)view).getText().toString())) return view; if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int n=0;n<group.getChildCount();n++) { View v=label(group.getChildAt(n),text); if(v!=null) return v; } } return null; }
    @Test public void currentManifestChecksSilentlyOnEveryForegroundWithCurrentUaAndNoCookies() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("native_preferences",0).edit().putString(UserAgent.KEY,"Chosen UA/3.2").commit(); server.enqueue(new MockResponse().setBody(note(BuildConfig.VERSION_CODE,false))); start(); waitForUpdate(); assertNull(get("updateDialog")); assertFalse(activity.isFinishing()); okhttp3.mockwebserver.RecordedRequest request=server.takeRequest(2,TimeUnit.SECONDS); assertEquals("GET",request.getMethod()); assertEquals("Chosen UA/3.2",request.getHeader("User-Agent")); assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization")); assertEquals(true,get("startupSessionRestored"));
        server.enqueue(new MockResponse().setBody(note(BuildConfig.VERSION_CODE,false))); controller.pause().stop().start().resume().visible(); waitForUpdate(); assertEquals(2,server.getRequestCount()); assertNull(get("updateDialog"));
    }
    @Test public void mandatoryUpdateCannotBeCancelledOrSkipped() throws Exception {
        server.enqueue(new MockResponse().setBody(note(BuildConfig.VERSION_CODE+1,true))); start(); waitForUpdate(); Dialog dialog=(Dialog)get("updateDialog"); assertTrue(dialog.isShowing()); assertNull(label(dialog.getWindow().getDecorView(),"稍后再说")); dialog.onBackPressed(); activity.onBackPressed(); assertTrue(dialog.isShowing()); assertFalse(label(dialog.getWindow().getDecorView(),"网盘更新").isEnabled());
    }
    @Test public void optionalUpdateCanBePostponedWithoutStoppingForegroundChecks() throws Exception {
        server.enqueue(new MockResponse().setBody(note(BuildConfig.VERSION_CODE+1,false))); start(); waitForUpdate(); Dialog dialog=(Dialog)get("updateDialog"); label(dialog.getWindow().getDecorView(),"稍后再说").performClick(); ShadowLooper.idleMainLooper(); assertFalse(dialog.isShowing()); server.enqueue(new MockResponse().setBody(note(BuildConfig.VERSION_CODE+1,false))); controller.pause().stop().start().resume().visible(); waitForUpdate(); assertEquals(2,server.getRequestCount()); assertNull(get("updateDialog"));
    }
    @Test public void malformedConfigurationOpensProjectAndExitsAsOriginalBehaviour() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"content\":\"［base64编码内容］\"}")); start(); waitForUpdate(); assertTrue(activity.isFinishing()); Intent intent=Shadows.shadowOf(activity).getNextStartedActivity(); assertEquals(UpdateInfo.PROJECT_URL,intent.getDataString()); assertTrue((intent.getFlags()&Intent.FLAG_ACTIVITY_NEW_TASK)!=0);
    }
    @Test public void httpFailureAlsoExitsAndCannotFallBackToStaleConfiguration() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500)); start(); waitForUpdate(); assertTrue(activity.isFinishing()); assertEquals(UpdateInfo.PROJECT_URL,Shadows.shadowOf(activity).getNextStartedActivity().getDataString());
    }
    @Test public void parserRejectsUnsafeLinksAndSupportsRichTextBrackets() throws Exception {
        String good=note(BuildConfig.VERSION_CODE,true); assertFalse(UpdateInfo.fromNote(good).required(BuildConfig.VERSION_CODE)); assertTrue(UpdateInfo.fromNote(note(BuildConfig.VERSION_CODE+1,true)).required(BuildConfig.VERSION_CODE));
        JSONObject unsafe=new JSONObject().put("schemaVersion",1).put("versionCode",99).put("versionName","3.2").put("forceUpdate",true).put("downloadUrl","javascript:alert(1)"); String raw=new JSONObject().put("content","［"+Base64.getEncoder().encodeToString(unsafe.toString().getBytes(StandardCharsets.UTF_8))+"］").toString(); try { UpdateInfo.fromNote(raw); fail(); } catch(Exception expected) {}
    }
}
