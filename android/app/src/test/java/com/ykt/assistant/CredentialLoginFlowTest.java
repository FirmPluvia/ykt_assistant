package com.ykt.assistant;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class CredentialLoginFlowTest {
    public static class TestActivity extends MainActivity {
        static MockWebServer server; static List<HumanVerification.Callback> callbacks; static List<Dialog> challenges; static List<RainApi> clients;
        @Override protected void checkForUpdates(boolean manual) {}
        @Override protected RainApi createLoginClient(int service,RainApi.CookieStorage storage) throws Exception { RainApi client=new RainApi(server.url("/"),storage); clients.add(client); return client; }
        @Override protected Dialog showHumanVerification(RainApi client,HumanVerification.Callback callback) { Dialog dialog=new Dialog(this); dialog.setContentView(new TextView(this)); dialog.show(); callbacks.add(callback); challenges.add(dialog); return dialog; }
    }
    private ActivityController<TestActivity> controller; private TestActivity activity; private MockWebServer server;
    private final List<String> paths=new CopyOnWriteArrayList<>(); private volatile boolean rejectPassword; private volatile CountDownLatch loginEntered,releaseLogin;
    @Before public void setup() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); app.getSharedPreferences("native_session",0).edit().clear().commit(); app.getSharedPreferences("native_preferences",0).edit().clear().commit(); server=new MockWebServer(); server.start(); TestActivity.server=server; TestActivity.callbacks=new ArrayList<>(); TestActivity.challenges=new ArrayList<>(); TestActivity.clients=new ArrayList<>();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
            String path=request.getRequestUrl().encodedPath(); paths.add(path);
            if(path.equals("/api/v3/user/login/app")) { if(loginEntered!=null) { loginEntered.countDown(); try { releaseLogin.await(5,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } } return new MockResponse().setBody(rejectPassword?"{\"code\":40001,\"msg\":\"密码不正确\"}":"{\"code\":0}"); }
            if(path.equals("/v/course_meta/user_info")) return new MockResponse().setBody("{\"data\":{\"user_profile\":{\"user_id\":\"fixture\",\"name\":\"登录同学\"}}}");
            if(path.equals("/v/course_meta/learning_list/")) return new MockResponse().setBody("{\"data\":[]}");
            if(path.equals("/api/v3/classroom/on-lesson")) return new MockResponse().setBody("{\"data\":{\"onLessonClassrooms\":[]}}");
            if(path.equals("/api/v3/user/code/send")||path.equals("/api/v3/user/code/verify")) return new MockResponse().setBody("{\"code\":0}");
            return new MockResponse().setResponseCode(404);
        } }); controller=Robolectric.buildActivity(TestActivity.class).setup(); activity=controller.get(); ShadowLooper.idleMainLooper();
    }
    @After public void cleanup() throws Exception { if(releaseLogin!=null) releaseLogin.countDown(); if(controller!=null) controller.pause().stop().destroy(); for(Dialog dialog:TestActivity.challenges) if(dialog.isShowing()) dialog.dismiss(); ShadowLooper.idleMainLooper(); server.close(); }
    private Object get(String name) throws Exception { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void open(boolean sms) throws Exception { Method m=MainActivity.class.getDeclaredMethod("openAccountLogin",boolean.class); m.setAccessible(true); m.invoke(activity,sms); ShadowLooper.idleMainLooper(); }
    private View panel() throws Exception { return ((Dialog)get("loginDialog")).getWindow().getDecorView(); }
    private View find(View view,String label) { if(view instanceof TextView&&label.equals(((TextView)view).getText().toString())) return view; if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) { View found=find(group.getChildAt(i),label); if(found!=null) return found; } } return null; }
    private EditText field(View view,String label) { if(view instanceof EditText&&label.equals(view.getContentDescription())) return (EditText)view; if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) { EditText found=field(group.getChildAt(i),label); if(found!=null) return found; } } return null; }
    private void fill(String label,String value) throws Exception { EditText input=field(panel(),label); assertNotNull(label,input); input.setText(value); }
    private void click(String label) throws Exception { View v=find(panel(),label); assertNotNull(label,v); assertTrue(label,v.performClick()); ShadowLooper.idleMainLooper(); }
    private void approve(int index) { TestActivity.challenges.get(index).dismiss(); TestActivity.callbacks.get(index).verified(new HumanVerification.Proof("ticket-"+index,"rand-"+index)); ShadowLooper.idleMainLooper(); }
    private void waitFor(BooleanSupplier condition) throws Exception { for(int i=0;i<200;i++) { ShadowLooper.idleMainLooper(); if(condition.getAsBoolean()) return; Thread.sleep(15); } fail("account flow timeout"); }
    private boolean signed() { try { return get("user")!=null; } catch(Exception e) { return false; } }
    @Test public void passwordNeedsManualChallengeThenLoadsCoursesAndClearsSecret() throws Exception {
        open(false); fill("手机号或邮箱","13800000000"); fill("密码","private-password"); EditText password=field(panel(),"密码"); assertFalse(password.isSaveEnabled()); click("登录"); assertEquals(0,server.getRequestCount()); approve(0); waitFor(this::signed); waitFor(()->paths.contains("/api/v3/classroom/on-lesson")); assertNull(get("loginDialog")); assertEquals("",password.getText().toString()); assertEquals(java.util.Arrays.asList("/api/v3/user/login/app","/v/course_meta/user_info","/v/course_meta/learning_list/","/api/v3/classroom/on-lesson"),paths); assertFalse(activity.getSharedPreferences("native_preferences",0).getAll().toString().contains("private-password")); String logs=new String(((DiagnosticLog)get("diagnostics")).today(),java.nio.charset.StandardCharsets.UTF_8); assertFalse(logs.contains("private-password")); assertFalse(logs.contains("13800000000")); assertFalse(logs.contains("ticket-0")); assertFalse(logs.contains("rand-0"));
    }
    @Test public void smsSendRequiresChallengeAndEnforcesCooldownBeforeLogin() throws Exception {
        open(true); fill("手机号","13800000000"); click("获取验证码"); assertEquals(0,server.getRequestCount()); approve(0); waitFor(()->{try{return find(panel(),"验证码已发送，请查看短信")!=null;}catch(Exception e){return false;}}); assertEquals(1,server.getRequestCount()); assertFalse(find(panel(),"60 秒后重试").isEnabled()); fill("短信验证码","123456"); click("登录"); waitFor(this::signed); assertEquals(1,TestActivity.callbacks.size()); assertTrue(paths.indexOf("/api/v3/user/code/verify")<paths.indexOf("/api/v3/user/login/app"));
    }
    @Test public void invalidFieldsNeverStartChallengeOrHttp() throws Exception { open(false); click("登录"); fill("手机号或邮箱","user@example.com"); click("登录"); assertEquals(0,TestActivity.callbacks.size()); assertEquals(0,server.getRequestCount()); }
    @Test public void closingRejectsLateProofAndClearsInput() throws Exception {
        open(false); fill("手机号或邮箱","13800000000"); fill("密码","secret"); EditText password=field(panel(),"密码"); click("登录"); ((Dialog)get("loginDialog")).dismiss(); ShadowLooper.idleMainLooper(); approve(0); assertEquals(0,server.getRequestCount()); assertNull(get("user")); assertEquals("",password.getText().toString()); assertTrue(TestActivity.clients.get(0).isClosed());
    }
    @Test public void switchingMethodRetiresOldClientAndProof() throws Exception {
        open(false); fill("手机号或邮箱","13800000000"); fill("密码","secret"); click("登录"); RainApi old=TestActivity.clients.get(0); click("短信验证码"); approve(0); assertTrue(old.isClosed()); assertEquals(2,TestActivity.clients.size()); assertEquals(0,server.getRequestCount()); assertEquals("",field(panel(),"短信验证码").getText().toString());
    }
    @Test public void changedPhoneCannotUseOldSmsProof() throws Exception {
        open(true); fill("手机号","13800000000"); click("获取验证码"); approve(0); waitFor(()->{try{return find(panel(),"验证码已发送，请查看短信")!=null;}catch(Exception e){return false;}}); fill("手机号","13900000000"); fill("短信验证码","123456"); click("登录"); assertNotNull(find(panel(),"请先为当前手机号获取验证码")); assertEquals(1,server.getRequestCount()); assertNull(get("user"));
    }
    @Test public void passwordFailureRetriesWithNewHumanProof() throws Exception {
        rejectPassword=true; open(false); fill("手机号或邮箱","13800000000"); fill("密码","secret"); click("登录"); approve(0); waitFor(()->{try{return find(panel(),"登录失败：密码不正确")!=null;}catch(Exception e){return false;}}); assertNull(get("user")); rejectPassword=false; click("登录"); assertEquals(2,TestActivity.callbacks.size()); approve(1); waitFor(this::signed);
    }
    @Test public void closingDuringRequestCannotAcceptLateLogin() throws Exception {
        loginEntered=new CountDownLatch(1); releaseLogin=new CountDownLatch(1); open(false); fill("手机号或邮箱","13800000000"); fill("密码","secret"); click("登录"); approve(0); assertTrue(loginEntered.await(3,TimeUnit.SECONDS)); ((Dialog)get("loginDialog")).dismiss(); ShadowLooper.idleMainLooper(); releaseLogin.countDown(); waitFor(()->{try{return ((Integer)get("busyCount"))==0;}catch(Exception e){return false;}}); assertNull(get("user")); assertTrue(TestActivity.clients.get(0).isClosed()); assertTrue(activity.getSharedPreferences("native_session",0).getBoolean("signed_out",false));
    }
    @Test public void serviceSwitchRejectsPreviouslyIssuedChallenge() throws Exception {
        open(false); fill("手机号或邮箱","13800000000"); fill("密码","secret"); click("登录"); RainApi old=TestActivity.clients.get(0); click("雨课堂"); Dialog picker=ShadowDialog.getLatestDialog(); View choice=find(picker.getWindow().getDecorView(),"长江雨课堂"); assertNotNull(choice); while(!choice.isClickable()&&choice.getParent() instanceof View) choice=(View)choice.getParent(); assertTrue(choice.performClick()); ShadowLooper.idleMainLooper(); approve(0); assertTrue(old.isClosed()); assertEquals(2,TestActivity.clients.size()); assertEquals(2,activity.getSharedPreferences("native_preferences",0).getInt("service",-1)); assertEquals(0,server.getRequestCount()); assertEquals("",field(panel(),"密码").getText().toString());
    }
}
