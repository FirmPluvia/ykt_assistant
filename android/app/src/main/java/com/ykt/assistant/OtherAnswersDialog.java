package com.ykt.assistant;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Review every recipient and optional individual subjective drafts before a sequential batch. */
final class OtherAnswersDialog {
    private final Activity activity;private final LessonQuiz quiz;private final LessonQuiz.Question q;private final MultiAccountAnswers support;private final ExecutorService workers;private final BooleanSupplier current;
    private final Handler ui=new Handler(Looper.getMainLooper());private final AtomicBoolean stopped=new AtomicBoolean();
    static final class Prepared{final MultiAccountAnswers.Plan plan;final List<MultiAccountAnswers.Task> tasks;Prepared(MultiAccountAnswers.Plan plan,List<MultiAccountAnswers.Task> tasks){this.plan=plan;this.tasks=new ArrayList<>(tasks);}}
    private RainApi analysisApi;private AiClient.Factory aiFactory=AiClient::new;private final java.util.Map<EditText,AiClient> aiClients=new java.util.concurrent.ConcurrentHashMap<>();private boolean aiBusy;private final BooleanSupplier aiStop=stopped::get;private final List<Button> aiButtons=new ArrayList<>();
    void setAi(RainApi api,AiClient.Factory factory){analysisApi=api;aiFactory=factory;}
    private MultiAccountAnswers.Plan supplied;private Set<String> preselected;private java.util.function.Consumer<Prepared> collected;private boolean progressOnly;
    void collect(MultiAccountAnswers.Plan plan,List<AccountStore.Account> accounts,java.util.function.Consumer<Prepared> done){supplied=plan;preselected=new HashSet<>();for(AccountStore.Account a:accounts)preselected.add(a.key);collected=done;show();}
    void showPrepared(Prepared data){supplied=data.plan;preselected=new HashSet<>();for(MultiAccountAnswers.Task task:data.tasks)preselected.add(task.account.key);progressOnly=true;show();if(dialog!=null&&dialog.isShowing()){if(separate!=null)separate.setChecked(true);for(int i=0;i<accounts.size();i++)for(MultiAccountAnswers.Task task:data.tasks)if(accounts.get(i).key.equals(task.account.key)&&task.text!=null)fields.get(i).setText(task.text);start(data.tasks);}}
    private MultiAccountAnswers.Plan plan;private MultiAccountAnswers.Job job;private Dialog dialog,confirmation;private TextView message;private Button review;private CheckBox separate;private boolean running;
    private final List<AccountStore.Account> accounts=new ArrayList<>();private final List<CheckBox> checks=new ArrayList<>();private final List<EditText> fields=new ArrayList<>();private final List<TextView> states=new ArrayList<>();
    OtherAnswersDialog(Activity activity,LessonQuiz quiz,LessonQuiz.Question q,MultiAccountAnswers support,ExecutorService workers,BooleanSupplier current){this.activity=activity;this.quiz=quiz;this.q=q;this.support=support;this.workers=workers;this.current=current;}
    boolean valid(){return !stopped.get()&&!activity.isDestroyed()&&!activity.isFinishing()&&current.getAsBoolean()&&(plan==null||plan.valid());}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout p=new LinearLayout(activity);p.setOrientation(LinearLayout.VERTICAL);return p;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(size>=15?0xff263d32:0xff7e8d81);v.setIncludeFontPadding(false);return v;}
    private void gap(LinearLayout p,int n){p.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(n)));}
    private LinearLayout panel(){LinearLayout p=column();p.setPadding(dp(20),dp(16),dp(20),dp(20));p.setBackground(bg(0xfff7f9f5));return p;}
    private void header(LinearLayout p,String title,Dialog owner){LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);TextView t=text(title,20);t.setTypeface(null,Typeface.BOLD);row.addView(t,new LinearLayout.LayoutParams(0,dp(44),1));row.addView(CardDialog.closeButton(activity,owner,title),new LinearLayout.LayoutParams(dp(44),dp(44)));p.addView(row);gap(p,10);}
    private Button button(String label,Runnable action){Button b=new FlatButton(activity);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextColor(0xffffffff);b.setBackground(bg(0xff2c5846));b.setOnClickListener(v->action.run());b.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(46)));return b;}
    private CheckBox check(String label){CheckBox b=new CheckBox(activity);b.setText(label);b.setTextSize(13);b.setTextColor(0xff263d32);b.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff2c5846));b.setMinHeight(dp(44));return b;}
    private void open(Dialog owner,LinearLayout root) {
        owner.setContentView(root);
        owner.show();
    }
    void show(){
        if(!valid())return;try{plan=supplied==null?new MultiAccountAnswers.Plan(quiz,q):supplied;}catch(Exception e){Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        dialog=new CardDialog(activity);LinearLayout root=panel();header(root,collected!=null?"编辑同步主观题":progressOnly?"同步答题结果":"为其他账号作答",dialog);root.addView(text(collected!=null?"确认后提交当前账号，并依次同步下列账号。各账号需有本堂课凭证。":progressOnly?"当前账号已提交；下方显示其他账号的独立处理结果。":"仅为勾选账号提交，不影响当前账号。各账号需已登录并保存本堂课凭证。",12));gap(root,12);
        LinearLayout list=column();root.addView(list);list.addView(text(q.typeName()+" · "+AiQuestion.plain(q.spec.optString("body","")),15));gap(list,12);
        if(q.type()==5){separate=check("分别填写主观题答案");separate.setTag("other-answer-separate");list.addView(separate);gap(list,8);list.addView(text(collected!=null?"请分别填写各账号的文字，当前账号草稿保留不变。":"关闭时使用当前文字答案；开启后可为各账号独立编辑，提交前逐项确认。",11));gap(list,12);}
        else{list.addView(text("使用当前题目草稿中的选项或填空答案。",12));gap(list,12);}
        accounts.addAll(support.eligible());
        for(AccountStore.Account a:accounts){LinearLayout row=column();row.setPadding(dp(12),dp(8),dp(12),dp(12));row.setBackground(bg(0xffffffff));CheckBox choice=check(a.name);choice.setTag("other-answer-account:"+a.key);boolean sent; synchronized(quiz.otherAnswerSuccess){sent=quiz.otherAnswerSuccess.contains(plan.key(a.key));}choice.setEnabled(!sent);choice.setChecked(!sent&&preselected!=null&&preselected.contains(a.key));checks.add(choice);row.addView(choice);TextView state=text(sent?"本题本轮已提交成功":choice.isChecked()?"已选择":"未选择",11);states.add(state);row.addView(state);EditText value=new EditText(activity);value.setText(collected!=null?"":q.text);value.setHint("为 "+a.name+" 填写文字答案");value.setTextSize(13);value.setTextColor(0xff263d32);value.setHintTextColor(0xff7e8d81);value.setMinLines(3);value.setMaxLines(8);value.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);value.setSaveEnabled(false);value.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);value.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10000)});value.setTag("other-answer-text:"+a.key);fields.add(value);row.addView(value);value.setVisibility(View.GONE);Button analyze=q.type()==5?button("AI 分析",()->analyzeText(value)):null;aiButtons.add(analyze);if(analyze!=null){analyze.setTextColor(0xff2c5846);analyze.setBackground(bg(0xffedf3e9));analyze.setTag("other-answer-ai:"+a.key);row.addView(analyze);analyze.setVisibility(View.GONE);}choice.setOnCheckedChangeListener((v,c)->{state.setText(c?"已选择":"未选择");updateFields();});list.addView(row);gap(list,10);}
        if(accounts.isEmpty())list.addView(text("暂无同服务器其他账号，请先在“我的 → 账号管理”添加。",13));
        if(separate!=null){separate.setOnCheckedChangeListener((v,c)->updateFields());if(collected!=null){separate.setChecked(true);separate.setVisibility(View.GONE);}}gap(list,8);message=text("",12);list.addView(message);gap(list,12);review=button("检查所选账号答案",this::review);review.setEnabled(!accounts.isEmpty());list.addView(review);gap(list,12);list.addView(text("按账号顺序请求。中断或失败的请求可能已到达服务端，请核对官方记录后再重试。",11));
        dialog.setOnDismissListener(d->cancel());open(dialog,root);ui.post(guard);
    }
    private void updateFields(){for(int i=0;i<fields.size();i++){boolean visible=separate!=null&&separate.isChecked()&&checks.get(i).isChecked();fields.get(i).setVisibility(visible?View.VISIBLE:View.GONE);if(aiButtons.get(i)!=null){aiButtons.get(i).setVisibility(visible?View.VISIBLE:View.GONE);aiButtons.get(i).setEnabled(!running&&!aiBusy&&checks.get(i).isEnabled());}}}
    private void review(){
        if(running||aiBusy||!valid())return;List<MultiAccountAnswers.Task> tasks=new ArrayList<>();
        try{for(int i=0;i<accounts.size();i++)if(checks.get(i).isChecked()&&checks.get(i).isEnabled()){String value=separate!=null&&separate.isChecked()?fields.get(i).getText().toString():plan.frozen.get(plan.id).text;plan.prepare(value);tasks.add(new MultiAccountAnswers.Task(accounts.get(i),value));}if(tasks.isEmpty())throw new Exception("请选择至少一个其他账号");}
        catch(Exception e){message.setText(e.getMessage());return;}
        confirmation=new CardDialog(activity);LinearLayout root=panel();header(root,collected!=null?"确认提交并同步":plan.retry?"确认其他账号补交":"确认其他账号答案",confirmation);LinearLayout previews=column();root.addView(previews);
        if(collected!=null){previews.addView(text("当前账号",16));gap(previews,6);previews.addView(text(plan.frozen.get(plan.id).text+(plan.frozen.get(plan.id).images.isEmpty()?"":"\n图片 "+plan.frozen.get(plan.id).images.size()+" 张，其他账号将独立上传所选图片"),13));gap(previews,16);}
        for(MultiAccountAnswers.Task task:tasks){previews.addView(text(task.account.name,16));gap(previews,6);try{TextView value=text(plan.preview(task.text),13);value.setTextIsSelectable(true);previews.addView(value);}catch(Exception e){message.setText(e.getMessage());return;}gap(previews,16);}
        if(plan.retry){previews.addView(text("题目已超时，将尝试补交；是否接受及计分由雨课堂决定。",12));gap(previews,12);}root.addView(button(collected!=null?"提交并同步 "+tasks.size()+" 个其他账号":"确认提交 "+tasks.size()+" 个账号",()->{confirmation.dismiss();if(collected!=null){java.util.function.Consumer<Prepared> done=collected;Prepared prepared=new Prepared(plan,tasks);close();done.accept(prepared);}else start(tasks);}));open(confirmation,root);
    }
    private void start(List<MultiAccountAnswers.Task> tasks){
        if(running||!valid())return;running=true;review.setEnabled(false);if(progressOnly)review.setText("重试所选失败账号");if(separate!=null)separate.setEnabled(false);for(CheckBox c:checks)c.setEnabled(false);for(EditText f:fields)f.setEnabled(false);for(Button b:aiButtons)if(b!=null)b.setEnabled(false);message.setText("正在按顺序提交…");job=support.new Job();MultiAccountAnswers.Job operation=job;
        workers.execute(()->{List<MultiAccountAnswers.Result> results=operation.run(plan,tasks,this::valid,(a,state)->ui.post(()->{if(valid())for(int i=0;i<accounts.size();i++)if(accounts.get(i).key.equals(a.key))states.get(i).setText(state);}));ui.post(()->{if(!valid())return;running=false;int success=0;for(MultiAccountAnswers.Result result:results)if(result.success)success++;message.setText("已处理 "+results.size()+" / "+tasks.size()+" 个账号，成功 "+success+" 个");for(int i=0;i<accounts.size();i++){boolean sent;synchronized(quiz.otherAnswerSuccess){sent=quiz.otherAnswerSuccess.contains(plan.key(accounts.get(i).key));}checks.get(i).setChecked(false);checks.get(i).setEnabled(!sent);fields.get(i).setEnabled(!sent);if(sent)states.get(i).setText("本题本轮已提交成功");else for(MultiAccountAnswers.Result result:results)if(result.account.key.equals(accounts.get(i).key))states.get(i).setText(result.message);}if(separate!=null)separate.setEnabled(true);review.setEnabled(true);updateFields();});});
    }
    private void analyzeText(EditText field){
        if(running||aiBusy||!valid())return;AiSettings settings=new AiSettings(activity);if(!settings.preview().configured()){Toast.makeText(activity,"请先在“我的”配置 AI 接口与模型",Toast.LENGTH_LONG).show();return;}long revision=settings.revision();AiQuestion snapshot;try{snapshot=new AiQuestion(q);}catch(Exception e){Toast.makeText(activity,e.getMessage(),Toast.LENGTH_LONG).show();return;}
        aiBusy=true;field.setEnabled(false);review.setEnabled(false);int index=fields.indexOf(field);Button action=aiButtons.get(index);action.setText("正在分析…");for(Button button:aiButtons)if(button!=null)button.setEnabled(false);
        workers.execute(()->{String text=null,failure=null;AiClient client=null;try{AiConfig config=settings.read();if(aiStop.getAsBoolean())return;client=aiFactory.open(config,UserAgent.current(activity.getSharedPreferences("native_preferences",0)));aiClients.put(field,client);if(aiStop.getAsBoolean()){client.close();return;}byte[] jpeg=null;if(config.includeImage){if(analysisApi==null||snapshot.slide==null)throw new Exception("无法读取当前题目课件图片，请在 AI 设置中关闭图片选项");jpeg=AiClient.prepareImage(analysisApi.slideImage(snapshot.slide,aiStop));}AiQuestion.Result result=client.analyze(snapshot,jpeg);if(!result.answered()||result.text.trim().isEmpty())throw new Exception("AI 未能确定文字答案，请重试");text=result.text;}catch(Exception e){failure=e.getMessage()==null?"AI 分析失败":e.getMessage();}finally{if(client!=null)client.close();aiClients.remove(field);}
            String answer=text,error=failure;ui.post(()->{if(!valid())return;aiBusy=false;field.setEnabled(true);action.setText("AI 分析");review.setEnabled(true);updateFields();if(settings.revision()!=revision){Toast.makeText(activity,"AI 配置已变化，未填入",Toast.LENGTH_LONG).show();return;}if(error!=null)Toast.makeText(activity,error,Toast.LENGTH_LONG).show();else field.setText(answer);});
        });
    }
    private final Runnable guard=new Runnable(){public void run(){if(dialog==null||!dialog.isShowing())return;if(!valid()){close();return;}ui.postDelayed(this,500);}};
    private void cancel(){stopped.set(true);for(AiClient client:aiClients.values())client.close();aiClients.clear();if(analysisApi!=null)analysisApi.cancelOperation(aiStop);ui.removeCallbacks(guard);if(job!=null)job.close();if(confirmation!=null)confirmation.dismiss();}
    void close(){cancel();if(dialog!=null)dialog.dismiss();}
}
