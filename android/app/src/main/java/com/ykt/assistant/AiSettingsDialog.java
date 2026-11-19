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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;

/** Editable provider configuration. The stored secret is represented only by a hint. */
final class AiSettingsDialog {
    private static final int GREEN=0xff2c5846,INK=0xff263d32,MUTED=0xff7e8d81,BG=0xfff7f9f5;
    private final Activity activity;private final AiSettings settings;private final ExecutorService workers;private final Runnable saved;private final Handler ui=new Handler(Looper.getMainLooper());
    private Dialog dialog,child;
    AiSettingsDialog(Activity activity,AiSettings settings,ExecutorService workers,Runnable saved){this.activity=activity;this.settings=settings;this.workers=workers;this.saved=saved;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(size>=16?INK:MUTED);v.setIncludeFontPadding(false);return v;}
    private void gap(LinearLayout v,int n){v.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(n)));}
    private Button button(String label,boolean primary,Runnable action){Button b=new FlatButton(activity);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(primary?0xffffffff:GREEN);b.setMinHeight(0);b.setMinimumHeight(0);b.setBackground(bg(primary?GREEN:0xffedf3e9));b.setOnClickListener(v->action.run());b.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(46)));return b;}
    private void header(LinearLayout panel,String title,Dialog owner){LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);TextView t=text(title,21);t.setTypeface(null,Typeface.BOLD);row.addView(t,new LinearLayout.LayoutParams(0,dp(44),1));row.addView(CardDialog.closeButton(activity,owner,title),new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(row);}
    private void show(Dialog owner,LinearLayout panel) {
        owner.setContentView(panel);
        owner.show();
    }
    private LinearLayout panel(){LinearLayout v=column();v.setPadding(dp(20),dp(16),dp(20),dp(20));v.setBackground(bg(BG));return v;}
    private EditText field(LinearLayout parent,String label,String value,boolean secret){parent.addView(text(label,12));gap(parent,6);EditText v=new EditText(activity);v.setContentDescription(label);v.setTextSize(13);v.setTextColor(INK);v.setHintTextColor(MUTED);v.setSingleLine(true);v.setBackground(bg(0xffffffff));v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setSaveEnabled(false);v.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);v.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(secret?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_VARIATION_URI));if(!secret)v.setText(value);parent.addView(v,new LinearLayout.LayoutParams(-1,dp(48)));gap(parent,12);return v;}
    private CheckBox check(LinearLayout parent,String label,boolean checked){CheckBox v=new CheckBox(activity);v.setText(label);v.setTextSize(12);v.setTextColor(INK);v.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));v.setChecked(checked);v.setMinHeight(dp(44));parent.addView(v);return v;}
    void show(){
        AiSettings.Preview state=settings.preview();dialog=new CardDialog(activity);LinearLayout root=panel();header(root,"AI 答题设置",dialog);gap(root,10);root.addView(text("兼容 OpenAI Chat Completions",12));gap(root,16);
        EditText endpoint=field(root,"接口地址",state.endpoint,false);endpoint.setTag("ai-endpoint");endpoint.setHint("例如 https://api.openai.com/v1");EditText model=field(root,"模型名称",state.model,false);model.setTag("ai-model");model.setHint("例如 deepseek");EditText key=field(root,"API Key","",true);key.setTag("ai-key");key.setHint(state.hasKey?"已保存 Key，留空保持原值":"Key 仅用于所选 AI 服务");
        CheckBox image=check(root,"附带当前课件图片（需要视觉模型）",state.includeImage),auto=check(root,"分析完成后自动填入答案（不自动提交）",state.autoFill);gap(root,12);TextView error=text("",12);error.setTextColor(0xffa35445);root.addView(error);gap(root,8);
        Button save=button("保存 AI 配置",true,()->{});root.addView(save);gap(root,10);root.addView(button("清除 AI 配置",false,this::confirmClear));
        save.setOnClickListener(v->{String address=endpoint.getText().toString(),name=model.getText().toString(),secret=key.getText().toString();boolean clearKey=false,jsonMode=true,attach=image.isChecked(),fill=auto.isChecked();try{new AiConfig(address,name,secret,jsonMode,attach,fill);}catch(IllegalArgumentException e){error.setText(e.getMessage());return;}dialog.setCancelable(false);save.setEnabled(false);error.setText("正在加密保存…");workers.execute(()->{String failure=null;try{settings.save(address,name,secret,clearKey,jsonMode,attach,fill,()->activity.isDestroyed()||activity.isFinishing());}catch(Exception e){failure=e.getMessage();}String result=failure;ui.post(()->{if(!dialog.isShowing()||activity.isDestroyed())return;dialog.setCancelable(true);save.setEnabled(true);if(result==null){dialog.dismiss();saved.run();}else error.setText(result);});});});
        dialog.setOnDismissListener(d->{key.setText("");if(child!=null)child.dismiss();});show(dialog,root);
    }
    private void confirmClear(){child=new CardDialog(activity);LinearLayout root=panel();header(root,"清除 AI 配置？",child);gap(root,12);root.addView(text("将删除接口、模型和本机保存的 API Key。",12));gap(root,18);root.addView(button("确认清除",true,()->{try{settings.clear();child.dismiss();dialog.dismiss();saved.run();}catch(Exception e){android.widget.Toast.makeText(activity,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}}));show(child,root);}
    void close(){if(child!=null)child.dismiss();if(dialog!=null)dialog.dismiss();}
}
