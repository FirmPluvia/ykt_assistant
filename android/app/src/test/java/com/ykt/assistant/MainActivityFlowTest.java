package com.ykt.assistant;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

/** Product flow regressions in Android's JVM sandbox; no real account or relay is used. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="w393dp-h851dp-hdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class MainActivityFlowTest {
    private ActivityController<? extends MainActivity> controller;
    public static class TestActivity extends MainActivity {
        @Override protected void checkForUpdates(boolean manual) {}  }
    private MainActivity activity;
    @Before public void create() {
        Context app=RuntimeEnvironment.getApplication();
        app.getSharedPreferences("native_session",0).edit().clear().commit();
        app.getSharedPreferences("native_preferences",0).edit().clear().commit();app.getSharedPreferences("saved_accounts",0).edit().clear().commit();app.getSharedPreferences("request_sync",0).edit().clear().commit();
        controller=Robolectric.buildActivity(TestActivity.class).setup(); activity=controller.get(); ShadowLooper.idleMainLooper();
    }
    @After public void destroy() { if(controller!=null) controller.pause().stop().destroy(); ShadowLooper.idleMainLooper(); }
    private Object get(String name) throws Exception { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); return f.get(activity); }
    private void set(String name,Object value) throws Exception { Field f=MainActivity.class.getDeclaredField(name); f.setAccessible(true); f.set(activity,value); }
    private void call(String name) throws Exception { Method m=MainActivity.class.getDeclaredMethod(name); m.setAccessible(true); m.invoke(activity); }
    private void loggedIn() throws Exception { set("user",RainApi.object("user_id","test-account","name","测试同学")); }
    private View root() { return activity.getWindow().getDecorView(); }
    private List<View> all(View root) { List<View> result=new ArrayList<>(); result.add(root); if(root instanceof ViewGroup) { ViewGroup group=(ViewGroup)root; for(int i=0;i<group.getChildCount();i++) result.addAll(all(group.getChildAt(i))); } return result; }
    private View find(View root,String label) { for(View v:all(root)) if(v instanceof TextView&&((TextView)v).getText().toString().equals(label)) return v; return null; }
    private void click(View root,String label) { View v=find(root,label); assertNotNull(label,v); assertTrue(label,v.performClick()); ShadowLooper.idleMainLooper(); }
    private void closeCard(View root) { View close=root.findViewWithTag("card-close"); assertNotNull(close); assertTrue(close.performClick()); ShadowLooper.idleMainLooper(); }
    private void waitFor(BooleanSupplier condition) throws Exception { for(int i=0;i<150;i++) { ShadowLooper.idleMainLooper(); if(condition.getAsBoolean()) return; Thread.sleep(20); } fail("async flow did not finish"); }
    private boolean loading() { try { return (Boolean)get("loadingCourses"); } catch(Exception e) { throw new RuntimeException(e); } }
    private MockResponse response(String body) { return new MockResponse().setHeader("Content-Type","application/json").setBody(body); }
    private void queueCourses(MockWebServer server,boolean live) {
        server.enqueue(response("{\"data\":[{\"course_id\":\"math\",\"course_name\":\"数学课\"},{\"course_id\":\"english\",\"course_name\":\"英语课\"}]}"));
        server.enqueue(response("{\"code\":0,\"data\":{\"onLessonClassrooms\":"+(live?"[{\"courseId\":\"math\",\"lessonId\":\"lesson\"}]":"[]")+"}}"));
    }
    @Test public void userAgentCardShowsOnlyDeviceSummaryButEditorKeepsTheFullHeader() throws Exception {
        loggedIn(); String ua=UserAgent.defaultFor("17","AAP-AN00","HONORAAP-AN00"); activity.getSharedPreferences("native_preferences",0).edit().putString(UserAgent.KEY,ua).commit(); set("tab",2); call("render"); assertNotNull(find(root(),"YOUR OWN LEARNING SPACE")); assertNotNull(find(root(),"Android 17; AAP-AN00")); assertNull(find(root(),ua)); TextView edit=(TextView)find(root(),"修改"),cookie=(TextView)find(root(),"管理 Cookie"); assertEquals(edit.getLayoutParams().height,cookie.getLayoutParams().height); assertEquals(edit.getLayoutParams().width,cookie.getLayoutParams().width); click(root(),"修改"); Dialog settings=ShadowDialog.getLatestDialog(); EditText input=(EditText)all(settings.getWindow().getDecorView()).stream().filter(v->v instanceof EditText).findFirst().get(); assertEquals(ua,input.getText().toString());
    }
    @Test public void minePageShowsTipsBeforeAuthorWithoutRetiredFeatureEntries() throws Exception {
        set("tab",2); call("render"); assertNotNull(find(root(),"账号管理")); assertNull(find(root(),"诊断日志")); assertNull(find(root(),"导出今日日志")); assertNull(find(root(),"查看随机点名记录")); java.util.List<View> views=all(root()); assertTrue(views.indexOf(find(root(),"使用小提示"))<views.indexOf(find(root(),"关于应用"))); assertTrue(views.indexOf(find(root(),"使用小提示"))<views.indexOf(find(root(),"作者 FirmPluvia"))); assertNotNull(find(root(),"雨课堂助手 "+BuildConfig.VERSION_NAME)); loggedIn(); call("render"); assertNotNull(find(root(),"管理 Cookie")); assertNull(find(root(),"诊断日志"));
    }
    @Test public void logoutConfirmationClearsAccountAndReturnsToLoggedOutHome() throws Exception {
        loggedIn(); set("tab",2); call("render"); click(root(),"退出当前账号");
        Dialog dialog=ShadowDialog.getLatestDialog(); assertTrue(dialog.isShowing());
        assertNull(find(dialog.getWindow().getDecorView(),"继续使用")); assertNotNull(dialog.getWindow().getDecorView().findViewWithTag("card-close"));
        click(dialog.getWindow().getDecorView(),"退出登录");
        assertFalse(dialog.isShowing()); assertNull(get("user")); assertNull(get("catalog")); assertNull(get("presentation")); assertEquals(0,get("tab"));
        assertTrue(activity.getSharedPreferences("native_session",0).getBoolean("signed_out",false));
        assertNotNull(find(root(),"扫码登录雨课堂")); assertEquals(0,get("busyCount"));
    }
    @Test public void oldPendingCallbackCannotRestoreAccountAfterLogout() throws Exception {
        loggedIn(); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),returned=new CountDownLatch(1);
        MainActivity.Task<JSONObject> task=() -> { entered.countDown(); if(!release.await(5,TimeUnit.SECONDS)) throw new Exception("timeout"); returned.countDown(); return RainApi.object("user_id","old-account"); };
        MainActivity.Done<JSONObject> done=p -> set("user",p);
        Method work=MainActivity.class.getDeclaredMethod("work",MainActivity.Task.class,MainActivity.Done.class); work.setAccessible(true); work.invoke(activity,task,done);
        assertTrue(entered.await(5,TimeUnit.SECONDS)); call("resetAccount"); release.countDown(); assertTrue(returned.await(5,TimeUnit.SECONDS));
        for(int i=0;i<10;i++) { Thread.sleep(20); ShadowLooper.idleMainLooper(); }
        assertNull(get("user")); assertEquals(0,get("busyCount"));
    }
    @Test public void refreshSelectsLiveThenFallsBackToAllAndHidesLiveFilter() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); ((RainApi)get("api")).cancel(); set("api",new RainApi(server.url("/"),new RainApiTest.Store())); loggedIn();
            queueCourses(server,true); call("loadCourses"); waitFor(() -> !loading());
            assertEquals(true,get("onlyLive")); assertNotNull(find(root(),"数学课")); assertNull(find(root(),"英语课"));
            queueCourses(server,false); call("loadCourses"); waitFor(() -> !loading());
            assertEquals(false,get("onlyLive")); assertNotNull(find(root(),"数学课")); assertNotNull(find(root(),"英语课")); assertNotNull(find(root(),"正在上课")); assertNotNull(find(root(),"无")); assertEquals(1,all(root()).stream().filter(v->v instanceof TextView&&((TextView)v).getText().toString().equals("正在上课")).count()); assertNotNull(find(root(),"刷新课堂"));
        }
    }
    @Test public void failedRefreshKeepsCoursesAndDoesNotAccumulateErrorCards() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); ((RainApi)get("api")).cancel(); set("api",new RainApi(server.url("/"),new RainApiTest.Store())); loggedIn();
            queueCourses(server,false); call("loadCourses"); waitFor(() -> !loading());
            for(int i=0;i<2;i++) { server.enqueue(new MockResponse().setResponseCode(503)); call("loadCourses"); waitFor(() -> !loading()); }
            assertNotNull(find(root(),"数学课")); assertNotNull(find(root(),"英语课"));
            int errors=0; for(View v:all(root())) if(v instanceof TextView&&((TextView)v).getText().toString().equals("刷新未完成")) errors++; assertEquals(1,errors);
        }
    }
    private CourseCatalog.Entry entryFixture() throws Exception {
        return CourseCatalog.from(RainApi.object("courses",new JSONArray().put(RainApi.object("course_id","math","course_name","数学课")),"live",new JSONArray().put(RainApi.object("courseId","math","lessonId","lesson")))).entries(true).get(0);
    }
    private RainApi installEntryApi(MockWebServer server) throws Exception {
        server.start(); ((RainApi)get("api")).cancel(); RainApi api=new RainApi(server.url("/"),new RainApiTest.Store());
        server.enqueue(response("{\"data\":{\"user_profile\":{\"user_id\":\"fixture\",\"name\":\"测试同学\"}}}")); api.profile(); server.takeRequest(); set("api",api); loggedIn(); return api;
    }
    private void clickClassroom() throws Exception { Method m=MainActivity.class.getDeclaredMethod("confirmCheckin",CourseCatalog.Entry.class); m.setAccessible(true); m.invoke(activity,entryFixture()); }
    private boolean entryBusy() { try { return (Boolean)get("checkingCredentials")||(Boolean)get("joining"); } catch(Exception e) { throw new RuntimeException(e); } }
    private boolean workBusy() { try { return (Integer)get("busyCount")>0; } catch(Exception e) { throw new RuntimeException(e); } }
    private void returnClassroomScan(String value) { activity.onActivityResult(46,android.app.Activity.RESULT_OK,new Intent().putExtra(QrScanActivity.CONTENT,value)); }
    @Test public void scanEntryIsBesideIdEntryAndOpensOnlyTheLocalScanner() throws Exception {
        try(MockWebServer server=new MockWebServer()) { installEntryApi(server); call("render"); View manual=find(root(),"使用课堂 ID 入课"),scan=find(root(),"扫码入课"); assertNotNull(manual); assertNotNull(scan); assertSame(manual.getParent(),scan.getParent()); assertEquals(manual.getLayoutParams().height,scan.getLayoutParams().height); click(root(),"扫码入课"); org.robolectric.shadows.ShadowActivity.IntentForResult next=Shadows.shadowOf(activity).getNextStartedActivityForResult(); assertEquals(46,next.requestCode); assertEquals(QrScanActivity.class.getName(),next.intent.getComponent().getClassName()); assertNull(Shadows.shadowOf(activity).getLastRequestedPermission()); activity.onActivityResult(46,android.app.Activity.RESULT_CANCELED,null); assertEquals(1,server.getRequestCount()); assertFalse((Boolean)get("scanningLesson")); }
    }
    @Test public void scanningDirectIdRequiresConfirmationBeforeCheckinAndPersistsCredentials() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); RainApiTest.Lessons cache=new RainApiTest.Lessons(); api.configure(cache); String id="1801803512884878080"; call("openLessonScanner"); returnClassroomScan(id); waitFor(()->!workBusy()&&!entryBusy()); Dialog prompt=ShadowDialog.getLatestDialog(); assertNotNull(find(prompt.getWindow().getDecorView(),"课堂 ID："+id)); assertEquals(1,server.getRequestCount()); assertNull(api.lesson()); server.enqueue(response("{\"data\":{\"lessonToken\":\"scanner-ws\"}}").setHeader("set-auth","scanner-http")); server.enqueue(new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener() { @Override public void onClosing(okhttp3.WebSocket ws,int code,String reason) { ws.close(code,reason); } })); click(prompt.getWindow().getDecorView(),"签到并进入课堂"); waitFor(()->!workBusy()&&!entryBusy()); assertEquals(id,api.lesson()); assertEquals("/api/v3/lesson/checkin",server.takeRequest(3,TimeUnit.SECONDS).getPath()); assertNotNull(cache.read(api.host(),api.user().optString("user_id"),id)); api.close(); }
    }
    @Test public void dynamicScanPostsOriginalUrlThenPromptsWithoutAutomaticallyCheckingIn() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); String qr="https://www.yuketang.cn/api/v3/lesson/check-in/dynamic-qr-code?sig=a%2Fb%3D&time=1700000000"; server.enqueue(response("{\"data\":{\"value\":\"12345678\"}}")); call("openLessonScanner"); returnClassroomScan(qr); waitFor(()->!workBusy()&&!entryBusy()); okhttp3.mockwebserver.RecordedRequest scan=server.takeRequest(3,TimeUnit.SECONDS); assertEquals("/api/v3/app/scan",scan.getPath()); assertEquals(qr,new JSONObject(scan.getBody().readUtf8()).getString("url")); assertEquals(api.user().optString("user_id"),scan.getHeader("x-uid")); assertNull(scan.getHeader("Authorization")); assertNull(api.lesson()); assertEquals(2,server.getRequestCount()); assertNotNull(find(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"课堂 ID：12345678")); }
    }
    @Test public void expiredScanShowsRetryWithoutCreatingClassroomCredentials() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); server.enqueue(response("{\"code\":51203}")); call("openLessonScanner"); returnClassroomScan("https://www.yuketang.cn/api/v3/lesson/check-in/dynamic-qr-code?sig=expired"); waitFor(()->!workBusy()); Dialog failed=ShadowDialog.getLatestDialog(); assertNotNull(find(failed.getWindow().getDecorView(),"重新扫码")); assertNotNull(find(failed.getWindow().getDecorView(),"签到二维码已过期，请取得新的二维码")); assertNull(api.lesson()); assertEquals(2,server.getRequestCount()); }
    }
    @Test public void scanResultAfterAccountLogoutCannotEnterOrCheckIn() throws Exception {
        try(MockWebServer server=new MockWebServer()) { installEntryApi(server); call("openLessonScanner"); call("resetAccount"); returnClassroomScan("12345678"); assertNull(get("user")); assertNull(((RainApi)get("api")).lesson()); assertEquals(1,server.getRequestCount()); assertFalse((Boolean)get("resolvingLessonScan")); }
    }
    private EditText manualLessonInput(Dialog dialog) { for(View view:all(dialog.getWindow().getDecorView())) if(view instanceof EditText) return (EditText)view; throw new AssertionError("missing lesson input"); }
    @Test public void liveCourseCopiesOnlyItsExactLessonIdWithoutRequestingEntry() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); String id="1801803512884878080";
            set("catalog",CourseCatalog.from(RainApi.object("courses",new JSONArray().put(RainApi.object("course_id","123","course_name","数学课")),"live",new JSONArray().put(RainApi.object("courseId","123","lessonId",id))))); call("render");
            click(root(),"复制课堂 ID"); android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE); assertEquals(id,clipboard.getPrimaryClip().getItemAt(0).getText().toString()); assertNull(api.lesson()); assertEquals(1,server.getRequestCount());
        }
    }
    @Test public void coursesWithoutLiveLessonsHaveManualEntryButNoCopyAction() throws Exception {
        try(MockWebServer server=new MockWebServer()) { installEntryApi(server); set("catalog",CourseCatalog.from(RainApi.object("courses",new JSONArray().put(RainApi.object("course_id","123","course_name","数学课")),"live",new JSONArray()))); call("render"); assertNull(find(root(),"复制课堂 ID")); assertNotNull(find(root(),"使用课堂 ID 入课")); }
    }
    @Test public void manualEntryRejectsEmptyNonNumericAndOversizeIdsWithoutNetwork() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); call("openLessonIdEntry"); Dialog dialog=ShadowDialog.getLatestDialog(); EditText input=manualLessonInput(dialog);
            for(String id:new String[]{"","0","000","12a","https://example.com/123","123456789012345678901"}) { input.setText(id); click(dialog.getWindow().getDecorView(),"继续入课"); assertTrue(dialog.isShowing()); assertNotNull(find(dialog.getWindow().getDecorView(),"请输入有效的数字课堂 ID（最多 20 位）")); }
            assertNull(api.lesson()); assertEquals(1,server.getRequestCount()); closeCard(dialog.getWindow().getDecorView());
        }
    }
    @Test public void manualIdRequiresConfirmationThenUsesOwnSessionAndCachesExactId() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); RainApiTest.Lessons cache=new RainApiTest.Lessons(); api.configure(cache); String id="1801803512884878080";
            call("render"); click(root(),"使用课堂 ID 入课"); Dialog input=ShadowDialog.getLatestDialog(); manualLessonInput(input).setText(id); click(input.getWindow().getDecorView(),"继续入课"); waitFor(() -> !entryBusy()); Dialog prompt=ShadowDialog.getLatestDialog(); assertNotNull(find(prompt.getWindow().getDecorView(),"课堂 ID："+id)); assertNull(api.lesson()); assertEquals(1,server.getRequestCount());
            server.enqueue(response("{\"data\":{\"lessonToken\":\"socket-fixture\"}}").setHeader("set-auth","http-fixture")); server.enqueue(new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener(){})); click(prompt.getWindow().getDecorView(),"签到并进入课堂"); waitFor(() -> !entryBusy());
            okhttp3.mockwebserver.RecordedRequest request=server.takeRequest(5,TimeUnit.SECONDS); assertEquals("/api/v3/lesson/checkin",request.getPath()); JSONObject body=new JSONObject(request.getBody().readUtf8()); assertEquals(id,body.getString("lessonId")); assertEquals(21,body.getInt("source")); assertTrue(body.getBoolean("joinIfNotIn")); assertEquals(id,api.lesson()); assertNotNull(cache.read(api.host(),api.user().optString("user_id"),id)); assertEquals(2,get("dynamicView"));
        }
    }
    @Test public void manualEntryRestoresSavedCredentialWithoutRepeatingCheckin() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); RainApiTest.Lessons cache=new RainApiTest.Lessons(); api.configure(cache); String id="12345678"; cache.write(api.host(),api.user().optString("user_id"),id,RainApi.object("bearer","saved-http","lessonToken","saved-ws"));
            server.enqueue(new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener(){})); call("openLessonIdEntry"); Dialog dialog=ShadowDialog.getLatestDialog(); manualLessonInput(dialog).setText(id); click(dialog.getWindow().getDecorView(),"继续入课"); waitFor(() -> !entryBusy()); assertEquals(id,api.lesson()); assertEquals("/wsapp/",server.takeRequest(5,TimeUnit.SECONDS).getPath()); assertEquals(2,server.getRequestCount()); assertFalse(dialog.isShowing());
        }
    }
    @Test public void rejectedManualLessonDoesNotShowSuccessfulEntryOrSaveCredential() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); RainApiTest.Lessons cache=new RainApiTest.Lessons(); api.configure(cache); call("openLessonIdEntry"); Dialog input=ShadowDialog.getLatestDialog(); manualLessonInput(input).setText("12345678"); click(input.getWindow().getDecorView(),"继续入课"); waitFor(() -> !entryBusy()); Dialog prompt=ShadowDialog.getLatestDialog(); server.enqueue(new MockResponse().setResponseCode(403)); click(prompt.getWindow().getDecorView(),"签到并进入课堂"); waitFor(() -> !entryBusy()); assertNull(api.lesson()); assertNull(cache.read(api.host(),api.user().optString("user_id"),"12345678")); assertTrue(prompt.isShowing()); assertNotNull(find(prompt.getWindow().getDecorView(),"重试进入课堂")); assertEquals(0,get("tab")); assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void missingLocalCredentialPromptsWithoutAnyReportOrCheckinRequest() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); clickClassroom(); waitFor(() -> !entryBusy());
            Dialog dialog=ShadowDialog.getLatestDialog(); View panel=dialog.getWindow().getDecorView(); assertNotNull(find(panel,"未找到课堂凭证")); assertNotNull(find(panel,"是否签到并进入课堂？")); assertNotNull(find(panel,"签到并进入课堂"));
            for(View v:all(panel)) assertFalse(v instanceof EditText);
            assertEquals(1,server.getRequestCount()); closeCard(panel); assertNull(api.lesson()); assertEquals(0,get("tab"));
        }
    }
    @Test public void missingCredentialSignsInOnlyAfterExplicitConfirmationAndStoresCredential() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); RainApiTest.Lessons cache=new RainApiTest.Lessons(); api.configure(cache); clickClassroom(); waitFor(() -> !entryBusy()); assertEquals(1,server.getRequestCount()); assertNull(api.lesson());
            server.enqueue(response("{\"data\":{\"lessonToken\":\"socket-fixture\"}}").setHeader("set-auth","http-fixture")); server.enqueue(new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener(){})); Dialog dialog=ShadowDialog.getLatestDialog(); click(dialog.getWindow().getDecorView(),"签到并进入课堂"); waitFor(() -> !entryBusy()); assertEquals("lesson",api.lesson()); assertEquals(2,get("dynamicView"));
            okhttp3.mockwebserver.RecordedRequest join=server.takeRequest(5,TimeUnit.SECONDS); assertNotNull(join); assertEquals("POST",join.getMethod()); assertEquals("/api/v3/lesson/checkin",join.getPath()); assertEquals("lesson",new JSONObject(join.getBody().readUtf8()).getString("lessonId")); assertNotNull(cache.read(api.host(),api.user().optString("user_id"),"lesson")); okhttp3.mockwebserver.RecordedRequest socket=server.takeRequest(5,TimeUnit.SECONDS); assertNotNull(socket); assertEquals("/wsapp/",socket.getPath()); assertEquals(3,server.getRequestCount());
        }
    }
    @Test public void currentClassroomReusesMemoryCredentialsAndPreservesReaderPosition() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); server.enqueue(response("{\"data\":{\"lessonToken\":\"socket-fixture\"}}").setHeader("set-auth","http-fixture")); api.checkin("lesson"); server.takeRequest();
            JSONObject deck=RainApi.object("id","existing","title","当前课件","slides",new JSONArray().put(RainApi.object("id","1")).put(RainApi.object("id","2"))); set("presentation",deck); set("page",1);
            clickClassroom(); waitFor(() -> !entryBusy()); assertEquals(2,get("dynamicView")); assertSame(deck,get("presentation")); assertEquals(1,get("page")); assertEquals("lesson",api.lesson()); assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void persistedLessonCredentialsSkipReportAndCheckinOnClassroomClick() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); RainApiTest.Lessons cache=new RainApiTest.Lessons(); api.configure(cache); cache.write(api.host(),api.user().optString("user_id"),"lesson",RainApi.object("bearer","saved-http","lessonToken","saved-ws")); server.enqueue(new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener(){}));
            Dialog previous=ShadowDialog.getLatestDialog(); clickClassroom(); assertSame(previous,ShadowDialog.getLatestDialog()); waitFor(() -> !entryBusy()); assertSame(previous,ShadowDialog.getLatestDialog()); ShadowLooper.idleMainLooper(600,TimeUnit.MILLISECONDS); assertSame(previous,ShadowDialog.getLatestDialog()); assertEquals("lesson",api.lesson()); assertEquals(2,get("dynamicView")); okhttp3.mockwebserver.RecordedRequest only=server.takeRequest(5,TimeUnit.SECONDS); assertNotNull(only); assertEquals("/wsapp/",only.getPath()); assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void unreadableLocalCredentialStillPromptsWithoutNetworkStatusLookup() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); api.configure(new RainApiTest.Lessons() { @Override public JSONObject read(String h,String u,String l) throws Exception { throw new java.io.IOException("unreadable fixture"); } }); clickClassroom(); waitFor(() -> !entryBusy()); Dialog prompt=ShadowDialog.getLatestDialog(); assertNotNull(find(prompt.getWindow().getDecorView(),"未找到课堂凭证")); assertNull(find(prompt.getWindow().getDecorView(),"重试查询")); assertNull(api.lesson()); assertEquals(1,server.getRequestCount()); closeCard(prompt.getWindow().getDecorView());
        }
    }
    @Test public void repeatedClickDuringLocalLookupDoesNotStartDuplicateLookupOrCheckin() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); java.util.concurrent.atomic.AtomicInteger reads=new java.util.concurrent.atomic.AtomicInteger(); api.configure(new RainApiTest.Lessons() { @Override public JSONObject read(String h,String u,String l) throws Exception { reads.incrementAndGet(); entered.countDown(); release.await(5,TimeUnit.SECONDS); return null; } });
            try { clickClassroom(); assertTrue(entered.await(5,TimeUnit.SECONDS)); clickClassroom(); assertEquals(1,reads.get()); release.countDown(); waitFor(() -> !entryBusy()); Dialog prompt=ShadowDialog.getLatestDialog(); assertNotNull(find(prompt.getWindow().getDecorView(),"未找到课堂凭证")); assertEquals(1,server.getRequestCount()); assertNull(api.lesson()); closeCard(prompt.getWindow().getDecorView()); } finally { release.countDown(); }
        }
    }
    @Test public void cancellingLocalLookupCannotRestoreCredentialOrEnterClassroom() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); RainApiTest.Lessons cache=new RainApiTest.Lessons() { @Override public JSONObject read(String h,String u,String l) throws Exception { entered.countDown(); release.await(5,TimeUnit.SECONDS); return super.read(h,u,l); } }; api.configure(cache); cache.write(api.host(),api.user().optString("user_id"),"lesson",RainApi.object("bearer","saved-http","lessonToken","saved-ws"));
            try { clickClassroom(); assertTrue(entered.await(5,TimeUnit.SECONDS)); ShadowLooper.idleMainLooper(600,TimeUnit.MILLISECONDS); Dialog waiting=ShadowDialog.getLatestDialog(); assertNotNull(waiting); closeCard(waiting.getWindow().getDecorView()); release.countDown(); waitFor(() -> { try { return ((Integer)get("busyCount"))==0; } catch(Exception e) { return false; } }); assertNull(api.lesson()); assertEquals(0,get("tab")); assertFalse(entryBusy()); assertEquals(1,server.getRequestCount()); } finally { release.countDown(); }
        }
    }
    @Test public void downloadFormatSelectionLaunchesMatchingSystemSaveIntent() throws Exception {
        loggedIn(); set("presentation",RainApi.object("title","课堂课件","slides",new JSONArray().put(RainApi.object("id","s1")))); call("downloadOptions");
        Dialog dialog=ShadowDialog.getLatestDialog(); View panel=dialog.getWindow().getDecorView(); assertNull(find(panel,"JSON 课件数据"));
        View label=find(panel,"ZIP 页面图片"); assertNotNull(label); View row=(View)label.getParent().getParent(); assertTrue(row.performClick()); assertTrue(row.isSelected());
        click(panel,"选择保存位置");
        org.robolectric.shadows.ShadowActivity.IntentForResult next=Shadows.shadowOf(activity).getNextStartedActivityForResult();
        assertNotNull(next); assertEquals(Intent.ACTION_CREATE_DOCUMENT,next.intent.getAction()); assertEquals("application/zip",next.intent.getType()); assertEquals("课堂课件.zip",next.intent.getStringExtra(Intent.EXTRA_TITLE));
    }
    @Test public void signedOutLeaseCannotReenablePersistedLogin() throws Exception {
        SessionVault vault=(SessionVault)get("vault"); RainApi.CookieStorage old=vault.session(); vault.clear();
        try { old.authenticated(); fail(); } catch(Exception expected) {}
        assertEquals("[]",old.read()); assertTrue(activity.getSharedPreferences("native_session",0).getBoolean("signed_out",false));
    }
    private void installHistoryApi(MockWebServer server,boolean detailFails) throws Exception {
        server.start(); server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
            @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                String path=request.getRequestUrl().encodedPath();
                if(path.equals("/api/v3/lesson/checkin")) return response("{\"code\":0,\"data\":{\"lessonToken\":\"live-token\"}}").setHeader("set-auth","live-bearer");
                if(path.equals("/api/v3/activities/received/")) return response("{\"code\":0,\"data\":[{\"coursewareType\":14,\"coursewareId\":\"history-1\",\"courseName\":\"历史数学课\",\"coursewareTitle\":\"第一讲\",\"teacher\":\"示例老师\",\"createTime\":1767225600000}]}");
                if(path.endsWith("/lesson-info")) return response("{\"code\":0,\"data\":{\"lessonId\":\"history-1\",\"courseName\":\"历史数学课\",\"classroomName\":\"示例一班\",\"teacherName\":\"示例老师\",\"lessonName\":\"第一讲\",\"start\":1767225600000,\"end\":1767229200000}}");
                if(path.endsWith("/detail")) return detailFails?new MockResponse().setResponseCode(503):response("{\"code\":0,\"data\":{\"detail\":{\"valid\":1,\"checkInTime\":1767225606000,\"checkInName\":\"扫二维码\",\"checkInChannel\":\"APP\"},\"presentation\":[{\"presentationId\":\"p1\",\"title\":\"函数与极限\",\"totalCount\":34}]}}");
                if(path.equals("/api/v3/classroom-report/student/ppt")) {
                    if(!"p1".equals(request.getRequestUrl().queryParameter("presentationId"))||request.getHeader("Authorization")!=null||!"history-1".equals(request.getRequestUrl().queryParameter("lesson_id"))) return new MockResponse().setResponseCode(403);
                    JSONArray slides=new JSONArray(); for(int i=0;i<34;i++) { try { slides.put(RainApi.object("id","original-"+i,"index",i,"visible",true,"cover","https://fixture.yuketang.cn/a.png?token=fixture")); } catch(Exception e) { throw new AssertionError(e); } }
                    return response("{\"data\":{\"title\":\"函数与极限\",\"slideList\":"+slides+"}}");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        okhttp3.OkHttpClient.Builder transport=new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
            if(chain.request().url().host().equals("fixture.yuketang.cn")) {
                byte[] png=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a4n8AAAAASUVORK5CYII=");
                return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").header("Content-Type","image/png").body(okhttp3.ResponseBody.create(png,okhttp3.MediaType.get("image/png"))).build();
            }
            return chain.proceed(chain.request());
        });
        ((RainApi)get("api")).cancel(); set("api",new RainApi(server.url("/"),new RainApiTest.Store(),transport)); loggedIn();
    }
    private void setLiveCatalog() throws Exception { set("catalog",CourseCatalog.from(RainApi.object("courses",new JSONArray().put(RainApi.object("course_id","math","course_name","数学课")),"live",new JSONArray().put(RainApi.object("courseId","math","lessonId","lesson"))))); }
    private void openLiveActivity() throws Exception { set("tab",1); Method method=MainActivity.class.getDeclaredMethod("openActivity",ActivityFeed.Entry.class); method.setAccessible(true); method.invoke(activity,new ActivityFeed.Entry(RainApi.object("coursewareType",14,"coursewareId","lesson","courseName","数学课","coursewareTitle","当前课"))); }
    private java.util.concurrent.atomic.AtomicInteger installLiveDeckDispatcher(MockWebServer server) {
        java.util.concurrent.atomic.AtomicInteger forbidden=new java.util.concurrent.atomic.AtomicInteger(); server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
            String path=request.getRequestUrl().encodedPath(); if(path.equals("/wsapp/")) return new MockResponse().withWebSocketUpgrade(new okhttp3.WebSocketListener() { @Override public void onMessage(okhttp3.WebSocket ws,String text) { try { if(new JSONObject(text).optString("op").equals("hello")) ws.send("{\"op\":\"hello\",\"presentation\":\"real-ppt\",\"timeline\":[]}"); } catch(Exception e) { throw new AssertionError(e); } } @Override public void onClosing(okhttp3.WebSocket ws,int code,String reason) { ws.close(code,reason); } });
            if(path.equals("/api/v3/lesson/presentation/fetch")) { assertEquals("Bearer saved-http",request.getHeader("Authorization")); assertEquals("real-ppt",request.getRequestUrl().queryParameter("presentation_id")); return response("{\"data\":{\"id\":\"real-ppt\",\"title\":\"实时教学课件\",\"slides\":[]}}"); }
            if(path.startsWith("/api/v3/classroom-report/")||path.equals("/api/v3/lesson/checkin")) forbidden.incrementAndGet(); return new MockResponse().setResponseCode(404);
        } }); return forbidden;
    }
    private void seedLiveCredential(RainApi api) throws Exception { RainApiTest.Lessons cache=new RainApiTest.Lessons(); api.configure(cache); cache.write(api.host(),api.user().optString("user_id"),"lesson",RainApi.object("bearer","saved-http","lessonToken","saved-ws")); }
    private boolean liveDeckReady() { try { JSONObject p=(JSONObject)get("presentation"); return p!=null&&p.optString("id").equals("real-ppt"); } catch(Exception e) { return false; } }
    @Test public void liveActivityRestoresCredentialAndFetchesRealtimePptWithoutHistoryOrCheckin() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); seedLiveCredential(api); setLiveCatalog(); java.util.concurrent.atomic.AtomicInteger forbidden=installLiveDeckDispatcher(server); openLiveActivity(); waitFor(this::liveDeckReady); assertEquals(2,get("dynamicView")); assertNotNull(find(root(),"实时教学课件")); assertNull(find(root(),"查看随机点名记录")); assertEquals(0,forbidden.get()); assertEquals("lesson",api.lesson());  }
    }
    @Test public void homeClassroomLoadsLiveDeckWithoutAnySelectedActivity() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); seedLiveCredential(api); installLiveDeckDispatcher(server); assertNull(get("selectedActivity")); clickClassroom(); waitFor(this::liveDeckReady);  assertNull(get("selectedActivity")); }
    }
    @Test public void liveActivityWithoutCredentialFallsBackToReportWithoutAutomaticCheckin() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); setLiveCatalog(); server.enqueue(response("{\"data\":{\"lessonId\":\"lesson\",\"courseName\":\"数学课\"}}")); server.enqueue(response("{\"data\":{\"presentation\":[]}}")); openLiveActivity(); waitFor(()->!historyBusy()&&!workBusy()); assertEquals(1,get("dynamicView")); assertNull(api.lesson()); assertNotNull(find(root(),"进入实时课堂")); assertEquals(3,server.getRequestCount()); for(int n=0;n<2;n++) assertTrue(server.takeRequest(3,TimeUnit.SECONDS).getPath().startsWith("/api/v3/classroom-report/")); }
    }
    @Test public void leavingDynamicDuringCredentialReadCannotEnterClassroomLater() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); setLiveCatalog(); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); RainApiTest.Lessons cache=new RainApiTest.Lessons() { @Override public JSONObject read(String host,String uid,String lesson) throws Exception { entered.countDown(); release.await(3,TimeUnit.SECONDS); return RainApi.object("bearer","saved-http","lessonToken","saved-ws"); } }; api.configure(cache); server.enqueue(response("{\"data\":[]}")); openLiveActivity(); assertTrue(entered.await(3,TimeUnit.SECONDS)); call("showFeed"); release.countDown(); waitFor(()->!workBusy()&&!historyBusy()); assertNull(api.lesson()); assertEquals(0,get("dynamicView")); assertEquals(2,server.getRequestCount()); }
    }
    @Test public void liveReportCanIdentifyOngoingClassWhenHomeCatalogHasNotLoaded() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); seedLiveCredential(api); java.util.concurrent.atomic.AtomicInteger forbidden=installLiveDeckDispatcher(server); okhttp3.mockwebserver.Dispatcher live=server.getDispatcher(); server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) throws InterruptedException { if(request.getRequestUrl().encodedPath().endsWith("/lesson-info")) return response("{\"data\":{\"lessonId\":\"lesson\",\"courseName\":\"数学课\",\"start\":1700000000000,\"end\":0}}"); if(request.getRequestUrl().encodedPath().endsWith("/detail")) return response("{\"data\":{\"presentation\":[]}}"); return live.dispatch(request); } }); assertNull(get("catalog")); openLiveActivity(); waitFor(this::liveDeckReady); assertEquals(2,get("dynamicView")); assertEquals(0,forbidden.get()); assertEquals("lesson",api.lesson()); }
    }
    @Test public void switchingTabsDuringDynamicCredentialReadCannotNavigateBackIntoReader() throws Exception {
        try(MockWebServer server=new MockWebServer()) { RainApi api=installEntryApi(server); setLiveCatalog(); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); api.configure(new RainApiTest.Lessons() { @Override public JSONObject read(String host,String uid,String lesson) throws Exception { entered.countDown(); release.await(3,TimeUnit.SECONDS); return RainApi.object("bearer","saved-http","lessonToken","saved-ws"); } }); openLiveActivity(); assertTrue(entered.await(3,TimeUnit.SECONDS)); View tab=find(root(),"课堂"); assertNotNull(tab); assertTrue(((View)tab.getParent()).performClick()); release.countDown(); waitFor(()->!workBusy()); assertEquals(0,get("tab")); assertNull(api.lesson()); assertEquals(1,server.getRequestCount()); }
    }
    private boolean historyBusy() { try { return (Boolean)get("loadingActivities")||(Boolean)get("loadingReport")||(Boolean)get("loadingHistory")||!((java.util.Set<?>)get("attendanceLoading")).isEmpty(); } catch(Exception e) { throw new RuntimeException(e); } }
    @Test public void historyFeedOpensFullOriginalWithoutMutatingLiveLesson() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            installHistoryApi(server,false); RainApi api=(RainApi)get("api"); api.checkin("live-lesson");
            JSONObject live=RainApi.object("id","live-presentation","title","实时课件","slides",new JSONArray()); set("presentation",live); set("currentCourse","当前课堂");
            set("tab",1); call("showFeed"); waitFor(() -> !historyBusy()); assertNotNull(find(root(),"已签到")); assertNotNull(find(root(),"历史数学课")); assertTrue(all(root()).stream().anyMatch(v -> v instanceof TextView&&((TextView)v).getText().toString().startsWith("签到于 "))); assertNotNull(find(root(),"动态")); assertNull(find(root(),"课件"));
            View detail=find(root(),"查看详情"); assertNotNull(detail); assertTrue(((View)detail.getParent().getParent()).performClick()); waitFor(() -> !historyBusy());
            assertNull(find(root(),"示例一班")); assertNotNull(find(root(),"扫二维码 · APP")); assertNotNull(find(root(),"函数与极限")); assertFalse(all(root()).stream().anyMatch(v -> v instanceof TextView&&((TextView)v).getText().toString().startsWith("这里查看的是课堂报告与回顾")));
            click(root(),"查看原版课件"); waitFor(() -> !historyBusy()); assertEquals(3,get("dynamicView")); assertNotNull(find(root(),"34 页 · 原版课件"));
            click(root(),"刷新课件"); waitFor(() -> !historyBusy()); assertNotNull(find(root(),"34 页 · 原版课件"));
            Method page=MainActivity.class.getDeclaredMethod("showPage",int.class,boolean.class); page.setAccessible(true); page.invoke(activity,1,true);
            assertTrue(activity.getSharedPreferences("native_preferences",0).getBoolean("follow",true)); assertSame(live,get("presentation")); assertEquals("live-lesson",api.lesson());
            activity.onBackPressed(); assertEquals(1,get("dynamicView")); activity.onBackPressed(); assertEquals(0,get("dynamicView"));
            call("resetAccount"); assertNull(get("activityFeed")); assertNull(get("selectedReport")); assertNull(get("historyPresentation")); assertTrue(((java.util.Map<?,?>)get("reports")).isEmpty());
        }
    }
    private void prepareSingleImageReader(MockWebServer server) throws Exception {
        installHistoryApi(server,false); set("historyPresentation",RainApi.object("title","单页测试","slides",new JSONArray().put(RainApi.object("id","one","cover","https://fixture.yuketang.cn/a.png")).put(RainApi.object("id","two","cover","https://fixture.yuketang.cn/b.png")))); set("tab",1); set("dynamicView",3); call("render");
        waitFor(() -> { try { return ((android.widget.ImageView)get("slideImage")).getDrawable()!=null; } catch(Exception e) { return false; } });
    }
    @Test public void fullScreenLongPressSavesCapturedPageBytesEvenAfterReaderPageChanges() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            prepareSingleImageReader(server); assertTrue(((View)get("slideImage")).performClick()); assertTrue(get("fullImage") instanceof ZoomImageView); ZoomImageView image=(ZoomImageView)get("fullImage"); image.layout(0,0,400,400); image.zoomAt(3,200,200); assertTrue(image.performLongClick()); Dialog options=ShadowDialog.getLatestDialog(); click(options.getWindow().getDecorView(),"保存此页图片"); set("historyPage",1);
            waitFor(() -> { try { return get("pendingImage")!=null; } catch(Exception e) { return false; } }); org.robolectric.shadows.ShadowActivity.IntentForResult next=Shadows.shadowOf(activity).getNextStartedActivityForResult(); assertNotNull(next); assertEquals(44,next.requestCode); assertEquals("image/png",next.intent.getType()); assertEquals("单页测试-第1页.png",next.intent.getStringExtra(Intent.EXTRA_TITLE));
            java.io.File out=java.io.File.createTempFile("single-slide-",".png",activity.getCacheDir()); activity.onActivityResult(44,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(out))); waitFor(() -> { try { return !(Boolean)get("downloading"); } catch(Exception e) { return false; } }); assertArrayEquals(java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a4n8AAAAASUVORK5CYII="),java.nio.file.Files.readAllBytes(out.toPath())); assertNull(get("pendingImage")); assertFalse(((RainApi)get("api")).isClosed()); assertEquals(3,image.zoomLevel(),.001f); out.delete();
        }
    }
    @Test public void singleImagePickerResultAfterLogoutCannotSaveAnotherAccountsImage() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            prepareSingleImageReader(server); assertTrue(((View)get("slideImage")).performLongClick()); Dialog options=ShadowDialog.getLatestDialog(); click(options.getWindow().getDecorView(),"保存此页图片"); waitFor(() -> { try { return get("pendingImage")!=null; } catch(Exception e) { return false; } }); call("resetAccount"); java.io.File out=java.io.File.createTempFile("stale-image-",".png",activity.getCacheDir()); activity.onActivityResult(44,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(out))); assertFalse(out.exists()); assertNull(get("pendingImage")); assertNull(get("pendingFormat")); assertNull(get("user"));
        }
    }
    @Test public void userAgentSettingsValidateSaveAndRestoreDeviceDefaultForRequests() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); set("tab",2); call("render"); click(root(),"修改"); Dialog settings=ShadowDialog.getLatestDialog(); EditText input=(EditText)all(settings.getWindow().getDecorView()).stream().filter(v -> v instanceof EditText).findFirst().get(); assertEquals(UserAgent.deviceDefault(),input.getText().toString());
            input.setText("bad\r\nInjected: value".replace("\\r","\r").replace("\\n","\n")); click(settings.getWindow().getDecorView(),"保存 User-Agent"); assertTrue(settings.isShowing()); assertFalse(activity.getSharedPreferences("native_preferences",0).contains(UserAgent.KEY));
            input.setText("StudyClient/1.0"); click(settings.getWindow().getDecorView(),"保存 User-Agent"); assertFalse(settings.isShowing()); assertEquals("StudyClient/1.0",activity.getSharedPreferences("native_preferences",0).getString(UserAgent.KEY,"")); assertSame(api,get("api")); assertFalse(api.isClosed()); server.enqueue(response("{\"data\":{\"user_profile\":{\"user_id\":\"fixture\"}}}")); api.profile(); assertEquals("StudyClient/1.0",server.takeRequest().getHeader("User-Agent"));
            click(root(),"修改"); settings=ShadowDialog.getLatestDialog(); click(settings.getWindow().getDecorView(),"恢复设备默认"); assertFalse(activity.getSharedPreferences("native_preferences",0).contains(UserAgent.KEY)); server.enqueue(response("{\"data\":{\"user_profile\":{\"user_id\":\"fixture\"}}}")); api.profile(); assertEquals(UserAgent.deviceDefault(),server.takeRequest().getHeader("User-Agent"));
        }
    }
    private void swipeImage(ZoomImageView image,boolean left,long time) {
        float start=left?320:80,end=left?80:320; android.view.MotionEvent down=android.view.MotionEvent.obtain(time,time,android.view.MotionEvent.ACTION_DOWN,start,200,0),move=android.view.MotionEvent.obtain(time,time+40,android.view.MotionEvent.ACTION_MOVE,end,200,0),up=android.view.MotionEvent.obtain(time,time+80,android.view.MotionEvent.ACTION_UP,end,200,0); image.onTouchEvent(down); image.onTouchEvent(move); image.onTouchEvent(up); down.recycle(); move.recycle(); up.recycle();
    }
    @Test public void fullScreenSwipesUseReaderPagesAndStopAtDeckBoundaries() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            prepareSingleImageReader(server); JSONObject live=RainApi.object("title","实时课件","slides",new JSONArray()); set("presentation",live); assertTrue(((View)get("slideImage")).performClick()); ZoomImageView image=(ZoomImageView)get("fullImage"); image.layout(0,0,400,400); long time=android.os.SystemClock.uptimeMillis(); swipeImage(image,true,time); waitFor(() -> image.getDrawable()!=null); assertEquals(1,get("historyPage")); swipeImage(image,true,time+1000); assertEquals(1,get("historyPage")); swipeImage(image,false,time+2000); waitFor(() -> image.getDrawable()!=null); assertEquals(0,get("historyPage")); swipeImage(image,false,time+3000); assertEquals(0,get("historyPage")); assertSame(live,get("presentation")); assertTrue(activity.getSharedPreferences("native_preferences",0).getBoolean("follow",true));
        }
    }
    @Test public void historyAttendanceNetworkErrorIsNotReportedAsAbsent() throws Exception {
        try(MockWebServer server=new MockWebServer()) { installHistoryApi(server,true); set("tab",1); call("showFeed"); waitFor(() -> !historyBusy()); assertNotNull(find(root(),"签到信息暂不可用")); assertNull(find(root(),"未签到")); assertNull(find(root(),"已签到")); }
    }
    @Test public void dynamicsTabBeforeLoginOffersLoginInsteadOfRequiringCurrentLesson() throws Exception {
        View label=find(root(),"动态"); assertNotNull(label); assertTrue(((View)label.getParent()).performClick()); assertNotNull(find(root(),"课堂动态")); assertNotNull(find(root(),"登录后查看课堂动态")); assertNotNull(find(root(),"扫码登录"));
    }

    private boolean pagingBusy() { try { return (Boolean)get("loadingActivities")||(Boolean)get("loadingMore"); } catch(Exception e) { throw new RuntimeException(e); } }
    @Test public void loadMoreRetriesKeepsOldRowsAndRefreshStartsOver() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); java.util.List<String> cursors=java.util.Collections.synchronizedList(new java.util.ArrayList<>()); java.util.concurrent.atomic.AtomicInteger firstCalls=new java.util.concurrent.atomic.AtomicInteger(),moreCalls=new java.util.concurrent.atomic.AtomicInteger();
            server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
                @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                    try {
                        JSONObject body=new JSONObject(request.getBody().readUtf8()); String cursor=body.has("searchAfter")?body.getString("searchAfter"):null; cursors.add(cursor);
                        if(cursor==null) return response("{\"code\":0,\"data\":[{\"id\":\"first-"+firstCalls.incrementAndGet()+"\",\"coursewareType\":5,\"coursewareTitle\":\"第一页作业\",\"createTime\":10,\"sort\":\"cursor-first\"}]}");
                        if(cursor.equals("cursor-first")) { if(moreCalls.incrementAndGet()==1) return new MockResponse().setResponseCode(503); return response("{\"code\":0,\"data\":[{\"id\":\"second\",\"coursewareType\":5,\"coursewareTitle\":\"第二页作业\",\"createTime\":9,\"sort\":\"cursor-second\"}]}"); }
                        return response("{\"code\":0,\"data\":[]}");
                    } catch(Exception e) { return new MockResponse().setResponseCode(500); }
                }
            });
            ((RainApi)get("api")).cancel(); set("api",new RainApi(server.url("/"),new RainApiTest.Store())); loggedIn(); set("tab",1); call("showFeed"); waitFor(() -> !pagingBusy());
            click(root(),"加载更多动态"); waitFor(() -> !pagingBusy()); assertNotNull(find(root(),"第一页作业")); assertNotNull(find(root(),"重试加载更多"));
            click(root(),"重试加载更多"); waitFor(() -> !pagingBusy()); assertNotNull(find(root(),"第一页作业")); assertNotNull(find(root(),"第二页作业"));
            click(root(),"加载更多动态"); waitFor(() -> !pagingBusy()); assertNotNull(find(root(),"已加载全部动态"));
            click(root(),"刷新动态"); waitFor(() -> !pagingBusy()); assertNull(find(root(),"第二页作业")); assertEquals(java.util.Arrays.asList(null,"cursor-first","cursor-first","cursor-second",null),cursors);
        }
    }
    @Test public void zipExportHasRealStageProgressAndNoJsonFileAndJsonFormatIsRejected() throws Exception {
        java.io.File file=java.io.File.createTempFile("rain-export-",".zip");
        try {
            JSONObject p=RainApi.object("title","课件","slides",new JSONArray().put(RainApi.object("id","p1")));
            java.util.List<ExportProgress> progress=new java.util.ArrayList<>();
            SlideExporter.save(activity,s -> new byte[]{(byte)137,80,78,71},p,"zip",android.net.Uri.fromFile(file),progress::add,() -> false);
            assertEquals(ExportProgress.Stage.FETCH,progress.get(0).stage); assertEquals(0,progress.get(0).pages);
            assertTrue(progress.stream().anyMatch(s -> s.stage==ExportProgress.Stage.BUILD)); assertEquals(ExportProgress.Stage.SAVE,progress.get(progress.size()-1).stage); assertEquals(100,progress.get(progress.size()-1).percent());
            try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(file)) { assertEquals(1,zip.size()); assertNotNull(zip.getEntry("001.png")); assertNull(zip.getEntry("presentation.json")); }
            try { SlideExporter.save(activity,s -> { throw new AssertionError("no fetch for removed format"); },p,"json",android.net.Uri.fromFile(file),progress::add,() -> false); fail(); } catch(IllegalArgumentException expected) {}
        } finally { file.delete(); }
    }
    @Test public void successfulDownloadShowsCompletionInProgressPanel() throws Exception {
        java.io.File file=java.io.File.createTempFile("rain-ui-export-",".zip");
        try(MockWebServer server=new MockWebServer()) {
            installHistoryApi(server,false); set("presentation",RainApi.object("title","下载测试","slides",new JSONArray().put(RainApi.object("id","s","cover","https://fixture.yuketang.cn/a.png")))); call("downloadOptions");
            Dialog choose=ShadowDialog.getLatestDialog(); View panel=choose.getWindow().getDecorView(); assertTrue(((View)find(panel,"ZIP 页面图片").getParent().getParent()).performClick()); click(panel,"选择保存位置");
            activity.onActivityResult(42,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(file))); Dialog downloading=ShadowDialog.getLatestDialog(); assertNotNull(downloading.getWindow().getDecorView().findViewWithTag("card-close"));
            waitFor(() -> { try { return !(Boolean)get("downloading"); } catch(Exception e) { throw new RuntimeException(e); } });
            assertNotNull(find(downloading.getWindow().getDecorView(),"保存完成")); assertNotNull(downloading.getWindow().getDecorView().findViewWithTag("card-close")); assertTrue(file.length()>0); closeCard(downloading.getWindow().getDecorView());
        } finally { file.delete(); }
    }

    private void layoutScreen() {
        View decor=root(); android.util.DisplayMetrics m=activity.getResources().getDisplayMetrics();
        decor.measure(View.MeasureSpec.makeMeasureSpec(m.widthPixels,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(m.heightPixels,View.MeasureSpec.EXACTLY)); decor.layout(0,0,m.widthPixels,m.heightPixels);
    }
    @Test public void loadingMoreAndAttendanceUpdateKeepTheSameScrolledContainerAndExistingRows() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); java.util.concurrent.CountDownLatch requested=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
            server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() {
                @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) {
                    requested.countDown();
                    try { if(!release.await(5,TimeUnit.SECONDS)) return new MockResponse().setResponseCode(504);
                        return response(RainApi.object("code",0,"data",new JSONArray().put(RainApi.object("id","extra","coursewareType",5,"coursewareTitle","追加作业","createTime",1,"sort","next-cursor"))).toString());
                    } catch(Exception e) { return new MockResponse().setResponseCode(500); }
                }
            });
            ((RainApi)get("api")).cancel(); set("api",new RainApi(server.url("/"),new RainApiTest.Store())); loggedIn();
            JSONArray rows=new JSONArray();
            for(int i=0;i<15;i++) rows.put(RainApi.object("id","a"+i,"coursewareType",5,"coursewareTitle","原有动态 "+i,"courseName","课程 "+i,"createTime",100-i,"sort","cursor-"+i));
            set("activityFeed",ActivityFeed.from(rows)); set("feedVisibleLimit",Integer.MAX_VALUE); set("tab",1); call("showFeed"); ShadowLooper.idleMainLooper(); layoutScreen();
            android.widget.ScrollView scroll=(android.widget.ScrollView)get("contentScroll"); scroll.scrollTo(0,1200); int position=scroll.getScrollY(); assertTrue(position>0); View original=find(root(),"原有动态 0");
            call("loadMoreActivities"); assertTrue(requested.await(5,TimeUnit.SECONDS));
            assertSame(scroll,get("contentScroll")); assertEquals(position,scroll.getScrollY()); release.countDown(); waitFor(() -> !pagingBusy()); layoutScreen();
            assertSame(scroll,get("contentScroll")); assertSame(original,find(root(),"原有动态 0")); assertEquals(position,scroll.getScrollY()); assertNotNull(find(root(),"追加作业"));
            call("render"); ShadowLooper.idleMainLooper(); layoutScreen(); assertEquals(position,scroll.getScrollY());
        }
    }
    @Test public void attendanceUpdatesDoNotReplaceExistingCardsOrResetScroll() throws Exception {
        loggedIn(); JSONArray rows=new JSONArray();
        for(int i=0;i<15;i++) rows.put(RainApi.object("id","a"+i,"coursewareType",14,"coursewareId","lesson-"+i,"coursewareTitle","课堂 "+i,"courseName","课程 "+i,"createTime",100-i,"sort","c"+i));
        set("activityFeed",ActivityFeed.from(rows)); set("feedVisibleLimit",Integer.MAX_VALUE); set("tab",1); call("showFeed"); ShadowLooper.idleMainLooper(); layoutScreen();
        android.widget.ScrollView scroll=(android.widget.ScrollView)get("contentScroll"); scroll.scrollTo(0,1200); int before=scroll.getScrollY(); assertTrue(before>0); View original=find(root(),"课堂 0");
        @SuppressWarnings("unchecked") java.util.Map<String,LessonReport> reports=(java.util.Map<String,LessonReport>)get("reports");
        reports.put("lesson-0",new LessonReport("lesson-0",null,RainApi.object("detail",RainApi.object("valid",1,"checkInTime",1767225600000L)),null,null));
        call("render"); ShadowLooper.idleMainLooper(); layoutScreen();
        assertSame(scroll,get("contentScroll")); assertSame(original,find(root(),"课堂 0")); assertEquals(before,scroll.getScrollY()); assertNotNull(find(root(),"已签到"));
    }
    @Test public void endedUnconfirmedAttendanceIsCompactAndHidesPlaceholderFields() throws Exception {
        loggedIn(); ActivityFeed feed=ActivityFeed.from(new JSONArray().put(RainApi.object("coursewareType",14,"coursewareId","old-lesson","coursewareTitle","历史课堂","courseName","历史课程")));
        JSONObject info=RainApi.object("lessonId","old-lesson","courseName","历史课程","lessonName","历史课堂","teacherName","教师","classroomName","班级","start",1767225600000L,"end",1767229200000L);
        set("selectedActivity",feed.entries.get(0)); set("selectedReport",new LessonReport("old-lesson",info,RainApi.object("detail",RainApi.object("valid",0,"checkInTime",0,"checkInName","无效方式占位","checkInChannel","无效渠道占位")),null,null)); set("tab",1); set("dynamicView",1); call("render");
        assertNotNull(find(root(),"未签到")); assertNull(find(root(),"签到待确认")); assertNull(find(root(),"签到时间")); assertNull(find(root(),"签到方式")); assertNull(find(root(),"签到渠道")); assertNull(find(root(),"无效方式占位")); assertNull(find(root(),"无效渠道占位"));
    }
    private JSONArray cachedActivityRows() throws Exception {
        JSONArray rows=FeedTimelineTest.rows(1,20); rows.put(0,RainApi.object("id","class-event","coursewareType",14,"coursewareId","history-1","courseName","历史数学课","coursewareTitle","第一讲","teacher","示例老师","createTime",9999,"sort","cursor-1")); return rows;
    }
    private JSONArray part(JSONArray rows,int start,int end) throws Exception { JSONArray result=new JSONArray(); for(int i=start;i<end;i++) result.put(rows.getJSONObject(i)); return result; }
    private FeedStore seedActivityStore(JSONArray rows) throws Exception {
        android.content.SharedPreferences prefs=activity.getSharedPreferences("activity-cache-ui",0); prefs.edit().clear().commit(); FeedStore store=new FeedStore(new java.io.File(activity.getCacheDir(),"activities-"+System.nanoTime()),prefs,FeedStoreTest.crypto());
        java.util.Map<String,LessonReport> records=new java.util.HashMap<>(); JSONObject info=RainApi.object("lessonId","history-1","courseName","历史数学课","lessonName","第一讲","teacherName","示例老师","start",1767225600000L,"end",1767229200000L),detail=RainApi.object("detail",RainApi.object("valid",1,"checkInTime",1767225606000L,"checkInName","扫二维码","checkInChannel","APP"),"presentation",new JSONArray().put(RainApi.object("presentationId","p1","title","函数与极限","totalCount",1))); records.put("history-1",new LessonReport("history-1",info,detail,null,null));
        java.util.Map<String,JSONObject> decks=new java.util.HashMap<>(); decks.put("history-1|p1",RainApi.object("id","p1","title","函数与极限","slides",new JSONArray().put(RainApi.object("id","page","cover","https://fixture.yuketang.cn/a.png")))); java.util.Map<String,Long> deckTimes=new java.util.HashMap<>(); deckTimes.put("history-1|p1",System.currentTimeMillis());
        ActivitySnapshot snapshot=new ActivitySnapshot(new FeedTimeline(ActivityFeed.from(part(rows,0,10)).append(part(rows,10,20))),records,new java.util.HashMap<>(),decks,deckTimes);
        RainApi api=(RainApi)get("api"); store.session(api.host(),((JSONObject)get("user")).optString("user_id")).write(snapshot.encode()); set("activityStore",store); return store;
    }
    @Test public void diskCacheShowsImmediatelyAndUnchangedHeadReusesTailReportsAndDecks() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            installHistoryApi(server,false); JSONArray rows=cachedActivityRows(); FeedStore store=seedActivityStore(rows); CountDownLatch release=new CountDownLatch(1); java.util.concurrent.atomic.AtomicInteger heads=new java.util.concurrent.atomic.AtomicInteger(),reports=new java.util.concurrent.atomic.AtomicInteger(); List<String> cursors=new java.util.concurrent.CopyOnWriteArrayList<>();
            server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest r) {
                try { if(r.getRequestUrl().encodedPath().equals("/api/v3/activities/received/")) { JSONObject body=new JSONObject(r.getBody().readUtf8()); String cursor=body.optString("searchAfter",""); cursors.add(cursor); if(cursor.isEmpty()) { heads.incrementAndGet(); release.await(5,TimeUnit.SECONDS); return response("{\"data\":"+part(rows,0,10)+"}"); } return response("{\"data\":"+FeedTimelineTest.rows(21,25)+"}"); } reports.incrementAndGet(); return new MockResponse().setResponseCode(500); } catch(Exception e) { throw new AssertionError(e); }
            } });
            try {
                set("tab",1); call("showFeed"); waitFor(() -> heads.get()==1); assertEquals(20,((ActivityFeed)get("activityFeed")).entries.size()); assertEquals(10,((ViewGroup)get("feedRows")).getChildCount()); assertNotNull(find(root(),"历史数学课")); release.countDown(); waitFor(() -> !historyBusy()); assertEquals(20,((ActivityFeed)get("activityFeed")).entries.size()); assertEquals("cursor-20",((ActivityFeed)get("activityFeed")).nextCursor); assertEquals(0,reports.get());
                View link=find(root(),"查看详情"); assertNotNull(link); assertTrue(((View)link.getParent().getParent()).performClick()); waitFor(() -> !historyBusy()); click(root(),"查看原版课件"); waitFor(() -> !historyBusy()); assertEquals(3,get("dynamicView")); assertEquals(0,reports.get()); activity.onBackPressed(); activity.onBackPressed(); call("loadMoreActivities"); assertEquals(20,((ViewGroup)get("feedRows")).getChildCount()); assertEquals(java.util.Arrays.asList(""),cursors); call("loadMoreActivities"); waitFor(() -> !pagingBusy()&&!historyBusy()); assertEquals(25,((ActivityFeed)get("activityFeed")).entries.size()); assertEquals(java.util.Arrays.asList("","cursor-20"),cursors); assertEquals(0,reports.get());
                call("flushActivityCache"); ((java.util.concurrent.ExecutorService)get("feedIo")).submit(() -> {}).get(5,TimeUnit.SECONDS); JSONObject disk=store.session(((RainApi)get("api")).host(),((JSONObject)get("user")).optString("user_id")).read(); assertNotNull(disk); assertEquals(25,ActivitySnapshot.decode(disk).timeline.visible.entries.size());
                call("loadActivities"); waitFor(() -> !pagingBusy()); assertEquals(10,((ViewGroup)get("feedRows")).getChildCount()); assertEquals(25,((ActivityFeed)get("activityFeed")).entries.size()); int beforeExpand=cursors.size(); click(root(),"加载更多动态"); assertEquals(25,((ViewGroup)get("feedRows")).getChildCount()); assertEquals(beforeExpand,cursors.size());
            } finally { release.countDown(); }
        }
    }
    @Test public void failedFirstPageRequestKeepsPreviouslySavedActivityData() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            installHistoryApi(server,false); seedActivityStore(cachedActivityRows()); java.util.concurrent.atomic.AtomicInteger heads=new java.util.concurrent.atomic.AtomicInteger(),reports=new java.util.concurrent.atomic.AtomicInteger(); server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest r) { if(r.getRequestUrl().encodedPath().equals("/api/v3/activities/received/")) heads.incrementAndGet(); else reports.incrementAndGet(); return new MockResponse().setResponseCode(500); } }); set("tab",1); call("showFeed"); waitFor(() -> !historyBusy()); assertEquals(1,heads.get()); assertEquals(0,reports.get()); assertEquals(20,((ActivityFeed)get("activityFeed")).entries.size()); assertNotNull(find(root(),"历史数学课")); assertNotNull(get("activityError"));
        }
    }
    @Test public void openingDetailDuringMetadataLoadSharesTheInFlightRequest() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            installHistoryApi(server,false); CountDownLatch release=new CountDownLatch(1); java.util.concurrent.atomic.AtomicInteger details=new java.util.concurrent.atomic.AtomicInteger(),infos=new java.util.concurrent.atomic.AtomicInteger(); server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest r) {
                String path=r.getRequestUrl().encodedPath(); try {
                    if(path.equals("/api/v3/activities/received/")) return response("{\"data\":[{\"id\":\"new-class\",\"coursewareType\":14,\"coursewareId\":\"shared\",\"courseName\":\"共享课堂\",\"coursewareTitle\":\"第一讲\",\"sort\":\"shared-tail\"}]}");
                    if(path.endsWith("/detail")) { details.incrementAndGet(); release.await(5,TimeUnit.SECONDS); return response("{\"data\":{\"detail\":{\"valid\":1,\"checkInTime\":1767225600000}}}"); }
                    if(path.endsWith("/lesson-info")) { infos.incrementAndGet(); return response("{\"data\":{\"lessonId\":\"shared\",\"courseName\":\"共享课堂\",\"end\":1767229200000}}"); }
                    return new MockResponse().setResponseCode(404);
                } catch(Exception e) { throw new AssertionError(e); }
            } });
            try { set("tab",1); call("showFeed"); waitFor(() -> details.get()==1); View link=find(root(),"查看详情"); assertNotNull(link); ((View)link.getParent().getParent()).performClick(); assertEquals(true,get("loadingReport")); release.countDown(); waitFor(() -> !historyBusy()); assertEquals(1,details.get()); assertEquals(1,infos.get()); assertNotNull(find(root(),"已签到")); } finally { release.countDown(); }
        }
    }

    @Test public void accountPageHasProjectAndAuthorButNoUpdateCheckEntry() throws Exception {
        set("tab",2); call("render"); assertNull(find(root(),"检查更新")); assertNotNull(find(root(),"作者 FirmPluvia")); click(root(),"项目主页"); assertEquals("https://github.com/FirmPluvia/ykt_assistant",Shadows.shadowOf(activity).getNextStartedActivity().getDataString()); assertNotNull(find(root(),"雨课堂助手 "+BuildConfig.VERSION_NAME));
    }

    @Test public void navigatingAwayDuringSilentLookupCancelsDelayedDialogAndEntry() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            RainApi api=installEntryApi(server); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); RainApiTest.Lessons cache=new RainApiTest.Lessons() { @Override public JSONObject read(String h,String u,String l) throws Exception { entered.countDown(); release.await(5,TimeUnit.SECONDS); return super.read(h,u,l); } }; api.configure(cache); cache.write(api.host(),api.user().optString("user_id"),"lesson",RainApi.object("bearer","saved-http","lessonToken","saved-ws"));
            try {
                Dialog previous=ShadowDialog.getLatestDialog(); clickClassroom(); assertTrue(entered.await(5,TimeUnit.SECONDS)); View mine=find(root(),"我的"); assertNotNull(mine); ((View)mine.getParent()).performClick(); ShadowLooper.idleMainLooper(600,TimeUnit.MILLISECONDS); assertSame(previous,ShadowDialog.getLatestDialog()); assertFalse(entryBusy());
                release.countDown(); waitFor(() -> { try { return ((Integer)get("busyCount"))==0; } catch(Exception e) { return false; } }); assertEquals(2,get("tab")); assertNull(api.lesson()); assertEquals(1,server.getRequestCount()); assertSame(previous,ShadowDialog.getLatestDialog());
            } finally { release.countDown(); }
        }
    }



    private final String locationFixture="{\"needLocationSignIn\":true,\"signed\":false,\"session\":{\"centerLat\":31.25,\"centerLng\":121.5},\"address\":\"教学楼\"}";
    private RainApi locationClass(MockWebServer server) throws Exception { RainApi api=installEntryApi(server); server.enqueue(response("{\"data\":{\"lessonToken\":\"ws\"}}").setHeader("set-auth","http")); api.checkin("lesson"); server.takeRequest(); set("tab",1); set("dynamicView",2); call("render"); return api; }
    private boolean locationBusy() { try { return (Boolean)get("checkingLocation")||(Boolean)get("locationSubmitting"); } catch(Exception e) { return false; } }
    @Test public void serverLocationNeedsConfirmationWithoutDeviceLocationPermission() throws Exception {
        try(MockWebServer server=new MockWebServer()) { locationClass(server); server.enqueue(response(locationFixture)); click(root(),"查看位置签到状态"); waitFor(()->!locationBusy()); Dialog sheet=(Dialog)get("locationDialog"); assertNotNull(sheet); assertNotNull(find(sheet.getWindow().getDecorView(),"默认使用课堂返回的坐标，可修改或在默认位置周围随机生成。")); assertNotNull(find(sheet.getWindow().getDecorView(),"31.250000, 121.500000")); assertEquals("GET",server.takeRequest().getMethod()); assertEquals(3,server.getRequestCount());
            server.enqueue(response(locationFixture)); server.enqueue(response("{\"code\":0}")); server.enqueue(response("{\"signed\":true}")); click(sheet.getWindow().getDecorView(),"确认位置签到"); waitFor(()->!locationBusy()); assertFalse(sheet.isShowing()); server.takeRequest(); assertEquals("POST",server.takeRequest().getMethod()); assertEquals("GET",server.takeRequest().getMethod()); assertNull(Shadows.shadowOf(activity).getLastRequestedPermission()); }
    }
    @Test public void pendingLocationRefreshNeverSubmitsTwice() throws Exception {
        try(MockWebServer server=new MockWebServer()) { locationClass(server); server.enqueue(response(locationFixture)); call("checkLocation"); waitFor(()->!locationBusy()); server.takeRequest(); Dialog sheet=(Dialog)get("locationDialog"); server.enqueue(response(locationFixture)); server.enqueue(response("{\"code\":0}")); server.enqueue(response(locationFixture)); click(sheet.getWindow().getDecorView(),"确认位置签到"); waitFor(()->!locationBusy()); assertNotNull(find(sheet.getWindow().getDecorView(),"位置已提交，雨课堂尚未确认。可刷新状态查看结果。")); assertFalse(find(sheet.getWindow().getDecorView(),"本轮已提交").isEnabled()); for(int n=0;n<3;n++) server.takeRequest(); server.enqueue(response(locationFixture)); click(sheet.getWindow().getDecorView(),"刷新状态"); waitFor(()->!locationBusy()); assertEquals("GET",server.takeRequest().getMethod()); Dialog next=(Dialog)get("locationDialog"); assertNotNull(find(next.getWindow().getDecorView(),"等待签到确认")); assertFalse(find(next.getWindow().getDecorView(),"本轮已提交").isEnabled()); }
    }
    @Test public void queryFailureReleasesLocationBusyFlagForRetry() throws Exception {
        try(MockWebServer server=new MockWebServer()) { locationClass(server); server.enqueue(new MockResponse().setResponseCode(500)); call("checkLocation"); waitFor(()->!locationBusy()); assertNull(get("locationDialog")); server.takeRequest(); server.enqueue(response(locationFixture)); call("checkLocation"); waitFor(()->!locationBusy()); assertNotNull(get("locationDialog")); assertEquals("GET",server.takeRequest().getMethod()); }
    }
    @Test public void lateLocationQueryCannotOpenPromptAfterLogout() throws Exception {
        try(MockWebServer server=new MockWebServer()) { locationClass(server); CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1); server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request) { entered.countDown(); try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } return response(locationFixture); } });
            try { call("checkLocation"); assertTrue(entered.await(3,TimeUnit.SECONDS)); call("resetAccount"); release.countDown(); waitFor(()->!locationBusy()); assertNull(get("locationDialog")); assertNull(get("user")); } finally { release.countDown(); } }
    }

    @Test public void liveReaderExposesQuizAndLogoutDiscardsAccountDraftsAndSheet() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); ((RainApi)get("api")).close(); RainApi owner=new QuizApiTest().joined(server); set("api",owner); set("user",owner.user()); set("tab",1); set("dynamicView",2);
            JSONObject spec=RainApi.object("problemId","question","problemType",1,"body","课堂练习","options",new JSONArray("[{\"key\":\"A\",\"value\":\"选项\"}]"));
            set("presentation",RainApi.object("id","pres","slides",new JSONArray().put(RainApi.object("problem",spec)))); call("render"); assertNotNull(find(root(),"课堂答题")); click(root(),"课堂答题");
            LessonQuiz quiz=(LessonQuiz)get("lessonQuiz"); assertNotNull(quiz.get("question")); assertFalse(quiz.get("question").opened); Dialog sheet=ShadowDialog.getLatestDialog(); assertTrue(sheet.isShowing());
            quiz.ingest(RainApi.object("op","unlockproblem","problem",RainApi.object("prob","question","dt",System.currentTimeMillis(),"limit",60))); ((ClassroomQuizDialog)get("quizDialog")).changed(); quiz.get("question").selected.add("A");
            call("resetAccount"); assertFalse(sheet.isShowing()); assertNull(get("lessonQuiz")); assertNull(get("quizDialog"));
        }
    }
    @Test public void customLocationCoordinatesAreSubmittedWithoutChangingTheServerTarget() throws Exception {
        try(MockWebServer server=new MockWebServer()) { locationClass(server); server.enqueue(response(locationFixture)); call("checkLocation"); waitFor(()->!locationBusy()); server.takeRequest(); Dialog sheet=(Dialog)get("locationDialog"); View view=sheet.getWindow().getDecorView(); EditText lat=view.findViewWithTag("location-latitude"),lng=view.findViewWithTag("location-longitude"); assertEquals("31.250000",lat.getText().toString()); assertEquals("121.500000",lng.getText().toString()); lat.setText("-12.345678"); lng.setText("-123.456789");
            server.enqueue(response(locationFixture)); server.enqueue(response("{\"code\":0}")); server.enqueue(response("{\"signed\":true}")); click(view,"确认位置签到"); waitFor(()->!locationBusy()); assertEquals("GET",server.takeRequest().getMethod()); JSONObject body=new JSONObject(server.takeRequest().getBody().readUtf8()); assertEquals(-12.345678,body.getDouble("lat"),0); assertEquals(-123.456789,body.getDouble("lng"),0); assertEquals(54,body.getInt("source")); assertEquals("lesson",body.getString("lessonId")); assertEquals("教学楼",body.getString("address")); assertEquals("GET",server.takeRequest().getMethod());
        }
    }
    @Test public void invalidLocationInputDoesNotQueryOrSubmit() throws Exception {
        try(MockWebServer server=new MockWebServer()) { locationClass(server); server.enqueue(response(locationFixture)); call("checkLocation"); waitFor(()->!locationBusy()); server.takeRequest(); Dialog sheet=(Dialog)get("locationDialog"); View view=sheet.getWindow().getDecorView(); EditText lat=view.findViewWithTag("location-latitude"); lat.setText("91"); click(view,"确认位置签到"); assertNotNull(find(view,"纬度须在 -90 到 90 之间")); assertNull(server.takeRequest(150,TimeUnit.MILLISECONDS)); assertFalse(locationBusy()); assertTrue(find(view,"确认位置签到").isEnabled()); }
    }
    @Test public void randomLocationStaysAroundDefaultAndResetRestoresBothFields() throws Exception {
        try(MockWebServer server=new MockWebServer()) { locationClass(server); server.enqueue(response(locationFixture)); call("checkLocation"); waitFor(()->!locationBusy()); server.takeRequest(); Dialog sheet=(Dialog)get("locationDialog"); View view=sheet.getWindow().getDecorView(); EditText lat=view.findViewWithTag("location-latitude"),lng=view.findViewWithTag("location-longitude"); lat.setText("0"); lng.setText("0");
            for(int i=0;i<20;i++) { click(view,"随机 50 米"); LocationCheckin.Point q=LocationCoordinates.parse(lat.getText().toString(),lng.getText().toString()); assertTrue(LocationCoordinatesTest.distance(new LocationCheckin.Point(31.25,121.5),q)<=50); }
            click(view,"恢复默认"); assertEquals("31.250000",lat.getText().toString()); assertEquals("121.500000",lng.getText().toString()); assertNull(server.takeRequest(150,TimeUnit.MILLISECONDS)); }
    }
    @Test public void longPressRandomRadiusValidatesPersistsAndUpdatesLocationButton() throws Exception {
        try(MockWebServer server=new MockWebServer()) { locationClass(server);server.enqueue(response(locationFixture));call("checkLocation");waitFor(()->!locationBusy());server.takeRequest();Dialog parent=(Dialog)get("locationDialog");View root=parent.getWindow().getDecorView();assertNotNull(find(root,"长按随机按钮修改距离 · 默认位置周围 50 米"));assertTrue(find(root,"随机 50 米").performLongClick());Dialog editor=ShadowDialog.getLatestDialog();EditText radius=editor.getWindow().getDecorView().findViewWithTag("location-random-radius");radius.setText("0");click(editor.getWindow().getDecorView(),"保存距离");assertTrue(editor.isShowing());radius.setText("500");click(editor.getWindow().getDecorView(),"保存距离");assertFalse(editor.isShowing());assertNotNull(find(root,"随机 500 米"));assertEquals("500",activity.getSharedPreferences("native_preferences",0).getString(LocationCoordinates.RADIUS_KEY,""));assertNull(server.takeRequest(150,TimeUnit.MILLISECONDS));
            parent.dismiss();server.enqueue(response(locationFixture));call("checkLocation");waitFor(()->!locationBusy());assertNotNull(find(((Dialog)get("locationDialog")).getWindow().getDecorView(),"随机 500 米"));
        }
    }
    @Test public void locationDismissalAlsoClosesRadiusEditor()throws Exception{
        try(MockWebServer server=new MockWebServer()){locationClass(server);server.enqueue(response(locationFixture));call("checkLocation");waitFor(()->!locationBusy());Dialog parent=(Dialog)get("locationDialog");find(parent.getWindow().getDecorView(),"随机 50 米").performLongClick();Dialog child=ShadowDialog.getLatestDialog();parent.dismiss();ShadowLooper.idleMainLooper();assertFalse(child.isShowing());}
    }
    @Test public void entryCardHasHeaderXAndHidesSyncForSingleAccount()throws Exception{
        try(MockWebServer server=new MockWebServer()){RainApi owner=installEntryApi(server);clickClassroom();waitFor(()->!entryBusy());Dialog sheet=ShadowDialog.getLatestDialog();View panel=sheet.getWindow().getDecorView();View close=panel.findViewWithTag("card-close");assertTrue(close instanceof android.widget.ImageButton);ViewGroup card=(ViewGroup)close.getParent().getParent();assertSame(close.getParent(),card.getChildAt(0));assertNull(find(panel,"暂不进入"));List<View> views=all(panel);assertNull(find(panel,"同时为其他账号签到入课"));assertNull(panel.findViewWithTag("sync-check:checkin"));closeCard(panel);assertFalse(sheet.isShowing());assertNull(owner.lesson());}
    }
    @Test public void joiningDisablesHeaderXAndFailureRestoresCancellation()throws Exception{
        try(MockWebServer server=new MockWebServer()){installEntryApi(server);clickClassroom();waitFor(()->!entryBusy());Dialog sheet=ShadowDialog.getLatestDialog();View close=sheet.getWindow().getDecorView().findViewWithTag("card-close");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);server.setDispatcher(new okhttp3.mockwebserver.Dispatcher(){public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest request){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}return new MockResponse().setResponseCode(500);}});
            try{click(sheet.getWindow().getDecorView(),"签到并进入课堂");assertTrue(entered.await(3,TimeUnit.SECONDS));assertFalse(close.isEnabled());close.performClick();sheet.onBackPressed();ShadowLooper.idleMainLooper();assertTrue(sheet.isShowing());release.countDown();waitFor(()->{try{return !(Boolean)get("joining");}catch(Exception e){return false;}});assertTrue(close.isEnabled());closeCard(sheet.getWindow().getDecorView());assertFalse(sheet.isShowing());}finally{release.countDown();}
        }
    }
    @Test public void progressHeaderXImmediatelyCancelsExportAndDoesNotReopenSheet()throws Exception{
        java.io.File file=java.io.File.createTempFile("card-cancel-",".zip");CountDownLatch entered=new CountDownLatch(1);
        try{((RainApi)get("api")).close();RainApi owner=new RainApi(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store()){@Override public byte[] slideImage(JSONObject slide,java.util.function.BooleanSupplier stopped)throws Exception{entered.countDown();while(!stopped.getAsBoolean())Thread.sleep(5);throw new java.io.InterruptedIOException("cancelled");}};set("api",owner);loggedIn();set("presentation",RainApi.object("title","取消下载","slides",new JSONArray().put(RainApi.object("id","one"))));call("downloadOptions");Dialog picker=ShadowDialog.getLatestDialog();View options=picker.getWindow().getDecorView();((View)find(options,"ZIP 页面图片").getParent().getParent()).performClick();click(options,"选择保存位置");activity.onActivityResult(42,android.app.Activity.RESULT_OK,new Intent().setData(android.net.Uri.fromFile(file)));Dialog progress=ShadowDialog.getLatestDialog();assertTrue(entered.await(3,TimeUnit.SECONDS));Object job=get("activeDownload");progress.getWindow().getDecorView().findViewWithTag("card-close").performClick();Field state=job.getClass().getDeclaredField("cancelled");state.setAccessible(true);assertTrue(((java.util.concurrent.atomic.AtomicBoolean)state.get(job)).get());ShadowLooper.idleMainLooper();waitFor(()->{try{return !(Boolean)get("downloading");}catch(Exception e){return false;}});assertFalse(progress.isShowing());assertEquals(0,file.length());}finally{file.delete();}
    }
    @Test public void answerPhotoPickerUsesSystemDocumentGrantAndRoutesMultipleUrisOnce()throws Exception{
        java.util.concurrent.atomic.AtomicReference<List<android.net.Uri>> received=new java.util.concurrent.atomic.AtomicReference<>();Method method=MainActivity.class.getDeclaredMethod("pickQuizImages",java.util.function.Consumer.class);method.setAccessible(true);method.invoke(activity,(java.util.function.Consumer<List<android.net.Uri>>)received::set);org.robolectric.shadows.ShadowActivity.IntentForResult pick=Shadows.shadowOf(activity).getNextStartedActivityForResult();assertEquals(Intent.ACTION_OPEN_DOCUMENT,pick.intent.getAction());assertEquals("image/*",pick.intent.getType());assertTrue(pick.intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,false));android.content.ClipData clip=android.content.ClipData.newRawUri("images",android.net.Uri.parse("content://photos/1"));clip.addItem(new android.content.ClipData.Item(android.net.Uri.parse("content://photos/2")));Intent selected=new Intent();selected.setClipData(clip);activity.onActivityResult(pick.requestCode,android.app.Activity.RESULT_OK,selected);assertEquals(2,received.get().size());assertNull(get("pendingQuizImages"));received.set(null);activity.onActivityResult(pick.requestCode,android.app.Activity.RESULT_CANCELED,null);assertNull(received.get());
    }
    @Test public void emptyClassroomQuizOnlyShowsToastWithoutCreatingAnswerCard()throws Exception{
        try(MockWebServer server=new MockWebServer()){locationClass(server);click(root(),"课堂答题");assertEquals("当前课堂未收到任何题目",org.robolectric.shadows.ShadowToast.getTextOfLatestToast());assertNull(get("quizDialog"));assertTrue(ShadowDialog.getShownDialogs().stream().noneMatch(Dialog::isShowing));assertEquals(2,server.getRequestCount());}
    }
    @Test public void locationCardOffersUncheckedSynchronizationOnlyForMultipleAccounts()throws Exception{
        try(MockWebServer server=new MockWebServer()){locationClass(server);AccountStore store=new AccountStore(activity);store.remember(0,(JSONObject)get("user"),"[]");store.remember(0,RainApi.object("user_id","another","name","其他同学"),"[]");server.enqueue(response(locationFixture));call("checkLocation");waitFor(()->!locationBusy());Dialog sheet=(Dialog)get("locationDialog");View root=sheet.getWindow().getDecorView();android.widget.CheckBox sync=root.findViewWithTag("sync-check:checkin");assertNotNull(sync);assertEquals("同步位置签到",sync.getText().toString());assertFalse(sync.isChecked());assertNotNull(root.findViewWithTag("sync-edit:checkin"));sheet.dismiss();store.remove("0|another");server.enqueue(response(locationFixture));call("checkLocation");waitFor(()->!locationBusy());assertNull(((Dialog)get("locationDialog")).getWindow().getDecorView().findViewWithTag("sync-check:checkin"));}
    }
    @Test public void localFiltersCombineCourseAndTypeAcrossCachedSegmentsWithoutRequest() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            installEntryApi(server);
            JSONArray old=new JSONArray().put(RainApi.object("id","old","coursewareType",14,
                "coursewareId",12,"courseName","高等数学","coursewareTitle","旧课堂",
                "createTime",1,"sort","old-cursor"));
            JSONArray head=new JSONArray()
                .put(RainApi.object("id","hw","coursewareType",5,"courseName","高等数学",
                    "coursewareTitle","数学作业","createTime",3,"sort","hw-cursor"))
                .put(RainApi.object("id","new","coursewareType",14,"coursewareId",13,
                    "courseName","大学英语","coursewareTitle","英语课堂","createTime",2,"sort","new-cursor"));
            FeedTimeline local=new FeedTimeline(ActivityFeed.from(old)).refresh(head);
            set("activityTimeline",local);
            set("activityFeed",local.visible);
            set("tab",1);
            call("showFeed");
            assertNull(find(root(),"旧课堂"));
            int requestCount=server.getRequestCount();
            click(root(),"筛选动态");
            View sheet=ShadowDialog.getLatestDialog().getWindow().getDecorView();
            assertFalse(all(sheet).stream().anyMatch(v -> v instanceof EditText));
            assertNull(find(sheet,"大学英语"));
            ((android.widget.RadioButton)find(sheet,"作业")).setChecked(true);
            click(sheet,"选择课程 · 全部课程");
            Dialog picker=ShadowDialog.getLatestDialog();
            View courseSheet=picker.getWindow().getDecorView();
            assertNull(find(courseSheet,"大学英语"));
            assertNotNull(find(courseSheet,"全部课程"));
            click(courseSheet,"高等数学");
            assertFalse(picker.isShowing());
            assertNotNull(find(sheet,"选择课程 · 高等数学"));
            click(sheet,"应用筛选");
            assertNotNull(find(root(),"数学作业"));
            assertNull(find(root(),"英语课堂"));
            assertNull(find(root(),"旧课堂"));
            click(root(),"筛选动态 · 作业 · 高等数学");
            sheet=ShadowDialog.getLatestDialog().getWindow().getDecorView();
            ((android.widget.RadioButton)find(sheet,"课堂")).setChecked(true);
            click(sheet,"选择课程 · 高等数学");
            picker=ShadowDialog.getLatestDialog();
            courseSheet=picker.getWindow().getDecorView();
            assertNotNull(find(courseSheet,"大学英语"));
            assertTrue(find(courseSheet,"高等数学").isSelected());
            courseSheet.findViewWithTag("card-close").performClick();
            ShadowLooper.idleMainLooper();
            assertFalse(picker.isShowing());
            assertNotNull(find(sheet,"选择课程 · 高等数学"));
            click(sheet,"应用筛选");
            assertNotNull(find(root(),"旧课堂"));
            assertNull(find(root(),"英语课堂"));
            assertNull(find(root(),"数学作业"));
            assertEquals("new-cursor",((ActivityFeed)get("activityFeed")).nextCursor);
            assertEquals(requestCount,server.getRequestCount());
            click(root(),"筛选动态 · 课堂 · 高等数学");
            click(ShadowDialog.getLatestDialog().getWindow().getDecorView(),"清除筛选");
            assertNotNull(find(root(),"数学作业"));
            assertNull(find(root(),"旧课堂"));
            assertEquals(requestCount,server.getRequestCount());
        }
    }
}
