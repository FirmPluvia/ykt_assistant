package com.ykt.assistant;
import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class CourseCatalogTest {
    private JSONObject courses(JSONArray live) throws Exception {
        return RainApi.object("courses",new JSONArray().put(RainApi.object("course_id",9007199254740993L,"course_name","数学")).put(RainApi.object("course_id","english","course_name","英语")),"live",live);
    }
    @Test public void liveLessonsAreFirstAndKeepExactCourseIds() throws Exception {
        CourseCatalog c=CourseCatalog.from(courses(new JSONArray().put(RainApi.object("courseId","english","lessonId","e1"))));
        assertTrue(c.hasLive()); assertEquals(1,c.liveCount()); assertEquals("英语",c.entries(true).get(0).course.getString("course_name"));
        assertEquals("英语",c.entries(false).get(0).course.getString("course_name")); assertEquals("9007199254740993",c.entries(false).get(1).course.optString("course_id"));
    }
    @Test public void noLiveAlwaysFallsBackToAllCoursesIncludingAfterRefresh() throws Exception {
        CourseCatalog c=CourseCatalog.from(courses(new JSONArray()));
        assertFalse(c.hasLive()); assertEquals(0,c.liveCount()); assertEquals(2,c.entries(true).size()); assertEquals(2,c.entries(false).size());
    }
    @Test public void sameCourseCanHaveMultipleDistinctLiveLessonsWithoutDuplicatingSameLesson() throws Exception {
        JSONArray live=new JSONArray().put(RainApi.object("courseId","english","lessonId","e1")).put(RainApi.object("courseId","english","lessonId","e1")).put(RainApi.object("courseId","english","lessonId","e2"));
        CourseCatalog c=CourseCatalog.from(courses(live)); assertEquals(2,c.liveCount()); assertEquals(3,c.entries(false).size()); assertEquals(2,c.courseCount);
    }
    @Test public void liveLessonWithoutMetadataIsStillAccessible() throws Exception {
        CourseCatalog c=CourseCatalog.from(courses(new JSONArray().put(RainApi.object("courseId","new","lessonId","n1"))));
        assertTrue(c.hasLive()); assertEquals("n1",c.entries(true).get(0).live.getString("lessonId")); assertEquals(3,c.courseCount);
    }
    @Test public void invalidLiveEntriesCannotCreateUnjoinableCards() throws Exception {
        JSONArray live=new JSONArray().put(RainApi.object("courseId","english")).put(JSONObject.NULL).put(RainApi.object("courseId","english","lessonId",""));
        CourseCatalog c=CourseCatalog.from(courses(live)); assertFalse(c.hasLive()); assertEquals(2,c.entries(true).size());
    }
    @Test public void malformedResponsesAreNotConfusedWithEmptySuccess() throws Exception {
        try { CourseCatalog.from(RainApi.object("courses",new JSONArray())); fail(); } catch(org.json.JSONException expected) {}
    }
}
