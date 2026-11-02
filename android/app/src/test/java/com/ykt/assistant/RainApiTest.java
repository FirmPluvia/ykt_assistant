package com.ykt.assistant;

import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RainApiTest {
    static class Store implements RainApi.CookieStorage { String data="[]"; public String read() { return data; } public void write(String s) { data=s; } }
    static class Lessons implements RainApi.LessonStorage {
        final java.util.Map<String,JSONObject> records=new java.util.concurrent.ConcurrentHashMap<>();
        private String key(String h,String u,String l) { return h+"|"+u+"|"+l; }
        public JSONObject read(String h,String u,String l) throws Exception { JSONObject v=records.get(key(h,u,l)); return v==null?null:new JSONObject(v.toString()); }
        public void write(String h,String u,String l,JSONObject data) throws Exception { records.put(key(h,u,l),new JSONObject(data.toString())); }
        public void remove(String h,String u,String l) { records.remove(key(h,u,l)); }
    }
    private MockResponse response(String data) { return new MockResponse().setHeader("Content-Type","application/json").setBody(data); }
    private void profile(MockWebServer server) { server.enqueue(response("{\"data\":{\"user_profile\":{\"user_id\":9007199254740993,\"name\":\"同学\"}}}")); }
    private void join(MockWebServer server) { server.enqueue(response("{\"code\":0,\"data\":{\"lessonToken\":\"ws-token\"}}").setHeader("set-auth","http-token")); }
    @Test public void loginCookiePersistedAndBearerSeparated() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); Store store=new Store(); RainApi api=new RainApi(s.url("/"),store);
            s.enqueue(response("{\"code\":0,\"data\":{\"token\":\"qr\",\"qrImage\":\"https://www.yuketang.cn/q.png\"}}").addHeader("Set-Cookie","csrftoken=csrf; Path=/").addHeader("Set-Cookie","sessionid=temporary; Path=/"));
            api.qr(); s.takeRequest();
            s.enqueue(response("{\"code\":0,\"data\":{}}").addHeader("Set-Cookie","sessionid=account; Path=/")); profile(s); api.pollQr("qr");
            RecordedRequest poll=s.takeRequest(),profile=s.takeRequest(); assertTrue(poll.getHeader("Cookie").contains("sessionid=temporary")); assertEquals("csrf",poll.getHeader("x-csrftoken")); assertNull(poll.getHeader("x-uid")); assertTrue(profile.getHeader("Cookie").contains("sessionid=account"));
            assertEquals("9007199254740993",api.user().optString("user_id"));
            join(s); api.checkin("lesson"); RecordedRequest check=s.takeRequest(); assertEquals("9007199254740993",check.getHeader("x-uid")); assertNull(check.getHeader("Authorization"));
            JSONObject b=new JSONObject(check.getBody().readUtf8()); assertEquals("lesson",b.getString("lessonId")); assertEquals(21,b.getInt("source")); assertTrue(b.getBoolean("joinIfNotIn"));
            s.enqueue(response("{\"signed\":true}")); api.locationStatus(); assertEquals("Bearer http-token",s.takeRequest().getHeader("Authorization"));
            RainApi restored=new RainApi(s.url("/"),store); assertTrue(restored.hasCookies());
            try { restored.locationStatus(); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); }
            restored.close(); api.close();
        }
    }
    @Test public void scanRequiredFailureDoesNotEnterClassroom() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi api=new RainApi(s.url("/"),new Store());
            s.enqueue(response("{\"code\":50070}"));
            try { api.checkin("selected"); fail(); } catch(RainApi.Failure e) { assertEquals(50070,e.code); assertFalse(e.getMessage().contains("链接")); }
            assertEquals("/api/v3/lesson/checkin",s.takeRequest().getPath()); assertNull(api.lesson()); assertEquals(1,s.getRequestCount()); api.close();
        }
    }
    @Test public void businessErrorsAndMissingClassroomTokensAreNotSuccess() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi api=new RainApi(s.url("/"),new Store());
            for(int code:new int[]{50001,50070,51203}) { s.enqueue(response("{\"code\":"+code+"}")); try { api.checkin("id"); fail(); } catch(RainApi.Failure e) { assertEquals(code,e.code); } }
            s.enqueue(response("{\"code\":0,\"data\":{\"lessonToken\":\"only-ws\"}}")); try { api.checkin("id"); fail(); } catch(RainApi.Failure e) { assertEquals(502,e.code); } assertNull(api.lesson());
            s.enqueue(new MockResponse().setResponseCode(302).setHeader("Location","https://example.com/")); try { api.profile(); fail(); } catch(RainApi.Failure e) { assertEquals(302,e.code); } api.close();
        }
    }
    @Test public void locationUsesActualInputThenQueriesConfirmation() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi api=new RainApi(s.url("/"),new Store()); join(s); api.checkin("lesson"); s.takeRequest();
            s.enqueue(response("{\"needLocationSignIn\":true,\"signed\":false}")); s.enqueue(response("{\"code\":0}")); s.enqueue(response("{\"signed\":true}")); assertTrue(api.submitLocation(31.234,121.456).getBoolean("signed"));
            assertEquals("/api/v3/lesson/location-checkin/eligibility",s.takeRequest().getPath()); RecordedRequest submit=s.takeRequest(); JSONObject b=new JSONObject(submit.getBody().readUtf8()); assertEquals("/api/v3/lesson/location-checkin/submit",submit.getPath()); assertEquals(31.234,b.getDouble("lat"),0); assertEquals("lesson",b.getString("lessonId")); assertEquals(54,b.getInt("source")); assertEquals("/api/v3/lesson/location-checkin/eligibility",s.takeRequest().getPath()); api.close();
        }
    }
    @Test public void directWebSocketUsesLessonTokenAndDiscoversPresentation() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi api=new RainApi(s.url("/"),new Store()); profile(s); api.profile(); s.takeRequest(); join(s); api.checkin("lesson"); s.takeRequest();
            CountDownLatch received=new CountDownLatch(1); List<JSONObject> hello=new ArrayList<>();
            s.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() { @Override public void onMessage(WebSocket ws,String text) { try { JSONObject j=new JSONObject(text); if(j.optString("op").equals("hello")) { hello.add(j); ws.send("{\"op\":\"hello\",\"presentation\":\"pres&1\",\"slideindex\":3,\"auth\":\"never-forward\"}"); } } catch(Exception ignored) {} } }));
            List<JSONObject> events=new ArrayList<>(); api.connect(e -> { if(e.has("presentationId")) { events.add(e); received.countDown(); } });
            assertTrue(received.await(10,TimeUnit.SECONDS)); assertEquals("ws-token",hello.get(0).getString("auth")); assertEquals("lesson",hello.get(0).getString("lessonid")); assertEquals("pres&1",api.presentation()); assertEquals(3,api.livePage()); assertFalse(events.toString().contains("never-forward"));
            RecordedRequest upgrade=s.takeRequest(); assertEquals("/wsapp/",upgrade.getPath()); assertNull(upgrade.getHeader("Authorization"));
            s.enqueue(response("{\"data\":{\"title\":\"课件\",\"slides\":[]}}")); api.fetchPresentation(); RecordedRequest fetch=s.takeRequest(); assertTrue(fetch.getPath().contains("presentation_id=pres%261")); assertEquals("Bearer http-token",fetch.getHeader("Authorization")); api.close();
        }
    }
    @Test public void assetsAreRestrictedToOfficialHttpsHosts() {
        for(String u:new String[]{"http://www.yuketang.cn/a","https://127.0.0.1/a","https://yuketang.cn.attacker.example/a","https://www.yuketang.cn:8443/a","https://user:pass@www.yuketang.cn/a"}) assertFalse(RainApi.allowedAsset(HttpUrl.parse(u)));
        assertTrue(RainApi.allowedAsset(HttpUrl.get("https://qn-scd1.yuketang.cn/a.jpg")));
    }
    @Test public void cookiesDoNotCrossAccounts() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi a=new RainApi(s.url("/"),new Store()),b=new RainApi(s.url("/"),new Store());
            s.enqueue(response("{\"code\":0,\"data\":{\"token\":\"q\",\"qrImage\":\"x\"}}").setHeader("Set-Cookie","sessionid=private; Path=/")); a.qr(); s.takeRequest(); profile(s); b.profile(); assertNull(s.takeRequest().getHeader("Cookie")); a.close(); b.close();
        }
    }
    @Test public void officialWechatQrImageIsAllowedOnlyForQrAndNeverReceivesCookies() throws Exception {
        Store store=new Store();
        store.data="[{\"raw\":\"sessionid=private; domain=yuketang.cn; path=/\",\"expires\":253402300799999}]";
        List<okhttp3.Request> seen=new ArrayList<>();
        byte[] image=new byte[]{(byte)137,80,78,71};
        okhttp3.OkHttpClient.Builder transport=new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
            seen.add(chain.request());
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                .body(okhttp3.ResponseBody.create(image,okhttp3.MediaType.get("image/png"))).addHeader("Content-Type","image/png").build();
        });
        RainApi api=new RainApi(HttpUrl.get("https://www.yuketang.cn/"),store,transport);
        assertArrayEquals(image,api.qrImage("https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=example"));
        assertNull(seen.get(0).header("Cookie")); assertNull(seen.get(0).header("Authorization"));
        try { api.slideImage(RainApi.object("cover","https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=example")); fail(); }
        catch(RainApi.Failure e) { assertEquals(400,e.code); }
        assertEquals(1,seen.size()); api.close();
    }
    @Test public void qrRedirectCannotEscapeSupportedHttpsImageHosts() throws Exception {
        List<okhttp3.Request> seen=new ArrayList<>();
        okhttp3.OkHttpClient.Builder transport=new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
            seen.add(chain.request());
            return new okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(302).message("Found")
                .body(okhttp3.ResponseBody.create(new byte[0],null)).addHeader("Location","https://untrusted.example/qr.png").build();
        });
        RainApi api=new RainApi(HttpUrl.get("https://www.yuketang.cn/"),new Store(),transport);
        try { api.qrImage("https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=example"); fail(); }
        catch(RainApi.Failure e) { assertEquals(400,e.code); }
        assertEquals(1,seen.size()); api.close();
    }
    @Test public void qrCanUseOfficialContentWithoutRemoteImageAndRetainsItsToken() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store());
            server.enqueue(response("{\"code\":0,\"data\":{\"token\":\"separate-login-token\",\"qrContent\":\"http://weixin.qq.com/q/example\"}}"));
            JSONObject result=api.qr();
            assertEquals("separate-login-token",result.getString("token"));
            assertEquals("http://weixin.qq.com/q/example",RainApi.qrContent(result));
            assertEquals(1,server.getRequestCount()); api.close();
        }
    }
    @Test public void qrPolicyDoesNotAllowOtherWechatPathsHttpPortsOrCredentialUrls() {
        assertTrue(RainApi.allowedQrImage(HttpUrl.get("https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=example")));
        for(String url:new String[]{"https://mp.weixin.qq.com/other","https://mp.weixin.qq.com.attacker.example/cgi-bin/showqrcode","http://mp.weixin.qq.com/cgi-bin/showqrcode","https://mp.weixin.qq.com:8443/cgi-bin/showqrcode","https://user:password@mp.weixin.qq.com/cgi-bin/showqrcode"}) assertFalse(RainApi.allowedQrImage(HttpUrl.get(url)));
    }

    @Test public void cancelDoesNotWaitForBlockedCookiePersistence() throws Exception {
        CountDownLatch saving=new CountDownLatch(1),release=new CountDownLatch(1);
        java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try(MockWebServer server=new MockWebServer()) {
            server.start();
            RainApi.CookieStorage slow=new RainApi.CookieStorage() {
                public String read() { return "[]"; }
                public void write(String value) throws Exception { saving.countDown(); if(!release.await(10,TimeUnit.SECONDS)) throw new Exception("test persistence timeout"); }
            };
            RainApi api=new RainApi(server.url("/"),slow);
            server.enqueue(response("{\"data\":{\"user_profile\":{\"user_id\":\"old-account\"}}}").setHeader("Set-Cookie","sessionid=old; Path=/"));
            java.util.concurrent.Future<?> request=pool.submit(() -> { try { api.profile(); } catch(Exception ignored) {} });
            assertTrue(saving.await(5,TimeUnit.SECONDS));
            try { pool.submit(api::cancel).get(1,TimeUnit.SECONDS); assertTrue(api.isClosed()); assertNull(api.user()); assertNull(api.lesson()); assertFalse(api.hasCookies()); }
            finally { release.countDown(); }
            request.get(5,TimeUnit.SECONDS); assertNull(api.user()); api.close();
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test public void closedAccountCannotMakeApiOrAssetRequestsOrRememberLogin() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store()); api.cancel();
            try { api.profile(); fail(); } catch(RainApi.Failure e) { assertEquals(401,e.code); }
            try { api.qrImage("https://mp.weixin.qq.com/cgi-bin/showqrcode?ticket=test"); fail(); } catch(RainApi.Failure e) { assertEquals(401,e.code); }
            try { api.rememberLogin(); fail(); } catch(RainApi.Failure e) { assertEquals(401,e.code); }
            assertEquals(0,server.getRequestCount()); api.close();
        }
    }

    @Test public void historyUsesCapturedBodyAndAccountAuthWithoutReenteringOrChangingLiveLesson() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store());
            join(server); api.checkin("live-lesson"); server.takeRequest();
            server.enqueue(response("{\"code\":0,\"data\":[]}")); api.activities();
            RecordedRequest activities=server.takeRequest(); assertEquals("POST",activities.getMethod()); assertEquals("/api/v3/activities/received/",activities.getPath()); assertNull(activities.getHeader("Authorization"));
            JSONObject body=new JSONObject(activities.getBody().readUtf8()); assertEquals(10,body.getInt("size")); assertEquals(0,body.getJSONArray("type").length()); assertTrue(body.isNull("beginTime")); assertTrue(body.isNull("endTime"));
            server.enqueue(response("{\"code\":0,\"data\":{\"lessonId\":\"history&lesson\",\"presentationIds\":[\"pres\"]}}"));
            server.enqueue(response("{\"code\":0,\"data\":{\"detail\":{\"valid\":1,\"checkInTime\":1767225600000}}}"));
            server.enqueue(response("{\"code\":0,\"data\":{\"timelineList\":[]}}"));
            api.lessonInfo("history&lesson"); api.lessonDetail("history&lesson"); api.lessonReview("history&lesson");
            for(String endpoint:new String[]{"lesson-info","detail","review"}) {
                RecordedRequest request=server.takeRequest(); assertEquals("GET",request.getMethod()); assertEquals("/api/v3/classroom-report/student/"+endpoint,request.getRequestUrl().encodedPath()); assertEquals("history&lesson",request.getRequestUrl().queryParameter("lesson_id")); assertTrue(Long.parseLong(request.getRequestUrl().queryParameter("front_time"))>1700000000000L); assertNull(request.getHeader("Authorization"));
            }
            assertEquals("live-lesson",api.lesson()); assertEquals(5,server.getRequestCount()); api.close();
        }
    }
    @Test public void originalDeckUsesOfficialHistoryRouteAndStudentVisiblePagesWithoutLiveBearer() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store()); join(server); api.checkin("live-lesson"); server.takeRequest();
            server.enqueue(response("{\"data\":{\"presentationId\":\"original&1\",\"slideList\":[{\"id\":\"unseen-page\",\"visible\":true},{\"id\":\"hidden\",\"visible\":false},{\"id\":\"not-open\"},{\"id\":\"last-page\",\"visible\":true}]}}"));
            JSONObject deck=api.originalPresentation("history&lesson","original&1"); assertEquals(2,deck.getJSONArray("slides").length()); assertEquals("unseen-page",deck.getJSONArray("slides").getJSONObject(0).getString("id")); assertEquals("last-page",deck.getJSONArray("slides").getJSONObject(1).getString("id"));
            RecordedRequest r=server.takeRequest(); assertEquals("GET",r.getMethod()); assertEquals("/api/v3/classroom-report/student/ppt",r.getRequestUrl().encodedPath()); assertEquals("original&1",r.getRequestUrl().queryParameter("presentationId")); assertEquals("history&lesson",r.getRequestUrl().queryParameter("lesson_id")); assertNull(r.getRequestUrl().queryParameter("presentation_id")); assertNull(r.getHeader("Authorization"));
            assertEquals("live-lesson",api.lesson()); assertEquals(2,server.getRequestCount()); api.close();
        }
    }
    @Test public void originalDeckPermissionFailureDoesNotSignInOrFallBackToReview() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store()); server.enqueue(new MockResponse().setResponseCode(403));
            try { api.originalPresentation("lesson","locked"); fail(); } catch(RainApi.Failure e) { assertEquals(403,e.code); assertTrue(e.getMessage().contains("课件")); assertFalse(e.getMessage().contains("登录已失效")); }
            assertEquals(1,server.getRequestCount()); assertNull(api.lesson()); api.close();
        }
    }
    @Test public void originalDeckRejectsMalformedOrMismatchedHistoryData() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store());
            for(String body:new String[]{"{\"slides\":[]}","{\"lessonId\":\"another\",\"slideList\":[]}","{\"presentationId\":\"another\",\"slideList\":[]}","{\"slideList\":[null]}"}) {
                server.enqueue(response("{\"data\":"+body+"}")); try { api.originalPresentation("lesson","pres"); fail(); } catch(RainApi.Failure e) { assertTrue(e.code==409||e.code==502); }
            }
            api.close();
        }
    }
    @Test public void originalDeckWithNoStudentVisiblePagesHasClearError() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store()); server.enqueue(response("{\"data\":{\"slideList\":[{\"visible\":false}]}}"));
            try { api.originalPresentation("lesson","pres"); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); assertTrue(e.getMessage().contains("开放")); } api.close();
        }
    }
    @Test public void historyLessonResponseMustMatchRequestedLesson() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store()); server.enqueue(response("{\"data\":{\"lessonId\":\"different\"}}"));
            try { api.lessonInfo("selected"); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); } api.close();
        }
    }
    @Test public void malformedActivityResponseIsNotAnEmptyFeed() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store()); server.enqueue(response("{\"code\":0,\"data\":{}}"));
            try { api.activities(); fail(); } catch(RainApi.Failure e) { assertEquals(502,e.code); } api.close();
        }
    }

    @Test public void paginationSendsOpaqueSearchAfterAsStringAndOmitsItOnRefresh() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); RainApi api=new RainApi(server.url("/"),new Store()); String cursor="[1760000000000,\"opaque-cursor\"]";
            for(int i=0;i<3;i++) server.enqueue(response("{\"code\":0,\"data\":[]}"));
            api.activities(); api.activities(cursor); api.activities();
            assertFalse(new JSONObject(server.takeRequest().getBody().readUtf8()).has("searchAfter"));
            JSONObject second=new JSONObject(server.takeRequest().getBody().readUtf8()); assertTrue(second.get("searchAfter") instanceof String); assertEquals(cursor,second.getString("searchAfter")); assertEquals(10,second.getInt("size")); assertEquals(0,second.getJSONArray("type").length());
            assertFalse(new JSONObject(server.takeRequest().getBody().readUtf8()).has("searchAfter")); api.close();
        }
    }
    @Test public void cancellingAnExportStopsItsImageCallWithoutClosingTheAccount() throws Exception {
        CountDownLatch started=new CountDownLatch(1); java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.BooleanSupplier operation=cancelled::get; java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(MockWebServer server=new MockWebServer()) {
            server.start(); okhttp3.OkHttpClient.Builder transport=new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> {
                if(chain.request().url().host().equals("fixture.yuketang.cn")) {
                    started.countDown(); long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    while(!chain.call().isCanceled()&&System.nanoTime()<deadline) try { Thread.sleep(10); } catch(InterruptedException e) { throw new java.io.InterruptedIOException(); }
                    throw new java.io.IOException("cancelled export image");
                }
                return chain.proceed(chain.request());
            });
            RainApi api=new RainApi(server.url("/"),new Store(),transport);
            java.util.concurrent.Future<Boolean> task=worker.submit(() -> { try { api.slideImage(RainApi.object("cover","https://fixture.yuketang.cn/image.png"),operation); return false; } catch(Exception expected) { return true; } });
            assertTrue(started.await(5,TimeUnit.SECONDS)); cancelled.set(true); api.cancelOperation(operation); assertTrue(task.get(2,TimeUnit.SECONDS)); assertFalse(api.isClosed());
            profile(server); assertNotNull(api.profile()); assertEquals("/v/course_meta/user_info",server.takeRequest().getPath()); api.close();
        } finally { worker.shutdownNow(); }
    }
    @Test public void savedCredentialsRestoreWithoutCheckinAndAuthenticateHttpAndSocket() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); Lessons lessons=new Lessons(); RainApi first=new RainApi(server.url("/"),new Store()); first.configure(lessons); profile(server); first.profile(); server.takeRequest(); join(server); first.checkin("remembered"); server.takeRequest(); first.close();
            RainApi second=new RainApi(server.url("/"),new Store()); second.configure(lessons); profile(server); second.profile(); server.takeRequest(); int before=server.getRequestCount(); assertTrue(second.restoreLesson("remembered")); assertEquals(before,server.getRequestCount());
            server.enqueue(response("{\"signed\":true}")); second.locationStatus(); assertEquals("Bearer http-token",server.takeRequest().getHeader("Authorization"));
            CountDownLatch received=new CountDownLatch(1); List<JSONObject> hello=new ArrayList<>(); server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() { @Override public void onMessage(WebSocket ws,String value) { try { hello.add(new JSONObject(value)); received.countDown(); } catch(Exception e) { throw new AssertionError(e); } } })); second.connect(e -> {}); assertTrue(received.await(5,TimeUnit.SECONDS)); assertEquals("ws-token",hello.get(0).getString("auth")); assertEquals("remembered",hello.get(0).getString("lessonid")); second.close();
        }
    }
    @Test public void rejectedCachedCredentialsAreRemovedWithoutSigningOutAccount() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); Lessons lessons=new Lessons(); RainApi api=new RainApi(server.url("/"),new Store()); api.configure(lessons); profile(server); api.profile(); server.takeRequest(); lessons.write(api.host(),api.user().optString("user_id"),"lesson",RainApi.object("bearer","stale","lessonToken","stale")); assertTrue(api.restoreLesson("lesson"));
            server.enqueue(new MockResponse().setResponseCode(401)); try { api.locationStatus(); fail(); } catch(RainApi.Failure e) { assertEquals(401,e.code); } assertNull(api.lesson()); assertNotNull(api.user()); assertFalse(api.restoreLesson("lesson")); assertTrue(lessons.records.isEmpty()); api.close();
        }
    }
    @Test public void delayedOldCredentialRejectionCannotClearNewClassroomCredentials() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); Lessons cache=new Lessons(); RainApi api=new RainApi(server.url("/"),new Store()); api.configure(cache); profile(server); api.profile(); server.takeRequest(); join(server); api.checkin("old"); server.takeRequest();
            CountDownLatch oldRequested=new CountDownLatch(1),release=new CountDownLatch(1); java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
            server.setDispatcher(new okhttp3.mockwebserver.Dispatcher() { @Override public MockResponse dispatch(RecordedRequest r) {
                if(r.getRequestUrl().encodedPath().endsWith("eligibility")) { oldRequested.countDown(); try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } return new MockResponse().setResponseCode(401); }
                return response("{\"data\":{\"lessonToken\":\"new-ws\"}}").setHeader("set-auth","new-http");
            } });
            try {
                java.util.concurrent.Future<Integer> result=worker.submit(() -> { try { api.locationStatus(); return 0; } catch(RainApi.Failure e) { return e.code; } }); assertTrue(oldRequested.await(5,TimeUnit.SECONDS)); api.checkin("new"); release.countDown(); assertEquals(Integer.valueOf(401),result.get(5,TimeUnit.SECONDS)); assertEquals("new",api.lesson()); assertNotNull(cache.read(api.host(),api.user().optString("user_id"),"new")); assertNull(cache.read(api.host(),api.user().optString("user_id"),"old"));
            } finally { release.countDown(); worker.shutdownNow(); api.close(); }
        }
    }
    @Test public void customUserAgentAppliesToHttpAssetsAndNextWebSocketHandshake() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.start(); List<String> assetUa=new ArrayList<>(); okhttp3.OkHttpClient.Builder transport=new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> { if(chain.request().url().host().equals("fixture.yuketang.cn")) { assetUa.add(chain.request().header("User-Agent")); return new Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK").header("Content-Type","image/png").body(okhttp3.ResponseBody.create(new byte[]{1,2,3},okhttp3.MediaType.get("image/png"))).build(); } return chain.proceed(chain.request()); });
            RainApi api=new RainApi(server.url("/"),new Store(),transport); api.setUserAgent("StudyClient/1"); api.setDeviceInfo("HONOR","17"); profile(server); api.profile(); RecordedRequest profile=server.takeRequest(); assertEquals("StudyClient/1",profile.getHeader("User-Agent")); assertEquals("HONOR",profile.getHeader("brand")); assertEquals("17",profile.getHeader("systemversion"));
            api.setUserAgent("StudyClient/2"); api.qrImage("https://fixture.yuketang.cn/q.png"); assertEquals("StudyClient/2",assetUa.get(0)); join(server); api.checkin("lesson"); assertEquals("StudyClient/2",server.takeRequest().getHeader("User-Agent")); server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener(){})); api.connect(e -> {}); RecordedRequest socket=server.takeRequest(5,TimeUnit.SECONDS); assertNotNull(socket); assertEquals("/wsapp/",socket.getPath()); assertEquals("StudyClient/2",socket.getHeader("User-Agent")); api.close();
        }
    }

}
