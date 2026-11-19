package com.ykt.assistant;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w393dp-h851dp-hdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class ClassroomQuizDialogTest {
    private ActivityController<Activity> controller; private Activity activity; private ClassroomQuizDialog sheet; private FakeApi api;
    private ExecutorService worker; private LessonQuiz quiz; private AtomicBoolean current; private long wall=1800000000000L,mono=5000;
    static class FakeApi extends RainApi {
        volatile java.util.concurrent.CountDownLatch entered,release; volatile int submissions; volatile LessonQuiz.Submission sent; volatile boolean reject; JSONObject deck;
        FakeApi() throws Exception { super(okhttp3.HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store()); }
        @Override public JSONObject submitAnswer(LessonQuiz.Submission s) throws Exception { sent=s; submissions++; if(entered!=null) { entered.countDown(); if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS)) throw new Exception("timeout"); } if(reject) throw new Exception("服务端拒绝"); return RainApi.object("code",0); }
        @Override public JSONObject quizPresentation(String lesson,String pres) { return deck; }
    }
    @Before public void setup() throws Exception {
        controller=Robolectric.buildActivity(Activity.class).setup(); activity=controller.get(); api=new FakeApi(); worker=Executors.newSingleThreadExecutor(); current=new AtomicBoolean(true); quiz=new LessonQuiz("lesson",()->wall,()->mono);
    }
    @After public void cleanup() { if(sheet!=null) sheet.close(); worker.shutdownNow(); api.close(); controller.pause().stop().destroy(); }
    private JSONObject spec(String id,int type) throws Exception { return RainApi.object("problemId",id,"problemType",type,"body","<p>测试题干</p>","options",new JSONArray("[{\"key\":\"A\",\"value\":\"甲\"},{\"key\":\"B\",\"value\":\"乙\"}]")); }
    private void question(String id,int type,boolean expired) throws Exception {
        quiz.deck("pres",RainApi.object("slides",new JSONArray().put(RainApi.object("problem",spec(id,type))))); quiz.ingest(RainApi.object("op","unlockproblem","presentationId","pres","problem",RainApi.object("prob",id,"dt",expired?wall-61000:wall,"limit",60)));
    }
    private void show(String initial) { sheet=new ClassroomQuizDialog(activity,api,quiz,worker,current::get); sheet.show(initial); ShadowLooper.idleMainLooper(); }
    private List<View> all(View v) { List<View> out=new ArrayList<>(); out.add(v); if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) out.addAll(all(((ViewGroup)v).getChildAt(i))); return out; }
    private View find(View root,String text) { for(View v:all(root)) if(v instanceof TextView&&((TextView)v).getText().toString().equals(text)) return v; return null; }
    private View root() { List<Dialog> dialogs=ShadowDialog.getShownDialogs(); for(int i=dialogs.size()-1;i>=0;i--) if(dialogs.get(i).isShowing()) return dialogs.get(i).getWindow().getDecorView(); throw new AssertionError("no shown dialog"); }
    private void click(String label) { View v=find(root(),label); assertNotNull(label,v); v.performClick(); ShadowLooper.idleMainLooper(); }
    private void waitFor(BooleanSupplier ready) throws Exception { for(int i=0;i<150;i++) { ShadowLooper.idleMainLooper(); if(ready.getAsBoolean()) return; Thread.sleep(10); } fail("callback did not finish"); }
    @Test public void choiceSubmitsManuallyOnlyAndPreventsDuplicateClicks() throws Exception {
        question("q",1,false); show("q"); assertEquals(0,api.submissions); assertNotNull(find(root(),"测试题干")); click("A. 甲"); assertEquals(0,api.submissions); click("提交答案"); waitFor(()->quiz.get("q").submitted); assertEquals(1,api.submissions); assertFalse(api.sent.retry); assertNotNull(find(root(),"已提交")); click("已提交"); assertEquals(1,api.submissions);
    }
    @Test public void singleSelectionAndDraftSurviveQuestionSwitch() throws Exception {
        question("first",1,false); question("second",1,false); show("first"); click("A. 甲"); click("B. 乙"); assertEquals(java.util.Collections.singleton("B"),quiz.get("first").selected);
        click("切换题目（2）"); View second=all(root()).stream().filter(v->v instanceof TextView&&((TextView)v).getText().toString().startsWith("2 ·")).findFirst().get(); second.performClick(); ShadowLooper.idleMainLooper(); click("A. 甲"); assertEquals(java.util.Collections.singleton("B"),quiz.get("first").selected); assertEquals(java.util.Collections.singleton("A"),quiz.get("second").selected);
        click("切换题目（2）"); all(root()).stream().filter(v->v instanceof TextView&&((TextView)v).getText().toString().startsWith("1 ·")).findFirst().get().performClick(); assertTrue(((CheckBox)find(root(),"B. 乙")).isChecked());
    }
    @Test public void retryRequiresConfirmationAndCancelDoesNotSend() throws Exception {
        question("q",2,true); quiz.get("q").selected.add("A"); show("q"); click("补交答案"); assertNotNull(find(root(),"确认补交答案？")); assertEquals(0,api.submissions); root().findViewWithTag("card-close").performClick(); ShadowLooper.idleMainLooper(); assertEquals(0,api.submissions);
        click("补交答案"); click("确认补交"); waitFor(()->quiz.get("q").submitted); assertEquals(1,api.submissions); assertTrue(api.sent.retry);
    }
    @Test public void textDraftSurvivesFailureAndAccountChangeClosesSheetWithoutSending() throws Exception {
        question("q",5,false); show("q"); EditText input=(EditText)all(root()).stream().filter(v->v instanceof EditText).findFirst().get(); input.setText("我的推导"); api.reject=true; click("提交答案"); waitFor(()->!quiz.get("q").sending&&!quiz.get("q").lastError.isEmpty()); assertEquals("我的推导",quiz.get("q").text); assertFalse(quiz.get("q").submitted); assertEquals(1,api.submissions);
        current.set(false); click("提交答案"); assertFalse(sheet.showing()); assertEquals(1,api.submissions);
    }
    @Test public void openedMetadataCanFetchOriginalQuestionWithoutRejoining() throws Exception {
        quiz.ingest(RainApi.object("op","unlockproblem","presentationId","past-pres","problem",RainApi.object("prob","q","dt",wall,"limit",60))); api.deck=RainApi.object("slides",new JSONArray().put(RainApi.object("problem",spec("q",1)))); show("q"); waitFor(()->quiz.get("q").spec!=null); assertNotNull(find(root(),"测试题干")); assertEquals(0,api.submissions);
    }
    @Test public void unsupportedQuestionTypeRemainsReadOnly() throws Exception { question("q",99,false); show("q"); assertFalse(find(root(),"提交答案").isEnabled()); assertEquals(0,api.submissions); }
    @Test public void oldAccountCompletionSettlesItsDraftWithoutRevivingClosedUi() throws Exception {
        question("q",1,false); show("q"); click("A. 甲"); api.entered=new java.util.concurrent.CountDownLatch(1); api.release=new java.util.concurrent.CountDownLatch(1); click("提交答案"); assertTrue(api.entered.await(3,java.util.concurrent.TimeUnit.SECONDS));
        current.set(false); api.release.countDown(); waitFor(()->!quiz.get("q").sending); assertFalse(sheet.showing()); assertEquals(1,api.submissions);
    }
    @Test public void headerXClosesQuizWithoutSubmittingOrDiscardingDraft()throws Exception{
        question("q",1,false);show("q");click("A. 甲");assertNull(find(root(),"关闭"));assertTrue(root().findViewWithTag("card-close") instanceof android.widget.ImageButton);root().findViewWithTag("card-close").performClick();ShadowLooper.idleMainLooper();assertFalse(sheet.showing());assertTrue(quiz.get("q").selected.contains("A"));assertEquals(0,api.submissions);
    }
    @Test public void judgmentAllowsExactlyOneOriginalOptionAndSubmitsTypeSix()throws Exception{
        question("q",6,false);show("q");assertNotNull(find(root(),"判断题"));click("A. 甲");click("B. 乙");assertEquals(java.util.Collections.singleton("B"),quiz.get("q").selected);click("提交答案");waitFor(()->quiz.get("q").submitted);assertEquals(6,api.sent.payload().getInt("problemType"));assertEquals("[\"B\"]",api.sent.payload().getJSONArray("result").toString());
    }
}
