package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Immutable course view model: live lessons first, with safe fallback to all courses. */
final class CourseCatalog {
    static final class Entry {
        final JSONObject course,live;
        Entry(JSONObject course,JSONObject live) { this.course=course; this.live=live; }
    }
    private final List<Entry> all,live;
    final int courseCount;
    private CourseCatalog(List<Entry> all,List<Entry> live,int count) { this.all=Collections.unmodifiableList(all); this.live=Collections.unmodifiableList(live); courseCount=count; }
    boolean hasLive() { return !live.isEmpty(); }
    int liveCount() { return live.size(); }
    List<Entry> entries(boolean preferLive) { return preferLive&&hasLive()?live:all; }
    static CourseCatalog from(JSONObject data) throws Exception {
        JSONArray courses=data.getJSONArray("courses"),lessons=data.getJSONArray("live");
        Map<String,JSONObject> metadata=new LinkedHashMap<>();
        for(int i=0;i<courses.length();i++) { JSONObject c=courses.optJSONObject(i); if(c==null||c.isNull("course_id")) continue; String id=c.optString("course_id"); if(!id.isEmpty()) metadata.put(id,c); }
        List<Entry> live=new ArrayList<>(),all=new ArrayList<>(); Set<String> seenLessons=new LinkedHashSet<>(),activeCourses=new LinkedHashSet<>(),courseIds=new LinkedHashSet<>(metadata.keySet());
        for(int i=0;i<lessons.length();i++) {
            JSONObject lesson=lessons.optJSONObject(i); if(lesson==null||lesson.isNull("courseId")||lesson.isNull("lessonId")) continue;
            String courseId=lesson.optString("courseId"),lessonId=lesson.optString("lessonId");
            if(courseId.isEmpty()||lessonId.isEmpty()||!seenLessons.add(lessonId)) continue;
            JSONObject c=metadata.get(courseId); if(c==null) c=RainApi.object("course_id",courseId,"course_name","正在进行的课堂","classroom_name","雨课堂课程");
            live.add(new Entry(c,lesson)); activeCourses.add(courseId); courseIds.add(courseId);
        }
        all.addAll(live);
        for(Map.Entry<String,JSONObject> e:metadata.entrySet()) if(!activeCourses.contains(e.getKey())) all.add(new Entry(e.getValue(),null));
        return new CourseCatalog(all,live,courseIds.size());
    }
}
