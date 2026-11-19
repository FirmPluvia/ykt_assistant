package com.ykt.assistant;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Sequential, explicitly selected account submissions; never shares account credentials. */
final class MultiAccountAnswers {
    interface Factory { RainApi open(AccountStore.Account account)throws Exception; }
    interface Progress {void update(AccountStore.Account account,String message);}
    static final class Task {final AccountStore.Account account;final String text;Task(AccountStore.Account a,String text){account=a;this.text=text;}}
    static final class Result {final AccountStore.Account account;final boolean success;final String message;Result(AccountStore.Account a,boolean s,String m){account=a;success=s;message=m;}}
    final AccountStore store;final int service;final String active;private final Factory factory;
    MultiAccountAnswers(AccountStore store,int service,String active,Factory factory){this.store=store;this.service=service;this.active=active;this.factory=factory;}
    List<AccountStore.Account> eligible(){List<AccountStore.Account> out=new ArrayList<>();for(AccountStore.Account a:store.list())if(a.service==service&&!a.key.equals(active))out.add(a);return out;}
    static final class Plan {
        final LessonQuiz origin,frozen;final LessonQuiz.Question source;final String id,spec,shape;final long round;final boolean retry;
        Plan(LessonQuiz quiz,LessonQuiz.Question q)throws Exception{
            if(q==null||!q.opened||q.spec==null||!q.supported()||q.sending||q.imageBusy)throw new Exception("请先加载已开放的题目，并完成当前提交");
            origin=quiz;source=q;id=q.id;round=q.round;spec=q.spec.toString();shape=LessonQuiz.answerShape(q);frozen=quiz.answerCopy(q,null);retry=quiz.expired(q);
            if(retry&&q.eventTime<=0)throw new Exception("缺少原题时间，暂不能为其他账号补交");
        }
        boolean valid(){return origin.get(id)==source&&source.opened&&!source.sending&&source.round==round&&source.spec!=null&&spec.equals(source.spec.toString());}
        String key(String account){return account+"|"+id+"|"+round;}
        LessonQuiz.Submission prepare(String text)throws Exception{
            if(!valid())throw new Exception("题目轮次或内容已变化，请重新确认");
            if(origin.expired(source)!=retry)throw new Exception("答题时限已变化，请重新打开并确认补交");
            LessonQuiz copy=origin.answerCopy(source,text==null?frozen.get(id).text:text);LessonQuiz.Question draft=copy.get(id);
            draft.selected.clear();draft.selected.addAll(frozen.get(id).selected);draft.fields.clear();draft.fields.addAll(frozen.get(id).fields);
            LessonQuiz.Submission submission=copy.prepare(draft);if(submission.retry!=retry)throw new Exception("答题时限已变化，请重新打开并确认补交");return submission;
        }
        LessonQuiz.Submission prepareWithImages(String text,List<AnswerImage> images)throws Exception{LessonQuiz.Submission checked=prepare(text);LessonQuiz copy=frozen.answerCopy(frozen.get(id),text);LessonQuiz.Question draft=copy.get(id);draft.images.clear();draft.images.addAll(images);draft.deadline=source.deadline;LessonQuiz.Submission result=copy.prepare(draft);if(result.retry!=checked.retry)throw new Exception("答题时限已变化，请重新确认");return result;}
        String preview(String text)throws Exception{
            JSONObject payload=prepare(text).payload();if(retry)payload=payload.getJSONArray("problems").getJSONObject(0);
            return source.type()==5?payload.getJSONObject("result").getString("content")+(frozen.get(id).images.isEmpty()?"":"\n图片 "+frozen.get(id).images.size()+" 张（各账号独立上传）"):payload.get("result").toString();
        }
    }
    final class Job implements AutoCloseable {
        private final AtomicBoolean cancelled=new AtomicBoolean(),started=new AtomicBoolean();private volatile RainApi client;
        List<Result> run(Plan plan,List<Task> tasks,BooleanSupplier current,Progress progress){
            if(!started.compareAndSet(false,true))throw new IllegalStateException("操作已开始");List<Result> results=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(Task task:tasks){
                if(cancelled.get()||!current.getAsBoolean()||!plan.valid())break;
                AccountStore.Account a=task.account;RainApi api=null;boolean sent=false;
                try{
                    if(a.service!=service||a.key.equals(active)||!seen.add(a.key))throw new Exception("账号服务器不一致、当前账号或重复选择");
                    AccountStore.Account latest=store.get(a.key);if(latest==null||!latest.uid.equals(a.uid)||latest.service!=service)throw new Exception("该账号已移除，请重新选择");
                    synchronized(plan.origin.otherAnswerSuccess){if(plan.origin.otherAnswerSuccess.contains(plan.key(a.key)))throw new Exception("该账号本题本轮已在本机提交成功，已跳过");}
                    plan.prepare(task.text);progress.update(a,"正在校验账号与课堂…");api=factory.open(latest);client=api;
                    if(cancelled.get()){api.cancel();break;}
                    JSONObject profile=api.profile();if(!a.uid.equals(profile.optString("user_id")))throw new Exception("账号身份不符，请重新登录");
                    api.promoteSession(store.storage(a.key));
                    if(cancelled.get()||!current.getAsBoolean()||!plan.valid())break;
                    if(!api.restoreLesson(plan.origin.lesson,cancelled::get))throw new Exception("未找到该账号的课堂凭证，请先为该账号签到入课");
                    if(cancelled.get()||!current.getAsBoolean()||!plan.valid())break;
                    JSONObject deck=api.quizPresentation(plan.origin.lesson,plan.source.presentation);LessonQuiz remote=new LessonQuiz(plan.origin.lesson,System::currentTimeMillis,android.os.SystemClock::elapsedRealtime);remote.deck(plan.source.presentation,deck);LessonQuiz.Question question=remote.get(plan.id);
                    if(question==null||!plan.shape.equals(LessonQuiz.answerShape(question)))throw new Exception("该账号取得的题目或选项不一致，未提交");
                    if(cancelled.get()||!current.getAsBoolean()||!plan.valid()||store.get(a.key)==null)break;
                    List<AnswerImage> images=new ArrayList<>();for(AnswerImage photo:plan.frozen.get(plan.id).images){if(cancelled.get()||!current.getAsBoolean()||!plan.valid())break;progress.update(a,"正在上传答案图片 "+(images.size()+1)+" / "+plan.frozen.get(plan.id).images.size());images.add(api.uploadAnswerImage(photo,()->cancelled.get()||!current.getAsBoolean()||!plan.valid()));}if(cancelled.get()||!current.getAsBoolean()||!plan.valid())break;LessonQuiz.Submission submission=images.isEmpty()?plan.prepare(task.text):plan.prepareWithImages(task.text,images);progress.update(a,submission.retry?"正在补交…":"正在提交…");
                    // Cancellation can stop later work; an already sent request may have reached the server.
                    if(cancelled.get()||!current.getAsBoolean()||!plan.valid())break;sent=true;api.submitAnswer(submission);
                    synchronized(plan.origin.otherAnswerSuccess){plan.origin.otherAnswerSuccess.add(plan.key(a.key));}
                    results.add(new Result(a,true,"提交成功"));progress.update(a,"提交成功");
                }catch(Exception e){if(cancelled.get())break;String message=e instanceof RainApi.Failure?e.getMessage():sent?"提交未确认，请核对官方记录后再重试":safe(e);results.add(new Result(a,false,message));progress.update(a,message);}
                finally{if(api!=null)api.close();client=null;}
            }
            return results;
        }
        boolean cancelled(){return cancelled.get();}
        @Override public void close(){cancelled.set(true);RainApi api=client;if(api!=null)api.cancel();}
    }
    private static String safe(Exception e){String message=e.getMessage();return message!=null&&(message.contains("题目")||message.contains("账号")||message.contains("课堂凭证")||message.contains("时限")||message.contains("答案")||message.contains("请选择")||message.contains("请填写")||message.contains("选项")||message.contains("补交"))?message:"账号校验或题目加载失败，请重新登录后重试";}
}
