package com.ykt.assistant;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Result preview; validated JSON can fill a draft, never invoke Rain answer endpoints. */
final class AiAnalysisDialog {
    private final Activity activity;private final RainApi api;private final LessonQuiz quiz;private final LessonQuiz.Question q;private final AiQuestion snapshot;private final AiSettings settings;private final long revision;private final ExecutorService workers;private final BooleanSupplier current;private final AiClient.Factory factory;private final Runnable applied,retry,configure,submit;
    private final Handler ui=new Handler(Looper.getMainLooper());private final AtomicBoolean stopped=new AtomicBoolean();private final BooleanSupplier stop=stopped::get;
    private java.util.function.Supplier<View> syncControls;
    void setSyncControls(java.util.function.Supplier<View> controls){syncControls=controls;}
    private volatile AiClient client;private Dialog dialog;private TextView state,answer,explanation,confidence;private ProgressBar loading;private Button fill,again,send;private boolean filled;private AiQuestion.Result result;
    AiAnalysisDialog(Activity activity,RainApi api,LessonQuiz quiz,LessonQuiz.Question q,AiSettings settings,ExecutorService workers,BooleanSupplier current,AiClient.Factory factory,Runnable applied,Runnable retry,Runnable configure,Runnable submit)throws Exception{this.activity=activity;this.api=api;this.quiz=quiz;this.q=q;this.settings=settings;this.workers=workers;this.current=current;this.factory=factory;this.applied=applied;this.retry=retry;this.configure=configure;this.submit=submit;snapshot=new AiQuestion(q);revision=settings.revision();}
    boolean valid(){return !stopped.get()&&!activity.isDestroyed()&&!activity.isFinishing()&&current.getAsBoolean()&&settings.revision()==revision&&snapshot.matches(quiz,q);}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(size>=16?0xff263d32:0xff7e8d81);v.setIncludeFontPadding(false);v.setLineSpacing(dp(3),1);return v;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
    private void gap(LinearLayout p,int n){p.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(n)));}
    private Button button(String label,boolean primary,Runnable action){Button b=new FlatButton(activity);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setMinHeight(0);b.setMinimumHeight(0);b.setTextColor(primary?0xffffffff:0xff2c5846);b.setBackground(bg(primary?0xff2c5846:0xffedf3e9));b.setOnClickListener(v->action.run());b.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(46)));return b;}
    void show(){
        if(!valid())return;AiSettings.Preview p=settings.preview();dialog=new CardDialog(activity);LinearLayout root=column();root.setPadding(dp(20),dp(16),dp(20),dp(20));root.setBackground(bg(0xfff7f9f5));LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("AI 分析",21);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));header.addView(CardDialog.closeButton(activity,dialog,"AI 分析"),new LinearLayout.LayoutParams(dp(44),dp(44)));root.addView(header);gap(root,10);root.addView(text(p.summary(),12));gap(root,16);
        LinearLayout content=column();root.addView(content);state=text("正在分析…",16);content.addView(state);gap(content,12);loading=new ProgressBar(activity);loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(0xff2c5846));LinearLayout.LayoutParams progress=new LinearLayout.LayoutParams(dp(28),dp(28));progress.gravity=Gravity.CENTER_HORIZONTAL;content.addView(loading,progress);gap(content,12);answer=text("",17);answer.setTextIsSelectable(true);content.addView(answer);gap(content,12);explanation=text("",13);explanation.setTextIsSelectable(true);content.addView(explanation);gap(content,12);confidence=text("",11);content.addView(confidence);gap(content,18);
        fill=button("填入答案",true,this::apply);fill.setVisibility(View.GONE);content.addView(fill);gap(content,10);again=button("重新分析",false,()->{close();retry.run();});again.setVisibility(View.GONE);content.addView(again);gap(content,10);content.addView(button("AI 设置",false,()->{close();configure.run();}));gap(content,24);if(syncControls!=null){View control=syncControls.get();if(control!=null){content.addView(control);gap(content,12);}}send=button("提交",true,this::submitAnswer);send.setVisibility(View.GONE);content.addView(send);gap(content,10);content.addView(text("AI 可能出错。填入会替换本题草稿，仍需你点击提交。",11));dialog.setContentView(root);dialog.setOnDismissListener(d->cancelWork());dialog.show();ui.post(guard);
        workers.execute(()->{
            AiQuestion.Result value=null;String failure=null;AiConfig config=null;
            try{if(stopped.get())return;config=settings.read();AiClient transport=factory.open(config,UserAgent.current(activity.getSharedPreferences("native_preferences",Activity.MODE_PRIVATE)));client=transport;if(stopped.get()){transport.close();return;}byte[] image=null;if(config.includeImage){if(snapshot.slide==null)throw new Exception("本题没有可附带的课件图片，请在 AI 设置中关闭图片选项");image=AiClient.prepareImage(api.slideImage(snapshot.slide,stop));}if(stopped.get())return;value=transport.analyze(snapshot,image);}catch(Exception e){failure=e.getMessage()==null?"AI 分析失败，请检查配置":e.getMessage();}finally{AiClient transport=client;if(transport!=null)transport.close();}
            AiQuestion.Result response=value;String error=failure;AiConfig used=config;
            ui.post(()->{if(!valid()||!dialog.isShowing())return;loading.setVisibility(View.GONE);again.setVisibility(View.VISIBLE);if(error!=null){state.setText("分析失败");explanation.setText(error);return;}result=response;state.setText(result.answered()?"分析完成":"AI 未能确定答案");answer.setText(result.display());explanation.setText("解析\n"+result.explanation);confidence.setText(String.format(java.util.Locale.ROOT,"置信度（模型自评）：%.2f",result.confidence));fill.setVisibility(result.answered()?View.VISIBLE:View.GONE);send.setVisibility(result.answered()?View.VISIBLE:View.GONE);
                if(used!=null&&used.autoFill&&result.answered()){if(snapshot.draft.equals(AiQuestion.draft(q))){try{snapshot.apply(quiz,q,result);applied.run();state.setText("已自动填入，尚未提交");filled=true;fill.setVisibility(View.GONE);}catch(Exception e){state.setText(e.getMessage());}}else state.setText("草稿已变化，请确认后手动填入");}
            });
        });
    }
    private final Runnable guard=new Runnable(){public void run(){if(dialog==null||!dialog.isShowing())return;if(!valid()){close();return;}ui.postDelayed(this,500);}};
    private void apply(){if(!valid()){close();return;}try{snapshot.apply(quiz,q,result);applied.run();state.setText("已填入，尚未提交");filled=true;fill.setVisibility(View.GONE);}catch(Exception e){state.setText(e.getMessage());}}
    private void submitAnswer(){
        if(!valid()||result==null||!result.answered())return;
        try { if(!filled){snapshot.apply(quiz,q,result);filled=true;applied.run();} close();submit.run(); }
        catch(Exception e){state.setText(e.getMessage());}
    }
    private void cancelWork(){stopped.set(true);ui.removeCallbacks(guard);AiClient transport=client;if(transport!=null)transport.close();api.cancelOperation(stop);}
    void close(){cancelWork();if(dialog!=null)dialog.dismiss();}
}
