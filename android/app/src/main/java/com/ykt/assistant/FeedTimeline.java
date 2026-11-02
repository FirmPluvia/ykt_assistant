package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Keeps cached history behind gaps until real server pages connect it to the refreshed head. */
final class FeedTimeline {
    final ActivityFeed visible;
    final List<ActivityFeed> deferred;
    FeedTimeline(ActivityFeed visible) { this(visible,new ArrayList<>()); }
    private FeedTimeline(ActivityFeed visible,List<ActivityFeed> deferred) { this.visible=visible; this.deferred=Collections.unmodifiableList(deferred); }
    FeedTimeline refresh(JSONArray head) { List<ActivityFeed> old=new ArrayList<>(); old.add(visible); old.addAll(deferred); return normalize(ActivityFeed.from(head),old); }
    FeedTimeline append(JSONArray page) { return normalize(visible.append(page),new ArrayList<>(deferred)); }
    private static FeedTimeline normalize(ActivityFeed front,List<ActivityFeed> tails) {
        while(!front.endReached&&!front.stalled&&!tails.isEmpty()) {
            int match=-1; for(int i=0;i<tails.size();i++) if(!tails.get(i).stalled&&(tails.get(i).endReached||tails.get(i).nextCursor!=null)&&front.overlaps(tails.get(i))) { match=i; break; }
            if(match<0) break;
            front=ActivityFeed.joinPrefix(front,tails.get(match)); tails=new ArrayList<>(tails.subList(match+1,tails.size()));
        }
        if(front.endReached) tails.clear(); return new FeedTimeline(front,tails);
    }
    JSONObject encode() throws Exception { JSONArray tails=new JSONArray(); for(ActivityFeed tail:deferred) tails.put(tail.encode()); return RainApi.object("visible",visible.encode(),"deferred",tails); }
    static FeedTimeline decode(JSONObject data) throws Exception { ActivityFeed front=ActivityFeed.decode(data.getJSONObject("visible")); JSONArray tails=data.getJSONArray("deferred"); List<ActivityFeed> old=new ArrayList<>(); for(int i=0;i<tails.length();i++) old.add(ActivityFeed.decode(tails.getJSONObject(i))); return new FeedTimeline(front,old); }
}
