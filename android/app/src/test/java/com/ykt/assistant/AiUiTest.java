package com.ykt.assistant;
import android.app.Activity;
import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;
import okhttp3.*;
import okio.Buffer;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,qualifiers="w393dp-h851dp-hdpi") @LooperMode(LooperMode.Mode.PAUSED)
public class AiUiTest {
    ActivityController<Activity> controller;Activity activity;ExecutorService worker;LessonQuiz quiz;LessonQuiz.Question q;ClassroomQuizDialog sheet;AiSettingsDialog settingsDialog;Fake api;AtomicBoolean current=new AtomicBoolean(true);CountDownLatch entered,release;
    static class Fake extends RainApi {int submissions;Fake()throws Exception{super(HttpUrl.get("https://www.yuketang.cn/"),new RainApiTest.Store());}@Override public JSONObject submitAnswer(LessonQuiz.Submission s)throws Exception{submissions++;return RainApi.object("code",0);}}
    @Before public void setup()throws Exception{RuntimeEnvironment.getApplication().getSharedPreferences("ai_settings",0).edit().clear().commit();controller=Robolectric.buildActivity(Activity.class).setup();activity=controller.get();worker=Executors.newSingleThreadExecutor();api=new Fake();quiz=new LessonQuiz("lesson",()->1800000000000L,()->1000L);JSONObject spec=RainApi.object("problemId","q","problemType",1,"body","题目","options",new JSONArray("[{\"key\":\"A\",\"value\":\"甲\"},{\"key\":\"B\",\"value\":\"乙\"}]"));quiz.deck("pres",RainApi.object("slides",new JSONArray().put(RainApi.object("problem",spec))));quiz.ingest(RainApi.object("op","unlockproblem","problem",RainApi.object("prob","q","dt",1800000000000L,"limit",60)));q=quiz.get("q");}
    @After public void cleanup(){if(release!=null)release.countDown();if(sheet!=null)sheet.close();if(settingsDialog!=null)settingsDialog.close();worker.shutdownNow();api.close();controller.pause().stop().destroy();ShadowLooper.idleMainLooper();}
    View root(){List<Dialog> list=ShadowDialog.getShownDialogs();for(int i=list.size()-1;i>=0;i--)if(list.get(i).isShowing())return list.get(i).getWindow().getDecorView();return activity.getWindow().getDecorView();}
    List<View> all(View v){List<View> out=new ArrayList<>();out.add(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)out.addAll(all(((ViewGroup)v).getChildAt(i)));return out;}
    View find(String text){for(View v:all(root()))if(v instanceof TextView&&((TextView)v).getText().toString().equals(text))return v;return null;}
    void click(String text){View v=find(text);assertNotNull(text,v);v.performClick();ShadowLooper.idleMainLooper();}
    void waitFor(BooleanSupplier ready)throws Exception{for(int i=0;i<250;i++){ShadowLooper.idleMainLooper();if(ready.getAsBoolean())return;Thread.sleep(10);}fail("callback timeout");}
    void configure(boolean auto)throws Exception{new AiSettings(activity).save("https://ai.example/v1","model","",false,true,false,auto,()->false);}
    void show()throws Exception{sheet=new ClassroomQuizDialog(activity,api,quiz,worker,current::get);sheet.setAiFactory((config,ua)->new AiClient(config,ua,new OkHttpClient.Builder().addInterceptor(chain->{if(entered!=null){entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}Buffer buffer=new Buffer();chain.request().body().writeTo(buffer);try{JSONObject request=new JSONObject(buffer.readUtf8());JSONObject question=new JSONObject(request.getJSONArray("messages").getJSONObject(1).getString("content"));JSONObject answer=RainApi.object("schema_version",1,"question_token",question.getString("question_token"),"problem_type",1,"status","answered","answer",RainApi.object("option_keys",new JSONArray().put("B"),"blank_values",new JSONArray(),"text",""),"explanation","测试模型的理由","confidence",.8);String body=RainApi.object("choices",new JSONArray().put(RainApi.object("finish_reason","stop","message",RainApi.object("content",answer.toString())))).toString();return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(ResponseBody.create(body,MediaType.get("application/json"))).build();}catch(Exception e){throw new java.io.IOException(e);}})));sheet.show("q");ShadowLooper.idleMainLooper();}
    @Test public void parsedSuggestionFillsOnTapAndNeverSubmits()throws Exception{configure(false);show();click("AI 分析");waitFor(()->find("分析完成")!=null);assertTrue(q.selected.isEmpty());click("填入答案");assertEquals(java.util.Collections.singleton("B"),q.selected);assertEquals(0,api.submissions);assertNotNull(find("已填入，尚未提交"));assertNull(find("再次填入答案"));assertEquals(View.GONE,find("填入答案").getVisibility());assertNotNull(find("提交"));root().findViewWithTag("card-close").performClick();ShadowLooper.idleMainLooper();assertTrue(((android.widget.CheckBox)find("B. 乙")).isChecked());}
    @Test public void optionalAutoFillSelectsWithoutSubmitting()throws Exception{configure(true);show();click("AI 分析");waitFor(()->find("已自动填入，尚未提交")!=null);assertEquals(java.util.Collections.singleton("B"),q.selected);assertEquals(0,api.submissions);}
    @Test public void changedRoundCancelsAndDiscardsLateSuggestion()throws Exception{configure(true);entered=new CountDownLatch(1);release=new CountDownLatch(1);show();click("AI 分析");assertTrue(entered.await(3,TimeUnit.SECONDS));q.round++;sheet.changed();release.countDown();waitFor(()->find("AI 分析")!=null);for(int i=0;i<10;i++){Thread.sleep(10);ShadowLooper.idleMainLooper();}assertTrue(q.selected.isEmpty());assertEquals(0,api.submissions);}
    @Test public void settingsNeverPrefillStoredKeyAndBlankSavePreservesIt()throws Exception{
        javax.crypto.KeyGenerator g=javax.crypto.KeyGenerator.getInstance("AES");g.init(128);javax.crypto.SecretKey secret=g.generateKey();AiSettings settings=new AiSettings(activity,new AiSettings.CipherCodec(create->secret));settings.save("https://ai.example/v1","model","saved-private-key",false,false,false,false,()->false);AtomicBoolean saved=new AtomicBoolean();settingsDialog=new AiSettingsDialog(activity,settings,worker,()->saved.set(true));settingsDialog.show();EditText key=root().findViewWithTag("ai-key");assertEquals("",key.getText().toString());assertTrue(key.getHint().toString().contains("已保存"));click("保存 AI 配置");waitFor(saved::get);assertEquals("saved-private-key",settings.read().key);
    }
    @Test public void analysisSubmitFillsAndSendsOnlyOnExplicitTap()throws Exception {
        configure(false);show();click("AI 分析");waitFor(()->find("分析完成")!=null);assertEquals(0,api.submissions);click("提交");waitFor(()->q.submitted);assertEquals(java.util.Collections.singleton("B"),q.selected);assertEquals(1,api.submissions);assertNotNull(find("已提交"));
    }
    @Test public void expiredAnalysisSubmitRetainsRetryConfirmation()throws Exception {
        configure(false);show();click("AI 分析");waitFor(()->find("分析完成")!=null);q.deadline=0;click("提交");assertNotNull(find("确认补交答案？"));assertEquals(0,api.submissions);root().findViewWithTag("card-close").performClick();ShadowLooper.idleMainLooper();assertEquals(0,api.submissions);assertEquals(java.util.Collections.singleton("B"),q.selected);
    }
    @Test public void newSettingsUseExampleHintAndHideRemovedOptions()throws Exception {
        settingsDialog=new AiSettingsDialog(activity,new AiSettings(activity),worker,()->{});settingsDialog.show();EditText endpoint=root().findViewWithTag("ai-endpoint");assertEquals("",endpoint.getText().toString());assertTrue(endpoint.getHint().toString().contains("https://"));assertNull(find("清除已保存的 Key"));assertNull(find("启用 JSON 模式参数（接口需支持）"));assertNull(find("查看 JSON 输出约定"));assertNotNull(root().findViewWithTag("card-rounded-viewport"));
    }
}
