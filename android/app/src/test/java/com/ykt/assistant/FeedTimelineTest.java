package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedTimelineTest {
    static JSONObject row(int id) throws Exception { return RainApi.object("id","row-"+id,"coursewareType",5,"coursewareId",id,"courseName","课程","coursewareTitle","动态 "+id,"createTime",10000-id,"sort","cursor-"+id); }
    static JSONArray rows(int from,int to) throws Exception { JSONArray array=new JSONArray(); for(int i=from;i<=to;i++) array.put(row(i)); return array; }
    @Test public void refreshedHeadReusesCachedTailAndUpdatesChangedRows() throws Exception {
        FeedTimeline cached=new FeedTimeline(ActivityFeed.from(rows(1,10)).append(rows(11,20))); JSONArray head=rows(-1,8); head.getJSONObject(9).put("coursewareTitle","更新标题"); FeedTimeline merged=cached.refresh(head);
        assertEquals(22,merged.visible.entries.size()); assertEquals("cursor-20",merged.visible.nextCursor); assertTrue(merged.deferred.isEmpty()); assertEquals("更新标题",merged.visible.entries.get(9).title()); assertEquals("row-20",merged.visible.entries.get(21).raw.getString("id"));
    }
    @Test public void missingBridgeIsRetainedAcrossRestartAndFilledWithoutSkippingRows() throws Exception {
        FeedTimeline fresh=new FeedTimeline(ActivityFeed.from(rows(21,40))).refresh(rows(1,10)); assertEquals(10,fresh.visible.entries.size()); assertEquals(1,fresh.deferred.size()); assertEquals("cursor-10",fresh.visible.nextCursor);
        fresh=FeedTimeline.decode(fresh.encode()).append(rows(11,20)); assertEquals(20,fresh.visible.entries.size()); assertEquals(1,fresh.deferred.size()); fresh=fresh.append(rows(21,30)); assertEquals(40,fresh.visible.entries.size()); assertTrue(fresh.deferred.isEmpty()); assertEquals("cursor-40",fresh.visible.nextCursor); for(int i=0;i<40;i++) assertEquals("row-"+(i+1),fresh.visible.entries.get(i).raw.getString("id"));
    }
    @Test public void deletionWithinRefreshedWindowDoesNotLeaveGhostRecord() throws Exception {
        JSONArray head=new JSONArray(); for(int i=0;i<=10;i++) if(i!=2) head.put(row(i)); FeedTimeline merged=new FeedTimeline(ActivityFeed.from(rows(1,20))).refresh(head); assertEquals(20,merged.visible.entries.size()); assertFalse(merged.visible.entries.stream().anyMatch(e -> e.raw.optString("id").equals("row-2"))); assertEquals("cursor-20",merged.visible.nextCursor);
    }
    @Test public void emptyAuthoritativeHeadClearsOldSegments() throws Exception {
        FeedTimeline empty=new FeedTimeline(ActivityFeed.from(rows(21,40))).refresh(rows(1,10)).refresh(new JSONArray()); assertTrue(empty.visible.entries.isEmpty()); assertTrue(empty.visible.endReached); assertTrue(empty.deferred.isEmpty());
    }
    @Test public void refreshRecoversBrokenCachedCursorAndRepeatedServerCursorStillStops() throws Exception {
        ActivityFeed broken=ActivityFeed.from(rows(1,10)).append(rows(1,10)); assertTrue(broken.stalled); FeedTimeline fresh=new FeedTimeline(broken).refresh(rows(1,10)); assertTrue(fresh.visible.hasMore()); fresh=fresh.append(rows(11,20)); assertTrue(fresh.visible.hasMore()); fresh=fresh.append(rows(11,20)); assertTrue(fresh.visible.stalled); assertFalse(fresh.visible.hasMore());
    }
    @Test public void severalUnconnectedRefreshesKeepHistoryUntilAllGapsConnect() throws Exception {
        FeedTimeline timeline=new FeedTimeline(ActivityFeed.from(rows(41,50))).refresh(rows(21,30)).refresh(rows(1,10)); assertEquals(2,timeline.deferred.size()); timeline=FeedTimeline.decode(timeline.encode()).append(rows(11,20)).append(rows(21,30)).append(rows(31,40)).append(rows(41,50)); assertEquals(50,timeline.visible.entries.size()); assertTrue(timeline.deferred.isEmpty()); assertEquals("cursor-50",timeline.visible.nextCursor);
    }
    @Test public void completedReportIsStableButActiveAndPartialReportsExpire() throws Exception {
        long now=System.currentTimeMillis(); JSONObject detail=RainApi.object("detail",RainApi.object("valid",1,"checkInTime",1)); LessonReport ended=new LessonReport("l",RainApi.object("end",now-1000),detail,null,null),active=new LessonReport("l",RainApi.object("end",0),detail,null,null);
        assertTrue(ActivitySnapshot.fresh(ended,1,now)); assertTrue(ActivitySnapshot.fresh(active,now,now)); assertFalse(ActivitySnapshot.fresh(active,now-ActivitySnapshot.ACTIVE_TTL,now)); assertFalse(ActivitySnapshot.fresh(new LessonReport("l",null,detail,null,null),now,now));
    }    @Test public void activityMovedToTheHeadIsNotMistakenForAContiguousHistoryAnchor() throws Exception {
        FeedTimeline old=new FeedTimeline(ActivityFeed.from(rows(1,100))); JSONArray head=new JSONArray().put(row(100).put("createTime",20000).put("sort","moved-cursor")); for(int i=-8;i<=0;i++) head.put(row(i)); FeedTimeline fresh=old.refresh(head); assertEquals(10,fresh.visible.entries.size()); assertEquals(1,fresh.deferred.size()); fresh=fresh.append(rows(1,10)); assertEquals(109,fresh.visible.entries.size()); assertTrue(fresh.visible.entries.stream().anyMatch(e -> e.raw.optString("id").equals("row-99"))); assertEquals(1,fresh.visible.entries.stream().filter(e -> e.raw.optString("id").equals("row-100")).count());
    }

}
