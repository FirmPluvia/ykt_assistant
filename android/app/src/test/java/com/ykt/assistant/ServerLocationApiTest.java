package com.ykt.assistant;

import org.json.JSONObject;
import org.junit.Test;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class ServerLocationApiTest {
    private final String pending="{\"data\":{\"needLocationSignIn\":true,\"signed\":false,\"session\":{\"id\":1,\"centerLat\":31.25,\"centerLng\":121.5},\"address\":\"教学楼\"}}";
    private MockResponse json(String raw) { return new MockResponse().setBody(raw); }
    private RainApi enter(MockWebServer server) throws Exception { server.start(); RainApi api=new RainApi(server.url("/"),new RainApiTest.Store()); server.enqueue(json("{\"data\":{\"lessonToken\":\"ws\"}}").setHeader("set-auth","http")); api.checkin("lesson"); server.takeRequest(); return api; }
    private LocationCheckin plan(RainApi api,MockWebServer server,String raw) throws Exception { server.enqueue(json(raw)); LocationCheckin info=api.locationInfo(); server.takeRequest(); return info; }
    private void event(RainApi api,String op) throws Exception { Method m=RainApi.class.getDeclaredMethod("locationEvent",String.class,JSONObject.class); m.setAccessible(true); m.invoke(api,"lesson",RainApi.object("op",op,"centerLat",22.5,"centerLng",113.5,"address","事件位置")); }
    @Test public void exactServerCoordinateAndAddressAreSubmittedThenConfirmed() throws Exception {
        try(MockWebServer server=new MockWebServer(); RainApi api=enter(server)) { LocationCheckin info=plan(api,server,pending); server.enqueue(json(pending)); server.enqueue(json("{\"code\":0}")); server.enqueue(json("{\"data\":{\"signed\":true}}")); assertTrue(api.submitServerLocation(info).signed); server.takeRequest(); RecordedRequest post=server.takeRequest(); JSONObject body=new JSONObject(post.getBody().readUtf8()); assertEquals(31.25,body.getDouble("lat"),0); assertEquals(121.5,body.getDouble("lng"),0); assertEquals("教学楼",body.getString("address")); assertEquals("lesson",body.getString("lessonId")); assertEquals("Bearer http",post.getHeader("Authorization")); assertEquals("GET",server.takeRequest().getMethod()); }
    }
    @Test public void becomingSignedBeforeSubmissionSkipsPost() throws Exception {
        try(MockWebServer server=new MockWebServer(); RainApi api=enter(server)) { LocationCheckin info=plan(api,server,pending); server.enqueue(json("{\"signed\":true}")); assertTrue(api.submitServerLocation(info).signed); assertEquals("GET",server.takeRequest().getMethod()); assertEquals(3,server.getRequestCount()); }
    }
    @Test public void changedPointOrEndedRoundCannotBeSubmitted() throws Exception {
        try(MockWebServer server=new MockWebServer(); RainApi api=enter(server)) { LocationCheckin info=plan(api,server,pending); for(String state:new String[]{pending.replace("31.25","32.25"),"{\"signed\":false,\"needLocationSignIn\":false}"}) { server.enqueue(json(state)); try { api.submitServerLocation(info); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); } assertEquals("GET",server.takeRequest().getMethod()); } assertEquals(4,server.getRequestCount()); }
    }
    @Test public void successfulPostWithoutConfirmationDoesNotGetPostedAgain() throws Exception {
        try(MockWebServer server=new MockWebServer(); RainApi api=enter(server)) { LocationCheckin info=plan(api,server,pending); server.enqueue(json(pending)); server.enqueue(json("{\"code\":0}")); server.enqueue(json(pending)); LocationCheckin result=api.submitServerLocation(info); assertFalse(result.signed); assertTrue(result.submitted); for(int i=0;i<3;i++) server.takeRequest(); server.enqueue(json(pending)); assertTrue(api.submitServerLocation(info).submitted); assertEquals("GET",server.takeRequest().getMethod()); assertEquals(6,server.getRequestCount()); }
    }
    @Test public void failedConfirmationQueryStillKeepsSuccessfulSubmissionGuard() throws Exception {
        try(MockWebServer server=new MockWebServer(); RainApi api=enter(server)) { LocationCheckin info=plan(api,server,pending); server.enqueue(json(pending)); server.enqueue(json("{\"code\":0}")); server.enqueue(new MockResponse().setResponseCode(500)); try { api.submitServerLocation(info); fail(); } catch(RainApi.Failure e) { assertEquals(500,e.code); } for(int i=0;i<3;i++) server.takeRequest(); server.enqueue(json(pending)); assertTrue(api.submitServerLocation(info).submitted); assertEquals("GET",server.takeRequest().getMethod()); }
    }
    @Test public void classroomEventIsFallbackAndEndEventRevokesIt() throws Exception {
        try(MockWebServer server=new MockWebServer(); RainApi api=enter(server)) { event(api,"locationcheckinext"); LocationCheckin info=plan(api,server,"{\"needLocationSignIn\":true,\"signed\":false}"); assertEquals(22.5,info.point.lat,0); assertEquals("事件位置",info.address); event(api,"locationcheckinend"); server.enqueue(json("{\"needLocationSignIn\":true}")); try { api.submitServerLocation(info); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); } assertEquals("GET",server.takeRequest().getMethod()); }
    }
    @Test public void oldRoundAndOldClassroomAreBothRejected() throws Exception {
        try(MockWebServer server=new MockWebServer(); RainApi api=enter(server)) { LocationCheckin info=plan(api,server,pending); event(api,"locationcheckinext"); server.enqueue(json(pending)); try { api.submitServerLocation(info); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); } server.takeRequest(); server.enqueue(json("{\"data\":{\"lessonToken\":\"next\"}}").setHeader("set-auth","new-http")); api.checkin("other"); server.takeRequest(); int before=server.getRequestCount(); try { api.submitServerLocation(info); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); } assertEquals(before,server.getRequestCount()); }
    }
    @Test public void businessFailureNeverBecomesSuccessfulSignIn() throws Exception {
        try(MockWebServer server=new MockWebServer(); RainApi api=enter(server)) { LocationCheckin info=plan(api,server,pending); server.enqueue(json(pending)); server.enqueue(json("{\"code\":45001,\"msg\":\"签到已结束\"}")); try { api.submitServerLocation(info); fail(); } catch(RainApi.Failure e) { assertEquals(45001,e.code); } server.takeRequest(); assertEquals("POST",server.takeRequest().getMethod()); assertEquals(4,server.getRequestCount()); }
    }
}
