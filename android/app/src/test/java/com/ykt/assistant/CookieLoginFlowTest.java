package com.ykt.assistant;

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONObject;
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
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w393dp-h851dp-hdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class CookieLoginFlowTest {
    public static class TestActivity extends MainActivity {
        static MockWebServer[] servers;
        @Override protected void checkForUpdates(boolean manual) {}
        @Override protected RainApi createLoginClient(int service,RainApi.CookieStorage storage) throws Exception { return new RainApi(servers[service].url("/"),storage); }
        @Override protected RainApi createStartupApi() throws Exception { return new RainApi(servers[getSharedPreferences("native_preferences",0).getInt("service",0)].url("/"),new SessionVault(this).session()); }
    }
    private MockWebServer first,second; private ActivityController<TestActivity> controller; private TestActivity activity; private CountDownLatch slowEntered,releaseSlow; private final java.util.List<RecordedRequest> requests=new java.util.concurrent.CopyOnWriteArrayList<>(); private boolean rejectPrimary;
    @Before public void setup() throws Exception {
        Context app=RuntimeEnvironment.getApplication(); for(String name:new String[]{"native_session","native_preferences","native_cookie_settings","saved_accounts","request_sync"}) app.getSharedPreferences(name,0).edit().clear().commit();
        first=new MockWebServer(); second=new MockWebServer(); Dispatcher dispatcher=new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            requests.add(request); String path=request.getRequestUrl().encodedPath();
            if(path.equals("/v/course_meta/user_info")) {
                String cookie=request.getHeader("Cookie");
                if(cookie!=null&&cookie.contains("sessionid=slow")) { slowEntered.countDown(); releaseSlow.await(5,TimeUnit.SECONDS); }
                if(cookie!=null&&cookie.contains("sessionid=second")) return response("{\"data\":{\"user_profile\":{\"user_id\":\"account-b\",\"name\":\"同学乙\"}}}").setHeader("Set-Cookie","sessionid=second-refresh; Path=/");
                if(cookie!=null&&(cookie.contains("sessionid=valid")||cookie.contains("sessionid=slow"))) return response("{\"data\":{\"user_profile\":{\"user_id\":\"account-a\",\"name\":\"同学甲\"}}}").setHeader("Set-Cookie","sessionid=valid-refresh; Path=/");
                return new MockResponse().setResponseCode(401);
            }
            if(path.equals("/v/course_meta/learning_list/")) return response("{\"data\":[{\"course_id\":\"course\",\"course_name\":\"数学课\"}]}");
            if(path.equals("/api/v3/classroom/on-lesson")) return response("{\"data\":{\"onLessonClassrooms\":[{\"courseId\":\"course\",\"lessonId\":\"123456\"}]}}");
            if(path.equals("/api/v3/lesson/checkin")&&rejectPrimary&&"account-a".equals(request.getHeader("x-uid"))) return new MockResponse().setResponseCode(403);
            if(path.equals("/api/v3/lesson/checkin")) return response("{\"data\":{\"lessonToken\":\"fixture-ws\"}}").setHeader("set-auth","fixture-http");
            return new MockResponse().setResponseCode(404);
        } }; first.setDispatcher(dispatcher); second.setDispatcher(dispatcher); first.start(); second.start(); TestActivity.servers=new MockWebServer[]{first,second,first,second}; start();
    }
    private MockResponse response(String value) { return new MockResponse().setHeader("Content-Type","application/json").setBody(value); }
    private void start() { controller=Robolectric.buildActivity(TestActivity.class).setup(); activity=controller.get(); ShadowLooper.idleMainLooper(); }
    @After public void cleanup() throws Exception { if(releaseSlow!=null) releaseSlow.countDown(); if(controller!=null) controller.pause().stop().destroy(); ShadowLooper.idleMainLooper(); first.close(); second.close(); }
    private Object get(String key) throws Exception { Field field=MainActivity.class.getDeclaredField(key); field.setAccessible(true); return field.get(activity); }
    private void set(String key,Object value) throws Exception { Field field=MainActivity.class.getDeclaredField(key); field.setAccessible(true); field.set(activity,value); }
    private void call(String name) throws Exception { Method method=MainActivity.class.getDeclaredMethod(name); method.setAccessible(true); method.invoke(activity); }
    private View find(View view,String text) { if(view instanceof TextView&&((TextView)view).getText().toString().equals(text)) return view; if(view instanceof ViewGroup) for(int n=0;n<((ViewGroup)view).getChildCount();n++) { View found=find(((ViewGroup)view).getChildAt(n),text); if(found!=null) return found; } return null; }
    private View panel() throws Exception { return ((Dialog)get("cookieDialog")).getWindow().getDecorView(); }
    private EditText input(View view) { if(view instanceof EditText) return (EditText)view; if(view instanceof ViewGroup) for(int n=0;n<((ViewGroup)view).getChildCount();n++) { EditText found=input(((ViewGroup)view).getChildAt(n)); if(found!=null) return found; } return null; }
    private void click(View view,String text) { View found=find(view,text); assertNotNull(text,found); while(!found.isClickable()&&found.getParent() instanceof View) found=(View)found.getParent(); if(found instanceof android.widget.CheckBox)found.performClick();else assertTrue(found.performClick()); ShadowLooper.idleMainLooper(); }
    private void waitFor(BooleanSupplier condition) throws Exception { for(int n=0;n<250;n++) { ShadowLooper.idleMainLooper(); if(condition.getAsBoolean()) return; Thread.sleep(10); } fail("async timeout"); }
    private boolean idle() { try { return (Integer)get("busyCount")==0&&!(Boolean)get("loadingCourses"); } catch(Exception e) { throw new RuntimeException(e); } }
    private void login() throws Exception { call("openCookieLogin"); input(panel()).setText("sessionid=valid; csrftoken=csrf-fixture"); click(panel(),"使用 Cookie 登录"); waitFor(()-> { try { return get("user")!=null&&idle(); } catch(Exception e) { return false; } }); }
    @Test public void cookieLoginIsSeparateBlankAndHasNoCopyOrSaveButton() throws Exception {
        new CookieSettings(activity).save(0,"sessionid=old-draft"); new CookieSettings(activity).save(1,"sessionid=another-old-draft"); set("tab",2); call("render"); call("openCookieLogin"); assertEquals("",input(panel()).getText().toString()); assertNull(find(panel(),"管理 Cookie")); assertNull(find(panel(),"复制 Cookie")); assertNull(find(panel(),"保存 Cookie")); input(panel()).setText("sessionid=first-fixture"); click(panel(),"雨课堂"); Dialog picker=ShadowDialog.getLatestDialog(); click(picker.getWindow().getDecorView(),"荷塘雨课堂"); assertEquals("",input(panel()).getText().toString()); assertEquals(0,activity.getSharedPreferences("native_preferences",0).getInt("service",0)); assertEquals(0,first.getRequestCount()); assertEquals(0,second.getRequestCount());
    }
    @Test public void verifiedCookieLoginPersistsRefreshedPlainSessionAndUsesConfiguredUa() throws Exception {
        activity.getSharedPreferences("native_preferences",0).edit().putString(UserAgent.KEY,"Cookie-Test-UA").commit(); login(); assertNull(get("cookieDialog")); RainApi api=(RainApi)get("api"); assertFalse(api.isClosed()); assertEquals("account-a",((JSONObject)get("user")).optString("user_id")); String plain=activity.getSharedPreferences("native_session",0).getString("cookies",""); assertTrue(plain.contains("valid-refresh")); assertFalse(activity.getSharedPreferences("native_session",0).contains("encrypted")); assertEquals(plain,new SessionVault(activity).session().read()); RecordedRequest profile=first.takeRequest(3,TimeUnit.SECONDS); assertEquals("/v/course_meta/user_info",profile.getPath()); assertEquals("Cookie-Test-UA",profile.getHeader("User-Agent")); assertTrue(profile.getHeader("Cookie").contains("sessionid=valid")); assertNull(profile.getHeader("Authorization")); 
    }
    @Test public void loggedInCookieManagementOnlyCopiesCurrentSessionWithoutLoginAction() throws Exception {
        login(); RainApi previous=(RainApi)get("api"); String plain=activity.getSharedPreferences("native_session",0).getString("cookies",""); int requests=first.getRequestCount(); call("openCookieSettings"); assertNull(find(panel(),"使用 Cookie 登录")); assertNull(find(panel(),"保存 Cookie")); assertTrue(input(panel()).getText().toString().contains("valid-refresh")); click(panel(),"复制 Cookie"); android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE); assertEquals(previous.loginCookieHeader(),clipboard.getPrimaryClip().getItemAt(0).getText().toString()); assertSame(previous,get("api")); assertEquals(requests,first.getRequestCount()); assertEquals(plain,activity.getSharedPreferences("native_session",0).getString("cookies",""));
    }
    @Test public void invalidCookieWhileLoggedOutDoesNotPersistOrCreateAnAccount() throws Exception {
        call("openCookieLogin"); input(panel()).setText("sessionid=invalid-fixture"); click(panel(),"使用 Cookie 登录"); waitFor(this::idle); assertNull(get("user")); assertFalse(activity.getSharedPreferences("native_session",0).contains("cookies")); assertNotNull(find(panel(),"Cookie 校验失败：登录已失效或没有访问权限，请重新登录"));
    }
    @Test public void cancellingPendingCookieValidationCannotLogInLater() throws Exception {
        slowEntered=new CountDownLatch(1); releaseSlow=new CountDownLatch(1); call("openCookieLogin"); input(panel()).setText("sessionid=slow"); click(panel(),"使用 Cookie 登录"); assertTrue(slowEntered.await(3,TimeUnit.SECONDS)); panel().findViewWithTag("card-close").performClick(); ShadowLooper.idleMainLooper(); releaseSlow.countDown(); waitFor(this::idle); assertNull(get("user")); assertNull(get("cookieDialog")); assertFalse(activity.getSharedPreferences("native_session",0).contains("cookies"));
    }
    @Test public void logoutClearsActiveCookiesAndAllManuallySavedServers() throws Exception {
        login(); new CookieSettings(activity).save(1,"sessionid=another-draft"); call("resetAccount"); assertNull(get("user")); assertFalse(activity.getSharedPreferences("native_session",0).contains("cookies")); assertTrue(activity.getSharedPreferences("native_session",0).getBoolean("signed_out",false)); assertEquals("",new CookieSettings(activity).read(0)); assertEquals("",new CookieSettings(activity).read(1));
    }
    @Test public void plainCookieRestoresThroughProfileAfterActivityRestart() throws Exception {
        login(); controller.pause().stop().destroy(); controller=null; start(); waitFor(()-> { try { return get("user")!=null&&idle(); } catch(Exception e) { return false; } }); assertEquals("account-a",((JSONObject)get("user")).optString("user_id")); assertFalse(((RainApi)get("api")).isClosed());
    }
    @Test public void cookieLoginBindsFreshCacheLeaseAndReusesRetainedCredentialsAfterLogout() throws Exception {
        java.io.File directory=java.nio.file.Files.createTempDirectory("cookie-lesson-retention").toFile(); javax.crypto.KeyGenerator generator=javax.crypto.KeyGenerator.getInstance("AES"); generator.init(128); javax.crypto.SecretKey key=generator.generateKey(); LessonCache cache=new LessonCache(directory,activity.getSharedPreferences("cookie-lesson-retention",0),new LessonCache.Crypto() {
            public byte[] seal(byte[] plain,byte[] aad) throws Exception { javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,key); cipher.updateAAD(aad); byte[] body=cipher.doFinal(plain),encoded=new byte[12+body.length]; System.arraycopy(cipher.getIV(),0,encoded,0,12); System.arraycopy(body,0,encoded,12,body.length); return encoded; }
            public byte[] open(byte[] encoded,byte[] aad) throws Exception { javax.crypto.Cipher cipher=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(javax.crypto.Cipher.DECRYPT_MODE,key,new javax.crypto.spec.GCMParameterSpec(128,java.util.Arrays.copyOf(encoded,12))); cipher.updateAAD(aad); return cipher.doFinal(java.util.Arrays.copyOfRange(encoded,12,encoded.length)); }
        },System::currentTimeMillis); set("lessonCache",cache);
        try { login(); RainApi firstApi=(RainApi)get("api"); firstApi.checkin("123456"); assertNotNull(cache.session().read(firstApi.host(),"account-a","123456")); call("resetAccount"); assertTrue(firstApi.isClosed()); assertNotNull(cache.session().read(firstApi.host(),"account-a","123456")); login(); RainApi next=(RainApi)get("api"); int before=first.getRequestCount(); assertTrue(next.restoreLesson("123456")); assertEquals(before,first.getRequestCount()); } finally { java.io.File[] files=directory.listFiles(); if(files!=null) for(java.io.File file:files) file.delete(); directory.delete(); }
    }
    private String saveSecondAccount(boolean valid) throws Exception { return new AccountStore(activity).remember(0,RainApi.object("user_id","account-b","name","同学乙"),new org.json.JSONArray().put(RainApi.object("raw","sessionid="+(valid?"second":"invalid-fixture")+"; Path=/","expires",Long.MAX_VALUE)).toString()); }
    private void switchAccount(String key) throws Exception { Method method=MainActivity.class.getDeclaredMethod("switchSavedAccount",String.class); method.setAccessible(true); method.invoke(activity,key); }
    private void selectSecondAccount(View view) { if(view instanceof android.widget.CheckBox&&((TextView)view).getText().toString().equals("同学乙")) { ((android.widget.CheckBox)view).setChecked(true); return; } if(view instanceof ViewGroup) for(int n=0;n<((ViewGroup)view).getChildCount();n++) selectSecondAccount(((ViewGroup)view).getChildAt(n)); }
    @Test public void switchesSavedAccountAndKeepsOriginalAccountAvailable() throws Exception {
        login(); RainApi original=(RainApi)get("api"); String second=saveSecondAccount(true); call("openAccountManager"); Dialog manager=ShadowDialog.getLatestDialog(); assertNotNull(find(manager.getWindow().getDecorView(),"当前")); assertNull(find(manager.getWindow().getDecorView(),"正在使用")); TextView current=(TextView)find(manager.getWindow().getDecorView(),"当前"),change=(TextView)find(manager.getWindow().getDecorView(),"切换"); assertEquals(current.getTextSize(),change.getTextSize(),0); assertEquals(current.getLayoutParams().width,change.getLayoutParams().width); assertEquals(current.getLayoutParams().height,change.getLayoutParams().height); assertEquals(current.getPaddingLeft(),change.getPaddingLeft()); assertFalse(current.isClickable()); assertTrue(change.isClickable()); click(manager.getWindow().getDecorView(),"切换"); waitFor(()-> { try { return get("user")!=null&&"account-b".equals(((JSONObject)get("user")).optString("user_id"))&&idle(); } catch(Exception e) { return false; } }); assertTrue(original.isClosed()); AccountStore store=new AccountStore(activity); assertEquals(2,store.list().size()); assertTrue(store.get("0|account-a").cookies.contains("valid-refresh")); assertTrue(store.get(second).cookies.contains("second-refresh")); assertEquals(second,store.active()); assertTrue(((RainApi)get("api")).loginCookieHeader().contains("second-refresh"));
    }
    @Test public void invalidSavedAccountDoesNotReplaceCurrentSession() throws Exception {
        login(); RainApi original=(RainApi)get("api"); switchAccount(saveSecondAccount(false)); waitFor(this::idle); assertSame(original,get("api")); assertFalse(original.isClosed()); assertEquals("account-a",((JSONObject)get("user")).optString("user_id")); assertEquals("0|account-a",new AccountStore(activity).active());
    }
    @Test public void cancelledSavedAccountValidationKeepsCurrentAccountAndServer() throws Exception {
        login(); RainApi original=(RainApi)get("api"); slowEntered=new CountDownLatch(1); releaseSlow=new CountDownLatch(1); String key=new AccountStore(activity).remember(1,RainApi.object("user_id","account-a","name","另一个服务器"),new org.json.JSONArray().put(RainApi.object("raw","sessionid=slow; Path=/","expires",Long.MAX_VALUE)).toString()); switchAccount(key); assertTrue(slowEntered.await(3,TimeUnit.SECONDS)); Dialog switching=ShadowDialog.getLatestDialog(); switching.getWindow().getDecorView().findViewWithTag("card-close").performClick(); ShadowLooper.idleMainLooper(); releaseSlow.countDown(); waitFor(this::idle); assertSame(original,get("api")); assertFalse(original.isClosed()); assertEquals(0,activity.getSharedPreferences("native_preferences",0).getInt("service",0)); assertEquals("0|account-a",new AccountStore(activity).active());
    }
    @Test public void cancellingAddAccountRestoresPreviouslySavedAccount() throws Exception {
        login(); call("beginAddAccount"); assertNull(get("user")); assertNotNull(get("loginDialog")); ((Dialog)get("loginDialog")).dismiss(); waitFor(()-> { try { return get("user")!=null&&idle(); } catch(Exception e) { return false; } }); assertEquals("account-a",((JSONObject)get("user")).optString("user_id")); assertEquals(1,new AccountStore(activity).list().size());
    }
    @Test public void addingAnotherCookieAccountKeepsBothSessionsForSwitching() throws Exception {
        login(); new CookieSettings(activity).save(0,"sessionid=old-account-draft"); call("beginAddAccount"); Dialog qr=(Dialog)get("loginDialog"); click(qr.getWindow().getDecorView(),"Cookie 登录"); assertEquals("",input(panel()).getText().toString()); assertNull(find(panel(),"复制 Cookie")); input(panel()).setText("sessionid=second"); click(panel(),"使用 Cookie 登录"); waitFor(()-> { try { return get("user")!=null&&"account-b".equals(((JSONObject)get("user")).optString("user_id"))&&idle(); } catch(Exception e) { return false; } }); assertEquals(2,new AccountStore(activity).list().size()); assertTrue(new AccountStore(activity).get("0|account-a").cookies.contains("valid-refresh"));
    }
    @Test public void liveReaderCanSignInSelectedOtherAccountWithoutSwitchingCurrentAccount() throws Exception {
        login(); activity.getSharedPreferences("request_sync",0).edit().clear().commit(); saveSecondAccount(true); RainApi owner=(RainApi)get("api"); owner.checkin("123456"); set("tab",1); set("dynamicView",2); call("render"); click(activity.getWindow().getDecorView(),"修改账号（1）"); Dialog choose=ShadowDialog.getLatestDialog(); selectSecondAccount(choose.getWindow().getDecorView()); click(choose.getWindow().getDecorView(),"使用所选账号"); click(activity.getWindow().getDecorView(),"同步签到"); waitFor(()-> { try { return get("batchCheckin")==null&&idle(); } catch(Exception e) { return false; } }); assertSame(owner,get("api")); assertEquals("account-a",((JSONObject)get("user")).optString("user_id")); assertEquals("123456",owner.lesson()); assertTrue(requests.stream().anyMatch(r->r.getPath().equals("/api/v3/lesson/checkin")&&"account-b".equals(r.getHeader("x-uid"))));
    }
    private java.util.List<android.widget.CheckBox> accountChoices(View view) { java.util.List<android.widget.CheckBox> choices=new java.util.ArrayList<>(); if(view instanceof android.widget.CheckBox) choices.add((android.widget.CheckBox)view); if(view instanceof ViewGroup) for(int n=0;n<((ViewGroup)view).getChildCount();n++) choices.addAll(accountChoices(((ViewGroup)view).getChildAt(n))); return choices; }
    @Test public void otherAccountsDefaultToAllEligibleAccountsSelected() throws Exception {
        login(); activity.getSharedPreferences("request_sync",0).edit().clear().commit(); saveSecondAccount(true); new AccountStore(activity).remember(1,RainApi.object("user_id","other-server","name","不同服务器"),"[]"); RainApi owner=(RainApi)get("api"); owner.checkin("123456"); set("tab",1); set("dynamicView",2); call("render"); click(activity.getWindow().getDecorView(),"修改账号（1）"); Dialog chooser=ShadowDialog.getLatestDialog(); java.util.List<android.widget.CheckBox> choices=accountChoices(chooser.getWindow().getDecorView()); assertEquals(1,choices.size()); assertTrue(choices.get(0).isChecked()); assertEquals("同学乙",choices.get(0).getText().toString()); assertFalse(requests.stream().anyMatch(r->"account-b".equals(r.getHeader("x-uid"))));
    }
    @Test public void explicitlyDeselectedAccountsStayDeselectedWhenReopeningSelector() throws Exception {
        login(); activity.getSharedPreferences("request_sync",0).edit().clear().commit(); saveSecondAccount(true); click(activity.getWindow().getDecorView(),"查看课堂"); waitFor(this::idle); Dialog primary=ShadowDialog.getLatestDialog(); click(primary.getWindow().getDecorView(),"修改账号（1）"); Dialog chooser=ShadowDialog.getLatestDialog(); java.util.List<android.widget.CheckBox> choices=accountChoices(chooser.getWindow().getDecorView()); assertTrue(choices.get(0).isChecked()); choices.get(0).setChecked(false); click(chooser.getWindow().getDecorView(),"使用所选账号"); click(primary.getWindow().getDecorView(),"修改账号（0）"); chooser=ShadowDialog.getLatestDialog(); assertFalse(accountChoices(chooser.getWindow().getDecorView()).get(0).isChecked()); assertFalse(requests.stream().anyMatch(r->r.getPath().equals("/api/v3/lesson/checkin")));
    }
    @Test public void primaryEntryOnlyDispatchesOtherAccountAfterExplicitSelectionAndSuccess() throws Exception {
        login(); activity.getSharedPreferences("request_sync",0).edit().clear().commit(); saveSecondAccount(true); click(activity.getWindow().getDecorView(),"查看课堂"); waitFor(this::idle); Dialog primary=ShadowDialog.getLatestDialog(); click(primary.getWindow().getDecorView(),"修改账号（1）"); Dialog choose=ShadowDialog.getLatestDialog(); selectSecondAccount(choose.getWindow().getDecorView()); click(choose.getWindow().getDecorView(),"使用所选账号"); assertFalse(requests.stream().anyMatch(r->r.getPath().equals("/api/v3/lesson/checkin"))); click(primary.getWindow().getDecorView(),"同步签到"); click(primary.getWindow().getDecorView(),"签到并进入课堂"); waitFor(()-> { try { return ((RainApi)get("api")).lesson()!=null&&get("batchCheckin")==null&&idle(); } catch(Exception e) { return false; } }); assertEquals(2,requests.stream().filter(r->r.getPath().equals("/api/v3/lesson/checkin")).count()); assertEquals("account-a",((JSONObject)get("user")).optString("user_id"));
    }
    @Test public void failedPrimaryEntryDoesNotDispatchOtherAccount() throws Exception {
        login(); activity.getSharedPreferences("request_sync",0).edit().clear().commit(); saveSecondAccount(true); rejectPrimary=true; click(activity.getWindow().getDecorView(),"查看课堂"); waitFor(this::idle); Dialog primary=ShadowDialog.getLatestDialog(); click(primary.getWindow().getDecorView(),"修改账号（1）"); Dialog choose=ShadowDialog.getLatestDialog(); selectSecondAccount(choose.getWindow().getDecorView()); click(choose.getWindow().getDecorView(),"使用所选账号"); click(primary.getWindow().getDecorView(),"同步签到"); click(primary.getWindow().getDecorView(),"签到并进入课堂"); waitFor(this::idle); assertNull(((RainApi)get("api")).lesson()); assertFalse(requests.stream().anyMatch(r->"account-b".equals(r.getHeader("x-uid")))); assertEquals(1,requests.stream().filter(r->r.getPath().equals("/api/v3/lesson/checkin")).count());
    }
    @Test public void redundantJoinedCardsAreRemovedAndCopyRemainsAccessible() throws Exception {
        login(); View root=activity.getWindow().getDecorView(); assertNull(find(root,"当前入课")); assertNotNull(find(root,"复制课堂 ID")); assertNotNull(find(root,"查看课堂")); View copy=find(root,"复制课堂 ID"),enter=find(root,"查看课堂"); android.widget.LinearLayout actions=(android.widget.LinearLayout)copy.getParent(); int width=Math.round(340*activity.getResources().getDisplayMetrics().density); actions.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.AT_MOST)); actions.layout(0,0,width,actions.getMeasuredHeight()); assertTrue(copy.getRight()<=enter.getLeft()); set("tab",2); call("render"); assertNull(find(activity.getWindow().getDecorView(),"连接方式")); assertNull(find(activity.getWindow().getDecorView(),"手机直连官方服务")); RainApi api=(RainApi)get("api"); api.checkin("123456"); set("tab",1); set("dynamicView",0); call("render"); root=activity.getWindow().getDecorView(); assertNull(find(root,"入课已确认")); assertNull(find(root,"查看当前课件")); assertNotNull(find(root,"最近课堂"));
    }
    @Test public void synchronizationSettingsOnlyAppearForMultipleSavedAccounts()throws Exception {
        login();set("tab",2);call("render");assertNull(find(activity.getWindow().getDecorView(),"同步设置"));saveSecondAccount(true);call("render");click(activity.getWindow().getDecorView(),"同步设置");View root=ShadowDialog.getLatestDialog().getWindow().getDecorView();assertNotNull(root.findViewWithTag("sync-check:checkin"));assertNotNull(root.findViewWithTag("sync-check:answer"));assertFalse(((android.widget.CheckBox)root.findViewWithTag("sync-result-cards")).isChecked());root.findViewWithTag("sync-result-cards").performClick();assertTrue(new SyncSettings(activity,new AccountStore(activity),0,"0|account-a").resultCards());assertFalse(((android.widget.CheckBox)root.findViewWithTag("sync-check:answer")).isChecked());root.findViewWithTag("sync-check:answer").performClick();ShadowDialog.getLatestDialog().dismiss();new AccountStore(activity).remove("0|account-b");call("render");assertNull(find(activity.getWindow().getDecorView(),"同步设置"));
    }
    @Test public void synchronizationCheckinToastModeDoesNotOpenProgressCard()throws Exception{login();saveSecondAccount(true);new SyncSettings(activity,new AccountStore(activity),0,"0|account-a").resultCards(false);RainApi owner=(RainApi)get("api");owner.checkin("123456");set("tab",1);set("dynamicView",2);call("render");click(activity.getWindow().getDecorView(),"同步签到");waitFor(()->"本次同步签到成功 1 条，失败 0 条".equals(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()));assertNull(get("batchCheckin"));assertTrue(ShadowDialog.getShownDialogs().stream().noneMatch(Dialog::isShowing));assertSame(owner,get("api"));}
}
