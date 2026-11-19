package com.ykt.assistant;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

/** A manual, current-account-only answer sheet. No answer is submitted by a timer or socket event. */
final class ClassroomQuizDialog {
    private static final int GREEN=0xff2c5846,INK=0xff263d32,MUTED=0xff7e8d81,BG=0xfff7f9f5,LINE=0xffe3e9df,PALE=0xffedf3e9;
    private final Activity activity; private final RainApi api; private final LessonQuiz quiz; private final ExecutorService workers; private final BooleanSupplier current;
    private final Handler ui=new Handler(Looper.getMainLooper()); private Dialog dialog,picker,confirmation,preview;
    private LinearLayout body; private TextView title,status,error; private Button submit,choose;
    private OtherAnswersDialog otherDialog;private MultiAccountAnswers others;private SyncSettings sync;private SyncControls syncControl;
    interface ImagePicker{void pick(java.util.function.Consumer<List<android.net.Uri>> done);}
    private ImagePicker imagePicker;void setImagePicker(ImagePicker picker){imagePicker=picker;}
    private ImageOperation imageOperation;private Button addImage;private TextView imageStatus;
    private static final class ImageOperation{final LessonQuiz.Question question;final long round;final String spec;final java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean();final BooleanSupplier stop=stopped::get;ImageOperation(LessonQuiz.Question q){question=q;round=q.round;spec=q.spec.toString();}}
    private AiSettingsDialog aiSettingsDialog; private AiAnalysisDialog aiDialog; private AiClient.Factory aiFactory=AiClient::new;
    private LessonQuiz.Question selected; private long shownRound; private String shownSpec="";
    private MultiAccountAnswers.Job toastSyncJob;
    private void stopToastSync(){if(toastSyncJob!=null){toastSyncJob.close();toastSyncJob=null;}}
    private long syncDispatchEpoch;private boolean foreground=true;
    private int viewEpoch; private boolean fetching; private final List<View> inputs=new ArrayList<>();
    ClassroomQuizDialog(Activity activity,RainApi api,LessonQuiz quiz,ExecutorService workers,BooleanSupplier current) { this.activity=activity; this.api=api; this.quiz=quiz; this.workers=workers; this.current=current; }
    void setAiFactory(AiClient.Factory factory) { aiFactory=factory; }
    void setOtherAccounts(MultiAccountAnswers support){others=support;sync=new SyncSettings(activity,support.store,support.service,support.active);if(showing())render();}
    private int dp(int n) { return Math.round(n*activity.getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v=new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private GradientDrawable background(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private void gap(LinearLayout v,int size) { v.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(size))); }
    private TextView text(String value,int size,int color) { TextView v=new TextView(activity); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setIncludeFontPadding(false); v.setLineSpacing(dp(3),1); return v; }
    private CharSequence rich(String raw) { android.text.Spanned value=Html.fromHtml(raw==null?"":raw,Html.FROM_HTML_MODE_COMPACT); int a=0,b=value.length(); while(a<b&&Character.isWhitespace(value.charAt(a))) a++; while(b>a&&Character.isWhitespace(value.charAt(b-1))) b--; return value.subSequence(a,b); }
    private Button button(String label,boolean primary,Runnable action) { Button v=new FlatButton(activity); v.setText(label); v.setAllCaps(false); v.setTextSize(13); v.setTextColor(primary?0xffffffff:GREEN); v.setMinHeight(0); v.setMinimumHeight(0); v.setBackground(background(primary?GREEN:PALE,12)); v.setOnClickListener(x->action.run()); v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(46))); return v; }
    private LinearLayout panel() { LinearLayout v=column(); v.setPadding(dp(20),dp(18),dp(20),dp(18)); v.setBackground(background(BG,22)); return v; }
    private void header(LinearLayout panel,String title,Dialog dialog,int size) {
        LinearLayout row=new LinearLayout(activity); row.setGravity(Gravity.TOP); TextView label=text(title,size,INK); label.setTypeface(null,Typeface.BOLD); label.setMinHeight(dp(44)); label.setGravity(Gravity.CENTER_VERTICAL); row.addView(label,new LinearLayout.LayoutParams(0,-2,1)); ImageButton close=CardDialog.closeButton(activity,dialog,title); LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(44),dp(44)); params.leftMargin=dp(10); row.addView(close,params); panel.addView(row);
    }
    private void open(Dialog dialog,View content) {
        dialog.setContentView(content);
        dialog.show();
    }
    void show(String initial) {
        if(!current.getAsBoolean()) return; selected=quiz.preferred(initial); dialog=new CardDialog(activity); LinearLayout root=panel();
        header(root,"课堂答题",dialog,20); gap(root,6);
        root.addView(text("当前账号作答",11,MUTED)); gap(root,12);
        choose=button("切换题目",false,this::showPicker); root.addView(choose); gap(root,12);
        body=column(); root.addView(body);
        dialog.setOnDismissListener(d->{stopToastSync(); cancelImageUpload();if(otherDialog!=null)otherDialog.close(); if(aiDialog!=null) aiDialog.close(); if(aiSettingsDialog!=null) aiSettingsDialog.close(); ui.removeCallbacks(ticker); if(picker!=null) picker.dismiss(); if(confirmation!=null) confirmation.dismiss(); if(preview!=null) preview.dismiss(); });
        open(dialog,root); render(); ui.post(ticker);
    }
    void foreground(boolean active) { foreground=active;if(!active){syncDispatchEpoch++;stopToastSync();} if(!active&&aiDialog!=null) aiDialog.close();if(!active&&otherDialog!=null)otherDialog.close();if(!active)cancelImageUpload(); ui.removeCallbacks(ticker); if(active&&showing()) { changed(); ui.post(ticker); } }
    boolean showing() { return dialog!=null&&dialog.isShowing(); }
    void close() { syncDispatchEpoch++;stopToastSync(); cancelImageUpload();if(otherDialog!=null)otherDialog.close(); if(aiDialog!=null) aiDialog.close(); if(aiSettingsDialog!=null) aiSettingsDialog.close(); ui.removeCallbacks(ticker); if(dialog!=null) dialog.dismiss(); }
    private final Runnable ticker=new Runnable() { public void run() { if(!showing()) return; if(!current.getAsBoolean()) { close(); return; } updateState(); ui.postDelayed(this,500); } };
    void changed() {
        if(!showing()) return; if(!current.getAsBoolean()) { close(); return; }
        if(selected==null) selected=quiz.preferred("");
        if(selected==null||selected.round!=shownRound||!spec(selected).equals(shownSpec)) render(); else updateState();
        choose.setText("切换题目（"+quiz.all().size()+"）");
    }
    private String spec(LessonQuiz.Question q) { return q.spec==null?"":q.spec.toString(); }
    private void render() {
        if(!showing()) return; if(aiDialog!=null&&!aiDialog.valid()) aiDialog.close();if(otherDialog!=null&&!otherDialog.valid())otherDialog.close(); int ticket=++viewEpoch; inputs.clear(); body.removeAllViews(); submit=null; status=null; error=null; title=null;addImage=null;imageStatus=null;
        choose.setText("切换题目（"+quiz.all().size()+"）");
        if(selected==null) { body.addView(text("尚未收到课堂题目",17,INK)); gap(body,10); body.addView(text("老师开放题目后会自动出现，也可重新连接课堂获取时间线。",13,MUTED)); return; }
        final LessonQuiz.Question q=selected; shownRound=q.round; shownSpec=spec(q);
        title=text(q.typeName(),18,INK); body.addView(title); gap(body,8); status=text(quiz.status(q),12,GREEN); body.addView(status); gap(body,14);
        if(q.spec==null) {
            body.addView(text(fetching?"正在获取题目…":"尚未取得题干与选项",14,MUTED)); gap(body,14);
            Button load=button(fetching?"正在加载…":"加载题目",true,this::load); load.setEnabled(!fetching&&!q.presentation.isEmpty()); body.addView(load);
            if(q.presentation.isEmpty()) { gap(body,10); body.addView(text("缺少关联课件，请关闭后重新连接课堂。",12,MUTED)); }
            if(!q.lastError.isEmpty()) { gap(body,10); body.addView(text(q.lastError,12,0xffa35445)); }
            if(!fetching&&!q.presentation.isEmpty()&&q.lastError.isEmpty()) load(); return;
        }
        TextView question=text("",16,INK); question.setText(rich(q.spec.optString("body",""))); question.setTextIsSelectable(true); body.addView(question); gap(body,12);
        if(q.slide!=null&&(!q.slide.optString("cover","").isEmpty()||!q.slide.optString("coverAlt","").isEmpty())) {
            Button image=button("查看题目课件图片",false,()->showImage(q)); body.addView(image); gap(body,12);
        }
        if(q.choice()) {
            JSONArray options=q.options(); List<CheckBox> checks=new ArrayList<>();
            if(options!=null) for(int i=0;i<options.length()&&i<256;i++) {
                JSONObject option=options.optJSONObject(i); String key=LessonQuiz.id(option,"key"); if(key.isEmpty()) continue;
                CheckBox check=new CheckBox(activity); check.setText(rich(key+". "+option.optString("value",""))); check.setTextSize(14); check.setTextColor(INK); check.setPadding(dp(8),dp(8),dp(8),dp(8)); check.setMinHeight(dp(48)); check.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN)); check.setBackground(background(q.selected.contains(key)?PALE:0xffffffff,12)); check.setChecked(q.selected.contains(key)); check.setTag(key); checks.add(check); inputs.add(check); body.addView(check,new LinearLayout.LayoutParams(-1,-2)); gap(body,7);
                check.setOnCheckedChangeListener((v,checked)-> {
                    if(ticket!=viewEpoch) return;
                    if(checked) { if(q.maximum()==1) { q.selected.clear(); for(CheckBox other:checks) if(other!=check) other.setChecked(false); } else if(q.selected.size()>=q.maximum()) { check.setChecked(false); error.setText("最多选择 "+q.maximum()+" 项"); return; } q.selected.add(key); }
                    else q.selected.remove(key);
                    for(CheckBox other:checks) other.setBackground(background(other.isChecked()?PALE:0xffffffff,12));
                });
            }
            if(q.type()==3) { body.addView(text("最多选择 "+q.maximum()+" 项",11,MUTED)); gap(body,8); }
        } else if(q.type()==4) {
            int blanks=q.blankCount(); if(blanks==0||blanks>100) body.addView(text("未取得有效的填空配置，暂不能作答。",13,MUTED));
            for(int i=0;i<Math.min(blanks,100);i++) { final int index=i; body.addView(text("第 "+(i+1)+" 空",12,MUTED)); gap(body,6); EditText field=input(q.fields.get(i),"填写第 "+(i+1)+" 空",false,value->q.fields.set(index,value)); body.addView(field); gap(body,12); }
        } else if(q.type()==5) {
            EditText field=input(q.text,"填写文字答案",true,value->q.text=value); body.addView(field); gap(body,8); body.addView(text("支持文字或纯图片作答；选图后直接上传，最多 6 张，暂不支持视频。",11,MUTED)); gap(body,12);answerImages(q);
        } else { body.addView(text("此题型的提交格式尚未确认，暂仅支持查看。",13,MUTED)); gap(body,12); }
        if(q.supported()) {
            AiSettings.Preview ai=new AiSettings(activity).preview(); Button analyze=button(ai.configured()?"AI 分析":"配置 AI 答题",false,this::analyze); analyze.setTag("ai-analyze"); analyze.setEnabled(q.opened&&!q.sending&&!q.submitted&&!q.imageBusy); body.addView(analyze); gap(body,8); body.addView(text(ai.configured()?"题目将发送到 "+ai.model+(ai.includeImage?"，并附带课件图片":"，不附带课件图片"):"配置接口、模型和 Key 后，可让 AI 分析并填入答案。",11,MUTED)); gap(body,12);
        }
        syncControl=null;if(q.supported()){View control=newSyncControls();if(control!=null){syncControl=(SyncControls)control;body.addView(control);gap(body,12);}}
        error=text(q.lastError,12,0xffa35445); body.addView(error); gap(body,12); submit=button("提交答案",true,this::confirm); body.addView(submit); updateState();
    }
    private EditText input(String value,String hint,boolean multiline,java.util.function.Consumer<String> changed) {
        EditText v=new EditText(activity); v.setTextSize(14); v.setTextColor(INK); v.setHintTextColor(MUTED); v.setHint(hint); v.setText(value); v.setSaveEnabled(false); v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); v.setBackground(background(0xffffffff,12)); v.setPadding(dp(14),dp(12),dp(14),dp(12)); v.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10000)}); v.setMinLines(multiline?4:1); v.setMaxLines(multiline?12:4); v.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE); inputs.add(v);
        int ticket=viewEpoch; v.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence s,int start,int count,int after) {} public void onTextChanged(CharSequence s,int start,int before,int count) { if(ticket==viewEpoch) changed.accept(s.toString()); } public void afterTextChanged(Editable e) {} }); return v;
    }
    private void updateState() {
        if(aiDialog!=null&&!aiDialog.valid()) aiDialog.close();if(otherDialog!=null&&!otherDialog.valid())otherDialog.close();
        if(selected==null) return; LessonQuiz.Question q=selected;
        if(status!=null) status.setText(quiz.status(q)); if(error!=null&&!q.lastError.isEmpty()) error.setText(q.lastError);
        if(imageOperation!=null&&!imageValid(imageOperation))cancelImageUpload();
        boolean enabled=q.opened&&q.spec!=null&&q.supported()&&!q.sending&&!q.submitted&&!q.imageBusy;
        for(View input:inputs) input.setEnabled(enabled); View analyze=body.findViewWithTag("ai-analyze"); if(analyze!=null) analyze.setEnabled(enabled);
        if(addImage!=null)addImage.setEnabled(enabled&&imagePicker!=null&&q.images.size()<AnswerImage.MAX_COUNT);if(imageStatus!=null)imageStatus.setText(q.imageStatus);
        if(syncControl!=null){syncControl.setEnabled(enabled);syncControl.refresh();}
        if(submit!=null) { submit.setEnabled(enabled&&(!quiz.expired(q)||q.eventTime>0)); submit.setText(q.sending?"正在提交…":q.submitted?"已提交":quiz.expired(q)?"补交答案":"提交答案"); }
    }
    private void showPicker() {
        if(!current.getAsBoolean()) { close(); return; } picker=new CardDialog(activity); LinearLayout panel=panel(); header(panel,"选择课堂题目",picker,19); gap(panel,12); LinearLayout list=column(); panel.addView(list);
        List<LessonQuiz.Question> all=quiz.all();
        for(int i=0;i<all.size();i++) { LessonQuiz.Question q=all.get(i); String body=q.spec==null?"题目待加载":rich(q.spec.optString("body","")).toString().replace('\n',' '); if(body.length()>45) body=body.substring(0,45)+"…"; Button option=button((i+1)+" · "+body+(q.submitted?" · 已提交":q.opened?"":" · 未开放"),false,()-> { selected=q; picker.dismiss(); render(); }); option.setMinHeight(dp(60));option.setMinimumHeight(dp(60));option.setLayoutParams(new LinearLayout.LayoutParams(-1,-2)); list.addView(option); gap(list,8); }
         open(picker,panel);
    }
    private void load() {
        if(fetching||selected==null||selected.presentation.isEmpty()||!current.getAsBoolean()) return; fetching=true; LessonQuiz.Question q=selected; String pres=q.presentation; render();
        workers.execute(()-> {
            JSONObject deck=null; String failure=null; try { deck=api.quizPresentation(quiz.lesson,pres); } catch(Exception e) { failure=message(e); } JSONObject data=deck; String errorText=failure;
            ui.post(()-> { fetching=false; if(!current.getAsBoolean()) { close(); return; } try { if(errorText!=null) q.lastError=errorText; else { quiz.deck(pres,data); if(q.spec==null) q.lastError="这份课件中未找到对应题目，可重新加载。"; } } catch(Exception e) { q.lastError=message(e); } if(showing()) render(); });
        });
    }
    private void aiSettings() {
        if(aiSettingsDialog!=null) aiSettingsDialog.close(); aiSettingsDialog=new AiSettingsDialog(activity,new AiSettings(activity),workers,()->{if(showing()&&current.getAsBoolean()) render();}); aiSettingsDialog.show();
    }
    private void analyze() {
        if(!current.getAsBoolean()||selected==null||!selected.opened||selected.sending||selected.submitted) return;
        if(!new AiSettings(activity).preview().configured()) { aiSettings(); return; }
        if(aiDialog!=null) aiDialog.close(); LessonQuiz.Question target=selected;
        try { aiDialog=new AiAnalysisDialog(activity,api,quiz,target,new AiSettings(activity),workers,()->current.getAsBoolean()&&showing()&&selected==target,aiFactory,()->{if(showing()) render();},this::analyze,this::aiSettings,this::confirm); aiDialog.setSyncControls(this::newSyncControls); aiDialog.show(); } catch(Exception e) { if(error!=null) error.setText(message(e)); }
    }
    private View newSyncControls(){if(sync==null||!sync.available()||selected==null)return null;return new SyncControls(activity,sync,selected.type()==5?SyncSettings.SUBJECTIVE:SyncSettings.ANSWER,null);}
    private void confirm() {
        if(!current.getAsBoolean()) { close(); return; }
        try {
            LessonQuiz.Submission submission=quiz.prepare(selected);OtherAnswersDialog.Prepared prepared=null;String kind=selected.type()==5?SyncSettings.SUBJECTIVE:SyncSettings.ANSWER;
            if(sync!=null&&sync.available()&&sync.enabled(kind)){
                List<AccountStore.Account> targets=sync.selected(SyncSettings.ANSWER);if(targets.isEmpty())throw new Exception("同步答题未选择账号，请修改账号或取消勾选同步");
                MultiAccountAnswers.Plan plan=new MultiAccountAnswers.Plan(quiz,selected);
                if(selected.type()==5&&sync.independentSubjective()){LessonQuiz.Question target=selected;if(otherDialog!=null)otherDialog.close();otherDialog=new OtherAnswersDialog(activity,quiz,target,others,workers,()->current.getAsBoolean()&&showing()&&selected==target);otherDialog.setAi(api,aiFactory);otherDialog.collect(plan,targets,batch->{try{confirmPrepared(quiz.prepare(target),batch);}catch(Exception e){if(error!=null)error.setText(message(e));}});return;}
                List<MultiAccountAnswers.Task> tasks=new ArrayList<>();for(AccountStore.Account account:targets)tasks.add(new MultiAccountAnswers.Task(account,null));prepared=new OtherAnswersDialog.Prepared(plan,tasks);
            }
            confirmPrepared(submission,prepared);
        } catch(Exception e) { if(error!=null) error.setText(message(e)); }
    }
    private void confirmPrepared(LessonQuiz.Submission submission,OtherAnswersDialog.Prepared prepared){
        if(!current.getAsBoolean()||!showing())return;
        if(prepared!=null)try{for(MultiAccountAnswers.Task task:prepared.tasks)prepared.plan.prepare(task.text);}catch(Exception e){if(error!=null)error.setText(message(e));return;}
        if(!submission.retry){send(submission,prepared);return;}
        confirmation=new CardDialog(activity);LinearLayout panel=panel();header(panel,"确认补交答案？",confirmation,20);gap(panel,12);panel.addView(text("答题时间已结束，将尝试提交当前答案"+(prepared==null?"":"并同步所选其他账号")+"。是否接受补交及如何计分由雨课堂决定。",13,MUTED));gap(panel,20);panel.addView(button("确认补交",true,()->{confirmation.dismiss();send(submission,prepared);}));open(confirmation,panel);
    }
    private void send(LessonQuiz.Submission submission,OtherAnswersDialog.Prepared prepared) {
        if(!current.getAsBoolean()) { close(); return; }
        try { quiz.begin(submission); } catch(Exception e) { if(error!=null) error.setText(message(e)); return; } updateState();final long dispatch=syncDispatchEpoch;
        workers.execute(()-> {
            String failure=null; try { api.submitAnswer(submission); } catch(Exception e) { failure=message(e)+"；草稿已保留。若网络中断，请先核对官方答题记录再重试。"; } String result=failure;
            ui.post(()-> { quiz.complete(submission,result); if(!current.getAsBoolean()) { close(); return; } if(showing()) { updateState(); if(result==null&&selected!=null&&selected.id.equals(submission.question)){error.setText("提交成功；是否答对及得分以雨课堂记录为准。");if(prepared!=null&&foreground&&dispatch==syncDispatchEpoch){LessonQuiz.Question target=selected;if(otherDialog!=null)otherDialog.close();otherDialog=new OtherAnswersDialog(activity,quiz,target,others,workers,()->current.getAsBoolean()&&showing()&&selected==target);otherDialog.setAi(api,aiFactory);if(sync.resultCards())otherDialog.showPrepared(prepared);else syncWithToast(prepared,target,dispatch);}}else if(result!=null&&prepared!=null)error.setText(result+" 当前账号提交未成功，其他账号尚未发送。"); } });
        });
    }
    private void syncWithToast(OtherAnswersDialog.Prepared prepared,LessonQuiz.Question target,long dispatch){
        stopToastSync();MultiAccountAnswers.Job job=others.new Job();toastSyncJob=job;workers.execute(()->{List<MultiAccountAnswers.Result> results=job.run(prepared.plan,prepared.tasks,()->current.getAsBoolean()&&showing()&&foreground&&selected==target&&syncDispatchEpoch==dispatch&&!job.cancelled(),(account,state)->{});ui.post(()->{if(toastSyncJob!=job)return;toastSyncJob=null;if(current.getAsBoolean()&&showing()&&foreground&&syncDispatchEpoch==dispatch&&prepared.plan.valid()&&!job.cancelled())android.widget.Toast.makeText(activity,SyncFeedback.summary("答题",results,result->result.success),android.widget.Toast.LENGTH_LONG).show();job.close();});});
    }
    private boolean imageValid(ImageOperation op){return !op.stopped.get()&&current.getAsBoolean()&&showing()&&selected==op.question&&op.question.round==op.round&&op.question.spec!=null&&op.spec.equals(op.question.spec.toString())&&!op.question.submitted&&!op.question.sending;}
    private void cancelImageUpload(){ImageOperation op=imageOperation;if(op==null)return;imageOperation=null;op.stopped.set(true);api.cancelOperation(op.stop);if(op.question.round==op.round){op.question.imageBusy=false;op.question.imageStatus="上传已取消";}}
    private void answerImages(LessonQuiz.Question q){
        for(AnswerImage photo:new ArrayList<>(q.images)){LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);ImageView thumb=new ImageView(activity);thumb.setImageBitmap(photo.thumbnail());thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);thumb.setContentDescription("答案图片预览");thumb.setOnClickListener(v->previewAnswerImage(photo));row.addView(thumb,new LinearLayout.LayoutParams(dp(64),dp(64)));TextView label=text("图片 "+(q.images.indexOf(photo)+1)+" · 已上传",12,MUTED);label.setPadding(dp(10),0,0,0);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));Button remove=button("移除",false,()->{if(current.getAsBoolean()&&!q.sending&&!q.submitted&&!q.imageBusy){q.images.remove(photo);render();}});inputs.add(remove);row.addView(remove,new LinearLayout.LayoutParams(dp(66),dp(42)));body.addView(row);gap(body,8);}
        addImage=button("添加答案图片",false,()->{if(imagePicker==null||q.imageBusy||q.sending||q.submitted)return;long round=q.round;String pickedSpec=q.spec.toString();imagePicker.pick(uris->{if(!current.getAsBoolean()||!showing()||selected!=q||q.round!=round||q.spec==null||!pickedSpec.equals(q.spec.toString()))return;uploadImages(q,uris);});});addImage.setTag("answer-add-images");body.addView(addImage);gap(body,8);imageStatus=text(q.imageStatus,12,MUTED);body.addView(imageStatus);gap(body,12);
    }
    private void uploadImages(LessonQuiz.Question q,List<android.net.Uri> uris){
        if(uris==null||uris.isEmpty())return;if(q.imageBusy||q.sending||q.submitted)return;if(uris.size()+q.images.size()>AnswerImage.MAX_COUNT){q.lastError="最多添加 6 张图片，请减少选择后重试";render();return;}
        ImageOperation op=new ImageOperation(q);imageOperation=op;q.imageBusy=true;q.lastError="";q.imageStatus="正在读取图片…";render();workers.execute(()->{List<AnswerImage> photos=new ArrayList<>();String failure=null;try{for(android.net.Uri uri:uris){if(op.stop.getAsBoolean())break;AnswerImage photo=AnswerImage.read(activity,uri);if(op.stop.getAsBoolean())break;int index=photos.size()+1;ui.post(()->{if(imageValid(op)){q.imageStatus="正在上传图片 "+index+" / "+uris.size();if(imageStatus!=null)imageStatus.setText(q.imageStatus);}});photos.add(api.uploadAnswerImage(photo,op.stop));}}catch(Exception e){failure=message(e);}String problem=failure;ui.post(()->{if(imageOperation!=op||!imageValid(op))return;imageOperation=null;q.imageBusy=false;q.images.addAll(photos);q.imageStatus="已添加 "+photos.size()+" 张图片";if(problem!=null)q.lastError=problem;render();});});
    }
    private void previewAnswerImage(AnswerImage photo){preview=new CardDialog(activity);Dialog owner=preview;LinearLayout root=panel();header(root,"答案图片",owner,19);ZoomImageView image=new ZoomImageView(activity);root.addView(image,new LinearLayout.LayoutParams(-1,dp(180)));open(owner,root);workers.execute(()->{try{Bitmap bitmap=SlideExporter.decode(photo.bytes(),2048);ui.post(()->{if(owner.isShowing())setPreviewBitmap(image,bitmap);else bitmap.recycle();});}catch(Exception ignored){}});}
    private void showImage(LessonQuiz.Question q) {
        preview=new CardDialog(activity); final Dialog viewer=preview; LinearLayout root=panel(); header(root,"题目课件图片",viewer,19); gap(root,12); ZoomImageView image=new ZoomImageView(activity); image.setContentDescription("题目课件图片，支持双指缩放"); root.addView(image,new LinearLayout.LayoutParams(-1,dp(180))); TextView message=text("正在加载图片…",12,MUTED); root.addView(message);  open(viewer,root);
        workers.execute(()-> { Bitmap bitmap=null; String failure=null; try { bitmap=SlideExporter.decode(api.slideImage(q.slide),2048); } catch(Exception e) { failure=message(e); } Bitmap value=bitmap; String problem=failure; ui.post(()-> { if(!viewer.isShowing()||!current.getAsBoolean()) return; if(problem==null) { setPreviewBitmap(image,value); message.setText("双指缩放或双击查看"); } else message.setText(problem); }); });
    }
    private void setPreviewBitmap(ZoomImageView image,Bitmap bitmap) {
        ViewGroup.LayoutParams params=image.getLayoutParams();
        int width=Math.max(1,image.getWidth());
        if(width==1) width=Math.min(dp(480),activity.getResources().getDisplayMetrics().widthPixels-dp(64));
        int naturalHeight=Math.round(width*(float)bitmap.getHeight()/bitmap.getWidth());
        int maxHeight=Math.max(dp(96),activity.getResources().getDisplayMetrics().heightPixels*3/5);
        params.height=Math.min(maxHeight,Math.max(dp(96),naturalHeight));
        image.setLayoutParams(params);
        image.setImageBitmap(bitmap);
    }
    private static String message(Exception e) { return e.getMessage()==null?"请求失败，请稍后重试":e.getMessage(); }
}
