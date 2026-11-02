package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A report timeline contains repeated animation steps, not a complete original slide deck. */
final class ReviewPresentation {
    static JSONObject build(String lessonId,JSONObject summary,JSONObject review) throws Exception {
        String presentationId=summary.optString("presentationId","");
        if(presentationId.isEmpty()) throw new RainApi.Failure("未返回有效课件 ID",502);
        JSONArray events=review.optJSONArray("timelineList");
        if(events==null) throw new RainApi.Failure("课堂回顾数据结构与抓包不一致",502);
        Map<String,JSONObject> pages=new LinkedHashMap<>();
        for(int i=0;i<events.length();i++) {
            JSONObject event=events.optJSONObject(i); if(event==null||!event.optString("type").equals("slide")) continue;
            String pres=ActivityFeed.label(event,"presentationId",event.optString("pres"));
            if(!presentationId.equals(pres)) continue;
            String id=ActivityFeed.label(event,"id",ActivityFeed.label(event,"sid","index:"+event.optInt("index",event.optInt("si",-1))));
            JSONObject previous=pages.get(id);
            if(previous==null||event.optLong("dt",0)>=previous.optLong("dt",0)) pages.put(id,event);
        }
        List<JSONObject> ordered=new ArrayList<>(pages.values());
        ordered.sort(Comparator.comparingInt(v -> v.optInt("index",v.optInt("si",Integer.MAX_VALUE))));
        JSONArray slides=new JSONArray();
        for(JSONObject event:ordered) {
            if(!event.optBoolean("visible",true)||event.optString("cover","").isEmpty()) continue;
            int original=event.optInt("index",event.optInt("si",0));
            slides.put(RainApi.object("id",ActivityFeed.label(event,"id",event.optString("sid","page-"+original)),"index",original,"cover",event.getString("cover"),"note","课堂回顾 · 原第 "+original+" 页","problem",event.opt("problem")));
        }
        if(slides.length()==0) throw new RainApi.Failure("这份课件没有可查看的回顾页面，可能未展示或暂未开放",409);
        return RainApi.object("id","history:"+lessonId+":"+presentationId,"presentationId",presentationId,"lessonId",lessonId,"title",ActivityFeed.label(summary,"title","课堂课件")+"（课堂回顾）","source","classroom-report","reportedTotal",summary.optInt("totalCount",0),"version",String.valueOf(System.currentTimeMillis()),"slides",slides);
    }
}
