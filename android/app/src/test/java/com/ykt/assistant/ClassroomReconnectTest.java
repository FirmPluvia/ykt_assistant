package com.ykt.assistant;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class ClassroomReconnectTest {
    public static class Host extends MainActivity {
        Fake transport;
        @Override protected RainApi createStartupApi() throws Exception { transport=new Fake();return transport; }
        @Override protected void checkForUpdates(boolean manual) {}
    }
    static class Fake extends RainApi {
        int attempts,teacherPage=4; boolean connected,connecting; String lesson="lesson"; Events events;
        Fake() throws Exception {super(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store());}
        @Override public String lesson(){return lesson;}
        @Override public String presentation(){return "pres";}
        @Override public int livePage(){return teacherPage;}
        @Override public boolean isClassroomConnected(){return connected;}
        @Override public boolean isClassroomConnecting(){return connecting;}
        @Override public void connect(Events e){attempts++;events=e;}
        @Override public byte[] slideImage(JSONObject slide) throws Exception {throw new Exception("test image");}
        void hello() throws Exception {connected=true;events.receive(RainApi.object("op","hello","presentationId","pres","slide",teacherPage));}
    }
    ActivityController<Host> controller; Host app; Fake api;
    @Before public void setup() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("native_preferences",0).edit().clear().commit();controller=Robolectric.buildActivity(Host.class).setup();app=controller.get();api=app.transport;
        set("user",RainApi.object("user_id","student"));set("tab",1);set("dynamicView",2);JSONArray slides=new JSONArray();for(int i=0;i<5;i++)slides.put(new JSONObject());set("presentation",RainApi.object("id","pres","slides",slides));call("render");
    }
    @After public void close(){controller.pause().stop().destroy();}
    void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(app,value);}
    Object get(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(app);}
    void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(app);}
    void advance(int seconds){ShadowLooper.idleMainLooper(seconds,TimeUnit.SECONDS);}
    View find(View v,String label){if(v instanceof TextView&&((TextView)v).getText().toString().equals(label))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
    @Test public void disconnectedReaderRetriesEveryFiveSecondsWithoutResetByRender()throws Exception{
        advance(4);assertEquals(0,api.attempts);call("render");advance(1);assertEquals(1,api.attempts);advance(5);assertEquals(2,api.attempts);api.connected=true;advance(10);assertEquals(2,api.attempts);
    }
    @Test public void checkedFollowReconnectsAndUsesFreshTeacherPageNotCachedPage()throws Exception{
        CheckBox follow=(CheckBox)find(app.getWindow().getDecorView(),"跟随教师翻页");follow.setChecked(false);set("page",0);follow.setChecked(true);assertEquals(1,api.attempts);assertEquals(0,get("page"));api.hello();ShadowLooper.idleMainLooper();assertEquals(3,get("page"));assertTrue(follow.isChecked());
    }
    @Test public void connectionInProgressDoesNotCreateParallelAttemptsAndOldCallbacksAreIgnored()throws Exception{
        api.connecting=true;advance(10);call("connectClass");assertEquals(0,api.attempts);api.connecting=false;call("connectClass");RainApi.Events old=api.events;call("connectClass");old.receive(RainApi.object("op","hello","presentationId","pres","slide",5));ShadowLooper.idleMainLooper();assertEquals(0,get("page"));api.hello();ShadowLooper.idleMainLooper();assertEquals(3,get("page"));
    }
    @Test public void leavingReaderAndBackgroundStopRetriesAndReturningResumes()throws Exception{
        set("tab",2);call("render");advance(10);assertEquals(0,api.attempts);set("tab",1);call("render");controller.pause().stop();advance(10);assertEquals(0,api.attempts);controller.start().resume();advance(5);assertEquals(1,api.attempts);
    }
    @Test public void finishedLessonAndMissingCredentialsStopFurtherRetries()throws Exception{
        call("connectClass");api.events.receive(RainApi.object("op","lessonfinished","finished",true));ShadowLooper.idleMainLooper();advance(10);assertEquals(1,api.attempts);set("classroomFinished",false);api.lesson=null;call("render");advance(10);assertEquals(1,api.attempts);
    }
}
