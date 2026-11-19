package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LessonQuizTest {
    long wall=1800000000000L,mono=500000;
    LessonQuiz quiz=new LessonQuiz("lesson",()->wall,()->mono);
    JSONObject spec(int type) throws Exception { return RainApi.object("problemId","q","problemType",type,"body","测试题目","options",new JSONArray("[{\"key\":\"A\",\"value\":\"一\"},{\"key\":\"C\",\"value\":\"二\"}]")); }
    void deck(JSONObject spec) throws Exception { quiz.deck("pres",RainApi.object("slides",new JSONArray().put(RainApi.object("problem",spec)))); }
    void open(long dt,int limit) throws Exception { quiz.ingest(RainApi.object("op","unlockproblem","presentationId","pres","problem",RainApi.object("prob","q","dt",dt,"limit",limit))); }
    void rejects(String message,Throwing action) throws Exception { try { action.run(); fail(message); } catch(Exception e) { assertTrue(e.getMessage(),e.getMessage().contains(message)); } }
    interface Throwing { void run() throws Exception; }
    @Test public void lockedSlidesAreViewableButCannotBeAnsweredUntilOpened() throws Exception { deck(spec(1)); LessonQuiz.Question q=quiz.get("q"); q.selected.add("A"); rejects("尚未开放",()->quiz.prepare(q)); open(wall,60); assertFalse(quiz.prepare(q).retry); }
    @Test public void countdownUsesElapsedTimeAndReplayDoesNotResetDraftOrSubmission() throws Exception {
        deck(spec(1)); open(wall,60); LessonQuiz.Question q=quiz.get("q"); q.selected.add("A"); mono+=20000; wall+=900000; assertEquals(40,quiz.remaining(q)); long round=q.round;
        open(q.eventTime,60); assertEquals(40,quiz.remaining(q)); assertEquals(round,q.round); assertTrue(q.selected.contains("A"));
        LessonQuiz.Submission s=quiz.prepare(q); quiz.begin(s); quiz.complete(s,null); open(q.eventTime,60); assertTrue(q.submitted);
    }
    @Test public void retryUsesOriginalEventTimeAndNullInsideProblemsEnvelope() throws Exception {
        deck(spec(2)); open(wall-61000,60); LessonQuiz.Question q=quiz.get("q"); q.selected.add("C"); LessonQuiz.Submission s=quiz.prepare(q); assertTrue(s.retry);
        JSONObject item=s.payload().getJSONArray("problems").getJSONObject(0); assertEquals(wall-61000,item.getLong("dt")); assertTrue(item.has("retry_times")); assertTrue(item.isNull("retry_times")); assertEquals("C",item.getJSONArray("result").getString(0));
    }
    @Test public void duplicateSubmissionIsBlockedAndFailurePreservesDraft() throws Exception {
        deck(spec(1)); open(wall,60); LessonQuiz.Question q=quiz.get("q"); q.selected.add("A"); LessonQuiz.Submission s=quiz.prepare(q); quiz.begin(s); rejects("正在提交",()->quiz.prepare(q)); rejects("重复",()->quiz.begin(s));
        quiz.complete(s,"接口错误"); assertFalse(q.submitted); assertEquals("接口错误",q.lastError); assertTrue(q.selected.contains("A")); quiz.begin(quiz.prepare(q));
    }
    @Test public void newRoundClearsOnlyThatQuestionAndDropsOldCompletion() throws Exception {
        deck(spec(1)); open(wall,60); LessonQuiz.Question q=quiz.get("q"); q.selected.add("A"); LessonQuiz.Submission old=quiz.prepare(q); quiz.begin(old); mono+=1000; wall+=1000; open(wall,60); assertTrue(q.selected.isEmpty()); quiz.complete(old,null); assertFalse(q.submitted); rejects("轮次已变化",()->quiz.begin(old));
    }
    @Test public void singleChoiceAndVoteLimitsAndOptionKeysAreValidated() throws Exception {
        deck(spec(1)); open(wall,60); LessonQuiz.Question q=quiz.get("q"); q.selected.add("A"); q.selected.add("C"); rejects("超出",()->quiz.prepare(q)); q.selected.clear(); q.selected.add("一"); rejects("选项已变化",()->quiz.prepare(q));
        JSONObject vote=spec(3); vote.put("pollingCount",2); deck(vote); q.selected.clear(); q.selected.add("C"); q.selected.add("A"); assertEquals("[\"A\",\"C\"]",quiz.prepare(q).payload().getJSONArray("result").toString());
    }
    @Test public void blankOrderAndSubjectiveTextHaveSeparatePayloads() throws Exception {
        JSONObject blanks=spec(4); blanks.put("blanks",new JSONArray("[{\"num\":2},{\"num\":1}]")); deck(blanks); open(wall,60); LessonQuiz.Question q=quiz.get("q"); q.fields.set(0,"first"); rejects("第 2 空",()->quiz.prepare(q)); q.fields.set(1,"second"); assertEquals("[\"first\",\"second\"]",quiz.prepare(q).payload().getJSONArray("result").toString());
        deck(spec(5)); q.text="推导过程"; JSONObject result=quiz.prepare(q).payload().getJSONObject("result"); assertEquals("推导过程",result.getString("content")); assertEquals(0,result.getJSONArray("pics").length()); assertEquals(0,result.getJSONArray("videos").length());
    }
    @Test public void unknownQuestionTypeAndMissingRetryTimestampCannotBeGuessed() throws Exception {
        deck(spec(99)); open(wall,60); LessonQuiz.Question q=quiz.get("q"); rejects("尚未确认",()->quiz.prepare(q)); deck(spec(1)); q.selected.add("A"); q.eventTime=0; mono+=61000; rejects("原始时间",()->quiz.prepare(q));
    }
    @Test public void timelineRecoversHistoricalQuestionsAndTeacherExtensionIsPerQuestion() throws Exception {
        deck(spec(1)); quiz.ingest(RainApi.object("op","hello","timeline",new JSONArray().put(RainApi.object("type","problem","prob","q","pres","pres","dt",wall-61000,"limit",60)))); LessonQuiz.Question q=quiz.get("q"); assertTrue(quiz.expired(q));
        JSONObject extension=RainApi.object("op","extendtime","problem",RainApi.object("prob","q","extend",30,"dt",wall)); quiz.ingest(extension); assertEquals(30,quiz.remaining(q)); quiz.ingest(extension); assertEquals(30,quiz.remaining(q)); assertFalse(quiz.expired(q));
    }
    @Test public void timeChangeWhileConfirmingAndSpecChangeInvalidatePreparedRequest() throws Exception {
        deck(spec(1)); open(wall,1); LessonQuiz.Question q=quiz.get("q"); q.selected.add("A"); LessonQuiz.Submission s=quiz.prepare(q); mono+=1000; rejects("时限已变化",()->quiz.begin(s));
        LessonQuiz.Submission retry=quiz.prepare(q); JSONObject changed=spec(1); changed.put("body","新题干"); deck(changed); rejects("内容已变化",()->quiz.begin(retry));
    }
    @Test public void lessonFinishedUsesRetryAndDraftsDoNotLeakToAnotherLesson() throws Exception {
        deck(spec(5)); open(wall,60); LessonQuiz.Question q=quiz.get("q"); q.text="我的答案"; quiz.ingest(RainApi.object("op","lessonfinished","finished",true)); assertTrue(quiz.prepare(q).retry);
        LessonQuiz other=new LessonQuiz("other",()->wall,()->mono); assertTrue(other.all().isEmpty());
    }
    @Test public void judgmentUsesOneOriginalOptionKeyAndPreservesTypeSix()throws Exception {
        deck(spec(6));open(wall,60);LessonQuiz.Question q=quiz.get("q");assertEquals("判断题",q.typeName());assertTrue(q.supported());q.selected.add("C");JSONObject body=quiz.prepare(q).payload();assertEquals(6,body.getInt("problemType"));assertEquals("[\"C\"]",body.getJSONArray("result").toString());q.selected.add("A");rejects("超出",()->quiz.prepare(q));
    }
    @Test public void judgmentDoesNotInventMissingOptionsOrBooleanKeys()throws Exception {
        JSONObject problem=spec(6);problem.remove("options");deck(problem);open(wall,60);LessonQuiz.Question q=quiz.get("q");q.selected.add("true");rejects("选项已变化",()->quiz.prepare(q));
    }
    @Test public void subjectiveAllowsImagesWithoutTextAndCopiesImagesIntoFrozenDraft()throws Exception {
        deck(spec(5));open(wall,60);LessonQuiz.Question q=quiz.get("q");q.images.add(new AnswerImage("photo",new byte[]{1},"https://qn-scd1.yuketang.cn/photo.jpg"));LessonQuiz copy=quiz.answerCopy(q,null);q.images.clear();JSONObject result=copy.prepare(copy.get("q")).payload().getJSONObject("result");assertEquals("",result.getString("content"));assertEquals("https://qn-scd1.yuketang.cn/photo.jpg",result.getJSONArray("pics").getJSONObject(0).getString("pic"));
    }
    @Test public void uploadingImageBlocksSubmissionAndNewRoundClearsImages()throws Exception {
        deck(spec(5));open(wall,60);LessonQuiz.Question q=quiz.get("q");q.imageBusy=true;q.text="答案";rejects("上传完成",()->quiz.prepare(q));q.imageBusy=false;q.images.add(new AnswerImage("photo",new byte[]{1},"https://qn-scd1.yuketang.cn/photo.jpg"));open(wall+1000,60);assertTrue(q.images.isEmpty());assertFalse(q.imageBusy);
    }
}
