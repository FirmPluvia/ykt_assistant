package com.ykt.assistant;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

/** One account's one lesson. Drafts are per question; timestamps are never invented for retry. */
final class LessonQuiz {
    final String lesson;
    private final LongSupplier wall,elapsed;
    private final LinkedHashMap<String,Question> questions=new LinkedHashMap<>();
    private final Set<String> extensions=new LinkedHashSet<>();
    private String active="";
    private boolean finished;
    final Set<String> otherAnswerSuccess=new LinkedHashSet<>();
    static final class Question {
        final String id;
        String presentation="",text="",lastError="",imageStatus="";
        JSONObject spec,slide;
        final LinkedHashSet<String> selected=new LinkedHashSet<>();
        final List<String> fields=new ArrayList<>();
        long eventTime,deadline=-1,round;
        boolean opened,submitted,sending,imageBusy;
        final List<AnswerImage> images=new ArrayList<>();
        Question(String id) { this.id=id; }
        int type() { return spec==null?0:spec.optInt("problemType",0); }
        JSONArray options() { return spec==null?null:spec.optJSONArray("options"); }
        boolean choice() { return type()>=1&&type()<=3||type()==6; }
        int maximum() { return type()==1||type()==6?1:type()==3?Math.max(1,spec.optInt("pollingCount",1)):Integer.MAX_VALUE; }
        int blankCount() { JSONArray a=spec==null?null:spec.optJSONArray("blanks"); return a==null?0:a.length(); }
        boolean supported() { return type()>=1&&type()<=6; }
        String typeName() { switch(type()) { case 1:return "单选题"; case 2:return "多选题"; case 3:return "投票"; case 4:return "填空题"; case 5:return "主观题"; case 6:return "判断题"; default:return "暂不支持的题型"; } }
    }
    static final class Submission {
        final String lesson,question,body,spec; final boolean retry; final long round;
        Submission(String lesson,Question q,boolean retry,JSONObject body) { this.lesson=lesson; question=q.id; round=q.round; this.retry=retry; this.body=body.toString(); spec=q.spec.toString(); }
        JSONObject payload() throws Exception { return new JSONObject(body); }
    }
    LessonQuiz(String lesson,LongSupplier wall,LongSupplier elapsed) { this.lesson=lesson; this.wall=wall; this.elapsed=elapsed; }
    List<Question> all() { return new ArrayList<>(questions.values()); }
    Question get(String id) { return questions.get(id); }
    private Question ensure(String id) { return questions.computeIfAbsent(id,Question::new); }
    static String id(JSONObject json,String key) { if(json==null||json.isNull(key)) return ""; Object v=json.opt(key); return v instanceof String||v instanceof Number?String.valueOf(v):""; }
    boolean ingest(JSONObject e) {
        boolean changed=false; String pres=id(e,"presentationId"); JSONArray timeline=e.optJSONArray("timeline");
        if(timeline!=null) for(int i=0;i<timeline.length();i++) { JSONObject item=timeline.optJSONObject(i); if(item!=null&&"problem".equals(item.optString("type"))) changed|=open(item,id(item,"pres").isEmpty()?pres:id(item,"pres")); }
        String op=e.optString("op"); JSONObject p=e.optJSONObject("problem");
        if("unlockproblem".equals(op)&&p!=null) changed|=open(p,pres);
        if("extendtime".equals(op)&&p!=null) {
            String key=id(p,"prob"); if(key.isEmpty()) key=active; Question q=questions.get(key); long extra=seconds(p.optLong("extend",0));
            if(q!=null&&q.opened&&extra>0) {
                String stamp=p.optLong("dt",0)>0?key+":"+p.optLong("dt")+":"+extra:null;
                if(stamp==null||extensions.add(stamp)) { if(q.deadline>=0) q.deadline=Math.max(elapsed.getAsLong(),q.deadline)+extra; changed=true; }
            }
        }
        if(e.optBoolean("finished")||"lessonfinished".equals(op)) { finished=true; changed=true; }
        return changed;
    }
    private static long seconds(long value) { return value>0&&value<=31536000?value*1000:0; }
    private boolean open(JSONObject e,String pres) {
        String id=id(e,"prob"); if(id.isEmpty()) return false; Question q=ensure(id);
        long dt=Math.max(0,e.optLong("dt",0));
        // Hello/timeline replay must not restart an existing timer or resurrect submitted drafts.
        if(q.opened&&(dt==0||dt<=q.eventTime)) { if(q.presentation.isEmpty()) q.presentation=pres; return false; }
        if(!pres.isEmpty()) q.presentation=pres;
        boolean newRound=q.opened&&dt>q.eventTime;
        if(newRound) { q.selected.clear(); q.fields.clear(); q.images.clear();q.imageBusy=false;q.imageStatus="";q.text=""; q.submitted=false; q.sending=false; q.lastError=""; }
        q.round++; q.opened=true; q.eventTime=dt; active=id;
        long limit=seconds(e.optLong("limit",0));
        long age=dt>0?Math.max(0,wall.getAsLong()-dt):0;
        q.deadline=limit>0?elapsed.getAsLong()+Math.max(0,limit-age):-1;
        return true;
    }
    void deck(String pres,JSONObject deck) throws Exception {
        JSONArray slides=deck.optJSONArray("slides"); if(slides==null) return;
        for(int i=0;i<slides.length();i++) {
            JSONObject slide=slides.optJSONObject(i),spec=slide==null?null:slide.optJSONObject("problem"); String id=id(spec,"problemId"); if(id.isEmpty()) continue;
            Question q=ensure(id); q.presentation=pres; q.spec=new JSONObject(spec.toString()); q.slide=new JSONObject(slide.toString());
            while(q.fields.size()<q.blankCount()&&q.fields.size()<100) q.fields.add("");
        }
    }
    Question preferred(String id) { Question q=get(id); if(q!=null) return q; q=get(active); if(q!=null) return q; List<Question> all=all(); return all.isEmpty()?null:all.get(all.size()-1); }
    boolean expired(Question q) { return finished||(q.deadline>=0&&elapsed.getAsLong()>=q.deadline); }
    long remaining(Question q) { return q.deadline<0?-1:Math.max(0,(q.deadline-elapsed.getAsLong()+999)/1000); }
    String status(Question q) {
        if(q.sending) return "正在提交…"; if(q.submitted) return "本机已提交成功"; if(!q.opened) return "尚未收到开放题目的通知";
        if(expired(q)) return q.eventTime>0?"答题时间已结束，可尝试补交":"答题时间已结束，缺少补交时间信息";
        long s=remaining(q); return s<0?"题目已开放 · 时限未提供":String.format(java.util.Locale.ROOT,"剩余 %02d:%02d",s/60,s%60);
    }
    Submission prepare(Question q) throws Exception {
        if(q==null||get(q.id)!=q||q.spec==null) throw new Exception("请先加载题目");
        if(q.imageBusy)throw new Exception("请等待图片上传完成");
        if(!q.opened) throw new Exception("这道题尚未开放"); if(q.sending) throw new Exception("这道题正在提交，请稍候"); if(q.submitted) throw new Exception("这道题本机已提交成功");
        if(!q.supported()) throw new Exception("当前题型的提交格式尚未确认，暂不支持提交");
        Object result;
        if(q.choice()) {
            JSONArray options=q.options(); Set<String> valid=new LinkedHashSet<>();
            if(options!=null) for(int i=0;i<options.length();i++) { String key=id(options.optJSONObject(i),"key"); if(!key.isEmpty()) valid.add(key); }
            if(q.selected.isEmpty()) throw new Exception("请选择答案");
            if(!valid.containsAll(q.selected)||q.selected.size()>q.maximum()) throw new Exception("选项已变化或超出可选数量，请重新选择");
            JSONArray selected=new JSONArray(); for(String key:valid) if(q.selected.contains(key)) selected.put(key); result=selected;
        } else if(q.type()==4) {
            int count=q.blankCount(); if(count==0||count>100) throw new Exception("未取得有效的填空配置"); JSONArray answers=new JSONArray();
            for(int i=0;i<count;i++) { String answer=i<q.fields.size()?q.fields.get(i):""; if(answer.trim().isEmpty()) throw new Exception("请填写第 "+(i+1)+" 空"); answers.put(answer); } result=answers;
        } else {
            if(q.text.trim().isEmpty()&&q.images.isEmpty()) throw new Exception("请填写文字答案或添加图片");if(q.images.size()>AnswerImage.MAX_COUNT)throw new Exception("最多添加 6 张图片");JSONArray pics=new JSONArray();for(AnswerImage image:q.images)pics.put(image.payload());result=RainApi.object("content",q.text,"pics",pics,"videos",new JSONArray());
        }
        boolean retry=expired(q); if(retry&&q.eventTime<=0) throw new Exception("缺少题目原始时间，暂不能补交，请重新连接课堂获取题目记录");
        JSONObject body=RainApi.object("problemId",q.id,"problemType",q.type(),"dt",retry?q.eventTime:wall.getAsLong(),"result",result);
        if(retry) { body.put("retry_times",JSONObject.NULL); body=RainApi.object("problems",new JSONArray().put(body)); }
        return new Submission(lesson,q,retry,body);
    }
    LessonQuiz answerCopy(Question source,String text) throws Exception {
        if(source==null||source.spec==null)throw new Exception("题目尚未加载");
        LessonQuiz copy=new LessonQuiz(lesson,wall,elapsed);copy.finished=finished;
        Question q=copy.ensure(source.id);q.spec=new JSONObject(source.spec.toString());q.presentation=source.presentation;q.opened=source.opened;q.eventTime=source.eventTime;q.deadline=source.deadline;q.round=source.round;q.selected.addAll(source.selected);q.fields.addAll(source.fields);q.text=text==null?source.text:text;q.images.addAll(source.images);q.imageBusy=source.imageBusy;
        return copy;
    }
    /** Compare visible semantics, never hidden answers or teacher metadata. */
    static String answerShape(Question q) throws Exception {
        java.util.TreeMap<String,String> options=new java.util.TreeMap<>();JSONArray raw=q.options();
        if(raw!=null)for(int i=0;i<raw.length();i++){JSONObject option=raw.optJSONObject(i);String key=id(option,"key");if(!key.isEmpty())options.put(key,option.optString("value",""));}
        JSONArray pairs=new JSONArray();for(java.util.Map.Entry<String,String> e:options.entrySet())pairs.put(new JSONArray().put(e.getKey()).put(e.getValue()));
        return new JSONArray().put(q.type()).put(q.spec.optString("body","")).put(q.blankCount()).put(q.maximum()).put(pairs).toString();
    }
    void begin(Submission submission) throws Exception {
        Question q=get(submission.question);
        if(!lesson.equals(submission.lesson)||q==null||q.round!=submission.round) throw new Exception("题目轮次已变化，请重新确认");
        if(q.spec==null||!submission.spec.equals(q.spec.toString())) throw new Exception("题目内容已变化，请重新确认答案");
        if(expired(q)!=submission.retry) throw new Exception("题目时限已变化，请重新提交");
        if(q.imageBusy)throw new Exception("请等待图片上传完成");
        if(q.submitted||q.sending) throw new Exception("请勿重复提交"); q.sending=true; q.lastError="";
    }
    void complete(Submission submission,String error) {
        Question q=get(submission.question); if(q==null||q.round!=submission.round) return;
        q.sending=false; q.submitted=error==null; q.lastError=error==null?"":error;
    }
}
