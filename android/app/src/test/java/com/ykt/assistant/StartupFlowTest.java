package com.ykt.assistant;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class StartupFlowTest {
    private static HttpUrl endpoint;
    public static class TestActivity extends MainActivity {
        @Override protected void checkForUpdates(boolean manual) {}
        @Override protected RainApi createStartupApi() throws Exception {
            RainApiTest.Store store=new RainApiTest.Store(); store.data="[{\"raw\":\"sessionid=fixture; Path=/\"}]"; return new RainApi(endpoint,store);
        }
    }
    private MockWebServer server;
    private ActivityController<TestActivity> controller;
    private TestActivity activity;
    @Before public void setup() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("native_session",0).edit().clear().commit();
        RuntimeEnvironment.getApplication().getSharedPreferences("native_preferences",0).edit().clear().commit();
        server=new MockWebServer(); server.start(); endpoint=server.url("/");
    }
    @After public void cleanup() throws Exception { if(controller!=null) controller.pause().stop().destroy(); ShadowLooper.idleMainLooper(); server.close(); }
    private void start() { controller=Robolectric.buildActivity(TestActivity.class).setup(); activity=controller.get(); }
    private Object get(String name) throws Exception { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private View find(View view,String label) { if(view instanceof TextView&&label.equals(((TextView)view).getText().toString())) return view; if(view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++) { View child=find(group.getChildAt(i),label); if(child!=null) return child; } } return null; }
    private void settle() throws Exception { for(int i=0;i<200;i++) { ShadowLooper.idleMainLooper(); if(((Integer)get("busyCount"))==0) return; Thread.sleep(10); } fail("startup did not settle"); }
    @Test public void autoLoginAndCoursesLoadWithoutStartupModalAndForegroundAddsNoRequests() throws Exception {
        List<String> paths=new CopyOnWriteArrayList<>();
        server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest r) {
            paths.add(r.getPath());
            if(r.getPath().equals("/v/course_meta/user_info")) return new MockResponse().setBody("{\"data\":{\"user_profile\":{\"user_id\":\"test-user\",\"name\":\"同学\"}}}");
            if(r.getPath().equals("/v/course_meta/learning_list/")) return new MockResponse().setBody("{\"data\":[{\"course_id\":\"math\",\"course_name\":\"测试数学课\"}]}");
            if(r.getPath().equals("/api/v3/classroom/on-lesson")) return new MockResponse().setBody("{\"data\":{\"onLessonClassrooms\":[]}}");
            return new MockResponse().setResponseCode(404);
        } });
        start(); settle(); assertNotNull(get("user")); assertNotNull(get("catalog")); assertNotNull(find(activity.getWindow().getDecorView(),"测试数学课")); assertNull(ShadowDialog.getLatestDialog()); assertEquals(BuildConfig.APPLICATION_ID,activity.getPackageName());
        assertEquals(java.util.Arrays.asList("/v/course_meta/user_info","/v/course_meta/learning_list/","/api/v3/classroom/on-lesson"),new ArrayList<>(paths));
        controller.pause().stop().start().resume().visible(); ShadowLooper.idleMainLooper(); assertEquals(3,server.getRequestCount()); assertFalse(activity.isFinishing()); assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void failedAccountRestoreDoesNotExitOrOpenProjectPage() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500)); start(); settle();
        assertNull(get("user")); assertFalse(activity.isFinishing()); assertEquals(1,server.getRequestCount()); assertNull(Shadows.shadowOf(activity).getNextStartedActivity()); assertNull(ShadowDialog.getLatestDialog());
        controller.pause().stop().start().resume().visible(); ShadowLooper.idleMainLooper(); assertEquals(1,server.getRequestCount()); assertFalse(activity.isFinishing());
    }
}
