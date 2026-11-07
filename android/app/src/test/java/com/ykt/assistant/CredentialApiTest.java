package com.ykt.assistant;

import android.net.Uri;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CredentialApiTest {
    private MockResponse ok(String raw) { return new MockResponse().setBody(raw); }
    private void profile(MockWebServer server) { server.enqueue(ok("{\"data\":{\"user_profile\":{\"user_id\":\"fixture\"}}}")); }
    @Test public void phonePasswordKeepsSecretWhitespaceAndValidatesCookieBackedProfile() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); RainApiTest.Store store=new RainApiTest.Store(); try(RainApi api=new RainApi(server.url("/"),store)) {
            api.setUserAgent("Chosen UA/3.1"); server.enqueue(ok("{\"code\":0}").addHeader("Set-Cookie","sessionid=accepted; Path=/")); profile(server); assertEquals("fixture",api.passwordLogin("13800000000","  password  ","proof","random").getString("user_id"));
            RecordedRequest request=server.takeRequest(); JSONObject body=new JSONObject(request.getBody().readUtf8()); assertEquals("/api/v3/user/login/app",request.getPath()); assertEquals("POST",request.getMethod()); assertEquals("Chosen UA/3.1",request.getHeader("User-Agent")); assertNull(request.getHeader("Authorization")); assertEquals(1,body.getInt("type")); assertEquals("13800000000",body.getString("phoneNumber")); assertEquals("",body.getString("email")); assertEquals("  password  ",body.getString("password")); assertEquals("proof",body.getString("ticket")); assertFalse(body.getString("pushDeviceId").isEmpty()); RecordedRequest user=server.takeRequest(); assertEquals("/v/course_meta/user_info",user.getPath()); assertTrue(user.getHeader("Cookie").contains("sessionid=accepted")); assertFalse(store.data.contains("password"));
        } }
    }
    @Test public void emailUsesTypeTwoAndDoesNotSendPhone() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); try(RainApi api=new RainApi(server.url("/"),new RainApiTest.Store())) { server.enqueue(ok("{\"code\":0}")); profile(server); api.passwordLogin(" user@example.com ","秘密","proof","rand"); JSONObject body=new JSONObject(server.takeRequest().getBody().readUtf8()); assertEquals(2,body.getInt("type")); assertEquals("user@example.com",body.getString("email")); assertEquals("",body.getString("phoneNumber")); } }
    }
    @Test public void smsIsSentExplicitlyThenVerifiedBeforeLoginAndProfile() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); try(RainApi api=new RainApi(server.url("/"),new RainApiTest.Store())) {
            server.enqueue(ok("{\"code\":0}")); api.sendLoginCode("13800000000","proof","rand"); RecordedRequest send=server.takeRequest(); assertEquals("/api/v3/user/code/send",send.getPath()); assertEquals("proof",new JSONObject(send.getBody().readUtf8()).getString("ticket")); server.enqueue(ok("{\"code\":0}")); server.enqueue(ok("{\"code\":0}")); profile(server); api.smsLogin("13800000000","123456","proof","rand"); assertEquals("/api/v3/user/code/verify",server.takeRequest().getPath()); RecordedRequest login=server.takeRequest(); JSONObject body=new JSONObject(login.getBody().readUtf8()); assertEquals("/api/v3/user/login/app",login.getPath()); assertEquals(3,body.getInt("type")); assertEquals("",body.getString("password")); assertEquals("123456",body.getString("code")); assertEquals("/v/course_meta/user_info",server.takeRequest().getPath());
        } }
    }
    @Test public void invalidSmsStopsBeforeLogin() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); try(RainApi api=new RainApi(server.url("/"),new RainApiTest.Store())) { server.enqueue(ok("{\"code\":45001,\"msg\":\"验证码不正确\"}")); try { api.smsLogin("13800000000","123456","proof","rand"); fail(); } catch(RainApi.Failure e) { assertEquals(45001,e.code); } assertEquals(1,server.getRequestCount()); assertNull(api.user()); } }
    }
    @Test public void rejectedPasswordOrMissingProfileCannotAuthenticate() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); try(RainApi api=new RainApi(server.url("/"),new RainApiTest.Store())) {
            server.enqueue(ok("{\"code\":40001}")); try { api.passwordLogin("13800000000","secret","proof","rand"); fail(); } catch(RainApi.Failure e) { assertEquals(40001,e.code); } assertNull(api.user()); assertEquals(1,server.getRequestCount()); server.enqueue(ok("{\"code\":0}")); server.enqueue(ok("{\"data\":{}}")); try { api.passwordLogin("13800000000","secret","proof","rand"); fail(); } catch(RainApi.Failure e) { assertEquals(401,e.code); } assertNull(api.user());
        } }
    }
    @Test public void invalidInputOrAbsentHumanProofMakesNoRequest() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); try(RainApi api=new RainApi(server.url("/"),new RainApiTest.Store())) {
            for(String phone:new String[]{"","123","user@example.com"}) try { api.sendLoginCode(phone,"proof","rand"); fail(); } catch(IllegalArgumentException expected) {}
            try { api.passwordLogin("13800000000","secret","",""); fail(); } catch(IllegalArgumentException expected) {}
            try { api.smsLogin("13800000000","invalid","proof","rand"); fail(); } catch(IllegalArgumentException expected) {} assertEquals(0,server.getRequestCount());
        } }
    }
    @Test public void onlyOfficialHttpsCaptchaAssetsAreAllowed() {
        for(String url:new String[]{"https://turing.captcha.qcloud.com/TCaptcha.js","https://t.captcha.qq.com/a","https://captcha.gtimg.com/a.js"}) assertTrue(HumanVerification.allowed(Uri.parse(url)));
        for(String url:new String[]{"http://turing.captcha.qcloud.com/a","https://captcha.qcloud.com.evil.test/","https://example.com/","file:///tmp/a","https://captcha.gtimg.com:8443/"}) assertFalse(HumanVerification.allowed(Uri.parse(url)));
    }
}
