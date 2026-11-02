package com.ykt.assistant;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class HistoryModelsTest {
    private JSONObject event(String id,int index,String pres,long dt,String url,boolean visible) throws Exception { return RainApi.object("id",id,"index",index,"presentationId",pres,"type","slide","dt",dt,"cover",url,"visible",visible); }
    @Test public void activitySortAndLessonLinkDoNotLoseLargeIntegerPrecision() throws Exception {
        JSONArray list=new JSONArray().put(RainApi.object("coursewareType",14,"coursewareId",9100000000000000001L,"createTime",100L,"courseName","旧记录"))
            .put(RainApi.object("coursewareType",14,"coursewareId","9100000000000000001","createTime",200L,"courseName","新记录"))
            .put(RainApi.object("coursewareType",3,"coursewareId","other","createTime",150L,"id","notification"));
        ActivityFeed feed=ActivityFeed.from(list); assertEquals(2,feed.entries.size()); assertEquals("9100000000000000001",feed.entries.get(0).lessonId()); assertEquals("新记录",feed.entries.get(0).course()); assertEquals("",feed.entries.get(1).lessonId());
    }
    @Test public void missingOrFailedAttendanceIsNeverReportedAsAbsentOrSigned() throws Exception {
        assertEquals("未返回签到记录",new LessonReport("l",null,null,null,null).attendanceLabel());
        LessonReport unknown=new LessonReport("l",null,RainApi.object("detail",RainApi.object("valid",0,"checkInTime",0)),null,null);
        assertFalse(unknown.signed()); assertEquals("签到待确认",unknown.attendanceLabel());
        LessonReport confirmed=new LessonReport("l",null,RainApi.object("detail",RainApi.object("valid",1,"checkInTime",1767225600000L,"checkInName","扫二维码")),null,null);
        assertTrue(confirmed.signed()); assertEquals(1767225600000L,confirmed.checkinTime());
        assertFalse(new LessonReport("l",null,confirmed.detail,null,"network error").signed());
    }
    @Test public void reviewDeduplicatesAnimationStepsAndPreservesOriginalPageNumbersAndSignedUrl() throws Exception {
        String signed="https://changjiang-private-qn.yuketang.cn/slide.jpg?e=2000000000&token=a%2Bb%3D";
        JSONArray events=new JSONArray().put(event("p22",22,"pres",1,"https://old.yuketang.cn/a.jpg",true)).put(event("p1",1,"pres",2,signed,true)).put(event("p22",22,"pres",3,signed,true)).put(event("other",2,"another",4,signed,true));
        JSONObject p=ReviewPresentation.build("lesson",RainApi.object("presentationId","pres","title","课件","totalCount",34),RainApi.object("timelineList",events));
        assertEquals(2,p.getJSONArray("slides").length()); assertEquals(1,p.getJSONArray("slides").getJSONObject(0).getInt("index")); assertEquals(22,p.getJSONArray("slides").getJSONObject(1).getInt("index")); assertEquals(signed,p.getJSONArray("slides").getJSONObject(1).getString("cover")); assertEquals(34,p.getInt("reportedTotal")); assertFalse(p.has("width"));
    }
    @Test public void latestHiddenPageIsNotRecoveredFromAnEarlierVisibleEvent() throws Exception {
        JSONArray events=new JSONArray().put(event("p1",1,"pres",1,"https://qn.yuketang.cn/a.jpg",true)).put(event("p1",1,"pres",2,"https://qn.yuketang.cn/a.jpg",false));
        try { ReviewPresentation.build("l",RainApi.object("presentationId","pres"),RainApi.object("timelineList",events)); fail(); } catch(RainApi.Failure e) { assertEquals(409,e.code); }
    }
    @Test public void capturedResponsesProduceReadonlyHistoryAndDeduplicatedPages() throws Exception {
        JSONObject captured;
        try(java.io.InputStream input=getClass().getResourceAsStream("/captured-history-redacted.json")) { assertNotNull(input); captured=new JSONObject(new String(RainApi.readLimit(input,1024*1024),java.nio.charset.StandardCharsets.UTF_8)); }
        JSONObject info=captured.getJSONObject("/api/v3/classroom-report/student/lesson-info").getJSONObject("data"),detail=captured.getJSONObject("/api/v3/classroom-report/student/detail").getJSONObject("data"),review=captured.getJSONObject("/api/v3/classroom-report/student/review").getJSONObject("data");
        ActivityFeed feed=ActivityFeed.from(captured.getJSONObject("/api/v3/activities/received/").getJSONArray("data")); assertEquals(10,feed.entries.size());
        assertTrue(feed.entries.stream().anyMatch(e -> e.lessonId().equals(info.optString("lessonId"))));
        LessonReport report=new LessonReport(info.getString("lessonId"),info,detail,null,null); assertTrue(report.signed()); assertEquals(2,report.presentations().length());
        int pages=0,total=0;
        for(int i=0;i<report.presentations().length();i++) { JSONObject summary=report.presentations().getJSONObject(i),p=ReviewPresentation.build(report.lessonId,summary,review); pages+=p.getJSONArray("slides").length(); total+=p.getInt("reportedTotal"); }
        assertEquals(63,review.getJSONArray("timelineList").length()); assertEquals(24,pages); assertEquals(65,total);
    }
    @Test public void endedAndSuccessfullyReadUnconfirmedAttendanceBecomesAbsent() throws Exception {
        JSONObject info=RainApi.object("end",System.currentTimeMillis()-1000),detail=RainApi.object("detail",RainApi.object("valid",0,"checkInTime",0));
        LessonReport report=new LessonReport("l",info,detail,null,null); assertTrue(report.ended()); assertTrue(report.absent()); assertEquals("未签到",report.attendanceLabel());
        LessonReport missingFields=new LessonReport("l",info,new JSONObject(),null,null); assertEquals("未签到",missingFields.attendanceLabel());
    }
    @Test public void futureOrOngoingLessonsAndNetworkErrorsAreNotMarkedAbsent() throws Exception {
        JSONObject detail=RainApi.object("detail",RainApi.object("valid",0,"checkInTime",0));
        for(long end:new long[]{0,System.currentTimeMillis()+60000}) { LessonReport report=new LessonReport("l",RainApi.object("end",end),detail,null,null); assertFalse(report.absent()); assertEquals("签到待确认",report.attendanceLabel()); }
        JSONObject past=RainApi.object("end",System.currentTimeMillis()-1000);
        assertEquals("签到信息暂不可用",new LessonReport("l",past,null,null,"network error").attendanceLabel());
        assertFalse(new LessonReport("l",past,detail,"info failed",null).absent());
    }
    @Test public void confirmedAttendanceRemainsSignedAfterClassEnds() throws Exception {
        LessonReport report=new LessonReport("l",RainApi.object("end",System.currentTimeMillis()-1000),RainApi.object("detail",RainApi.object("valid",1,"checkInTime",1767225600000L)),null,null);
        assertFalse(report.absent()); assertEquals("已签到",report.attendanceLabel());
    }

}
