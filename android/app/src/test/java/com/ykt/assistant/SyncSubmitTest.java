package com.ykt.assistant;
import android.view.View;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowLooper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w393dp-h851dp-hdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class SyncSubmitTest {
    AiUiTest f;AccountStore store;SyncSettings settings;Map<String,JSONObject> sent=new ConcurrentHashMap<>();boolean rejectB;
    @Before public void setup()throws Exception{f=new AiUiTest();f.setup();f.activity.getSharedPreferences("request_sync",0).edit().clear().commit();f.activity.getSharedPreferences("saved_accounts",0).edit().clear().commit();store=new AccountStore(f.activity);for(String uid:new String[]{"self","b","c"})store.remember(0,RainApi.object("user_id",uid,"name",uid),"[]");settings=new SyncSettings(f.activity,store,0,"0|self");settings.resultCards(true);}
    @After public void cleanup(){f.cleanup();}
    MultiAccountAnswers support(){return new MultiAccountAnswers(store,0,"0|self",a->new RainApi(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store()){
        @Override public JSONObject profile()throws Exception{return RainApi.object("user_id",a.uid);}
        @Override void promoteSession(CookieStorage storage){}
        @Override boolean restoreLesson(String id,java.util.function.BooleanSupplier stop){return !stop.getAsBoolean();}
        @Override public JSONObject quizPresentation(String lesson,String pres)throws Exception{return RainApi.object("slides",new JSONArray().put(RainApi.object("problem",new JSONObject(f.q.spec.toString()))));}
        @Override public JSONObject submitAnswer(LessonQuiz.Submission s)throws Exception{assertTrue("primary must finish before others",f.q.submitted);assertEquals(1,f.api.submissions);if(rejectB&&a.uid.equals("b"))throw new RainApi.Failure("模拟拒绝",403);sent.put(a.uid,s.payload());return RainApi.object("code",0);}
    });}
    void show()throws Exception{f.show();f.sheet.setOtherAccounts(support());}
    void selectAnswer(){f.click("A. 甲");}
    void waitBatch()throws Exception{f.waitFor(()->sent.size()==2&&f.find("已处理 2 / 2 个账号，成功 2 个")!=null);}
    @Test public void checkboxSubmitsCurrentThenAllOtherAccountsWithSameObjectiveAnswer()throws Exception{show();assertFalse(((CheckBox)f.root().findViewWithTag("sync-check:answer")).isChecked());f.root().findViewWithTag("sync-check:answer").performClick();selectAnswer();f.click("提交答案");waitBatch();assertEquals("[\"A\"]",sent.get("b").getJSONArray("result").toString());assertEquals("[\"A\"]",sent.get("c").getJSONArray("result").toString());}
    @Test public void recipientPickerDefaultsAllAndSavedSubsetControlsSubmission()throws Exception{show();f.root().findViewWithTag("sync-edit:answer").performClick();CheckBox b=f.root().findViewWithTag("sync-account:0|b"),c=f.root().findViewWithTag("sync-account:0|c");assertTrue(b.isChecked());assertTrue(c.isChecked());c.setChecked(false);f.click("使用所选账号");f.root().findViewWithTag("sync-check:answer").performClick();selectAnswer();f.click("提交答案");f.waitFor(()->sent.containsKey("b")&&f.find("已处理 1 / 1 个账号，成功 1 个")!=null);assertFalse(sent.containsKey("c"));}
    @Test public void aiAnalysisHasSameCheckboxAndUsesSharedSubmitPipeline()throws Exception{f.configure(false);show();f.click("AI 分析");f.waitFor(()->f.find("分析完成")!=null);assertNotNull(f.root().findViewWithTag("sync-check:answer"));f.root().findViewWithTag("sync-check:answer").performClick();f.click("提交");waitBatch();assertEquals("[\"B\"]",sent.get("b").getJSONArray("result").toString());}
    @Test public void subjectiveIndependentEditingCanBeEnabled()throws Exception{settings.enabled(SyncSettings.ANSWER,true);settings.independentSubjective(true);f.q.spec.put("problemType",5);f.q.text="本账号文字";show();assertTrue(((CheckBox)f.root().findViewWithTag("sync-check:subjective")).isChecked());f.click("提交答案");assertNotNull(f.find("编辑同步主观题"));assertEquals(0,f.api.submissions);assertTrue(sent.isEmpty());}
    @Test public void subjectiveOptInCollectsDistinctTextsBeforeAnySubmission()throws Exception{settings.independentSubjective(true);f.q.spec.put("problemType",5);f.q.text="当前文字";show();f.root().findViewWithTag("sync-check:subjective").performClick();f.click("提交答案");assertEquals(0,f.api.submissions);EditText b=f.root().findViewWithTag("other-answer-text:0|b"),c=f.root().findViewWithTag("other-answer-text:0|c");assertEquals("",b.getText().toString());assertEquals("",c.getText().toString());b.setText("甲的文字");c.setText("乙的文字");f.click("检查所选账号答案");assertEquals(0,f.api.submissions);f.click("提交并同步 2 个其他账号");waitBatch();assertEquals("甲的文字",sent.get("b").getJSONObject("result").getString("content"));assertEquals("乙的文字",sent.get("c").getJSONObject("result").getString("content"));assertEquals("当前文字",f.q.text);}
    @Test public void singleAccountHidesAllAnswerSyncControls()throws Exception{store.remove("0|b");store.remove("0|c");f.configure(false);show();assertNull(f.root().findViewWithTag("sync-check:answer"));f.click("AI 分析");f.waitFor(()->f.find("分析完成")!=null);assertNull(f.root().findViewWithTag("sync-check:answer"));}
    @Test public void failedPrimarySubmissionDoesNotSendAnyOtherAccount()throws Exception {
        settings.enabled(SyncSettings.ANSWER,true);RainApi bad=new RainApi(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store()){@Override public JSONObject submitAnswer(LessonQuiz.Submission s)throws Exception{f.api.submissions++;throw new Exception("模拟失败");}};
        try{f.sheet=new ClassroomQuizDialog(f.activity,bad,f.quiz,f.worker,f.current::get);f.sheet.setOtherAccounts(support());f.sheet.show("q");selectAnswer();f.click("提交答案");f.waitFor(()->!f.q.lastError.isEmpty());assertTrue(sent.isEmpty());assertFalse(f.q.submitted);assertEquals(1,f.api.submissions);}finally{bad.close();}
    }
    @Test public void backgroundDuringPrimarySubmissionCancelsPendingSynchronization()throws Exception {
        settings.enabled(SyncSettings.ANSWER,true);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);RainApi slow=new RainApi(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store()){@Override public JSONObject submitAnswer(LessonQuiz.Submission s)throws Exception{f.api.submissions++;entered.countDown();release.await(5,TimeUnit.SECONDS);return RainApi.object("code",0);}};
        try{f.sheet=new ClassroomQuizDialog(f.activity,slow,f.quiz,f.worker,f.current::get);f.sheet.setOtherAccounts(support());f.sheet.show("q");selectAnswer();f.click("提交答案");assertTrue(entered.await(3,TimeUnit.SECONDS));f.sheet.foreground(false);f.sheet.foreground(true);release.countDown();f.waitFor(()->f.q.submitted);assertTrue(sent.isEmpty());}finally{release.countDown();slow.close();}
    }
    @Test public void subjectiveCanExplicitlyReuseCurrentTextWhenIndependentEditingDisabled()throws Exception {
        settings.enabled(SyncSettings.ANSWER,true);settings.independentSubjective(false);f.q.spec.put("problemType",5);f.q.text="选择共用的文字";show();f.click("提交答案");waitBatch();assertEquals("选择共用的文字",sent.get("b").getJSONObject("result").getString("content"));assertEquals("选择共用的文字",sent.get("c").getJSONObject("result").getString("content"));
    }
    @Test public void toastModeSynchronizesWithoutOpeningResultCard()throws Exception{settings.resultCards(false);settings.enabled(SyncSettings.ANSWER,true);show();selectAnswer();f.click("提交答案");f.waitFor(()->"本次同步答题成功 2 条，失败 0 条".equals(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()));assertEquals(2,sent.size());assertNull(f.find("同步答题结果"));}
    @Test public void aiSubmitAlsoUsesToastMode()throws Exception{settings.resultCards(false);settings.enabled(SyncSettings.ANSWER,true);f.configure(false);show();f.click("AI 分析");f.waitFor(()->f.find("分析完成")!=null);f.click("提交");f.waitFor(()->"本次同步答题成功 2 条，失败 0 条".equals(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()));assertEquals(2,sent.size());assertNull(f.find("同步答题结果"));}
    @Test public void toastSummaryCountsFailuresInsteadOfClaimingAllSucceeded()throws Exception{rejectB=true;settings.resultCards(false);settings.enabled(SyncSettings.ANSWER,true);show();selectAnswer();f.click("提交答案");f.waitFor(()->"本次同步答题成功 1 条，失败 1 条".equals(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()));assertEquals(1,sent.size());}
}
