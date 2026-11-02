package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;

/** Read-only lesson report. Attendance is final only when a successfully loaded lesson has ended. */
final class LessonReport {
    final String lessonId;
    final JSONObject info,detail;
    final String infoError,detailError;
    LessonReport(String id,JSONObject info,JSONObject detail,String infoError,String detailError) { lessonId=id; this.info=info; this.detail=detail; this.infoError=infoError; this.detailError=detailError; }
    JSONObject attendance() { return detail==null?null:detail.optJSONObject("detail"); }
    JSONArray presentations() { JSONArray list=detail==null?null:detail.optJSONArray("presentation"); return list==null?new JSONArray():list; }
    boolean signed() { JSONObject a=attendance(); return detailError==null&&a!=null&&a.optInt("valid",-1)==1&&a.optLong("checkInTime",0)>0; }
    boolean ended() { long end=end(); return infoError==null&&info!=null&&end>0&&end<=System.currentTimeMillis(); }
    boolean absent() { return !signed()&&ended()&&detailError==null&&detail!=null; }
    String attendanceLabel() {
        if(detailError!=null) return "签到信息暂不可用";
        if(signed()) return "已签到";
        if(absent()) return "未签到";
        if(attendance()==null) return "未返回签到记录";
        return "签到待确认";
    }
    long checkinTime() { return attendance()==null?0:attendance().optLong("checkInTime",0); }
    long start() { return info==null?0:info.optLong("start",0); }
    long end() { return info==null?0:info.optLong("end",0); }
}
