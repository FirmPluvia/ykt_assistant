package com.ykt.assistant;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SyncAnswerImageTest {
    AccountStore store;LessonQuiz quiz;LessonQuiz.Question question;Map<String,JSONObject> sent=new HashMap<>();List<String> uploads=new ArrayList<>();boolean failB;
    @Before public void setup()throws Exception{android.content.Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("saved_accounts",0).edit().clear().commit();store=new AccountStore(c);for(String uid:new String[]{"self","b","c"})store.remember(0,RainApi.object("user_id",uid),"[]");quiz=new LessonQuiz("lesson",()->1800000000000L,()->1000L);quiz.deck("pres",deck());quiz.ingest(RainApi.object("op","unlockproblem","presentationId","pres","problem",RainApi.object("prob","q","dt",1800000000000L,"limit",60)));question=quiz.get("q");question.images.add(new AnswerImage("photo",new byte[]{1,2},"https://qn-scd1.yuketang.cn/current.jpg"));question.submitted=true;}
    JSONObject deck()throws Exception{return RainApi.object("slides",new JSONArray().put(RainApi.object("problem",RainApi.object("problemId","q","problemType",5,"body","文字或图片"))));}
    void run()throws Exception{MultiAccountAnswers support=new MultiAccountAnswers(store,0,"0|self",a->new RainApi(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store()){
        @Override public JSONObject profile()throws Exception{return RainApi.object("user_id",a.uid);}@Override void promoteSession(CookieStorage storage){}@Override boolean restoreLesson(String lesson,java.util.function.BooleanSupplier stop){return true;}@Override public JSONObject quizPresentation(String lesson,String pres)throws Exception{return deck();}
        @Override public AnswerImage uploadAnswerImage(AnswerImage image,java.util.function.BooleanSupplier stop)throws Exception{uploads.add(a.uid);if(failB&&a.uid.equals("b"))throw new Exception("上传失败");return image.uploaded("https://qn-scd1.yuketang.cn/"+a.uid+".jpg");}
        @Override public JSONObject submitAnswer(LessonQuiz.Submission s)throws Exception{sent.put(a.uid,s.payload());return RainApi.object("code",0);}
    });List<MultiAccountAnswers.Task> tasks=new ArrayList<>();for(AccountStore.Account a:support.eligible())tasks.add(new MultiAccountAnswers.Task(a,null));try(MultiAccountAnswers.Job job=support.new Job()){job.run(new MultiAccountAnswers.Plan(quiz,question),tasks,()->true,(a,s)->{});}}
    @Test public void eachAccountUploadsItsOwnCopyBeforeImageOnlySubmission()throws Exception{run();assertEquals(Arrays.asList("b","c"),uploads);assertEquals("https://qn-scd1.yuketang.cn/b.jpg",sent.get("b").getJSONObject("result").getJSONArray("pics").getJSONObject(0).getString("pic"));assertEquals("https://qn-scd1.yuketang.cn/c.jpg",sent.get("c").getJSONObject("result").getJSONArray("pics").getJSONObject(0).getString("pic"));assertEquals("https://qn-scd1.yuketang.cn/current.jpg",question.images.get(0).url);}
    @Test public void uploadFailureSkipsThatSubmissionAndContinuesNextAccount()throws Exception{failB=true;run();assertFalse(sent.containsKey("b"));assertTrue(sent.containsKey("c"));}
}
