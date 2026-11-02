package com.ykt.assistant;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class PaginationTest {
    private JSONObject row(String id,long time,String sort) throws Exception { return RainApi.object("id","event-"+id,"coursewareType",14,"coursewareId",id,"courseName",id,"createTime",time,"sort",sort); }
    @Test public void cursorComesFromOriginalTailNotSortedOrDeduplicatedDisplay() throws Exception {
        String raw=" [1760000000000,\"opaque+tail%2F\"] ";
        ActivityFeed feed=ActivityFeed.from(new JSONArray().put(row("older",1,"other")).put(row("newer",9,raw)));
        assertEquals("newer",feed.entries.get(0).lessonId()); assertEquals(raw,feed.nextCursor); assertTrue(feed.hasMore());
    }
    @Test public void appendDeduplicatesAcrossPagesAndAdvancesEvenWhenTailIsDuplicate() throws Exception {
        ActivityFeed first=ActivityFeed.from(new JSONArray().put(row("a",10,"c1")).put(row("b",9,"c2")));
        ActivityFeed next=first.append(new JSONArray().put(row("c",8,"c3")).put(row("a",7,"c4")));
        assertEquals(3,next.entries.size()); assertEquals("a",next.entries.get(0).lessonId()); assertEquals("c4",next.nextCursor); assertTrue(next.hasMore()); assertEquals(2,first.entries.size());
    }
    @Test public void emptyPageEndsPaginationWithoutErasingLoadedEntries() throws Exception {
        ActivityFeed first=ActivityFeed.from(new JSONArray().put(row("a",10,"c1"))),done=first.append(new JSONArray());
        assertEquals(1,done.entries.size()); assertTrue(done.endReached); assertFalse(done.hasMore());
    }
    @Test public void repeatingOrCyclingCursorStopsWithoutLosingRows() throws Exception {
        ActivityFeed first=ActivityFeed.from(new JSONArray().put(row("a",10,"c1")));
        ActivityFeed repeated=first.append(new JSONArray().put(row("b",9,"c1"))); assertTrue(repeated.stalled); assertFalse(repeated.hasMore()); assertEquals(2,repeated.entries.size());
        ActivityFeed cycle=first.append(new JSONArray().put(row("b",9,"c2"))).append(new JSONArray().put(row("c",8,"c1"))); assertTrue(cycle.stalled); assertEquals(3,cycle.entries.size());
    }
    @Test public void homeworkPreservesPaginationButIsNotAClassroomLesson() throws Exception {
        ActivityFeed feed=ActivityFeed.from(new JSONArray().put(row("a",10,"c1")).put(RainApi.object("id","homework","coursewareType",5,"coursewareId",123,"createTime",9,"sort","homework-cursor")));
        assertEquals("",feed.entries.get(1).lessonId()); assertEquals("作业通知",feed.entries.get(1).kind()); assertEquals("homework-cursor",feed.nextCursor);
    }
    @Test public void missingOrNonStringCursorIsNotInvented() throws Exception {
        assertFalse(ActivityFeed.from(new JSONArray().put(RainApi.object("id","x"))).hasMore());
        assertFalse(ActivityFeed.from(new JSONArray().put(RainApi.object("id","x","sort",new JSONArray()))).hasMore());
    }
}
