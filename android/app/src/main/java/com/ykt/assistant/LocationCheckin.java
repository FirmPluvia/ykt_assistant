package com.ykt.assistant;

import org.json.JSONObject;

/** One immutable, classroom-bound location target. Coordinate halves never cross sources. */
final class LocationCheckin {
    static final class Point {
        final double lat,lng;
        Point(double lat,double lng) { this.lat=lat; this.lng=lng; }
        boolean same(Point p) { return p!=null&&Double.compare(lat,p.lat)==0&&Double.compare(lng,p.lng)==0; }
    }
    final String lesson,address,sessionId;
    final int round;
    final boolean signed,submitted;
    final Boolean needed;
    final Point point;
    private LocationCheckin(String lesson,int round,boolean signed,Boolean needed,Point point,String address,String sessionId,boolean submitted) { this.lesson=lesson; this.round=round; this.signed=signed; this.needed=needed; this.point=point; this.address=address; this.sessionId=sessionId; this.submitted=submitted; }
    static JSONObject data(JSONObject raw) { JSONObject wrapped=raw.optJSONObject("data"); return wrapped==null?raw:wrapped; }
    static LocationCheckin parse(JSONObject raw,JSONObject event,String lesson,int round,boolean submitted) {
        JSONObject d=data(raw),session=d.optJSONObject("session"); Point point=pair(session,"centerLat","centerLng"); if(point==null) point=pair(d,"lat","lng"); if(point==null) point=pair(event,"centerLat","centerLng");
        String address=text(d,"address"); if(address.isEmpty()) address=text(session,"address"); if(address.isEmpty()) address=text(event,"address"); String id=text(session,"id"); if(id.isEmpty()) id=text(d,"sessionId");
        return new LocationCheckin(lesson,round,Boolean.TRUE.equals(flag(d,"signed")),flag(d,"needLocationSignIn"),point,address,id,submitted);
    }
    LocationCheckin submitted(boolean value) { return new LocationCheckin(lesson,round,signed,needed,point,address,sessionId,value); }
    boolean sameTarget(LocationCheckin other) { return other!=null&&lesson.equals(other.lesson)&&round==other.round&&sessionId.equals(other.sessionId)&&point!=null&&point.same(other.point); }
    String key() { return lesson+"|"+round+"|"+sessionId+"|"+(point==null?"":point.lat+","+point.lng); }
    void requireAvailable() throws RainApi.Failure {
        if(needed==null) throw new RainApi.Failure("服务端未返回有效的位置签到状态，请重新查询",502);
        if(!needed) throw new RainApi.Failure("本轮位置签到尚未开始或已经结束",409);
        if(point==null) throw new RainApi.Failure("服务端未提供有效的签到位置，请刷新课堂后重试",422);
    }
    private static String text(JSONObject d,String key) { if(d==null||d.isNull(key)) return ""; Object value=d.opt(key); return value instanceof String||value instanceof Number?String.valueOf(value).trim():""; }
    private static Boolean flag(JSONObject d,String key) { Object v=d.opt(key); if(Boolean.TRUE.equals(v)||"true".equalsIgnoreCase(String.valueOf(v))||"1".equals(String.valueOf(v))) return true; if(Boolean.FALSE.equals(v)||"false".equalsIgnoreCase(String.valueOf(v))||"0".equals(String.valueOf(v))) return false; return null; }
    private static Double number(Object value) { if(!(value instanceof String||value instanceof Number)) return null; try { double n=Double.parseDouble(String.valueOf(value).trim()); return Double.isFinite(n)?n:null; } catch(Exception e) { return null; } }
    private static Point pair(JSONObject d,String latKey,String lngKey) { if(d==null) return null; Double lat=number(d.opt(latKey)),lng=number(d.opt(lngKey)); return lat!=null&&lng!=null&&Math.abs(lat)<=90&&Math.abs(lng)<=180?new Point(lat,lng):null; }
}
