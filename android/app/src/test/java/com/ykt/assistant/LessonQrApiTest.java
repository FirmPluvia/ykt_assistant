package com.ykt.assistant;

import org.junit.Test;
import org.json.JSONObject;
import okhttp3.mockwebserver.*;
import static org.junit.Assert.*;

public class LessonQrApiTest {
    private static final String QR="https://www.yuketang.cn/api/v3/lesson/check-in/dynamic-qr-code?sig=a%2Fb%3D&time=1700000000";
    @Test public void dynamicQrPostsCompleteUrlWithOwnAccountCookieAndNoClassroomBearer() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); RainApi api=new RainApi(server.url("/"),new RainApiTest.Store()); api.importLoginCookies("sessionid=own-fixture; csrftoken=own-csrf"); server.enqueue(new MockResponse().setBody("{\"data\":{\"value\":\"1801803512884878080\"}}")); assertEquals("1801803512884878080",api.resolveLessonQr(LessonQr.parse(QR,0))); RecordedRequest request=server.takeRequest(); assertEquals("POST",request.getMethod()); assertEquals("/api/v3/app/scan",request.getPath()); assertEquals(QR,new JSONObject(request.getBody().readUtf8()).getString("url")); assertTrue(request.getHeader("Cookie").contains("sessionid=own-fixture")); assertEquals("own-csrf",request.getHeader("x-csrftoken")); assertNull(request.getHeader("Authorization")); assertNull(api.lesson()); api.close(); }
    }
    @Test public void directIdDoesNotSendAParseOrCheckinRequest() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); RainApi api=new RainApi(server.url("/"),new RainApiTest.Store()); assertEquals("123456",api.resolveLessonQr(LessonQr.parse("123456",0))); assertEquals(0,server.getRequestCount()); assertNull(api.lesson()); api.close(); }
    }
    @Test public void expiredOrMalformedQrDoesNotProduceLessonCredentials() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); RainApi api=new RainApi(server.url("/"),new RainApiTest.Store()); server.enqueue(new MockResponse().setBody("{\"code\":51203}")); try { api.resolveLessonQr(LessonQr.parse(QR,0)); fail(); } catch(RainApi.Failure expected) { assertEquals(51203,expected.code); }
            for(String response:new String[]{"{\"data\":{\"value\":0}}","{\"data\":{\"value\":\"classroom-123\"}}","{\"data\":{}}"}) { server.enqueue(new MockResponse().setBody(response)); try { api.resolveLessonQr(LessonQr.parse(QR,0)); fail(); } catch(RainApi.Failure expected) { assertEquals(502,expected.code); } }
            assertNull(api.lesson()); api.close(); }
    }
}
