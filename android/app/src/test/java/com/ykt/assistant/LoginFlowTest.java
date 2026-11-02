package com.ykt.assistant;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w393dp-h851dp-hdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class LoginFlowTest {
    public static class LoginActivity extends MainActivity {
        
        static MockWebServer[] servers;
        static List<RainApi> clients;
        static List<Integer> selected;
        @Override protected RainApi createLoginClient(int service,RainApi.CookieStorage storage) throws Exception {
            selected.add(service); RainApi client=new RainApi(servers[service].url("/"),storage); clients.add(client); return client;
        }
    }
    private ActivityController<LoginActivity> controller;
    private LoginActivity activity;
    private MockWebServer first,second;
    private AtomicInteger firstQr,secondQr;
    private CountDownLatch release;
    private MockResponse response(String json) { return new MockResponse().setHeader("Content-Type","application/json").setBody(json); }
    private Dispatcher dispatcher(AtomicInteger qr,boolean login) {
        return new Dispatcher() {
            @Override public MockResponse dispatch(RecordedRequest request) {
                String path=request.getRequestUrl().encodedPath();
                if(path.endsWith("/pre-info")) {
                    int n=qr.incrementAndGet(); if(release!=null&&qr==firstQr&&n==1) { try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } }
                    return response("{\"data\":{\"token\":\"qr-"+n+"\",\"qrContent\":\"https://www.yuketang.cn/login?token="+n+"\"}}");
                }
                if(path.equals("/api/v3/user/login")) return login?response("{\"data\":{}}"):new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE);
                if(path.equals("/v/course_meta/user_info")) return response("{\"data\":{\"user_profile\":{\"user_id\":\"fixture\",\"name\":\"扫码同学\"}}}");
                if(path.equals("/v/course_meta/learning_list/")) return response("{\"data\":[]}");
                if(path.equals("/api/v3/classroom/on-lesson")) return response("{\"data\":{\"onLessonClassrooms\":[]}}");
                return new MockResponse().setResponseCode(404);
            }
        };
    }
    @Before public void create() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); app.getSharedPreferences("native_session",0).edit().clear().commit(); app.getSharedPreferences("native_preferences",0).edit().clear().commit();
        first=new MockWebServer(); second=new MockWebServer(); firstQr=new AtomicInteger(); secondQr=new AtomicInteger(); first.setDispatcher(dispatcher(firstQr,false)); second.setDispatcher(dispatcher(secondQr,false)); first.start(); second.start();
        LoginActivity.servers=new MockWebServer[]{first,second,first,second}; LoginActivity.clients=new ArrayList<>(); LoginActivity.selected=new ArrayList<>();
        controller=Robolectric.buildActivity(LoginActivity.class).setup(); activity=controller.get(); ShadowLooper.idleMainLooper();
    }
    @After public void destroy() throws Exception {
        if(release!=null) release.countDown(); if(controller!=null) controller.pause().stop().destroy(); for(RainApi client:LoginActivity.clients) client.cancel(); first.shutdown(); second.shutdown(); ShadowLooper.idleMainLooper();
    }
    private Object get(String name) throws Exception { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void open() throws Exception { Method m=MainActivity.class.getDeclaredMethod("openLogin"); m.setAccessible(true); m.invoke(activity); }
    private View find(View v,String text) { if(v instanceof TextView&&((TextView)v).getText().toString().equals(text)) return v; if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) { View found=find(((ViewGroup)v).getChildAt(i),text); if(found!=null) return found; } return null; }
    private View panel() throws Exception { return ((Dialog)get("loginDialog")).getWindow().getDecorView(); }
    private void click(View panel,String text) { View v=find(panel,text); assertNotNull(text,v); while(!v.isClickable()&&v.getParent() instanceof View) v=(View)v.getParent(); assertTrue(text,v.performClick()); ShadowLooper.idleMainLooper(); }
    private void waitFor(BooleanSupplier condition) throws Exception { for(int i=0;i<200;i++) { ShadowLooper.idleMainLooper(); if(condition.getAsBoolean()) return; Thread.sleep(20); } fail("login operation timed out"); }
    private boolean ready() { try { View v=find(panel(),"刷新二维码"); return v!=null&&v.isEnabled(); } catch(Exception e) { return false; } }
    @Test public void openingAutomaticallyLoadsQrAndRefreshRequiresConfirmation() throws Exception {
        open(); waitFor(this::ready); assertEquals(1,firstQr.get()); RainApi original=LoginActivity.clients.get(0);
        click(panel(),"刷新二维码"); Dialog confirm=ShadowDialog.getLatestDialog(); assertNotNull(find(confirm.getWindow().getDecorView(),"刷新登录二维码？")); assertEquals(1,firstQr.get()); assertFalse(original.isClosed());
        click(confirm.getWindow().getDecorView(),"继续扫码"); assertEquals(1,firstQr.get()); assertFalse(original.isClosed());
        click(panel(),"刷新二维码"); confirm=ShadowDialog.getLatestDialog(); click(confirm.getWindow().getDecorView(),"确认刷新"); waitFor(this::ready); assertEquals(2,firstQr.get()); assertTrue(original.isClosed()); assertEquals(2,LoginActivity.clients.size());
        click(panel(),"稍后再说"); assertTrue(LoginActivity.clients.get(1).isClosed()); assertNull(get("user")); assertNull(get("loginDialog"));
    }
    @Test public void serviceSwitchDuringFetchCancelsOldAttemptAndLoadsSelectedService() throws Exception {
        release=new CountDownLatch(1); open(); waitFor(() -> firstQr.get()==1); RainApi old=LoginActivity.clients.get(0);
        click(panel(),"雨课堂"); Dialog picker=ShadowDialog.getLatestDialog(); click(picker.getWindow().getDecorView(),"荷塘雨课堂"); waitFor(this::ready);
        assertTrue(old.isClosed()); assertEquals(1,secondQr.get()); assertEquals(Integer.valueOf(1),LoginActivity.selected.get(1)); assertNotNull(find(panel(),"荷塘雨课堂")); release.countDown();
        waitFor(() -> { try { return ((Integer)get("busyCount"))==1; } catch(Exception e) { return false; } }); assertNull(get("user")); assertSame(LoginActivity.clients.get(1),get("api"));
    }
    @Test public void dismissingLoginBeforeQrCompletesCannotRestoreLogin() throws Exception {
        release=new CountDownLatch(1); open(); waitFor(() -> firstQr.get()==1); Dialog login=(Dialog)get("loginDialog"); RainApi old=LoginActivity.clients.get(0); login.dismiss(); release.countDown();
        waitFor(() -> { try { return ((Integer)get("busyCount"))==0; } catch(Exception e) { return false; } }); assertTrue(old.isClosed()); assertNull(get("user")); assertNull(get("loginDialog"));
    }
    @Test public void scannedQrCompletesLoginAndKeepsAcceptedClientOpen() throws Exception {
        first.setDispatcher(dispatcher(firstQr,true)); open(); waitFor(() -> { try { return get("user")!=null; } catch(Exception e) { return false; } });
        assertNull(get("loginDialog")); assertFalse(LoginActivity.clients.get(0).isClosed()); assertSame(LoginActivity.clients.get(0),get("api")); assertFalse(activity.getSharedPreferences("native_session",0).getBoolean("signed_out",true));
    }
}
