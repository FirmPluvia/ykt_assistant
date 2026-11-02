package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pagination uses the raw response tail's opaque sort string, before sorting or deduplication. */
final class ActivityFeed {
    static final class Entry {
        final JSONObject raw;
        Entry(JSONObject raw) { this.raw=raw; }
        String lessonId() { return raw.optInt("coursewareType",-1)==14&&!raw.isNull("coursewareId")?raw.optString("coursewareId"):""; }
        String course() { return label(raw,"courseName","课程动态"); }
        String title() { return label(raw,"coursewareTitle","课堂记录"); }
        String teacher() { return label(raw,"teacher","教师信息未提供"); }
        String kind() { return !lessonId().isEmpty()?"课堂记录":raw.optInt("coursewareType",-1)==5?"作业通知":"课程动态"; }
        long created() { return raw.optLong("createTime",0); }
    }
    final List<Entry> entries;
    final String nextCursor;
    final boolean endReached,stalled;
    private final Set<String> seenCursors;
    private ActivityFeed(List<Entry> entries,String cursor,boolean end,boolean stalled,Set<String> seen) {
        this.entries=Collections.unmodifiableList(entries); nextCursor=cursor; endReached=end; this.stalled=stalled; seenCursors=Collections.unmodifiableSet(seen);
    }
    boolean hasMore() { return !endReached&&!stalled&&nextCursor!=null; }
    static ActivityFeed from(JSONArray array) { return page(null,array); }
    ActivityFeed append(JSONArray array) { return page(this,array); }
    private static ActivityFeed page(ActivityFeed previous,JSONArray array) {
        List<Entry> ordered=new ArrayList<>();
        for(int i=0;i<array.length();i++) { JSONObject raw=array.optJSONObject(i); if(raw!=null) ordered.add(new Entry(raw)); }
        if(previous!=null) ordered.addAll(previous.entries);
        ordered.sort(Comparator.comparingLong(Entry::created).reversed());
        Map<String,Entry> unique=new LinkedHashMap<>(); int anonymous=0;
        for(Entry entry:ordered) { String key=!entry.lessonId().isEmpty()?"lesson:"+entry.lessonId():label(entry.raw,"id","row:"+(anonymous++)); if(!unique.containsKey(key)) unique.put(key,entry); }
        JSONObject tail=array.length()==0?null:array.optJSONObject(array.length()-1);
        Object rawSort=tail==null?null:tail.opt("sort");
        String cursor=rawSort instanceof String&&!((String)rawSort).trim().isEmpty()?(String)rawSort:null;
        Set<String> seen=new LinkedHashSet<>(); if(previous!=null) seen.addAll(previous.seenCursors);
        boolean repeated=cursor!=null&&seen.contains(cursor); if(cursor!=null) seen.add(cursor);
        return new ActivityFeed(new ArrayList<>(unique.values()),cursor,array.length()==0,repeated,seen);
    }
    static String key(Entry entry) { return !entry.lessonId().isEmpty()?"lesson:"+entry.lessonId():label(entry.raw,"id",entry.raw.toString()); }
    private static String anchor(Entry entry) { return key(entry)+"\n"+entry.created()+"\n"+entry.raw.optString("sort",""); }
    boolean overlaps(ActivityFeed other) { Set<String> anchors=new LinkedHashSet<>(); for(Entry e:entries) anchors.add(anchor(e)); for(Entry e:other.entries) if(anchors.contains(anchor(e))) return true; return false; }
    static ActivityFeed joinPrefix(ActivityFeed fresh,ActivityFeed cached) {
        Set<String> keys=new LinkedHashSet<>(),anchors=new LinkedHashSet<>(); for(Entry e:fresh.entries) { keys.add(key(e)); anchors.add(anchor(e)); } int cut=-1;
        for(int i=0;i<cached.entries.size();i++) if(anchors.contains(anchor(cached.entries.get(i)))) cut=i;
        if(cut<0) throw new IllegalArgumentException("分页片段尚未衔接");
        List<Entry> rows=new ArrayList<>(fresh.entries); for(int i=cut+1;i<cached.entries.size();i++) if(keys.add(key(cached.entries.get(i)))) rows.add(cached.entries.get(i));
        rows.sort(Comparator.comparingLong(Entry::created).reversed()); Set<String> seen=new LinkedHashSet<>(fresh.seenCursors); seen.addAll(cached.seenCursors);
        return new ActivityFeed(rows,cached.nextCursor,cached.endReached,fresh.stalled||cached.stalled,seen);
    }
    JSONObject encode() throws Exception {
        JSONArray rows=new JSONArray(),seen=new JSONArray(); for(Entry e:entries) rows.put(e.raw); for(String cursor:seenCursors) seen.put(cursor);
        return RainApi.object("rows",rows,"cursor",nextCursor==null?JSONObject.NULL:nextCursor,"end",endReached,"stalled",stalled,"seen",seen);
    }
    static ActivityFeed decode(JSONObject json) throws Exception {
        JSONArray rows=json.getJSONArray("rows"),seen=json.getJSONArray("seen"); List<Entry> entries=new ArrayList<>(); Set<String> cursors=new LinkedHashSet<>();
        for(int i=0;i<rows.length();i++) entries.add(new Entry(rows.getJSONObject(i)));
        for(int i=0;i<seen.length();i++) cursors.add(seen.getString(i));
        String cursor=json.isNull("cursor")?null:json.getString("cursor");
        return new ActivityFeed(entries,cursor,json.getBoolean("end"),json.getBoolean("stalled"),cursors);
    }
    static String label(JSONObject object,String key,String fallback) {
        if(object==null||object.isNull(key)) return fallback; String value=object.optString(key,"").trim(); return value.isEmpty()?fallback:value;
    }
}
