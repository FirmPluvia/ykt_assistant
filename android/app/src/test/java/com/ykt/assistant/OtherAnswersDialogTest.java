package com.ykt.assistant;

import android.app.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w393dp-h851dp-hdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class OtherAnswersDialogTest {
    ActivityController<Activity> controller;Activity activity;AccountStore store;LessonQuiz quiz;LessonQuiz.Question q;ExecutorService workers;OtherAnswersDialog panel;ClassroomQuizDialog sheet;int opens;Map<String,String> texts=new ConcurrentHashMap<>();long mono=1000;
    @Before public void setup()throws Exception{
        controller=Robolectric.buildActivity(Activity.class).setup();activity=controller.get();activity.getSharedPreferences("saved_accounts",0).edit().clear().commit();activity.getSharedPreferences("ai_settings",0).edit().clear().commit();store=new AccountStore(activity);for(String uid:new String[]{"self","b","c"})store.remember(0,RainApi.object("user_id",uid,"name",uid),"[]");workers=Executors.newSingleThreadExecutor();quiz=new LessonQuiz("lesson",()->1800000000000L,()->mono);quiz.deck("pres",RainApi.object("slides",new JSONArray().put(RainApi.object("problem",RainApi.object("problemId","q","problemType",5,"body","论述题")))));quiz.ingest(RainApi.object("op","unlockproblem","presentationId","pres","problem",RainApi.object("prob","q","dt",1800000000000L,"limit",60)));q=quiz.get("q");q.text="当前账号原文";
    }
    @After public void cleanup(){if(panel!=null)panel.close();if(sheet!=null)sheet.close();workers.shutdownNow();controller.pause().stop().destroy();ShadowLooper.idleMainLooper();}
    MultiAccountAnswers support(){return new MultiAccountAnswers(store,0,"0|self",a->{opens++;return new RainApi(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store()){
        @Override public JSONObject profile()throws Exception{return RainApi.object("user_id",a.uid);}
        @Override void promoteSession(CookieStorage target){}
        @Override boolean restoreLesson(String id,BooleanSupplier stop){return !stop.getAsBoolean();}
        @Override public JSONObject quizPresentation(String lesson,String pres)throws Exception{return RainApi.object("slides",new JSONArray().put(RainApi.object("problem",new JSONObject(q.spec.toString()))));}
        @Override public JSONObject submitAnswer(LessonQuiz.Submission s)throws Exception{JSONObject body=s.payload();if(s.retry)body=body.getJSONArray("problems").getJSONObject(0);texts.put(a.uid,body.getJSONObject("result").getString("content"));return RainApi.object("code",0);}
    };});}
    View root(){List<Dialog> ds=ShadowDialog.getShownDialogs();for(int i=ds.size()-1;i>=0;i--)if(ds.get(i).isShowing())return ds.get(i).getWindow().getDecorView();return activity.getWindow().getDecorView();}
    List<View> all(View v){List<View> r=new ArrayList<>();r.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)r.addAll(all(((ViewGroup)v).getChildAt(i)));return r;}
    View find(String label){for(View v:all(root()))if(v instanceof TextView&&((TextView)v).getText().toString().equals(label))return v;return null;}
    void click(String label){View v=find(label);assertNotNull(label,v);v.performClick();ShadowLooper.idleMainLooper();}
    void waitFor(BooleanSupplier ready)throws Exception{for(int i=0;i<250;i++){ShadowLooper.idleMainLooper();if(ready.getAsBoolean())return;Thread.sleep(10);}fail("flow timeout");}
    void show(){panel=new OtherAnswersDialog(activity,quiz,q,support(),workers,()->true);panel.show();ShadowLooper.idleMainLooper();}
    void choose(String uid){((CheckBox)root().findViewWithTag("other-answer-account:0|"+uid)).performClick();ShadowLooper.idleMainLooper();}
    @Test public void separateSubjectiveTextsAreReviewedAndSentOnlyAfterConfirmation()throws Exception{
        show();assertNull(root().findViewWithTag("other-answer-account:0|self"));choose("b");choose("c");click("分别填写主观题答案");((EditText)root().findViewWithTag("other-answer-text:0|b")).setText("甲自己的解答");((EditText)root().findViewWithTag("other-answer-text:0|c")).setText("乙自己的解答");click("检查所选账号答案");assertNotNull(find("甲自己的解答"));assertNotNull(find("乙自己的解答"));assertEquals(0,opens);click("确认提交 2 个账号");waitFor(()->texts.size()==2&&find("已处理 2 / 2 个账号，成功 2 个")!=null);assertEquals("甲自己的解答",texts.get("b"));assertEquals("乙自己的解答",texts.get("c"));assertEquals("当前账号原文",q.text);assertFalse(q.submitted);assertFalse(((CheckBox)root().findViewWithTag("other-answer-account:0|b")).isEnabled());
    }
    @Test public void emptySelectionAndClosingConfirmationDoNotSend()throws Exception{
        show();click("检查所选账号答案");assertNotNull(find("请选择至少一个其他账号"));choose("b");click("检查所选账号答案");root().findViewWithTag("card-close").performClick();ShadowLooper.idleMainLooper();assertEquals(0,opens);assertTrue(texts.isEmpty());assertNotNull(find("检查所选账号答案"));
    }
    @Test public void roundChangeClosesReviewAndNeverStartsRequests()throws Exception{
        show();choose("b");click("检查所选账号答案");q.round++;ShadowLooper.idleMainLooper(600,TimeUnit.MILLISECONDS);assertFalse(panel.valid());assertNull(find("确认提交 1 个账号"));assertEquals(0,opens);
    }
    @Test public void alreadySubmittedCurrentAccountStillOffersOtherAccounts()throws Exception{
        q.submitted=true;RainApi api=new RainApi(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store());try{sheet=new ClassroomQuizDialog(activity,api,quiz,workers,()->true);sheet.setOtherAccounts(support());sheet.show("q");assertNull(find("为其他账号作答"));assertFalse(root().findViewWithTag("sync-check:subjective").isEnabled());assertEquals(0,opens);}finally{api.close();}
    }
    @Test public void perAccountAiButtonDirectlyFillsWithoutOpeningResultCard()throws Exception {
        new AiSettings(activity).save("https://ai.example/v1","model","",false,true,false,false,()->false);java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();panel=new OtherAnswersDialog(activity,quiz,q,support(),workers,()->true);panel.setAi(null,(config,ua)->new AiClient(config,ua){@Override AiQuestion.Result analyze(AiQuestion question,byte[] image)throws Exception{int n=count.incrementAndGet();return question.parse(RainApi.object("schema_version",1,"question_token",question.token,"problem_type",5,"status","answered","answer",RainApi.object("option_keys",new JSONArray(),"blank_values",new JSONArray(),"text","AI 文字 "+n),"explanation","理由","confidence",.8).toString());}});panel.show();choose("b");click("分别填写主观题答案");EditText field=root().findViewWithTag("other-answer-text:0|b");root().findViewWithTag("other-answer-ai:0|b").performClick();waitFor(()->field.getText().toString().equals("AI 文字 1"));assertNotNull(find("检查所选账号答案"));assertNull(find("填入答案"));root().findViewWithTag("other-answer-ai:0|b").performClick();waitFor(()->field.getText().toString().equals("AI 文字 2"));assertEquals(0,opens);
    }
    @Test public void perAccountAiFailureKeepsPreviousTextAndRestoresButton()throws Exception {
        new AiSettings(activity).save("https://ai.example/v1","model","",false,true,false,false,()->false);panel=new OtherAnswersDialog(activity,quiz,q,support(),workers,()->true);panel.setAi(null,(config,ua)->new AiClient(config,ua){@Override AiQuestion.Result analyze(AiQuestion question,byte[] image)throws Exception{throw new Exception("模拟错误");}});panel.show();choose("b");click("分别填写主观题答案");EditText field=root().findViewWithTag("other-answer-text:0|b");field.setText("保留文字");View button=root().findViewWithTag("other-answer-ai:0|b");button.performClick();waitFor(button::isEnabled);assertEquals("保留文字",field.getText().toString());assertEquals(0,opens);
    }
    @Test public void closingPerAccountEditorDropsLateAiResult()throws Exception{
        new AiSettings(activity).save("https://ai.example/v1","model","",false,true,false,false,()->false);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);panel=new OtherAnswersDialog(activity,quiz,q,support(),workers,()->true);panel.setAi(null,(config,ua)->new AiClient(config,ua){@Override AiQuestion.Result analyze(AiQuestion question,byte[] image)throws Exception{entered.countDown();release.await(5,TimeUnit.SECONDS);return question.parse(RainApi.object("schema_version",1,"question_token",question.token,"problem_type",5,"status","answered","answer",RainApi.object("option_keys",new JSONArray(),"blank_values",new JSONArray(),"text","迟到文字"),"explanation","理由","confidence",.8).toString());}});panel.show();choose("b");click("分别填写主观题答案");EditText field=root().findViewWithTag("other-answer-text:0|b");field.setText("保留草稿");root().findViewWithTag("other-answer-ai:0|b").performClick();assertTrue(entered.await(3,TimeUnit.SECONDS));panel.close();release.countDown();workers.submit(()->{}).get(3,TimeUnit.SECONDS);ShadowLooper.idleMainLooper();assertEquals("保留草稿",field.getText().toString());assertFalse(panel.valid());
    }
}
