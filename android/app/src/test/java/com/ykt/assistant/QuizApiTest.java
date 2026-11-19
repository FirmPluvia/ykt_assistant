package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class QuizApiTest {
    MockResponse json(String value) { return new MockResponse().setHeader("Content-Type","application/json").setBody(value); }
    RainApi joined(MockWebServer s) throws Exception {
        RainApi api=new RainApi(s.url("/"),new RainApiTest.Store()); s.enqueue(json("{\"data\":{\"user_profile\":{\"user_id\":\"student\"}}}")); api.profile(); s.takeRequest(); s.enqueue(json("{\"code\":0,\"data\":{\"lessonToken\":\"socket-secret\"}}").setHeader("set-auth","lesson-secret")); api.checkin("lesson"); s.takeRequest(); return api;
    }
    LessonQuiz.Submission submission(boolean retry) throws Exception {
        long now=1800000000000L; LessonQuiz quiz=new LessonQuiz("lesson",()->now,()->1000L); quiz.deck("pres",RainApi.object("slides",new JSONArray().put(RainApi.object("problem",RainApi.object("problemId","q","problemType",1,"options",new JSONArray("[{\"key\":\"A\"}]")))))); quiz.ingest(RainApi.object("op","unlockproblem","problem",RainApi.object("prob","q","dt",retry?now-61000:now,"limit",60))); quiz.get("q").selected.add("A"); return quiz.prepare(quiz.get("q"));
    }
    @Test public void answerAndRetryUseSeparatePathsAndAccountLessonBearer() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi api=joined(s);
            s.enqueue(json("{\"code\":0}")); api.submitAnswer(submission(false)); RecordedRequest answer=s.takeRequest(); assertEquals("/api/v3/lesson/problem/answer",answer.getPath()); assertEquals("Bearer lesson-secret",answer.getHeader("Authorization")); assertEquals("student",answer.getHeader("x-uid")); JSONObject normal=new JSONObject(answer.getBody().readUtf8()); assertEquals("q",normal.getString("problemId")); assertFalse(normal.has("problems"));
            s.enqueue(json("{\"code\":0}")); api.submitAnswer(submission(true)); RecordedRequest retry=s.takeRequest(); assertEquals("/api/v3/lesson/problem/retry",retry.getPath()); JSONObject item=new JSONObject(retry.getBody().readUtf8()).getJSONArray("problems").getJSONObject(0); assertEquals(1799999939000L,item.getLong("dt")); assertTrue(item.isNull("retry_times")); api.close(); }
    }
    @Test public void submitRequiresExplicitSuccessAndPropagatesRejection() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi api=joined(s);
            s.enqueue(json("{\"data\":{}}")); try { api.submitAnswer(submission(false)); fail(); } catch(RainApi.Failure e) { assertEquals(502,e.code); } s.takeRequest();
            s.enqueue(json("{\"code\":1234,\"msg\":\"不接受补交\"}")); try { api.submitAnswer(submission(true)); fail(); } catch(RainApi.Failure e) { assertEquals("不接受补交",e.getMessage()); } api.close(); }
    }
    @Test public void switchedLessonCannotSubmitPreparedAnswerAndQuestionFetchDoesNotJoin() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi api=joined(s); s.enqueue(json("{\"data\":{\"slides\":[]}}")); api.quizPresentation("lesson","old&pres"); RecordedRequest fetch=s.takeRequest(); assertEquals("/api/v3/lesson/presentation/fetch?presentation_id=old%26pres",fetch.getPath()); assertEquals("Bearer lesson-secret",fetch.getHeader("Authorization")); assertNull(api.presentation());
            s.enqueue(json("{\"code\":0,\"data\":{\"lessonToken\":\"new-socket\"}}").setHeader("set-auth","new-bearer")); api.checkin("other"); s.takeRequest(); try { api.submitAnswer(submission(false)); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); } assertNull(s.takeRequest(150,TimeUnit.MILLISECONDS)); api.close(); }
    }
    @Test public void socketForwardsOnlyQuestionMetadataAndNeverAuthenticationOrRandomPick() throws Exception {
        try(MockWebServer s=new MockWebServer()) { s.start(); RainApi api=joined(s); CountDownLatch done=new CountDownLatch(2); List<JSONObject> received=new ArrayList<>();
            s.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() { @Override public void onMessage(WebSocket ws,String text) {
                ws.send("{\"op\":\"hello\",\"presentation\":\"pres\",\"timeline\":[{\"type\":\"problem\",\"prob\":\"q\",\"pres\":\"pres\",\"dt\":1800000000000,\"limit\":60,\"auth\":\"secret\"},{\"type\":\"randompick\",\"code\":\"RANDOM_PICK\"}]}");
                ws.send("{\"op\":\"unlockproblem\",\"auth\":\"secret\",\"problem\":{\"prob\":\"q\",\"dt\":1800000000000,\"limit\":60,\"token\":\"secret\"}}");
            } }));
            api.connect(event->{ if(event.has("timeline")||event.has("problem")) { synchronized(received) { received.add(event); } done.countDown(); } }); assertTrue(done.await(5,TimeUnit.SECONDS)); assertEquals(1,received.get(0).getJSONArray("timeline").length()); assertEquals("q",received.get(1).getJSONObject("problem").getString("prob")); assertFalse(received.toString().contains("secret")); assertFalse(received.toString().contains("RANDOM_PICK")); api.close(); }
    }
    @Test public void transportDoesNotReplayManualAnswerOnRetryable503() throws Exception {
        try(MockWebServer server=new MockWebServer()) { server.start(); RainApi api=joined(server); server.enqueue(json("{\"msg\":\"busy\"}").setResponseCode(503).setHeader("Retry-After","0")); server.enqueue(json("{\"code\":0}"));
            try { api.submitAnswer(submission(false)); fail(); } catch(RainApi.Failure e) { assertEquals(503,e.code); }
            assertEquals("/api/v3/lesson/problem/answer",server.takeRequest().getPath()); assertNull(server.takeRequest(150,TimeUnit.MILLISECONDS)); api.close(); }
    }
}
