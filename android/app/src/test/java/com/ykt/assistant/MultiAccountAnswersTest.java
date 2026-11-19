package com.ykt.assistant;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import okhttp3.mockwebserver.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class MultiAccountAnswersTest {
    MockWebServer server;AccountStore store;RainApiTest.Lessons credentials;LessonQuiz quiz;LessonQuiz.Question q;long wall=1800000000000L,mono=1000L;
    final List<String> calls=new CopyOnWriteArrayList<>();final Map<String,JSONObject> sent=new ConcurrentHashMap<>();
    boolean wrongIdentity,denyB,mismatchB;CountDownLatch entered,release;String blockPath;
    @Before public void setup()throws Exception{
        RuntimeEnvironment.getApplication().getSharedPreferences("saved_accounts",0).edit().clear().commit();store=new AccountStore(RuntimeEnvironment.getApplication());credentials=new RainApiTest.Lessons();server=new MockWebServer();
        server.setDispatcher(new Dispatcher(){@Override public MockResponse dispatch(RecordedRequest r)throws InterruptedException{
            String uid=r.getHeader("Cookie").contains("sessionid=b")?"b":"c",path=r.getRequestUrl().encodedPath();calls.add(uid+":"+path);
            if(path.equals(blockPath)&&uid.equals("b")){entered.countDown();release.await(5,TimeUnit.SECONDS);}
            try{
                if(path.equals("/v/course_meta/user_info"))return json(RainApi.object("data",RainApi.object("user_profile",RainApi.object("user_id",wrongIdentity?"wrong":uid))).toString());
                assertEquals(uid,r.getHeader("x-uid"));assertEquals("Bearer token-"+uid,r.getHeader("Authorization"));
                if(path.equals("/api/v3/lesson/presentation/fetch")){JSONObject spec=new JSONObject(q.spec.toString());if(mismatchB&&uid.equals("b"))spec.put("body","another question");return json(RainApi.object("data",RainApi.object("id","pres","slides",new JSONArray().put(RainApi.object("problem",spec)))).toString());}
                if(path.endsWith("/problem/answer")||path.endsWith("/problem/retry")){sent.put(uid,new JSONObject(r.getBody().readUtf8()));return uid.equals("b")&&denyB?json("{\"code\":403,\"msg\":\"拒绝\"}"):json("{\"code\":0}");}
                return new MockResponse().setResponseCode(404);
            }catch(Exception e){throw new AssertionError(e);}
        }});server.start();add(0,"self");add(0,"b");add(0,"c");add(1,"elsewhere");question(1);
        for(String uid:new String[]{"b","c"})credentials.write(server.url("/").host(),uid,"lesson",RainApi.object("bearer","token-"+uid,"lessonToken","ws-"+uid));
    }
    @After public void close()throws Exception{if(release!=null)release.countDown();server.close();}
    MockResponse json(String s){return new MockResponse().setHeader("Content-Type","application/json").setBody(s);}
    void add(int service,String uid)throws Exception{store.remember(service,RainApi.object("user_id",uid,"name",uid),new JSONArray().put(RainApi.object("raw","sessionid="+uid+"; Path=/","expires",Long.MAX_VALUE)).toString());}
    void question(int type)throws Exception{quiz=new LessonQuiz("lesson",()->wall,()->mono);quiz.deck("pres",RainApi.object("slides",new JSONArray().put(RainApi.object("problem",RainApi.object("problemId","q","problemType",type,"body","题干","options",new JSONArray("[{\"key\":\"A\",\"value\":\"正确\"},{\"key\":\"B\",\"value\":\"错误\"}]"))))));quiz.ingest(RainApi.object("op","unlockproblem","presentationId","pres","problem",RainApi.object("prob","q","dt",wall,"limit",60)));q=quiz.get("q");q.selected.add("A");q.text="当前答案";}
    RainApi open(AccountStore.Account a)throws Exception{LoginCookies.Memory cookies=new LoginCookies.Memory();cookies.write(a.cookies);RainApi api=new RainApi(server.url("/"),cookies);api.configure(credentials);return api;}
    MultiAccountAnswers support(){return new MultiAccountAnswers(store,0,"0|self",this::open);}
    List<MultiAccountAnswers.Task> tasks(){return Arrays.asList(new MultiAccountAnswers.Task(store.get("0|b"),"甲答案"),new MultiAccountAnswers.Task(store.get("0|c"),"乙答案"));}
    List<MultiAccountAnswers.Result> run()throws Exception{MultiAccountAnswers service=support();try(MultiAccountAnswers.Job job=service.new Job()){return job.run(new MultiAccountAnswers.Plan(quiz,q),tasks(),()->true,(a,s)->{});}}
    @Test public void ownCookiesAndCachedBearersAreIsolatedAndSuccessIsNotResubmitted()throws Exception{
        assertEquals(2,support().eligible().size());q.submitted=true;assertEquals(2,run().stream().filter(r->r.success).count());assertEquals(2,sent.size());assertTrue(q.submitted);assertFalse(q.sending);assertEquals(Collections.singleton("A"),q.selected);assertEquals(6,calls.size());sent.clear();assertEquals(0,run().stream().filter(r->r.success).count());assertTrue(sent.isEmpty());assertEquals(6,calls.size());assertEquals("",store.active());
    }
    @Test public void subjectiveAccountsCanUseDifferentTextWithoutChangingCurrentDraft()throws Exception{question(5);assertEquals(2,run().size());assertEquals("甲答案",sent.get("b").getJSONObject("result").getString("content"));assertEquals("乙答案",sent.get("c").getJSONObject("result").getString("content"));assertEquals("当前答案",q.text);assertFalse(q.submitted);}
    @Test public void judgmentKeepsTypeSixAndOriginalKeysForEachAccount()throws Exception{question(6);run();assertEquals(6,sent.get("b").getInt("problemType"));assertEquals("[\"A\"]",sent.get("c").getJSONArray("result").toString());}
    @Test public void oneRejectionDoesNotPreventNextAccountAndOnlySuccessIsRemembered()throws Exception{denyB=true;List<MultiAccountAnswers.Result> result=run();assertFalse(result.get(0).success);assertTrue(result.get(1).success);assertEquals(1,quiz.otherAnswerSuccess.size());}
    @Test public void missingCredentialAndWrongIdentityNeverJoinOrSubmit()throws Exception{credentials.records.clear();assertEquals(0,run().stream().filter(r->r.success).count());assertTrue(sent.isEmpty());assertEquals(2,calls.size());wrongIdentity=true;assertEquals(0,run().stream().filter(r->r.success).count());assertTrue(sent.isEmpty());}
    @Test public void mismatchedAccountQuestionIsRejected()throws Exception{mismatchB=true;List<MultiAccountAnswers.Result> result=run();assertFalse(result.get(0).success);assertTrue(result.get(1).success);assertFalse(sent.containsKey("b"));}
    @Test public void accountsAreSequentialUntilPreviousSubmissionResponseCompletes()throws Exception{
        blockPath="/api/v3/lesson/problem/answer";entered=new CountDownLatch(1);release=new CountDownLatch(1);MultiAccountAnswers service=support();MultiAccountAnswers.Job job=service.new Job();MultiAccountAnswers.Plan plan=new MultiAccountAnswers.Plan(quiz,q);ExecutorService worker=Executors.newSingleThreadExecutor();try{Future<?> future=worker.submit(()->job.run(plan,tasks(),()->true,(a,s)->{}));assertTrue(entered.await(3,TimeUnit.SECONDS));assertEquals(3,calls.size());assertFalse(calls.stream().anyMatch(s->s.startsWith("c:")));release.countDown();future.get(5,TimeUnit.SECONDS);assertEquals(2,sent.size());}finally{release.countDown();job.close();worker.shutdownNow();}}
    @Test public void cancellingDuringFetchStopsSubmissionAndLaterAccounts()throws Exception{blockPath="/api/v3/lesson/presentation/fetch";entered=new CountDownLatch(1);release=new CountDownLatch(1);MultiAccountAnswers service=support();MultiAccountAnswers.Job job=service.new Job();MultiAccountAnswers.Plan plan=new MultiAccountAnswers.Plan(quiz,q);ExecutorService worker=Executors.newSingleThreadExecutor();try{Future<?> future=worker.submit(()->job.run(plan,tasks(),()->true,(a,s)->{}));assertTrue(entered.await(3,TimeUnit.SECONDS));job.close();release.countDown();future.get(5,TimeUnit.SECONDS);assertTrue(sent.isEmpty());assertFalse(calls.stream().anyMatch(s->s.startsWith("c:")));}finally{release.countDown();job.close();worker.shutdownNow();}}
    @Test public void newRoundOrElapsedDeadlineCannotReuseOldConfirmation()throws Exception{MultiAccountAnswers.Plan plan=new MultiAccountAnswers.Plan(quiz,q);mono+=61000;try{plan.prepare(null);fail();}catch(Exception e){assertTrue(e.getMessage().contains("时限"));}MultiAccountAnswers.Plan expired=new MultiAccountAnswers.Plan(quiz,q);MultiAccountAnswers service=support();try(MultiAccountAnswers.Job job=service.new Job()){job.run(expired,tasks(),()->true,(a,s)->{});}assertEquals(wall,sent.get("b").getJSONArray("problems").getJSONObject(0).getLong("dt"));q.round++;assertFalse(expired.valid());}
    @Test public void currentWrongServerDuplicateAndRemovedAccountsDoNotSubmit()throws Exception{MultiAccountAnswers service=support();List<MultiAccountAnswers.Task> selected=new ArrayList<>();for(String key:new String[]{"0|self","1|elsewhere","0|b","0|b","0|c"})selected.add(new MultiAccountAnswers.Task(store.get(key),null));store.remove("0|c");try(MultiAccountAnswers.Job job=service.new Job()){List<MultiAccountAnswers.Result> results=job.run(new MultiAccountAnswers.Plan(quiz,q),selected,()->true,(a,s)->{});assertEquals(1,results.stream().filter(r->r.success).count());}assertEquals(Collections.singleton("b"),sent.keySet());}
    @Test public void teacherExtensionUsesNewDeadlineWithConfirmedFrozenAnswer()throws Exception {
        MultiAccountAnswers.Plan plan=new MultiAccountAnswers.Plan(quiz,q);mono+=59000;quiz.ingest(RainApi.object("op","extendtime","problem",RainApi.object("prob","q","extend",30,"dt",wall+59000)));mono+=2000;q.selected.clear();q.selected.add("B");LessonQuiz.Submission submission=plan.prepare(null);assertFalse(submission.retry);assertEquals("[\"A\"]",submission.payload().getJSONArray("result").toString());
    }
}
