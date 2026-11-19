package com.ykt.assistant;

import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import okhttp3.mockwebserver.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MultiAccountCheckinTest {
    private MockWebServer server; private AccountStore store; private RainApiTest.Lessons lessons; private List<String> signed;
    private boolean denyB,wrongIdentity; private CountDownLatch entered,release,checkinEntered,releaseCheckin;
    @Before public void setup() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("saved_accounts",0).edit().clear().commit(); store=new AccountStore(RuntimeEnvironment.getApplication()); lessons=new RainApiTest.Lessons(); signed=new CopyOnWriteArrayList<>(); server=new MockWebServer(); server.setDispatcher(new Dispatcher() { @Override public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            String cookie=request.getHeader("Cookie"),uid=cookie!=null&&cookie.contains("sessionid=b")?"b":"a"; String path=request.getRequestUrl().encodedPath();
            if(path.equals("/v/course_meta/user_info")) { if(entered!=null) { entered.countDown(); release.await(5,TimeUnit.SECONDS); } return json("{\"data\":{\"user_profile\":{\"user_id\":\""+(wrongIdentity?"unknown":uid)+"\"}}}"); }
            if(path.equals("/api/v3/lesson/checkin")) { assertEquals(uid,request.getHeader("x-uid")); assertNull(request.getHeader("Authorization")); signed.add(uid); if(checkinEntered!=null&&uid.equals("a")) { checkinEntered.countDown(); releaseCheckin.await(5,TimeUnit.SECONDS); } if(denyB&&uid.equals("b")) return new MockResponse().setResponseCode(403); return json("{\"data\":{\"lessonToken\":\"ws-"+uid+"\"}}").setHeader("set-auth","http-"+uid); }
            return new MockResponse().setResponseCode(404);
        } }); server.start(); add(0,"a"); add(0,"b");
    }
    private MockResponse json(String value) { return new MockResponse().setHeader("Content-Type","application/json").setBody(value); }
    private void add(int service,String uid) throws Exception { store.remember(service,RainApi.object("user_id",uid,"name",uid),new JSONArray().put(RainApi.object("raw","sessionid="+uid+"; Path=/","expires",Long.MAX_VALUE)).toString()); }
    private RainApi open(AccountStore.Account account) throws Exception { LoginCookies.Memory cookies=new LoginCookies.Memory(); cookies.write(account.cookies); RainApi api=new RainApi(server.url("/"),cookies); api.configure(lessons); return api; }
    @After public void cleanup() throws Exception { if(release!=null) release.countDown(); if(releaseCheckin!=null) releaseCheckin.countDown(); server.close(); }
    @Test public void selectedAccountsUseOwnSessionsAndPersistSeparateClassroomCredentials() throws Exception {
        MultiAccountCheckin job=new MultiAccountCheckin(store,this::open); List<MultiAccountCheckin.Result> result=job.run(store.list(),0,"123456",(a,s)->{}); assertEquals(2,result.size()); assertTrue(result.stream().allMatch(r->r.success)); assertEquals(new HashSet<>(Arrays.asList("a","b")),new HashSet<>(signed)); assertEquals("http-a",lessons.read(server.url("/").host(),"a","123456").getString("bearer")); assertEquals("ws-b",lessons.read(server.url("/").host(),"b","123456").getString("lessonToken")); assertEquals("",store.active());
    }
    @Test public void oneRejectedAccountDoesNotChangeAnotherAccountsSuccess() throws Exception {
        denyB=true; List<MultiAccountCheckin.Result> result=new MultiAccountCheckin(store,this::open).run(store.list(),0,"lesson",(a,s)->{}); assertEquals(1,result.stream().filter(r->r.success).count()); assertNotNull(lessons.read(server.url("/").host(),"a","lesson")); assertNull(lessons.read(server.url("/").host(),"b","lesson"));
    }
    @Test public void cachedCredentialsAreReusedWithoutSendingAnotherCheckin() throws Exception {
        lessons.write(server.url("/").host(),"b","lesson",RainApi.object("bearer","saved-http","lessonToken","saved-ws")); List<MultiAccountCheckin.Result> result=new MultiAccountCheckin(store,this::open).run(Collections.singletonList(store.get("0|b")),0,"lesson",(a,s)->{}); assertEquals("已复用本机课堂凭证",result.get(0).message); assertTrue(signed.isEmpty()); assertEquals(1,server.getRequestCount());
    }
    @Test public void identityMismatchAndOtherServerCannotSubmitAttendance() throws Exception {
        wrongIdentity=true; add(1,"c"); List<MultiAccountCheckin.Result> result=new MultiAccountCheckin(store,this::open).run(store.list(),0,"lesson",(a,s)->{}); assertTrue(result.stream().noneMatch(r->r.success)); assertTrue(signed.isEmpty()); assertEquals(2,server.getRequestCount());
    }
    @Test public void nextAccountCannotStartUntilPreviousAccountsCheckinResponseCompletes() throws Exception {
        checkinEntered=new CountDownLatch(1); releaseCheckin=new CountDownLatch(1); MultiAccountCheckin job=new MultiAccountCheckin(store,this::open); ExecutorService worker=Executors.newSingleThreadExecutor();
        try { Future<List<MultiAccountCheckin.Result>> future=worker.submit(()->job.run(Arrays.asList(store.get("0|a"),store.get("0|b")),0,"lesson",(a,s)->{})); assertTrue(checkinEntered.await(3,TimeUnit.SECONDS)); RecordedRequest profileA=server.takeRequest(3,TimeUnit.SECONDS),checkinA=server.takeRequest(3,TimeUnit.SECONDS); assertEquals("/v/course_meta/user_info",profileA.getPath()); assertEquals("/api/v3/lesson/checkin",checkinA.getPath()); assertTrue(profileA.getHeader("Cookie").contains("sessionid=a")); assertNull(server.takeRequest(200,TimeUnit.MILLISECONDS)); assertEquals(2,server.getRequestCount()); releaseCheckin.countDown(); assertEquals(2,future.get(3,TimeUnit.SECONDS).size()); RecordedRequest profileB=server.takeRequest(3,TimeUnit.SECONDS),checkinB=server.takeRequest(3,TimeUnit.SECONDS); assertEquals("/v/course_meta/user_info",profileB.getPath()); assertEquals("/api/v3/lesson/checkin",checkinB.getPath()); assertTrue(profileB.getHeader("Cookie").contains("sessionid=b")); assertEquals("b",checkinB.getHeader("x-uid")); } finally { job.close(); releaseCheckin.countDown(); worker.shutdownNow(); }
    }
    @Test public void cancellingDuringValidationStopsLaterAccountsAndDoesNotSubmit() throws Exception {
        entered=new CountDownLatch(1); release=new CountDownLatch(1); MultiAccountCheckin job=new MultiAccountCheckin(store,this::open); ExecutorService worker=Executors.newSingleThreadExecutor(); try { Future<?> future=worker.submit(()->job.run(store.list(),0,"lesson",(a,s)->{})); assertTrue(entered.await(3,TimeUnit.SECONDS)); job.close(); release.countDown(); future.get(3,TimeUnit.SECONDS); assertTrue(signed.isEmpty()); assertEquals(1,server.getRequestCount()); } finally { release.countDown(); worker.shutdownNow(); }
    }
}
