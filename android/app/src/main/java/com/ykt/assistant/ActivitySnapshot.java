package com.ykt.assistant;

import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.Map;

/** Payloads are immutable after acceptance; timestamps control refresh of non-final reports and signed image URLs. */
final class ActivitySnapshot {
    static final long ACTIVE_TTL=60_000,DECK_TTL=30*60_000;
    final FeedTimeline timeline;
    final Map<String,LessonReport> reports;
    final Map<String,Long> reportAt,deckAt;
    final Map<String,JSONObject> decks;
    ActivitySnapshot(FeedTimeline timeline,Map<String,LessonReport> reports,Map<String,Long> reportAt,Map<String,JSONObject> decks,Map<String,Long> deckAt) { this.timeline=timeline; this.reports=new LinkedHashMap<>(reports); this.reportAt=new LinkedHashMap<>(reportAt); this.decks=new LinkedHashMap<>(decks); this.deckAt=new LinkedHashMap<>(deckAt); }
    static boolean fresh(LessonReport report,long fetched,long now) { return report!=null&&report.info!=null&&report.detail!=null&&report.attendance()!=null&&report.infoError==null&&report.detailError==null&&(report.ended()||(now>=fetched&&now-fetched<ACTIVE_TTL)); }
    JSONObject encode() throws Exception {
        JSONObject records=new JSONObject(),pictures=new JSONObject();
        for(Map.Entry<String,LessonReport> entry:reports.entrySet()) { LessonReport r=entry.getValue(); if(r.info!=null||r.detail!=null) records.put(entry.getKey(),RainApi.object("info",r.info==null?JSONObject.NULL:r.info,"detail",r.detail==null?JSONObject.NULL:r.detail,"at",reportAt.getOrDefault(entry.getKey(),0L))); }
        for(Map.Entry<String,JSONObject> entry:decks.entrySet()) { JSONObject deck=new JSONObject(entry.getValue().toString()); deck.remove("slideList"); pictures.put(entry.getKey(),RainApi.object("data",deck,"at",deckAt.getOrDefault(entry.getKey(),0L))); }
        return RainApi.object("version",1,"timeline",timeline.encode(),"reports",records,"decks",pictures);
    }
    static ActivitySnapshot decode(JSONObject data) throws Exception {
        if(data.getInt("version")!=1) throw new IllegalArgumentException("动态缓存版本不支持");
        Map<String,LessonReport> reports=new LinkedHashMap<>(); Map<String,Long> reportAt=new LinkedHashMap<>(),deckAt=new LinkedHashMap<>(); Map<String,JSONObject> decks=new LinkedHashMap<>();
        JSONObject records=data.getJSONObject("reports"),pictures=data.getJSONObject("decks"); java.util.Iterator<String> keys=records.keys();
        while(keys.hasNext()) { String id=keys.next(); JSONObject item=records.getJSONObject(id); reports.put(id,new LessonReport(id,item.optJSONObject("info"),item.optJSONObject("detail"),null,null)); reportAt.put(id,item.optLong("at",0)); }
        keys=pictures.keys(); while(keys.hasNext()) { String key=keys.next(); JSONObject item=pictures.getJSONObject(key),deck=item.getJSONObject("data"); if(deck.optJSONArray("slides")!=null) { decks.put(key,deck); deckAt.put(key,item.optLong("at",0)); } }
        return new ActivitySnapshot(FeedTimeline.decode(data.getJSONObject("timeline")),reports,reportAt,decks,deckAt);
    }
}
