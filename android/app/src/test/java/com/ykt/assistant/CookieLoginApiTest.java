package com.ykt.assistant;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Test;
import static org.junit.Assert.*;

public class CookieLoginApiTest {
    @Test public void normalizesHeaderAndPreservesEqualsInsideValuesWithoutAcceptingOtherHeaders() {
        assertEquals("sessionid=a=b==; csrftoken=x",LoginCookies.normalize(" Cookie: sessionid=a=b==; csrftoken=x; "));
        for(String raw:new String[]{"", "sessionid=x\r\nX-Injected: yes", "Cookie: sessionid=x\nX-Test=y", "https://example.com", "sessionid=x; Path=/", "sessionid=x; Secure", "invalid name=x"}) { try { LoginCookies.normalize(raw); fail(raw); } catch(IllegalArgumentException expected) {} }
        Cookie cookie=LoginCookies.parse(HttpUrl.get("https://changjiang.yuketang.cn/"),"sessionid=fixture").get(0); assertTrue(cookie.hostOnly()); assertTrue(cookie.secure()); assertTrue(cookie.matches(HttpUrl.get("https://changjiang.yuketang.cn/api/test"))); assertFalse(cookie.matches(HttpUrl.get("https://www.yuketang.cn/api/test")));
    }
    @Test public void validatesOwnCookiesThenPromotesRenewedSessionAndKeepsFutureUpdates() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); LoginCookies.Memory stage=new LoginCookies.Memory(); RainApi api=new RainApi(server.url("/"),stage); api.importLoginCookies("sessionid=fixture; csrftoken=csrf-fixture"); api.setUserAgent("Selected-UA"); server.enqueue(new MockResponse().setBody("{\"data\":{\"user_profile\":{\"user_id\":\"account-a\"}}}").setHeader("Set-Cookie","sessionid=renewed; Path=/")); api.profile(); RecordedRequest request=server.takeRequest(); assertEquals("fixture",request.getHeader("Cookie").split("sessionid=")[1].split(";")[0]); assertEquals("csrf-fixture",request.getHeader("x-csrftoken")); assertEquals("Selected-UA",request.getHeader("User-Agent")); assertNull(request.getHeader("Authorization"));
            RainApiTest.Store permanent=new RainApiTest.Store(); api.promoteSession(permanent); api.rememberLogin(); assertTrue(permanent.data.contains("renewed")); server.enqueue(new MockResponse().setBody("{\"data\":{\"user_profile\":{\"user_id\":\"account-a\"}}}").setHeader("Set-Cookie","sessionid=renewed-again; Path=/")); api.profile(); assertTrue(permanent.data.contains("renewed-again")); RainApi restored=new RainApi(server.url("/"),permanent); assertTrue(restored.loginCookieHeader().contains("sessionid=renewed-again")); restored.close(); api.close();
        }
    }
    @Test public void expiredSavedCookieIsNotRestoredAndUnvalidatedSessionCannotBePromoted() throws Exception {
        RainApiTest.Store store=new RainApiTest.Store(); store.data="[{\"raw\":\"sessionid=expired; Max-Age=3600; Path=/\",\"expires\":1}]"; RainApi api=new RainApi(HttpUrl.get("https://changjiang.yuketang.cn/"),store); assertFalse(api.hasCookies()); try { api.promoteSession(new RainApiTest.Store()); fail(); } catch(RainApi.Failure expected) { assertEquals(401,expected.code); } api.close();
    }
}
