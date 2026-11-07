package com.ykt.assistant;

import android.graphics.Bitmap;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class QrLoginControllerTest {
    private QrLoginController flow; private final List<RainApi> clients=new CopyOnWriteArrayList<>();
    private final List<QrLoginController.State> states=new CopyOnWriteArrayList<>();
    private volatile RainApi accepted; private volatile Bitmap latestImage; private volatile String message;
    private final QrLoginController.Listener listener=new QrLoginController.Listener() {
        @Override public void state(QrLoginController.State state,Bitmap bitmap,String text,RainApi owner) { states.add(state); latestImage=bitmap; message=text; }
        @Override public void signedIn(RainApi owner,JSONObject profile) { accepted=owner; }
    };
    private static Bitmap image(RainApi api,JSONObject raw) { return Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888); }
    private void await(BooleanSupplier condition) throws Exception { for(int i=0;i<250;i++) { ShadowLooper.idleMainLooper(); if(condition.getAsBoolean()) return; Thread.sleep(10); } fail("QR state timeout: "+states+" / "+message); }
    @After public void cleanup() { if(flow!=null) flow.close(); for(RainApi client:clients) client.cancel(); ShadowLooper.idleMainLooper(); }
    private class Fake extends RainApi {
        final AtomicInteger fetch=new AtomicInteger(),waits=new AtomicInteger(),profiles=new AtomicInteger(); int profileFailures; CountDownLatch waitRelease; boolean offline;
        Fake() throws Exception { super(HttpUrl.get("https://fixture.yuketang.cn/"),new RainApiTest.Store()); clients.add(this); }
        @Override public JSONObject qr() throws Exception { fetch.incrementAndGet(); if(offline) throw new java.io.IOException("simulated"); return object("token","private-token","qrContent","https://weixin.qq.com/q/fixture"); }
        @Override void awaitQrAuthorization(String token) throws Exception { waits.incrementAndGet(); if(waitRelease!=null) waitRelease.await(5,TimeUnit.SECONDS); }
        @Override public JSONObject profile() throws Exception { if(profiles.incrementAndGet()<=profileFailures) throw new RainApi.Failure("simulated profile error",500); return object("user_id","fixture"); }
    }
    @Test public void timeoutImmediatelyRemovesOldImageThenRenewsTokenAndCookieTogether() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); AtomicInteger tokens=new AtomicInteger(); CountDownLatch expireGate=new CountDownLatch(1),secondWait=new CountDownLatch(1),authorize=new CountDownLatch(1); List<String> matching=new CopyOnWriteArrayList<>();
            server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(okhttp3.mockwebserver.RecordedRequest r) {
                try {
                    if(r.getPath().endsWith("pre-info")) { int n=tokens.incrementAndGet(); return new MockResponse().setBody("{\"data\":{\"token\":\"token-"+n+"\",\"qrContent\":\"https://weixin.qq.com/q/round-"+n+"\"}}").addHeader("Set-Cookie","sessionid=round-"+n+"; Path=/").addHeader("Set-Cookie","csrftoken=csrf-"+n+"; Path=/"); }
                    if(r.getPath().equals("/api/v3/user/login")) { String token=new JSONObject(r.getBody().readUtf8()).getString("token"); String expected=token.endsWith("1")?"1":"2"; matching.add(token+"|"+r.getHeader("Cookie")+"|"+r.getHeader("x-csrftoken")); if(expected.equals("1")) return new MockResponse().setBody("{\"code\":50001}"); secondWait.countDown(); authorize.await(5,TimeUnit.SECONDS); return new MockResponse().setBody("{\"code\":0}").addHeader("Set-Cookie","sessionid=accepted; Path=/"); }
                    return new MockResponse().setBody("{\"data\":{\"user_profile\":{\"user_id\":\"fixture\"}}}");
                } catch(Exception e) { return new MockResponse().setResponseCode(500); }
            } });
            AtomicInteger pauses=new AtomicInteger(); flow=new QrLoginController((service,round) -> { RainApi api=new RainApi(server.url("/"),new RainApiTest.Store()); clients.add(api); return api; },listener,null,millis -> { if(pauses.incrementAndGet()==1) expireGate.await(5,TimeUnit.SECONDS); },(api,data) -> { int n=Integer.parseInt(data.getString("token").substring(6)); assertEquals("https://weixin.qq.com/q/round-"+n,data.getString("qrContent")); assertSame(clients.get(n-1),api); Bitmap image=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888); image.setPixel(0,0,0xff000000|n); return image; });
            try {
                flow.start(0); await(() -> states.contains(QrLoginController.State.EXPIRED)); assertNull(latestImage); assertNull(accepted); expireGate.countDown(); assertTrue(secondWait.await(3,TimeUnit.SECONDS)); await(() -> states.get(states.size()-1)==QrLoginController.State.READY); assertNotNull(latestImage); assertEquals(0xff000002,latestImage.getPixel(0,0)); assertEquals(2,clients.size()); assertTrue(clients.get(0).isClosed());
                assertTrue(matching.get(0).contains("sessionid=round-1")); assertTrue(matching.get(0).endsWith("csrf-1")); assertTrue(matching.get(1).contains("sessionid=round-2")); assertFalse(matching.get(1).contains("round-1")); authorize.countDown(); await(() -> accepted!=null); assertSame(clients.get(1),accepted); flow.close(); assertFalse(accepted.isClosed());
            } finally { expireGate.countDown(); authorize.countDown(); }
        }
    }
    @Test public void authorizedSessionRetriesProfileWithoutNewQrOrAuthorization() throws Exception {
        Fake client=new Fake(); client.profileFailures=2; flow=new QrLoginController((s,r)->client,listener,null,millis->{},QrLoginControllerTest::image); flow.start(0); await(()->accepted!=null); assertEquals(1,client.fetch.get()); assertEquals(1,client.waits.get()); assertEquals(3,client.profiles.get()); assertTrue(states.contains(QrLoginController.State.PROFILE_RETRY)); assertFalse(client.isClosed());
    }
    @Test public void exhaustedProfileCanRetryUsingExactlyTheSameSession() throws Exception {
        Fake client=new Fake(); client.profileFailures=3; flow=new QrLoginController((s,r)->client,listener,null,millis->{},QrLoginControllerTest::image); flow.start(0); await(()->states.contains(QrLoginController.State.PROFILE_FAILED)); assertNull(latestImage); assertFalse(client.isClosed()); flow.retryProfile(); await(()->accepted!=null); assertEquals(1,client.fetch.get()); assertEquals(1,client.waits.get()); assertEquals(4,client.profiles.get());
    }
    @Test public void closingWhileWaitingRejectsLateSuccessAndCancelsClient() throws Exception {
        Fake client=new Fake(); client.waitRelease=new CountDownLatch(1); flow=new QrLoginController((s,r)->client,listener,null,millis->{},QrLoginControllerTest::image); flow.start(0); await(()->client.waits.get()==1); flow.close(); client.waitRelease.countDown(); ShadowLooper.idleMainLooper(); assertNull(accepted); assertEquals(0,client.profiles.get()); assertTrue(client.isClosed());
    }
    @Test public void serviceSwitchCannotAcceptOldRound() throws Exception {
        Fake old=new Fake(),next=new Fake(); old.waitRelease=new CountDownLatch(1); flow=new QrLoginController((service,round)->service==0?old:next,listener,null,millis->{},QrLoginControllerTest::image); flow.start(0); await(()->old.waits.get()==1&&latestImage!=null); flow.start(1); assertNull(latestImage); old.waitRelease.countDown(); await(()->accepted!=null); assertSame(next,accepted); assertTrue(old.isClosed()); assertEquals(0,old.profiles.get());
    }
    @Test public void repeatedImmediateFailuresBackOffAndStopWithoutRequestStorm() throws Exception {
        AtomicInteger opened=new AtomicInteger(),pauses=new AtomicInteger(); flow=new QrLoginController((s,r)-> { opened.incrementAndGet(); Fake client=new Fake(); client.offline=true; return client; },listener,null,millis->pauses.incrementAndGet(),QrLoginControllerTest::image); flow.start(0); await(()->states.contains(QrLoginController.State.FAILED)); assertEquals(5,opened.get()); assertTrue(pauses.get()>=4); assertNull(latestImage); assertNull(accepted);
    }
}
