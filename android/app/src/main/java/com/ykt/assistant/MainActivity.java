package com.ykt.assistant;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int GREEN=0xff2c5846,INK=0xff263d32,MUTED=0xff7e8d81,BG=0xfff7f9f5,LINE=0xffe3e9df,PALE=0xffedf3e9,WHITE=0xffffffff;
    private static final String PROJECT_URL="https://github.com/FirmPluvia/ykt_assistant";
    private static final int SAVE=42,SAVE_IMAGE=44,EXPORT_LOGS=45;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(4);
    private final LruCache<String,Bitmap> bitmaps=new LruCache<String,Bitmap>(16*1024*1024) {
        @Override protected int sizeOf(String key,Bitmap value) { return value.getAllocationByteCount(); }
    };
    private RainApi api;
    private QrLoginController qrController;
    private DiagnosticLog diagnostics;
    private SessionVault vault;
    private LessonCache lessonCache;
    private SharedPreferences prefs;
    private LinearLayout root,body,nav;
    private FrameLayout content;
    private ProgressBar loading;
    private TextView slideNumber,connectionLabel;
    private ImageView slideImage;
    private CheckBox follow;
    private LinearLayout thumbStrip;
    private JSONObject user,presentation;
    private CourseCatalog catalog;
    private String courseError;
    private long courseUpdated;
    private String currentCourse="",connection="等待课堂连接",pendingFormat;
    private JSONObject exportPresentation;
    private Runnable cancelEntryLookup;
    private boolean checkingCredentials,onlyLive,fetchingPpt,joining,downloading,loadingCourses,locationSubmitting;
    private int tab,page,imageEpoch,loginEpoch,busyCount,lessonEpoch,entryRevision,pendingExportEpoch;
    private volatile int accountEpoch;
    private RainApi pendingExportApi;
    private final List<Dialog> dialogs=new ArrayList<>();
    private Dialog loginDialog,fullDialog,humanVerificationDialog;
    private ZoomImageView fullImage;
    interface Task<T> { T get() throws Exception; }
    interface Done<T> { void accept(T value) throws Exception; }
    interface Fail { void accept(Exception error); }
    private <T> void work(Task<T> task,Done<T> done) { work(task,done,e -> toast(error(e))); }
    private <T> void work(Task<T> task,Done<T> done,Fail fail) {
        if(isDestroyed()||isFinishing()) return;
        final int epoch=accountEpoch; busyCount++; showLoading();
        workers.execute(() -> { T result=null; Exception error=null; try { result=task.get(); } catch(Exception e) { error=e; }
            T value=result; Exception failure=error;
            ui.post(() -> { if(isDestroyed()||epoch!=accountEpoch) return; busyCount=Math.max(0,busyCount-1); showLoading();
                if(failure!=null) { fail.accept(failure); } else try { done.accept(value); } catch(Exception e) { fail.accept(e); }
            });
        });
    }
    private void showLoading() { if(loading!=null) loading.setVisibility(busyCount>0?View.VISIBLE:View.INVISIBLE); }
    private String error(Exception e) { if(e.getMessage()!=null&&!e.getMessage().isEmpty()) return e.getMessage(); return "网络请求失败，请检查网络后重试"; }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    private int dp(float n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int color,float radius,int stroke) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); if(stroke!=0) d.setStroke(dp(1),stroke); return d; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private LinearLayout.LayoutParams lp(int width,int height) { return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height)); }
    private void gap(LinearLayout layout,int height) { View v=new View(this); layout.addView(v,lp(1,height)); }
    private TextView text(String value,int size,int color,boolean bold) { TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setIncludeFontPadding(false); t.setLineSpacing(dp(3),1); if(bold) t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); return t; }
    private Button button(String label,boolean primary,Runnable action) {
        Button b=new Button(this); b.setAllCaps(false); b.setText(label); b.setTextSize(13); b.setTextColor(primary?WHITE:GREEN); b.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); b.setIncludeFontPadding(false);
        b.setMinHeight(0); b.setMinimumHeight(0); b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(16),0,dp(16),0); b.setBackground(bg(primary?GREEN:WHITE,12,primary?0:LINE));
        b.setStateListAnimator(null); b.setOnClickListener(v -> action.run()); b.setLayoutParams(lp(-1,48)); return b;
    }
    private TextView pill(String value,int color,int background) { TextView t=text(value,11,color,true); t.setPadding(dp(10),dp(5),dp(10),dp(5)); t.setBackground(bg(background,20,0)); t.setGravity(Gravity.CENTER); return t; }
    private LinearLayout compactCard() { LinearLayout c=column(); c.setPadding(dp(16),dp(14),dp(16),dp(14)); c.setBackground(bg(WHITE,16,LINE)); return c; }
    private LinearLayout card() { LinearLayout c=column(); c.setPadding(dp(20),dp(20),dp(20),dp(20)); c.setBackground(bg(WHITE,20,LINE)); return c; }
    private void addCard(LinearLayout c) { body.addView(c,lp(-1,-2)); gap(body,16); }
    private TextView lineLabel(String value) { TextView t=text(value,11,MUTED,true); t.setLetterSpacing(.12f); return t; }
    private void backLink(String label,Runnable action) {
        TextView back=text("← "+label,14,GREEN,true); back.setMinHeight(dp(44)); back.setGravity(Gravity.CENTER_VERTICAL); back.setOnClickListener(v -> action.run()); body.addView(back); gap(body,4);
    }
    private void heading(String eyebrow,String title,String subtitle) { body.addView(lineLabel(eyebrow)); gap(body,10); body.addView(text(title,28,INK,true)); gap(body,10); body.addView(text(subtitle,13,MUTED,false)); gap(body,24); }
    private void section(String title,String right,Runnable action) { LinearLayout r=row(); r.addView(text(title,18,INK,true),new LinearLayout.LayoutParams(0,-2,1)); if(right!=null) { TextView t=text(right,12,GREEN,true); t.setGravity(Gravity.CENTER); t.setPadding(dp(14),dp(12),0,dp(12)); t.setMinHeight(dp(44)); t.setOnClickListener(v -> action.run()); r.addView(t); } body.addView(r,lp(-1,-2)); gap(body,12); }
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); prefs=getSharedPreferences("native_preferences",MODE_PRIVATE); vault=new SessionVault(this);
        lessonCache=new LessonCache(this); activityStore=new FeedStore(this); diagnostics=new DiagnosticLog(this); diagnostics.event("APP","startup","OPEN",0,0,0);
        // The v1 WebView relay configuration is intentionally neither read nor used.
        getSharedPreferences("assistant",MODE_PRIVATE).edit().clear().apply();
        root=column(); root.setBackgroundColor(BG);
        if(android.os.Build.VERSION.SDK_INT>=30) root.setOnApplyWindowInsetsListener((v,insets) -> { android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()); v.setPadding(i.left,i.top,i.right,i.bottom); return insets; }); else root.setFitsSystemWindows(true);
        setContentView(root);
        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); loading.setIndeterminate(true); loading.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(GREEN)); root.addView(loading,lp(-1,2)); loading.setVisibility(View.INVISIBLE);
        content=new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        FrameLayout dock=new FrameLayout(this); dock.setBackgroundColor(BG);
        nav=row(); nav.setPadding(dp(8),dp(6),dp(8),dp(6)); nav.setBackground(bg(WHITE,24,LINE));
        int dockWidth=Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(480));
        int navHeight=Math.max(70,Math.round(54+14*getResources().getConfiguration().fontScale));
        FrameLayout.LayoutParams dockParams=new FrameLayout.LayoutParams(dockWidth,dp(navHeight),Gravity.TOP|Gravity.CENTER_HORIZONTAL); dockParams.topMargin=dp(6);
        dock.addView(nav,dockParams); root.addView(dock,lp(-1,navHeight+18));
        render();
        try {
            api=createStartupApi();
        } catch(Exception e) { vault.clear(); toast("本机会话无法读取，请重新登录"); try { api=configureApi(new RainApi(prefs.getInt("service",0),vault.session())); } catch(Exception ignored) {} }
        restoreStartupSession();
    }
    protected RainApi createStartupApi() throws Exception { return configureApi(new RainApi(prefs.getInt("service",0),vault.session())); }
    private void migrateLegacyLogs() {
        if(prefs.getBoolean("legacy_logs_removed_v273",false)) return;
        workers.execute(() -> {
            try {
                LegacyLogCleanup.remove(new java.io.File(LessonCache.privateRoot(this),"logs"));
                LegacyLogCleanup.remove(new java.io.File(getFilesDir(),"logs"));
                prefs.edit().putBoolean("legacy_logs_removed_v273",true).apply();
            } catch(Exception ignored) { /* Retry upgrade cleanup on the next launch. */ }
        });
    }
    private void render() {
        imageEpoch++;
        if(tab==1&&dynamicView==FEED&&user!=null&&feedScroll!=null&&feedAccount==accountEpoch&&!resetFeedScroll) {
            contentScroll=feedScroll; body=feedBody;
            if(feedScroll.getParent()!=content) { content.removeAllViews(); content.addView(feedScroll,new FrameLayout.LayoutParams(-1,-1)); }
            renderNavigation(); bindFeed(); return;
        }
        if(resetFeedScroll) clearFeedViews(); resetFeedScroll=false;
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); contentScroll=scroll;
        FrameLayout center=new FrameLayout(this); body=column(); body.setPadding(dp(20),dp(tab==1&&dynamicView!=FEED?8:28),dp(20),dp(24));
        int width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(720)); FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(width,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL); center.addView(body,p); scroll.addView(center); content.removeAllViews(); content.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        renderNavigation();
        if(tab==0) coursesPage(); else if(tab==1) dynamicsPage(); else accountPage();

    }
    private void renderNavigation() {
        nav.removeAllViews(); String[] labels={"课堂","动态","我的"};
        for(int i=0;i<labels.length;i++) {
            final int target=i; boolean selected=i==tab;
            LinearLayout item=column(); item.setGravity(Gravity.CENTER); item.setSelected(selected);
            item.setBackground(new RippleDrawable(ColorStateList.valueOf(0x142c5846),bg(WHITE,18,0),bg(WHITE,18,0)));
            FrameLayout iconPill=new FrameLayout(this); iconPill.setBackground(bg(selected?PALE:WHITE,16,0));
            Icon icon=new Icon(this,i,selected?GREEN:MUTED); iconPill.addView(icon,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
            item.addView(iconPill,lp(58,30)); gap(item,4);
            TextView label=text(labels[i],11,selected?GREEN:MUTED,selected); label.setGravity(Gravity.CENTER); item.addView(label,lp(-1,-2));
            nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));
            item.setContentDescription(labels[i]); item.setFocusable(true);
            item.setOnClickListener(v -> { if(target!=tab&&cancelEntryLookup!=null) cancelEntryLookup.run(); if(target==1) { tab=1; showFeed(); if(user!=null&&!loadingActivities) loadActivities(); } else if(tab!=target) { tab=target; render(); } });
        }
    }
    private void courseFilters() {
        LinearLayout segmented=row(); segmented.setPadding(dp(4),dp(4),dp(4),dp(4)); segmented.setBackground(bg(PALE,14,0));
        String[] labels={"正在上课","全部课程"};
        for(int i=0;i<labels.length;i++) {
            final boolean live=i==0; boolean selected=onlyLive==live;
            TextView choice=text(labels[i],12,selected?GREEN:MUTED,selected); choice.setGravity(Gravity.CENTER); choice.setSelected(selected);
            choice.setBackground(new RippleDrawable(ColorStateList.valueOf(0x142c5846),bg(selected?WHITE:PALE,11,selected?LINE:0),bg(WHITE,11,0)));
            choice.setOnClickListener(v -> { if(onlyLive!=live) { onlyLive=live; render(); } }); choice.setFocusable(true);
            LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(0,dp(48),1); c.setMargins(dp(2),0,dp(2),0); segmented.addView(choice,c);
        }
        body.addView(segmented,lp(-1,56)); gap(body,16);
    }
    private void coursesPage() {
        heading("YOUR LEARNING SPACE",user==null?"每一堂课，都从容开始":user.optString("name","同学")+"，上课见",user==null?"查看课堂，轻松签到，把重要的每一页留在身边。":"先上好这一堂课，再把知识留在身边。");
        if(user==null) {
            LinearLayout hero=column(); hero.setPadding(dp(24),dp(24),dp(24),dp(24)); hero.setBackground(bg(GREEN,24,0));
            hero.addView(pill("专注学习，从这里开始",0xffe0eadc,0xff426b53)); gap(hero,20); hero.addView(text("连接课堂的\n每一个瞬间",26,WHITE,true)); gap(hero,12); hero.addView(text("扫码连接你的账号，\n让课程、签到与课件井井有条。",13,0xffc5d6c0,false)); gap(hero,24);
            Button start=button("扫码登录雨课堂",false,this::openLogin); start.setBackground(bg(0xffedf2e6,12,0)); hero.addView(start); addCard(hero); return;
        }
        boolean hasLive=catalog!=null&&catalog.hasLive();
        LinearLayout stats=row(); stat(stats,"我的课程",catalog==null?"—":String.valueOf(catalog.courseCount)); if(hasLive) stat(stats,"正在上课",String.valueOf(catalog.liveCount())); stat(stats,"当前入课",hasLesson()?"1":"0"); body.addView(stats,lp(-1,-2)); gap(body,24);
        section(hasLive?"我的课堂":"全部课程",loadingCourses?"刷新中…":"刷新课堂",this::loadCourses);
        if(hasLive) courseFilters();
        if(courseError!=null) {
            LinearLayout warning=card(); warning.setBackground(bg(0xfffaf3eb,16,0xffeee0cd)); warning.addView(text(catalog==null?"课程加载失败":"刷新未完成",14,0xff8c6844,true)); gap(warning,8); warning.addView(text(courseError,12,MUTED,false)); gap(warning,14); warning.addView(button("重新刷新",false,this::loadCourses)); addCard(warning);
        }
        if(catalog==null) { if(courseError==null) emptyCard("正在获取你的课程","正在直接连接雨课堂，请稍候。",null,null); return; }
        List<CourseCatalog.Entry> entries=catalog.entries(onlyLive);
        if(entries.isEmpty()) emptyCard("还没有课程","可以稍后刷新，或在官方雨课堂确认你的课程。","刷新课堂",this::loadCourses);
        for(CourseCatalog.Entry entry:entries) courseCard(entry);
        if(courseUpdated>0) { TextView updated=text("更新于 "+android.text.format.DateFormat.format("HH:mm",courseUpdated)+" · 来自雨课堂",10,MUTED,false); updated.setGravity(Gravity.CENTER); body.addView(updated); }
    }
    private void stat(LinearLayout row,String label,String value) {
        LinearLayout item=column(); item.setPadding(dp(14),dp(16),dp(12),dp(16)); item.setBackground(bg(WHITE,16,LINE)); item.addView(text(value,25,INK,true)); gap(item,8); item.addView(text(label,11,MUTED,false));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
        if(row.getChildCount()>0) { p.leftMargin=dp(5); View previous=row.getChildAt(row.getChildCount()-1); LinearLayout.LayoutParams prev=(LinearLayout.LayoutParams)previous.getLayoutParams(); prev.rightMargin=dp(5); previous.setLayoutParams(prev); }
        row.addView(item,p);
    }
    private void emptyCard(String title,String description,String action,Runnable run) { LinearLayout c=card(); c.addView(text(title,17,INK,true)); gap(c,10); c.addView(text(description,12,MUTED,false)); if(action!=null) { gap(c,20); c.addView(button(action,true,run)); } addCard(c); }
    private void courseCard(CourseCatalog.Entry item) {
        JSONObject c=item.course; boolean live=item.live!=null; LinearLayout card=compactCard(),top=row(),names=column(); top.setGravity(Gravity.TOP);
        FrameLayout tile=new FrameLayout(this); tile.setBackground(bg(PALE,12,0)); tile.addView(new Icon(this,0,GREEN),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER)); LinearLayout.LayoutParams tileParams=lp(36,36); tileParams.rightMargin=dp(12); top.addView(tile,tileParams);
        TextView title=text(c.optString("course_name","未命名课程"),16,INK,true); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); names.addView(title);
        JSONObject teacher=c.optJSONObject("teacher"); String teacherName=teacher==null?"":teacher.optString("name",""); gap(names,6); TextView teacherLabel=text(teacherName.isEmpty()?"教师待更新":teacherName,12,MUTED,false); teacherLabel.setSingleLine(true); teacherLabel.setEllipsize(android.text.TextUtils.TruncateAt.END); names.addView(teacherLabel);
        top.addView(names,new LinearLayout.LayoutParams(0,-2,1)); TextView state=pill(live?"正在上课":"暂未上课",live?GREEN:MUTED,live?PALE:BG); LinearLayout.LayoutParams badge=lp(-2,-2); badge.leftMargin=dp(10); top.addView(state,badge); card.addView(top);
        if(live) {
            gap(card,14); card.addView(button("查看课堂",true,() -> confirmCheckin(item)));
        }
        card.setMinimumHeight(dp(84)); addCard(card);
    }
    private boolean hasLesson() { return api!=null&&api.lesson()!=null; }
    private void loadCourses() {
        if(user==null||api==null||api.isClosed()) { openLogin(); return; }
        if(loadingCourses) return;
        RainApi a=api; loadingCourses=true; courseError=null; if(tab==0) render();
        work(() -> CourseCatalog.from(a.courses()),data -> {
            loadingCourses=false; catalog=data; onlyLive=data.hasLive(); courseUpdated=System.currentTimeMillis(); courseError=null; if(tab==0) render();
        },e -> { loadingCourses=false; courseError=error(e); if(tab==0) render(); else toast("刷新课程失败："+courseError); });
    }
    private void confirmCheckin(CourseCatalog.Entry entry) {
        if(user==null||api==null||api.isClosed()) { openLogin(); return; }
        if(checkingCredentials) { toast("正在读取课堂凭证，请稍候"); return; }
        if(joining||locationSubmitting||checkingLocation) { toast("请先完成当前课堂操作"); return; }
        if(downloading||pendingFormat!=null) { toast("请先完成课件保存，再切换课堂"); return; }
        if(entry.live==null||entry.live.optString("lessonId","").isEmpty()) { toast("当前课程没有正在进行的课堂"); return; }
        final RainApi owner=api; final String id=entry.live.optString("lessonId"); final int revision=++entryRevision,account=accountEpoch;
        if(id.equals(owner.lesson())) { showLiveClass(entry,false); return; }
        final boolean[] completed={false}; final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean(); checkingCredentials=true;
        final Sheet[] waiting={null};
        Runnable showWaiting=() -> {
            if(completed[0]||cancelled.get()||revision!=entryRevision||account!=accountEpoch||api!=owner||isDestroyed()||isFinishing()) return;
            Sheet sheet=createSheet("读取课堂凭证","正在检查本机保存的课堂凭证。"); waiting[0]=sheet;
            ProgressBar progress=new ProgressBar(this); progress.setIndeterminateTintList(ColorStateList.valueOf(GREEN)); LinearLayout.LayoutParams pp=lp(28,28); pp.gravity=Gravity.CENTER_HORIZONTAL; sheet.panel.addView(progress,pp); gap(sheet.panel,20); sheet.panel.addView(button("暂不进入",false,sheet.dialog::dismiss));
            sheet.dialog.setOnDismissListener(d -> { if(!completed[0]) { cancelled.set(true); if(revision==entryRevision) { checkingCredentials=false; entryRevision++; cancelEntryLookup=null; } } }); showSheet(sheet);
        };
        cancelEntryLookup=() -> {
            cancelled.set(true); completed[0]=true; ui.removeCallbacks(showWaiting);
            if(revision==entryRevision) { checkingCredentials=false; entryRevision++; cancelEntryLookup=null; }
            if(waiting[0]!=null) waiting[0].dialog.dismiss();
        };
        ui.postDelayed(showWaiting,500);
        work(() -> owner.restoreLesson(id,() -> cancelled.get()||account!=accountEpoch),restored -> {
            ui.removeCallbacks(showWaiting);
            if(revision!=entryRevision||api!=owner||cancelled.get()) return;
            completed[0]=true; checkingCredentials=false; cancelEntryLookup=null; if(waiting[0]!=null) waiting[0].dialog.dismiss();
            if(restored) showLiveClass(entry,true); else showEntryDialog(entry);
        },e -> {
            ui.removeCallbacks(showWaiting);
            if(revision!=entryRevision||api!=owner||cancelled.get()) return;
            completed[0]=true; checkingCredentials=false; cancelEntryLookup=null; if(waiting[0]!=null) waiting[0].dialog.dismiss(); showEntryDialog(entry);
        });
    }

    private void showEntryDialog(CourseCatalog.Entry entry) {
        String id=entry.live.optString("lessonId"); RainApi owner=api;
        Sheet sheet=createSheet("未找到课堂凭证","是否签到并进入课堂？");
        LinearLayout summary=compactCard(); summary.addView(text(entry.course.optString("course_name","当前课堂"),17,INK,true)); JSONObject teacher=entry.course.optJSONObject("teacher"); if(teacher!=null&&!teacher.optString("name","").isEmpty()) { gap(summary,8); summary.addView(text(teacher.optString("name"),12,MUTED,false)); } sheet.panel.addView(summary); gap(sheet.panel,18);
        TextView errorLabel=text("",12,0xffa35445,false); sheet.panel.addView(errorLabel); gap(sheet.panel,6);
        Button confirm=button("签到并进入课堂",true,() -> {}); sheet.panel.addView(confirm); gap(sheet.panel,10); Button cancel=button("暂不进入",false,sheet.dialog::dismiss); sheet.panel.addView(cancel);
        confirm.setOnClickListener(v -> {
            if(api!=owner||owner==null||owner.isClosed()||joining) return;
            joining=true; confirm.setEnabled(false); confirm.setText("正在进入课堂…"); cancel.setEnabled(false); sheet.dialog.setCancelable(false); errorLabel.setText("");
            work(() -> { owner.checkin(id); return true; },ok -> {
                joining=false; sheet.dialog.dismiss(); showLiveClass(entry,true); toast(owner.storageWarning()!=null?owner.storageWarning():"入课并签到成功，位置签到可单独查询");
            },e -> { joining=false; confirm.setEnabled(true); confirm.setText("重试进入课堂"); cancel.setEnabled(true); sheet.dialog.setCancelable(true); errorLabel.setText(error(e)); });
        });
        showSheet(sheet);
    }
    private void showLiveClass(CourseCatalog.Entry entry,boolean fresh) {
        currentCourse=entry.course.optString("course_name","当前课堂");
        if(fresh) { cancelLocationFlow(); lessonEpoch++; fetchingPpt=false; presentation=null; page=0; bitmaps.evictAll(); connection="正在连接课堂…"; }
        tab=1; dynamicView=LIVE_READER; render(); if(fresh) connectClass();
    }
    private static final int FEED=0,DETAIL=1,LIVE_READER=2,HISTORY_READER=3;
    private ActivityFeed activityFeed;
    private int feedVisibleLimit=10;
    private int visibleActivityCount() { return activityFeed==null?0:Math.min(feedVisibleLimit,activityFeed.entries.size()); }
    private final java.util.Map<String,LessonReport> reports=new java.util.LinkedHashMap<>();
    private final java.util.Set<String> attendanceLoading=new java.util.HashSet<>();
    private ActivityFeed.Entry selectedActivity;
    private LessonReport selectedReport;
    private JSONObject historyPresentation,historySummary;
    private String activityError,historyError;
    private boolean loadingActivities,loadingReport,loadingHistory,loadingMore;
    private String waitingActivityReport;
    private String pageError;
    private ScrollView contentScroll;
    private boolean resetFeedScroll;
    private int dynamicView=FEED,activityRevision,historyRevision,historyPage,reportSerial;
    private final java.util.Map<String,Integer> reportTickets=new java.util.HashMap<>();
    private long activityUpdated;
    private JSONObject readerPresentation() { return dynamicView==HISTORY_READER?historyPresentation:presentation; }
    private int readerIndex() { return dynamicView==HISTORY_READER?historyPage:page; }
    private String time(long millis) { return millis>0?android.text.format.DateFormat.format("MM月dd日 HH:mm",millis).toString():"未提供"; }
    private void showFeed() {
        dynamicView=FEED; historyRevision++; loadingHistory=false; render();
        if(user!=null&&activityFeed==null&&!loadingActivities) loadActivities();
    }
    private void dynamicsPage() {
        if(dynamicView==LIVE_READER||dynamicView==HISTORY_READER) {
            backLink(dynamicView==HISTORY_READER?"返回课堂详情":"返回动态",() -> { if(dynamicView==HISTORY_READER) { dynamicView=DETAIL; render(); } else showFeed(); }); readerPage(); return;
        }
        if(dynamicView==DETAIL&&selectedActivity!=null) { activityDetailPage(); return; }
        heading("YOUR CLASSROOM JOURNAL","课堂动态","回顾最近的课堂，把上课与签到记录留在一起。");
        if(user==null) { emptyCard("登录后查看课堂动态","扫码连接账号，查看最近上课、签到与课件。","扫码登录",this::openLogin); return; }
        if(hasLesson()) {
            LinearLayout current=compactCard(),top=row(); TextView name=text(currentCourse.isEmpty()?"正在进行的课堂":currentCourse,16,INK,true); top.addView(name,new LinearLayout.LayoutParams(0,-2,1)); top.addView(pill("当前课堂",GREEN,PALE)); current.addView(top); gap(current,6); current.addView(text("入课已确认",12,MUTED,false)); TextView open=text("查看当前课件",12,GREEN,true); open.setGravity(Gravity.END|Gravity.CENTER_VERTICAL); open.setMinHeight(dp(44)); open.setOnClickListener(v -> { dynamicView=LIVE_READER; render(); }); current.addView(open,lp(-1,-2)); addCard(current);
        }
        LinearLayout section=row(); section.addView(text("最近课堂",18,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        feedRefresh=text("刷新动态",12,GREEN,true); feedRefresh.setGravity(Gravity.CENTER); feedRefresh.setPadding(dp(14),dp(12),0,dp(12)); feedRefresh.setMinHeight(dp(44)); feedRefresh.setOnClickListener(v -> loadActivities()); section.addView(feedRefresh); body.addView(section); gap(body,12);
        feedScroll=contentScroll; feedBody=body; feedAccount=accountEpoch; feedCards.clear();
        feedScroll.setFocusableInTouchMode(true); feedScroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS); feedScroll.requestFocus();
        feedWarning=column(); feedEmpty=column(); feedRows=column(); body.addView(feedWarning); body.addView(feedEmpty); body.addView(feedRows);
        feedPageError=text("",12,0xffa35445,false); body.addView(feedPageError); gap(body,14);
        feedMore=button("加载更多动态",false,this::loadMoreActivities); body.addView(feedMore); gap(body,18);
        feedEnd=text("",11,MUTED,false); body.addView(feedEnd); gap(body,14); feedUpdated=text("",10,MUTED,false); feedUpdated.setGravity(Gravity.CENTER); body.addView(feedUpdated); bindFeed();
    }
    private ScrollView feedScroll;
    private LinearLayout feedBody,feedRows,feedWarning,feedEmpty;
    private TextView feedRefresh,feedPageError,feedEnd,feedUpdated;
    private Button feedMore;
    private int feedAccount=-1;
    private final java.util.Map<String,FeedCard> feedCards=new java.util.LinkedHashMap<>();
    private String feedKey(ActivityFeed.Entry entry) { return entry.lessonId().isEmpty()?ActivityFeed.label(entry.raw,"id",entry.raw.toString()):"lesson:"+entry.lessonId(); }
    private void clearFeedViews() {
        feedScroll=null; feedBody=null; feedRows=null; feedWarning=null; feedEmpty=null; feedCards.clear(); feedAccount=-1;
    }
    private void setLabel(TextView view,String value) { if(!view.getText().toString().equals(value)) view.setText(value); }
    private final class FeedCard {
        ActivityFeed.Entry entry;
        final LinearLayout view;
        final TextView kind,timestamp,course,title,teacher,attendance,checkin;
        FeedCard(ActivityFeed.Entry entry) {
            this.entry=entry; view=compactCard(); LinearLayout top=row(); kind=pill("课堂记录",GREEN,PALE); top.addView(kind); timestamp=text("",11,MUTED,false); timestamp.setGravity(Gravity.END); top.addView(timestamp,new LinearLayout.LayoutParams(0,-2,1)); view.addView(top); gap(view,10);
            course=text("",16,INK,true); course.setMaxLines(2); course.setEllipsize(android.text.TextUtils.TruncateAt.END); view.addView(course); gap(view,5); title=text("",12,INK,false); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END); view.addView(title); gap(view,5); teacher=text("",11,MUTED,false); view.addView(teacher); gap(view,10);
            LinearLayout footer=row(),labels=column(); attendance=text("",12,MUTED,true); attendance.setSingleLine(true); attendance.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(attendance); gap(labels,3);
            checkin=text("",11,MUTED,false); checkin.setSingleLine(true); checkin.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(checkin);
            footer.addView(labels,new LinearLayout.LayoutParams(0,-2,1)); footer.addView(text("查看详情",12,GREEN,true)); view.addView(footer);
            view.setFocusable(true); view.setOnClickListener(v -> openActivity(this.entry)); bind(entry);
        }
        void bind(ActivityFeed.Entry entry) {
            this.entry=entry; LessonReport report=reports.get(entry.lessonId());
            setLabel(kind,entry.kind()); setLabel(timestamp,time(report!=null&&report.start()>0?report.start():entry.created())); setLabel(course,entry.course()); setLabel(title,entry.title()); setLabel(teacher,entry.teacher());
            setLabel(attendance,entry.lessonId().isEmpty()?"详情请在官方雨课堂查看":report==null?"正在查询签到信息…":report.attendanceLabel()); attendance.setTextColor(report!=null&&report.signed()?GREEN:MUTED);
            boolean signed=report!=null&&report.signed(); setLabel(checkin,signed?"签到于 "+time(report.checkinTime()):"");
            // Reserve the second line while metadata loads so earlier cards do not shift the viewport.
            checkin.setVisibility(signed?View.VISIBLE:View.INVISIBLE);
        }
    }
    private void bindFeed() {
        if(feedRows==null) return;
        setLabel(feedRefresh,loadingActivities?"刷新中…":"刷新动态");
        feedWarning.removeAllViews();
        if(activityError!=null) {
            LinearLayout warning=card(); warning.addView(text(activityFeed==null?"动态加载失败":"刷新未完成",15,INK,true)); gap(warning,8); warning.addView(text(activityError,12,MUTED,false)); gap(warning,16); warning.addView(button("重新刷新",false,this::loadActivities)); feedWarning.addView(warning,lp(-1,-2)); gap(feedWarning,16);
        }
        feedEmpty.removeAllViews();
        if(activityFeed==null||activityFeed.entries.isEmpty()) {
            if(activityFeed!=null||activityError==null) {
                LinearLayout empty=card(); empty.addView(text(activityFeed==null?(loadingActivities?"正在同步最近课堂":"查看你的课堂足迹"):"最近还没有课堂动态",17,INK,true)); gap(empty,10); empty.addView(text("刷新后可查看最新的课堂记录。",12,MUTED,false));
                if(!loadingActivities) { gap(empty,18); empty.addView(button("刷新动态",false,this::loadActivities)); }
                feedEmpty.addView(empty,lp(-1,-2)); gap(feedEmpty,16);
            }
        }
        java.util.Set<String> keys=new java.util.LinkedHashSet<>(); int position=0;
        if(activityFeed!=null) for(ActivityFeed.Entry entry:activityFeed.entries.subList(0,visibleActivityCount())) {
            String key=feedKey(entry); keys.add(key); FeedCard card=feedCards.get(key);
            if(card==null) { card=new FeedCard(entry); feedCards.put(key,card); } else card.bind(entry);
            if(feedRows.indexOfChild(card.view)!=position) {
                if(card.view.getParent()==feedRows) feedRows.removeView(card.view);
                LinearLayout.LayoutParams params=lp(-1,-2); params.bottomMargin=dp(12); feedRows.addView(card.view,position,params);
            }
            position++;
        }
        for(String key:new ArrayList<>(feedCards.keySet())) if(!keys.contains(key)) { feedRows.removeView(feedCards.remove(key).view); }
        boolean more=activityFeed!=null&&(visibleActivityCount()<activityFeed.entries.size()||activityFeed.hasMore()); feedMore.setVisibility(more?View.VISIBLE:View.GONE); feedMore.setEnabled(!loadingMore&&!loadingActivities); setLabel(feedMore,loadingMore?"正在加载…":pageError==null?"加载更多动态":"重试加载更多");
        feedPageError.setVisibility(pageError==null?View.GONE:View.VISIBLE); setLabel(feedPageError,pageError==null?"":"加载更多失败："+pageError);
        boolean ended=activityFeed!=null&&!activityFeed.entries.isEmpty()&&!more; feedEnd.setVisibility(ended?View.VISIBLE:View.GONE);
        if(ended) setLabel(feedEnd,activityFeed.endReached?"已加载全部动态":activityFeed.stalled?"分页位置未更新，请刷新后重试":"服务未提供后续分页位置");
        feedUpdated.setVisibility(activityUpdated>0?View.VISIBLE:View.GONE);
        if(activityUpdated>0) setLabel(feedUpdated,"已显示 "+visibleActivityCount()+" 条 · 更新于 "+android.text.format.DateFormat.format("HH:mm",activityUpdated));
    }
    private FeedStore activityStore;
    private final ExecutorService feedIo=Executors.newSingleThreadExecutor();
    private FeedStore.Session activitySession;
    private String activityScope="";
    private boolean activityCacheLoaded,activityCacheWarning;
    private FeedTimeline activityTimeline;
    private final java.util.Map<String,Long> reportAt=new java.util.HashMap<>(),deckAt=new java.util.HashMap<>();
    private final java.util.Map<String,JSONObject> historyDecks=new java.util.HashMap<>();
    private final Runnable saveActivityCache=this::flushActivityCache;
    private void bindActivityCache(RainApi owner) {
        String scope=owner.host()+"|"+user.optString("user_id");
        if(!scope.equals(activityScope)) { if(!activityScope.isEmpty()) { activityFeed=null; activityTimeline=null; reports.clear(); reportAt.clear(); historyDecks.clear(); deckAt.clear(); clearFeedViews(); } activityScope=scope; activitySession=activityStore.session(owner.host(),user.optString("user_id")); activityCacheLoaded=false; activityCacheWarning=false; }
    }
    private void persistActivities() { ui.removeCallbacks(saveActivityCache); ui.postDelayed(saveActivityCache,250); }
    private void flushActivityCache() {
        ui.removeCallbacks(saveActivityCache); if(user==null||activityFeed==null||activitySession==null||feedIo.isShutdown()) return;
        ActivitySnapshot snapshot=new ActivitySnapshot(activityTimeline==null?new FeedTimeline(activityFeed):activityTimeline,reports,reportAt,historyDecks,deckAt); FeedStore.Session session=activitySession; int account=accountEpoch;
        feedIo.execute(() -> { try { session.write(snapshot.encode()); } catch(Exception e) { ui.post(() -> { if(!isDestroyed()&&account==accountEpoch&&!activityCacheWarning) { activityCacheWarning=true; toast("动态缓存暂无法保存，本次仍可正常查看"); } }); } });
    }
    private void acceptReport(String id,LessonReport report) {
        LessonReport previous=reports.get(id);
        if(previous!=null&&previous.detail!=null&&report.detail!=null) try { if(!FeedStore.canonical(previous.detail).equals(FeedStore.canonical(report.detail))) invalidateDecks(id); } catch(Exception ignored) {}
        reports.put(id,report); if(report.detailError==null&&report.detail!=null) reportAt.put(id,System.currentTimeMillis()); persistActivities();
    }
    private boolean reportFresh(String id) { return ActivitySnapshot.fresh(reports.get(id),reportAt.getOrDefault(id,0L),System.currentTimeMillis()); }
    private void fetchNeededReports() {
        if(activityFeed==null) return; long now=System.currentTimeMillis();
        for(ActivityFeed.Entry entry:activityFeed.entries.subList(0,visibleActivityCount())) {
            String id=entry.lessonId(); if(id.isEmpty()||reportFresh(id)) continue;
            LessonReport record=reports.get(id); long age=now-reportAt.getOrDefault(id,0L);
            if(record!=null&&record.detail!=null&&record.detailError==null&&age>=0&&age<ActivitySnapshot.ACTIVE_TTL) continue;
            loadAttendance(id);
        }
    }
    private void compareActivityHead(FeedTimeline previous,org.json.JSONArray rows) {
        if(previous==null) return;
        java.util.Map<String,ActivityFeed.Entry> old=new java.util.HashMap<>(); for(ActivityFeed.Entry e:previous.visible.entries) old.put(ActivityFeed.key(e),e); for(ActivityFeed tail:previous.deferred) for(ActivityFeed.Entry e:tail.entries) old.putIfAbsent(ActivityFeed.key(e),e);
        for(int i=0;i<rows.length();i++) {
            JSONObject raw=rows.optJSONObject(i); if(raw==null) continue; ActivityFeed.Entry fresh=new ActivityFeed.Entry(raw),before=old.get(ActivityFeed.key(fresh));
            if(before!=null&&!fresh.lessonId().isEmpty()) try { if(!FeedStore.canonical(before.raw).equals(FeedStore.canonical(raw))) invalidateActivityReport(fresh.lessonId()); } catch(Exception ignored) {}
        }
    }
    private void invalidateActivityReport(String id) {
        reports.remove(id); reportAt.remove(id); reportTickets.remove(id); attendanceLoading.remove(id); invalidateDecks(id);
        if(selectedActivity!=null&&id.equals(selectedActivity.lessonId())) { loadingReport=false; waitingActivityReport=null; }
    }
    private void invalidateDecks(String id) { for(String key:new ArrayList<>(historyDecks.keySet())) if(key.startsWith(id+"|")) { historyDecks.remove(key); deckAt.remove(key); }
    }
    private void pruneActivityCache() {
        if(activityTimeline==null) return; java.util.Set<String> lessons=new java.util.HashSet<>();
        for(ActivityFeed.Entry entry:activityTimeline.visible.entries) if(!entry.lessonId().isEmpty()) lessons.add(entry.lessonId());
        for(ActivityFeed segment:activityTimeline.deferred) for(ActivityFeed.Entry entry:segment.entries) if(!entry.lessonId().isEmpty()) lessons.add(entry.lessonId());
        for(String id:new ArrayList<>(reports.keySet())) if(!lessons.contains(id)) invalidateActivityReport(id);
        for(String key:new ArrayList<>(historyDecks.keySet())) { int split=key.indexOf('|'); if(split<0||!lessons.contains(key.substring(0,split))) { historyDecks.remove(key); deckAt.remove(key); } }
    }
    private void fetchActivityHead(RainApi owner,int revision) {
        work(owner::activities,rows -> {
            if(revision!=activityRevision||owner!=api) return;
            FeedTimeline previous=activityTimeline; if(previous==null&&activityFeed!=null) previous=new FeedTimeline(activityFeed);
            compareActivityHead(previous,rows); activityTimeline=previous==null?new FeedTimeline(ActivityFeed.from(rows)):previous.refresh(rows); activityFeed=activityTimeline.visible;
            loadingActivities=false; activityUpdated=System.currentTimeMillis();
            if(selectedActivity!=null) for(ActivityFeed.Entry entry:activityFeed.entries) if(ActivityFeed.key(entry).equals(ActivityFeed.key(selectedActivity))) { selectedActivity=entry; break; }
            pruneActivityCache(); persistActivities(); if(tab==1&&dynamicView==FEED) render(); fetchNeededReports();
        },e -> { if(revision!=activityRevision||owner!=api) return; loadingActivities=false; activityError=error(e); if(tab==1&&dynamicView==FEED) render(); else toast("动态刷新失败："+activityError); });
    }
    private void loadActivities() {
        if(user==null||api==null||api.isClosed()) { openLogin(); return; } if(loadingActivities) return;
        int revision=++activityRevision; feedVisibleLimit=10; loadingMore=false; pageError=null; resetFeedScroll=true;
        loadingActivities=true; activityError=null; RainApi owner=api; bindActivityCache(owner); if(tab==1&&dynamicView==FEED) render();
        if(activityCacheLoaded) { fetchActivityHead(owner,revision); return; }
        FeedStore.Session session=activitySession;
        work(() -> { JSONObject data=session.read(); return data==null?null:ActivitySnapshot.decode(data); },snapshot -> {
            if(revision!=activityRevision||owner!=api) return; activityCacheLoaded=true;
            if(snapshot!=null) {
                if(activityFeed==null) { activityTimeline=snapshot.timeline; activityFeed=activityTimeline.visible; }
                for(String id:snapshot.reports.keySet()) if(!reports.containsKey(id)) { reports.put(id,snapshot.reports.get(id)); reportAt.put(id,snapshot.reportAt.getOrDefault(id,0L)); }
                for(String key:snapshot.decks.keySet()) if(!historyDecks.containsKey(key)) { historyDecks.put(key,snapshot.decks.get(key)); deckAt.put(key,snapshot.deckAt.getOrDefault(key,0L)); }
                if(tab==1&&dynamicView==FEED) render();
            }
            fetchActivityHead(owner,revision);
        },e -> { if(revision!=activityRevision||owner!=api) return; activityCacheLoaded=true; fetchActivityHead(owner,revision); });
    }
    private void loadMoreActivities() {
        if(user==null||api==null||api.isClosed()) { openLogin(); return; }
        if(loadingMore||loadingActivities||activityFeed==null) return;
        if(visibleActivityCount()<activityFeed.entries.size()) { feedVisibleLimit=Integer.MAX_VALUE; pageError=null; render(); fetchNeededReports(); return; }
        if(!activityFeed.hasMore()) return;
        feedVisibleLimit=Integer.MAX_VALUE;
        ActivityFeed previous=activityFeed; FeedTimeline timeline=activityTimeline==null?new FeedTimeline(previous):activityTimeline; int revision=activityRevision; RainApi owner=api;
        loadingMore=true; pageError=null; if(tab==1&&dynamicView==FEED) render();
        work(() -> timeline.append(owner.activities(previous.nextCursor)),next -> {
            if(revision!=activityRevision||activityFeed!=previous) return;
            loadingMore=false; activityTimeline=next; activityFeed=next.visible; persistActivities(); if(tab==1&&dynamicView==FEED) render(); fetchNeededReports();
        },e -> { if(revision!=activityRevision||activityFeed!=previous) return; loadingMore=false; pageError=error(e); if(tab==1&&dynamicView==FEED) render(); });
    }
    private void loadAttendance(String id) {
        if(attendanceLoading.contains(id)||(loadingReport&&selectedActivity!=null&&id.equals(selectedActivity.lessonId()))) return;
        attendanceLoading.add(id); int ticket=++reportSerial; reportTickets.put(id,ticket); RainApi owner=api; LessonReport cached=reports.get(id); long age=System.currentTimeMillis()-reportAt.getOrDefault(id,0L);
        work(() -> {
            JSONObject detail=cached!=null&&cached.detail!=null&&cached.detailError==null&&age>=0&&age<ActivitySnapshot.ACTIVE_TTL?cached.detail:owner.lessonDetail(id),info=cached==null?null:cached.info; String infoError=cached==null?null:cached.infoError;
            LessonReport candidate=new LessonReport(id,info,detail,infoError,null);
            if(info==null||!candidate.ended()) try { info=owner.lessonInfo(id); infoError=null; } catch(Exception e) { infoError=error(e); }
            return new LessonReport(id,info,detail,infoError,null);
        },report -> {
            if(api!=owner||!Integer.valueOf(ticket).equals(reportTickets.get(id))) return; reportTickets.remove(id); attendanceLoading.remove(id); acceptReport(id,report);
            updateSelectedActivityReport(id);
        },e -> { if(api!=owner||!Integer.valueOf(ticket).equals(reportTickets.get(id))) return; reportTickets.remove(id); attendanceLoading.remove(id); LessonReport old=reports.get(id); reports.put(id,new LessonReport(id,old==null?null:old.info,old==null?null:old.detail,old==null?null:old.infoError,error(e))); updateSelectedActivityReport(id); });
    }
    private void updateSelectedActivityReport(String id) {
        if(selectedActivity!=null&&id.equals(selectedActivity.lessonId())) { selectedReport=reports.get(id); if(id.equals(waitingActivityReport)) { waitingActivityReport=null; loadingReport=false; } if(tab==1&&dynamicView==DETAIL) render(); }
        if(tab==1&&dynamicView==FEED) render();
    }
    private void openActivity(ActivityFeed.Entry entry) {
        if(user==null||api==null||api.isClosed()) { openLogin(); return; }
        if(entry.lessonId().isEmpty()) { toast("当前只支持课堂记录的课程信息和课件"); return; }
        selectedActivity=entry; selectedReport=reports.get(entry.lessonId()); historyPresentation=null; historySummary=null; historyError=null; historyRevision++; loadingHistory=false; dynamicView=DETAIL; loadActivityReport(false);
    }
    private void loadActivityReport(boolean refresh) {
        if(selectedActivity==null) return; final String id=selectedActivity.lessonId();
        if(attendanceLoading.contains(id)) { waitingActivityReport=id; loadingReport=true; if(tab==1&&dynamicView==DETAIL) render(); return; }
        waitingActivityReport=null; final RainApi owner=api;
        LessonReport cached=selectedReport; long fetchedAt=reportAt.getOrDefault(id,0L); if(!refresh&&reportFresh(id)) { loadingReport=false; if(tab==1&&dynamicView==DETAIL) render(); return; } loadingReport=true; int ticket=++reportSerial; reportTickets.put(id,ticket); attendanceLoading.add(id); if(tab==1&&dynamicView==DETAIL) render();
        work(() -> {
            JSONObject info=cached==null?null:cached.info,detail=cached==null?null:cached.detail; String infoError=null,detailError=null;
            try { info=owner.lessonInfo(id); } catch(Exception e) { infoError=error(e); }
            if(!refresh&&cached!=null&&cached.detail!=null&&cached.detailError==null&&System.currentTimeMillis()>=fetchedAt&&System.currentTimeMillis()-fetchedAt<ActivitySnapshot.ACTIVE_TTL) detail=cached.detail;
            else try { detail=owner.lessonDetail(id); } catch(Exception e) { detailError=error(e); }
            return new LessonReport(id,info,detail,infoError,detailError);
        },report -> {
            if(api!=owner||!Integer.valueOf(ticket).equals(reportTickets.get(id))) return; reportTickets.remove(id); attendanceLoading.remove(id); acceptReport(id,report);
            if(selectedActivity!=null&&id.equals(selectedActivity.lessonId())) { loadingReport=false; selectedReport=report; }
            updateSelectedActivityReport(id);
        },e -> { if(api!=owner||!Integer.valueOf(ticket).equals(reportTickets.get(id))) return; reportTickets.remove(id); attendanceLoading.remove(id);
            if(selectedActivity!=null&&id.equals(selectedActivity.lessonId())) { loadingReport=false; waitingActivityReport=null; if(tab==1&&dynamicView==DETAIL) { toast(error(e)); render(); } } });
    }
    private void activityDetailPage() {
        backLink("返回动态",this::showFeed);
        ActivityFeed.Entry entry=selectedActivity; LessonReport report=selectedReport; JSONObject info=report==null?null:report.info;
        String course=ActivityFeed.label(info,"courseName",entry.course()),lesson=ActivityFeed.label(info,"lessonName",entry.title());
        body.addView(text(course,24,INK,true));
        if(!lesson.trim().isEmpty()&&!lesson.equals(course)) { gap(body,6); body.addView(text(lesson,13,MUTED,false)); }
        gap(body,16);
        section("课堂信息",loadingReport?"更新中…":"刷新记录",() -> { if(!loadingReport) loadActivityReport(true); });
        LinearLayout details=compactCard(); detailLine(details,"任课教师",ActivityFeed.label(info,"teacherName",entry.teacher())); detailLine(details,"上课时间",report!=null&&report.start()>0?time(report.start()):"尚未取得课堂时间"); detailLine(details,"下课时间",report!=null&&report.end()>0?time(report.end()):"未提供");
        if(report!=null&&report.infoError!=null) details.addView(text("课堂信息暂未更新："+report.infoError,11,MUTED,false)); addCard(details);
        attendanceCard(report);
        section("课堂课件",null,null); JSONArray decks=report==null?new JSONArray():report.presentations();
        if(decks.length()==0) emptyCard(loadingReport?"正在读取课件信息":"暂未取得可查看的课件",loadingReport?"正在获取这堂课的课堂报告。":"可刷新记录；部分课堂可能没有展示课件。",loadingReport?null:"刷新记录",() -> loadActivityReport(true));
        for(int i=0;i<decks.length();i++) {
            JSONObject summary=decks.optJSONObject(i); if(summary==null) continue; LinearLayout deck=card(); deck.addView(text(ActivityFeed.label(summary,"title","课堂课件"),17,INK,true)); gap(deck,10); deck.addView(text(summary.optInt("totalCount",0)+" 页",12,MUTED,false)); gap(deck,18);
            Button open=button(loadingHistory&&historySummary==summary?"正在加载课件…":"查看原版课件",true,() -> loadHistoryPresentation(summary)); open.setEnabled(!loadingHistory); deck.addView(open); addCard(deck);
        }
        if(historyError!=null) { body.addView(text("课件加载失败："+historyError,12,0xffa35445,false)); gap(body,16); }
    }
    private void attendanceCard(LessonReport report) {
        LinearLayout check=column(); check.setPadding(dp(16),dp(14),dp(16),dp(14)); check.setBackground(bg(WHITE,16,LINE));
        LinearLayout top=row(); top.addView(text("签到记录",14,INK,true),new LinearLayout.LayoutParams(0,-2,1));
        String status=report==null?"正在查询":report.attendanceLabel(); boolean signed=report!=null&&report.signed(); boolean absent=report!=null&&report.absent();
        top.addView(pill(status,signed?GREEN:absent?0xffa35b4a:MUTED,signed?PALE:absent?0xfffaf0eb:BG)); check.addView(top);
        if(signed) {
            gap(check,10); TextView stamp=text(time(report.checkinTime()),13,INK,true); check.addView(stamp);
            JSONObject attendance=report.attendance(); String method=ActivityFeed.label(attendance,"checkInName",""),channel=ActivityFeed.label(attendance,"checkInChannel","");
            String meta=method.isEmpty()?channel:channel.isEmpty()?method:method+" · "+channel;
            if(!meta.isEmpty()) { gap(check,6); check.addView(text(meta,11,MUTED,false)); }
        } else if(report!=null&&report.detailError!=null) { gap(check,8); check.addView(text(report.detailError,11,MUTED,false)); }
        addCard(check);
    }
    private void detailLine(LinearLayout parent,String label,String value) {
        LinearLayout row=row(); row.setGravity(Gravity.TOP); TextView key=text(label,12,MUTED,false); row.addView(key,lp(76,-2)); TextView v=text(value,13,INK,false); row.addView(v,new LinearLayout.LayoutParams(0,-2,1)); if(parent.getChildCount()>0) gap(parent,8); parent.addView(row);
    }
    private void loadHistoryPresentation(JSONObject summary) { loadHistoryPresentation(summary,false); }
    private void loadHistoryPresentation(JSONObject summary,boolean refresh) {
        if(selectedActivity==null||loadingHistory) return; RainApi owner=api; String id=selectedActivity.lessonId(),key=id+"|"+summary.optString("presentationId",""); int revision=++historyRevision; historySummary=summary; historyError=null;
        long age=System.currentTimeMillis()-deckAt.getOrDefault(key,0L); if(!refresh&&historyDecks.containsKey(key)&&age>=0&&age<ActivitySnapshot.DECK_TTL) { historyPresentation=historyDecks.get(key); historyPage=0; dynamicView=HISTORY_READER; render(); return; }
        loadingHistory=true;
        if(tab==1) render();
        work(() -> owner.originalPresentation(id,summary.optString("presentationId","")),p -> {
            if(revision!=historyRevision||selectedActivity==null||!id.equals(selectedActivity.lessonId())) return; loadingHistory=false; if(p.optString("title","").isEmpty()) { try { p.put("title",ActivityFeed.label(summary,"title","课堂课件")); } catch(Exception ignored) {} } historyPresentation=p; historyDecks.put(key,p); deckAt.put(key,System.currentTimeMillis()); persistActivities(); historyPage=0; dynamicView=HISTORY_READER; if(tab==1) render();
        },e -> { if(revision!=historyRevision) return; loadingHistory=false; historyError=error(e); if(tab==1) render(); });
    }
    private void readerPage() {
        boolean historical=dynamicView==HISTORY_READER; JSONObject shown=readerPresentation();
        body.addView(text("课堂课件",24,INK,true)); String course=historical&&selectedActivity!=null?selectedActivity.course():hasLesson()?currentCourse:"";
        if(!course.trim().isEmpty()) { gap(body,6); body.addView(text(course,13,MUTED,false)); } gap(body,16);
        if(!historical&&!hasLesson()) { emptyCard("下一页知识，等你开启","先选择正在上课的课程，确认签到入课。","查看我的课堂",() -> { tab=0; render(); }); return; }
        if(!historical) { LinearLayout connect=row(); connectionLabel=text(connection,11,MUTED,false); connect.addView(connectionLabel,new LinearLayout.LayoutParams(0,-2,1)); TextView reconnect=text("重新连接",12,GREEN,true); reconnect.setPadding(dp(12),dp(12),0,dp(12)); reconnect.setMinHeight(dp(44)); reconnect.setOnClickListener(v -> connectClass()); connect.addView(reconnect); body.addView(connect); gap(body,8); }
        LinearLayout reader=card();
        reader.addView(text(shown==null?"等待老师展示课件":shown.optString("title","未命名课件"),18,INK,true)); gap(reader,16);
        LinearLayout opts=row(); follow=null; if(!historical) { follow=new CheckBox(this); follow.setText("跟随教师翻页"); follow.setTextSize(12); follow.setTextColor(MUTED); follow.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN)); follow.setChecked(prefs.getBoolean("follow",true)); follow.setPadding(0,0,0,0); follow.setMinHeight(dp(44)); follow.setOnCheckedChangeListener((b,value) -> { prefs.edit().putBoolean("follow",value).apply(); if(value&&api!=null) showPage(api.livePage()-1,false); }); opts.addView(follow,new LinearLayout.LayoutParams(0,dp(44),1)); } else { TextView count=text(shown==null?"原版课件":shown.optJSONArray("slides").length()+" 页 · 原版课件",11,MUTED,false); opts.addView(count,new LinearLayout.LayoutParams(0,-2,1)); }
        TextView refresh=text("刷新课件",12,GREEN,true); refresh.setGravity(Gravity.CENTER); refresh.setMinHeight(dp(44)); refresh.setPadding(dp(12),0,0,0); refresh.setOnClickListener(v -> { if(historical) loadHistoryPresentation(historySummary,true); else fetchPresentation(true); }); opts.addView(refresh); reader.addView(opts); gap(reader,8);
        FrameLayout imageFrame=new FrameLayout(this); imageFrame.setBackground(bg(PALE,12,0)); slideImage=new ImageView(this); slideImage.setScaleType(ImageView.ScaleType.FIT_CENTER); slideImage.setContentDescription("课件页面，点击全屏查看"); imageFrame.addView(slideImage,new FrameLayout.LayoutParams(-1,-1));
        int available=Math.min(getResources().getDisplayMetrics().widthPixels,dp(720))-dp(80); reader.addView(imageFrame,lp(-1,Math.max(130,Math.round(available/getResources().getDisplayMetrics().density*9/16)))); slideImage.setOnClickListener(v -> fullscreen()); slideImage.setOnLongClickListener(v -> { imageOptions(); return true; });
        if(shown==null) { TextView waiting=text("正在等待课件\n请确认老师已开始展示",12,MUTED,false); waiting.setGravity(Gravity.CENTER); imageFrame.addView(waiting,new FrameLayout.LayoutParams(-1,-1)); }
        gap(reader,16); LinearLayout controls=row(); Button prev=button("上一页",false,() -> showPage(readerIndex()-1,true)),next=button("下一页",false,() -> showPage(readerIndex()+1,true)); controls.addView(prev,lp(78,42)); slideNumber=text("0 / 0",13,MUTED,true); slideNumber.setGravity(Gravity.CENTER); controls.addView(slideNumber,new LinearLayout.LayoutParams(0,dp(42),1)); controls.addView(next,lp(78,42)); reader.addView(controls); gap(reader,16);
        HorizontalScrollView thumbs=new HorizontalScrollView(this); thumbs.setHorizontalScrollBarEnabled(false); thumbStrip=row(); thumbs.addView(thumbStrip); reader.addView(thumbs,lp(-1,42)); gap(reader,18);
        gap(reader,16); reader.addView(button("下载课件",true,this::downloadOptions)); addCard(reader);
        if(shown!=null) showPage(readerIndex(),false);
        if(historical) { if(historyError!=null) body.addView(text(historyError,12,0xffa35445,false)); return; }
        LinearLayout check=card(); check.addView(text("课堂签到",17,INK,true)); gap(check,10); check.addView(text("入课已确认 · 位置签到状态需单独查询",12,MUTED,false)); gap(check,18); check.addView(button("查看位置签到状态",false,this::checkLocation)); addCard(check);
    }
    private void connectClass() {
        if(api==null||api.lesson()==null) return; RainApi a=api; int epoch=accountEpoch,lesson=lessonEpoch;
        try { a.connect(e -> ui.post(() -> {
            if(isDestroyed()||epoch!=accountEpoch||api!=a||lesson!=lessonEpoch||joining) return;
            String op=e.optString("op");
            if(op.equals("connection")) { String state=e.optString("state"); connection=state.equals("credentials-expired")?"课堂凭证已失效，请返回首页重新进入":state.equals("connected")?"●  课堂已连接":state.equals("closed")?"连接已断开，点击重新连接":"连接失败，点击重新连接"; }
            else connection=e.optBoolean("finished")?"本次课堂已结束 · 课件仍可阅读":"●  课堂已连接 · 实时更新中";
            if(connectionLabel!=null&&tab==1&&dynamicView==LIVE_READER) connectionLabel.setText(connection);
            String id=e.optString("presentationId");
            if(!id.isEmpty()&&(presentation==null||!id.equals(presentation.optString("id"))||op.equals("showpresentation"))) fetchPresentation(false);
            else if(presentation!=null&&e.has("slide")&&prefs.getBoolean("follow",true)&&tab==1&&dynamicView==LIVE_READER) showPage(e.optInt("slide",1)-1,false);
            if(op.equals("locationcheckinext")) { cancelLocationFlow(); toast("老师已开启位置签到，可在课堂签到卡片确认签到"); }
            if(op.equals("locationcheckinend")||e.optBoolean("finished")) { cancelLocationFlow(); if(op.equals("locationcheckinend")) toast("本轮位置签到已结束"); }
        })); } catch(Exception e) { toast(error(e)); }
    }
    private void fetchPresentation(boolean notify) {
        if(!hasLesson()) { toast("请先签到进入课堂"); return; }
        if(fetchingPpt||joining) return; RainApi a=api; String lesson=a.lesson(); final int revision=lessonEpoch; fetchingPpt=true;
        work(a::fetchPresentation,p -> {
            if(revision!=lessonEpoch) return; fetchingPpt=false; if(!lesson.equals(a.lesson())) return;
            if(!p.optString("id").equals(a.presentation())) { fetchPresentation(false); return; }
            boolean changed=presentation==null||!presentation.optString("id").equals(p.optString("id")); presentation=p; bitmaps.evictAll();
            if(changed&&prefs.getBoolean("follow",true)) page=a.livePage()-1;
            if(tab==1&&dynamicView==LIVE_READER) render(); if(notify) toast("课件已刷新");
        },e -> { if(revision!=lessonEpoch) return; fetchingPpt=false; if(notify) toast(error(e)); else { connection=error(e); if(connectionLabel!=null&&tab==1&&dynamicView==LIVE_READER) connectionLabel.setText(connection); } });
    }
    private void showPage(int target,boolean manual) {
        JSONObject displayed=readerPresentation(); JSONArray slides=displayed==null?null:displayed.optJSONArray("slides"); if(slides==null||slides.length()==0) return;
        int index=Math.max(0,Math.min(target,slides.length()-1)); if(dynamicView==HISTORY_READER) historyPage=index; else page=index; if(manual&&dynamicView!=HISTORY_READER) { prefs.edit().putBoolean("follow",false).apply(); if(follow!=null) follow.setChecked(false); }
        if(slideNumber!=null) slideNumber.setText(getString(R.string.page_number,index+1,slides.length()));
        if(thumbStrip!=null) { thumbStrip.removeAllViews(); for(int i=0;i<slides.length();i++) { final int destination=i; TextView t=text(String.valueOf(i+1),12,i==index?WHITE:GREEN,true); t.setGravity(Gravity.CENTER); t.setBackground(bg(i==index?GREEN:PALE,9,0)); LinearLayout.LayoutParams p=lp(40,38); p.setMargins(0,0,dp(8),0); thumbStrip.addView(t,p); t.setOnClickListener(v -> showPage(destination,true)); } }
        int epoch=++imageEpoch,account=accountEpoch; JSONObject p=displayed,s=slides.optJSONObject(index); String key=p.optString("id")+":"+p.optString("version")+":"+index;
        Bitmap cached=bitmaps.get(key); if(cached!=null) { displayBitmap(cached); return; } if(slideImage!=null) slideImage.setImageDrawable(null); if(fullImage!=null) fullImage.setImageDrawable(null);
        RainApi a=api;
        workers.execute(() -> { try { Bitmap bmp=SlideExporter.decode(a.slideImage(s),2048); ui.post(() -> { if(isDestroyed()||account!=accountEpoch) return; bitmaps.put(key,bmp); if(epoch==imageEpoch) displayBitmap(bmp); }); } catch(Exception e) { ui.post(() -> { if(!isDestroyed()&&epoch==imageEpoch) toast("本页图片加载失败："+error(e)); }); } });
    }
    private void displayBitmap(Bitmap bmp) { if(slideImage!=null&&tab==1) slideImage.setImageBitmap(bmp); if(fullImage!=null&&fullDialog!=null&&fullDialog.isShowing()) fullImage.setImageBitmap(bmp); }
    private void fullscreen() {
        if(readerPresentation()==null||(fullDialog!=null&&fullDialog.isShowing())) return;
        fullDialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar_Fullscreen); LinearLayout screen=column(); screen.setBackgroundColor(0xff192a21); fullImage=new ZoomImageView(this); fullImage.setOnPageSwipeListener(this::swipePage); fullImage.setOnLongClickListener(v -> { imageOptions(); return true; }); if(slideImage!=null) fullImage.setImageDrawable(slideImage.getDrawable()); screen.addView(fullImage,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=row(); controls.setPadding(dp(16),dp(12),dp(16),dp(12)); Button previous=button("上一页",false,() -> showPage(readerIndex()-1,true)),close=button("关闭全屏",false,() -> fullDialog.dismiss()),next=button("下一页",false,() -> showPage(readerIndex()+1,true));
        for(Button b:new Button[]{previous,close,next}) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(46),1); p.setMargins(dp(4),0,dp(4),0); controls.addView(b,p); } screen.addView(controls); fullDialog.setContentView(screen); final Dialog viewer=fullDialog; final ZoomImageView image=fullImage; viewer.setOnDismissListener(d -> { image.setImageDrawable(null); if(fullDialog==viewer) { fullImage=null; fullDialog=null; } }); viewer.show();
    }
    private void swipePage(int direction) {
        JSONObject shown=readerPresentation(); JSONArray slides=shown==null?null:shown.optJSONArray("slides"); int next=readerIndex()+direction;
        if(slides!=null&&next>=0&&next<slides.length()) showPage(next,true);
    }
    private String accountInitial() { String name=user==null?"雨":user.optString("name","同学"); return name.isEmpty()?"同":name.substring(0,1); }
    private void accountPage() {
        heading("YOUR OWN LEARNING SPACE","学习，自有节奏","账号与课堂信息保存在本机，你的手机直接连接雨课堂。");
        LinearLayout profile=card(); LinearLayout top=row(); TextView avatar=text(user==null?"雨":accountInitial(),22,GREEN,true); avatar.setGravity(Gravity.CENTER); avatar.setBackground(bg(PALE,18,0)); top.addView(avatar,lp(56,56)); LinearLayout info=column(); info.setPadding(dp(16),0,0,0); info.addView(text(user==null?"尚未登录":user.optString("name","同学"),20,INK,true)); gap(info,8); info.addView(text(user==null?"连接你的雨课堂账号":user.optString("school","雨课堂账号"),12,MUTED,false)); top.addView(info,new LinearLayout.LayoutParams(0,-2,1)); profile.addView(top); gap(profile,24); profile.addView(button(user==null?"登录账号":"退出当前账号",user==null,user==null?this::openLogin:this::logout)); addCard(profile);
        section("连接方式",null,null); LinearLayout local=card(); local.addView(text("手机直连官方服务",17,INK,true)); gap(local,10); local.addView(text("HTTP 与课堂 WebSocket 均从手机发出。无需配置服务器，不启动本地 HTTP 服务，不经过开发者中继。",13,MUTED,false)); gap(local,16); local.addView(pill(RainApi.SERVICES[prefs.getInt("service",0)],GREEN,PALE)); addCard(local);
        section("请求设置",null,null); LinearLayout ua=compactCard(),uaTop=row(); uaTop.addView(text("User-Agent",15,INK,true),new LinearLayout.LayoutParams(0,-2,1)); uaTop.addView(pill(UserAgent.isCustom(prefs)?"自定义":"设备默认",GREEN,PALE)); ua.addView(uaTop); gap(ua,10); TextView preview=text(UserAgent.current(prefs),11,MUTED,false); preview.setMaxLines(3); preview.setEllipsize(android.text.TextUtils.TruncateAt.END); ua.addView(preview); gap(ua,14); ua.addView(button("修改 User-Agent",false,this::userAgentSettings)); addCard(ua);
        section("关于应用",null,null); LinearLayout about=compactCard(); about.addView(text("作者 FirmPluvia",14,INK,true)); gap(about,12); about.addView(button("项目主页",false,() -> openWeb(PROJECT_URL))); addCard(about);
        section("诊断日志",null,null); LinearLayout log=compactCard(); log.addView(text("记录登录阶段、耗时与错误码，不记录密码、验证码、token 或 Cookie。",12,MUTED,false)); gap(log,12); log.addView(button("导出今日日志",false,this::exportDiagnostics)); addCard(log);
        section("使用小提示",null,null); LinearLayout guide=card(); guideRow(guide,"01","连接账号","选择雨课堂版本，支持二维码、手机号 / 邮箱密码，以及短信验证码登录。"); guideRow(guide,"02","确认签到","点击课程优先使用本地凭证；未找到时确认签到入课，位置签到单独查询。"); guideRow(guide,"03","保存课件","支持本机生成图片版 PPTX 和图片 ZIP。"); addCard(guide);
        gap(body,8); TextView foot=text("雨课堂助手 "+BuildConfig.VERSION_NAME,10,MUTED,false); foot.setGravity(Gravity.CENTER); body.addView(foot);
    }
    private void userAgentSettings() {
        Sheet sheet=createSheet("自定义 User-Agent","设备默认值会读取 Android 版本、手机型号和 Build 编号。保存后对后续请求生效。");
        android.widget.EditText input=new android.widget.EditText(this); input.setText(UserAgent.current(prefs)); input.setTextSize(12); input.setTextColor(INK); input.setPadding(dp(14),dp(12),dp(14),dp(12)); input.setBackground(bg(WHITE,12,LINE)); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); input.setMinLines(3); input.setMaxLines(6); input.setGravity(Gravity.TOP); input.setContentDescription("User-Agent 内容"); sheet.panel.addView(input,lp(-1,-2)); gap(sheet.panel,10);
        TextView errorLabel=text("",12,0xffa35445,false); sheet.panel.addView(errorLabel); gap(sheet.panel,12);
        sheet.panel.addView(button("保存 User-Agent",true,() -> {
            String value; try { value=UserAgent.validate(input.getText().toString()); } catch(IllegalArgumentException e) { errorLabel.setText(e.getMessage()); return; }
            prefs.edit().putString(UserAgent.KEY,value).apply(); if(api!=null&&!api.isClosed()) api.setUserAgent(value); sheet.dialog.dismiss(); if(tab==2) render(); toast("User-Agent 已保存");
        })); gap(sheet.panel,10); sheet.panel.addView(button("恢复设备默认",false,() -> { prefs.edit().remove(UserAgent.KEY).apply(); if(api!=null&&!api.isClosed()) api.setUserAgent(UserAgent.deviceDefault()); sheet.dialog.dismiss(); if(tab==2) render(); toast("已恢复设备默认 User-Agent"); })); gap(sheet.panel,10); sheet.panel.addView(button("取消",false,sheet.dialog::dismiss)); showSheet(sheet); if(sheet.dialog.getWindow()!=null) sheet.dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void guideRow(LinearLayout parent,String number,String title,String description) { LinearLayout r=row(); r.setGravity(Gravity.TOP); r.addView(pill(number,GREEN,PALE)); LinearLayout details=column(); details.setPadding(dp(14),dp(2),0,0); details.addView(text(title,14,INK,true)); gap(details,8); details.addView(text(description,12,MUTED,false)); r.addView(details,new LinearLayout.LayoutParams(0,-2,1)); parent.addView(r); gap(parent,20); }
    private void logout() {
        if(user==null) return;
        Sheet sheet=createSheet("退出当前账号？","退出后需重新扫码登录，已保存的课件会保留。");
        LinearLayout account=card(); account.addView(text(user.optString("name","当前账号"),18,INK,true)); gap(account,8); account.addView(text(RainApi.SERVICES[prefs.getInt("service",0)],12,MUTED,false)); sheet.panel.addView(account); gap(sheet.panel,22);
        Button confirm=button("退出登录",true,() -> { sheet.dialog.dismiss(); resetAccount(); }); confirm.setBackground(bg(0xff9b5048,12,0)); sheet.panel.addView(confirm); gap(sheet.panel,10); sheet.panel.addView(button("继续使用",false,sheet.dialog::dismiss)); showSheet(sheet);
    }
    private void retire(RainApi client) {
        if(client==null) return; client.cancel();
        if(!workers.isShutdown()) workers.execute(client::close);
    }
    private void resetAccount() {
        if(cancelEntryLookup!=null) cancelEntryLookup.run();
        // Invalidate callbacks and persisted authentication before waiting for any background cleanup.
        accountEpoch++; loginEpoch++; lessonEpoch++; entryRevision++; checkingCredentials=false; busyCount=0;
        RainApi previous=api; api=null; vault.clear(); lessonCache.revoke(); workers.execute(lessonCache::purgeRevoked); activityStore.revoke(); feedIo.execute(activityStore::purgeRevoked); ui.removeCallbacks(saveActivityCache); activitySession=null; activityScope=""; activityCacheLoaded=false; activityTimeline=null; waitingActivityReport=null; reportAt.clear(); deckAt.clear(); historyDecks.clear(); clearFeedViews();
        user=null; presentation=null; catalog=null; activityFeed=null; feedVisibleLimit=10; selectedActivity=null; selectedReport=null; historyPresentation=null; historySummary=null; reports.clear(); attendanceLoading.clear(); activityRevision++; reportTickets.clear(); historyRevision++; activityUpdated=0; activityError=null; historyError=null; dynamicView=FEED; loadingActivities=false; loadingReport=false; loadingHistory=false; loadingMore=false; pageError=null; activeDownload=null; courseError=null; courseUpdated=0; currentCourse=""; connection="等待课堂连接";
        fetchingPpt=false; joining=false; loadingCourses=false; locationSubmitting=false; downloading=false; onlyLive=false; page=0; tab=0;
        if(imageJob!=null) imageJob.cancel(); imageJob=null; pendingImage=null;
        pendingFormat=null; exportPresentation=null; pendingExportApi=null; bitmaps.evictAll();
        cancelLocationFlow();
        for(Dialog dialog:new ArrayList<>(dialogs)) if(dialog.isShowing()) dialog.dismiss(); dialogs.clear();
        if(fullDialog!=null) fullDialog.dismiss(); fullImage=null; slideImage=null; follow=null; thumbStrip=null; connectionLabel=null;
        retire(previous);
        try { api=configureApi(new RainApi(prefs.getInt("service",0),vault.session())); } catch(Exception e) { toast("已退出，请重新打开登录窗口"); }
        render(); showLoading(); toast("已退出登录"); showPendingUpdate();
    }
    private boolean startupSessionRestored;
    private void restoreStartupSession() {
        if(startupSessionRestored||isFinishing()) return;
        startupSessionRestored=true;
        if(api!=null&&api.hasCookies()&&user==null) { RainApi a=api; work(a::profile,p -> { user=p; render(); loadCourses(); if(tab==1&&dynamicView==FEED) loadActivities(); if(a.storageWarning()!=null) toast(a.storageWarning()); },e -> toast("会话恢复失败："+error(e))); }
    }
    private void openWeb(String url) {
        if(url==null||url.isEmpty()) { toast("链接暂未配置"); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); } catch(Exception e) { toast("无法打开链接，请安装浏览器后重试"); }
    }

    private UpdateChecker updateChecker;
    private UpdateInfo pendingUpdate;
    private Dialog updateDialog;
    private boolean checkingUpdate,exitingForUpdate;
    private final ExecutorService updateIo=Executors.newSingleThreadExecutor();
    private final java.util.Set<Integer> dismissedUpdates=new java.util.HashSet<>();
    protected UpdateChecker createUpdateChecker() { return new UpdateChecker(); }
    protected void checkForUpdates(boolean manual) {
        if(checkingUpdate||exitingForUpdate||isDestroyed()||isFinishing()) return;
        checkingUpdate=true; if(updateChecker==null) updateChecker=createUpdateChecker(); String ua=UserAgent.current(prefs); UpdateChecker checker=updateChecker;
        updateIo.execute(() -> {
            UpdateInfo value=null; Exception failure=null; try { value=checker.fetch(ua,diagnostics); } catch(Exception e) { failure=e; }
            UpdateInfo info=value; Exception problem=failure;
            ui.post(() -> {
                if(isDestroyed()||isFinishing()||exitingForUpdate) return; checkingUpdate=false;
                if(problem!=null) { exitAfterUpdateFailure(); return; }
                if(updateDialog!=null) { updateDialog.dismiss(); updateDialog=null; }
                pendingUpdate=info.newer(BuildConfig.VERSION_CODE)?info:null;
                if(manual) dismissedUpdates.remove(info.versionCode); showPendingUpdate();
            });
        });
    }
    private void exitAfterUpdateFailure() {
        if(exitingForUpdate||isFinishing()) return; exitingForUpdate=true; accountEpoch++; loginEpoch++; if(qrController!=null) qrController.close(); if(api!=null) api.cancel();
        diagnostics.event("UPDATE","startup","EXIT_TO_PROJECT",0,0,0); toast("无法获取更新信息，将打开项目主页并退出应用");
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdateInfo.PROJECT_URL)).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch(Exception e) { toast("无法打开项目主页，应用即将退出"); } finally { finishAndRemoveTask(); }
    }
    private void showPendingUpdate() {
        if(pendingUpdate==null||isDestroyed()||isFinishing()||(updateDialog!=null&&updateDialog.isShowing())) return;
        UpdateInfo info=pendingUpdate; boolean required=info.required(BuildConfig.VERSION_CODE); if(!required&&dismissedUpdates.contains(info.versionCode)) return;
        Sheet sheet=createSheet(required?"需要更新后继续使用":info.title,"新版本 "+info.versionName+" · 当前 "+BuildConfig.VERSION_NAME); updateDialog=sheet.dialog;
        if(!info.description.isEmpty()) { sheet.panel.addView(text(info.description,13,INK,false)); gap(sheet.panel,18); }
        Button disk=button("网盘更新",true,() -> openWeb(info.downloadUrl)); disk.setEnabled(!info.downloadUrl.isEmpty()); sheet.panel.addView(disk); gap(sheet.panel,10);
        Button github=button("GitHub 更新",false,() -> openWeb(info.githubUrl)); github.setEnabled(!info.githubUrl.isEmpty()); sheet.panel.addView(github); gap(sheet.panel,10);
        if(info.downloadUrl.isEmpty()&&info.githubUrl.isEmpty()) { sheet.panel.addView(text("更新链接暂未配置，请稍后重新检查。",12,MUTED,false)); gap(sheet.panel,10); }
        sheet.panel.addView(button("重新检查",false,() -> checkForUpdates(true))); gap(sheet.panel,10);
        sheet.panel.addView(button(required?"退出应用":"稍后再说",false,() -> { if(required) finishAndRemoveTask(); else { dismissedUpdates.add(info.versionCode); sheet.dialog.dismiss(); } }));
        sheet.dialog.setCancelable(!required); sheet.dialog.setCanceledOnTouchOutside(!required); sheet.dialog.setOnCancelListener(d -> dismissedUpdates.add(info.versionCode)); sheet.dialog.setOnDismissListener(d -> { if(updateDialog==sheet.dialog) updateDialog=null; }); showSheet(sheet); dialogs.remove(sheet.dialog);
    }
    private void exportDiagnostics() {
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"ykt-assistant-"+new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(new java.util.Date())+".log");
        try { startActivityForResult(save,EXPORT_LOGS); } catch(Exception e) { toast("未找到系统文件保存程序"); }
    }

    private static final class Sheet {
        final Dialog dialog; final LinearLayout panel;
        Sheet(Dialog dialog,LinearLayout panel) { this.dialog=dialog; this.panel=panel; }
    }
    private Sheet createSheet(String title,String description) {
        Dialog dialog=new Dialog(this); LinearLayout panel=column(); panel.setPadding(dp(22),dp(12),dp(22),dp(22)); panel.setBackground(bg(BG,26,0));
        View handle=new View(this); handle.setBackground(bg(0xffcfd9cb,3,0)); LinearLayout.LayoutParams hp=lp(34,4); hp.gravity=Gravity.CENTER_HORIZONTAL; panel.addView(handle,hp); gap(panel,24);
        panel.addView(text(title,22,INK,true)); gap(panel,10); panel.addView(text(description,12,MUTED,false)); gap(panel,22); return new Sheet(dialog,panel);
    }
    private void showSheet(Sheet sheet) {
        dialogs.removeIf(d -> !d.isShowing()); dialogs.add(sheet.dialog);
        ScrollView scroll=new ScrollView(this); scroll.addView(sheet.panel); sheet.dialog.setContentView(scroll); sheet.dialog.show();
        if(sheet.dialog.getWindow()!=null) {
            sheet.dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); sheet.dialog.getWindow().setGravity(Gravity.BOTTOM);
            android.view.WindowManager.LayoutParams params=sheet.dialog.getWindow().getAttributes(); params.width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(460)); params.height=ViewGroup.LayoutParams.WRAP_CONTENT; params.y=dp(12); params.dimAmount=.32f; sheet.dialog.getWindow().setAttributes(params);
        }
    }
    private Dialog chooseService(int current,java.util.function.IntConsumer selected) {
        Dialog picker=new Dialog(this); LinearLayout sheet=column(); sheet.setPadding(dp(20),dp(12),dp(20),dp(20)); sheet.setBackground(bg(BG,26,0));
        View handle=new View(this); handle.setBackground(bg(0xffcfd9cb,3,0)); LinearLayout.LayoutParams handleParams=lp(34,4); handleParams.gravity=Gravity.CENTER_HORIZONTAL; sheet.addView(handle,handleParams); gap(sheet,24);
        sheet.addView(text("选择雨课堂版本",22,INK,true)); gap(sheet,8); sheet.addView(text("请选择与你的官方账号一致的版本",12,MUTED,false)); gap(sheet,22);
        String[] descriptions={"标准版课堂服务","荷塘版课堂服务","长江版课堂服务","黄河版课堂服务"};
        for(int i=0;i<RainApi.SERVICES.length;i++) {
            final int index=i; boolean active=i==current;
            LinearLayout option=row(); option.setPadding(dp(16),dp(14),dp(16),dp(14)); option.setSelected(active); option.setFocusable(true);
            option.setBackground(new RippleDrawable(ColorStateList.valueOf(0x142c5846),bg(active?PALE:WHITE,16,active?0:LINE),bg(WHITE,16,0)));
            LinearLayout names=column(); names.addView(text(RainApi.SERVICES[i],15,active?GREEN:INK,true)); gap(names,7); names.addView(text(descriptions[i],11,MUTED,false)); option.addView(names,new LinearLayout.LayoutParams(0,-2,1));
            option.addView(new Icon(this,active?5:6,active?GREEN:0xffbdcbb6),lp(24,24));
            option.setContentDescription(RainApi.SERVICES[i]+(active?"，已选择":""));
            option.setOnClickListener(v -> { selected.accept(index); picker.dismiss(); });
            option.setMinimumHeight(dp(72)); sheet.addView(option,lp(-1,-2)); gap(sheet,10);
        }
        gap(sheet,4); Button cancel=button("取消",false,picker::dismiss); cancel.setBackground(bg(BG,12,0)); sheet.addView(cancel);
        ScrollView pickerScroll=new ScrollView(this); pickerScroll.addView(sheet); picker.setContentView(pickerScroll); picker.show();
        if(picker.getWindow()!=null) {
            picker.getWindow().setBackgroundDrawableResource(android.R.color.transparent); picker.getWindow().setGravity(Gravity.BOTTOM);
            android.view.WindowManager.LayoutParams params=picker.getWindow().getAttributes(); params.width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(24),dp(460)); params.height=ViewGroup.LayoutParams.WRAP_CONTENT; params.y=dp(12); params.dimAmount=.32f; picker.getWindow().setAttributes(params);
        }
        return picker;
    }
    private void openLogin() {
        if(downloading||joining) { toast("请先完成当前任务"); return; }
        if(user!=null) { toast("请先退出当前账号"); return; }
        if(loginDialog!=null&&loginDialog.isShowing()) return;
        final Dialog dialog=new Dialog(this); loginDialog=dialog;
        ScrollView scroll=new ScrollView(this); LinearLayout form=column(); form.setPadding(dp(24),dp(28),dp(24),dp(18)); form.setBackground(bg(BG,26,0)); scroll.addView(form); dialog.setContentView(scroll);
        form.addView(lineLabel("WELCOME TO YOUR CLASSROOM")); gap(form,12); form.addView(text("扫码连接课堂",25,INK,true)); gap(form,10); form.addView(text("二维码失效后会自动重新获取；请扫描当前显示的新码。",12,MUTED,false)); gap(form,20);
        LinearLayout alternatives=row(); Button password=button("账号密码登录",false,() -> { dialog.dismiss(); openAccountLogin(false); }),sms=button("验证码登录",false,() -> { dialog.dismiss(); openAccountLogin(true); }); LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(44),1); left.rightMargin=dp(5); alternatives.addView(password,left); alternatives.addView(sms,new LinearLayout.LayoutParams(0,dp(44),1)); form.addView(alternatives); gap(form,18);
        final int[] service={prefs.getInt("service",0)};
        form.addView(text("雨课堂版本",12,MUTED,true)); gap(form,9);
        Button servicePicker=button(RainApi.SERVICES[service[0]],false,() -> {}); servicePicker.setContentDescription("雨课堂版本："+RainApi.SERVICES[service[0]]+"，点击切换"); form.addView(servicePicker); gap(form,18);
        FrameLayout qrFrame=new FrameLayout(this); qrFrame.setBackground(bg(WHITE,18,LINE));
        ImageView qrImage=new ImageView(this); qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER); qrImage.setPadding(dp(16),dp(16),dp(16),dp(16)); qrImage.setContentDescription("雨课堂官方登录二维码"); qrFrame.addView(qrImage,new FrameLayout.LayoutParams(-1,-1));
        TextView placeholder=text("正在获取新的登录二维码…",13,MUTED,false); placeholder.setGravity(Gravity.CENTER); placeholder.setPadding(dp(18),0,dp(18),0); qrFrame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1)); form.addView(qrFrame,lp(-1,224)); gap(form,14);
        TextView hint=text("正在连接官方登录服务…",12,MUTED,false); hint.setGravity(Gravity.CENTER); form.addView(hint); gap(form,18);
        Button qrButton=button("正在获取二维码…",true,() -> {}); qrButton.setEnabled(false); form.addView(qrButton); gap(form,10); form.addView(button("稍后再说",false,dialog::dismiss));
        accountEpoch++; busyCount=0; showLoading(); retire(api);
        final QrLoginController.State[] state={QrLoginController.State.FETCHING}; final Dialog[] picker={null},confirmation={null};
        QrLoginController controller=new QrLoginController((selected,round) -> {
            vault.clear(); prefs.edit().putInt("service",selected).apply(); RainApi client=configureApi(createLoginClient(selected,vault.session())); client.setDiagnostics(diagnostics,round); return client;
        },new QrLoginController.Listener() {
            @Override public void state(QrLoginController.State next,Bitmap image,String message,RainApi owner) {
                if(loginDialog!=dialog||!dialog.isShowing()||user!=null) return; state[0]=next; if(owner!=null) api=owner;
                boolean ready=next==QrLoginController.State.READY; qrImage.setImageBitmap(ready?image:null); qrImage.setVisibility(ready?View.VISIBLE:View.INVISIBLE); placeholder.setVisibility(ready?View.GONE:View.VISIBLE); placeholder.setText(next==QrLoginController.State.FETCHING?"正在获取新二维码…":next==QrLoginController.State.AUTHORIZED||next==QrLoginController.State.PROFILE_RETRY||next==QrLoginController.State.PROFILE_FAILED?"扫码已授权":next==QrLoginController.State.FAILED?"暂无可用二维码":"二维码已失效"); hint.setText(message);
                boolean profileFailed=next==QrLoginController.State.PROFILE_FAILED; qrButton.setText(profileFailed?"重试获取账号信息":next==QrLoginController.State.FETCHING?"正在获取二维码…":"刷新二维码"); qrButton.setEnabled(next==QrLoginController.State.READY||next==QrLoginController.State.FAILED||next==QrLoginController.State.EXPIRED||next==QrLoginController.State.BACKOFF||profileFailed);
            }
            @Override public void signedIn(RainApi owner,JSONObject profile) {
                if(loginDialog!=dialog||!dialog.isShowing()||user!=null) { owner.cancel(); return; }
                if(picker[0]!=null) picker[0].dismiss(); if(confirmation[0]!=null) confirmation[0].dismiss(); loginSuccess(profile,owner);
            }
        },diagnostics);
        qrController=controller;
        qrButton.setOnClickListener(v -> {
            if(state[0]==QrLoginController.State.PROFILE_FAILED) { controller.retryProfile(); return; }
            if(confirmation[0]!=null&&confirmation[0].isShowing()) return;
            Sheet sheet=createSheet("刷新登录二维码？","确认后将停用当前二维码，获取新码并重新等待扫码。"); confirmation[0]=sheet.dialog;
            sheet.panel.addView(button("确认刷新",true,() -> { sheet.dialog.dismiss(); controller.start(service[0]); })); gap(sheet.panel,10); sheet.panel.addView(button("继续扫码",false,sheet.dialog::dismiss)); showSheet(sheet);
        });
        servicePicker.setOnClickListener(v -> {
            if(picker[0]!=null&&picker[0].isShowing()) return;
            picker[0]=chooseService(service[0],index -> { if(loginDialog!=dialog||!dialog.isShowing()||user!=null||index==service[0]) return; service[0]=index; servicePicker.setText(RainApi.SERVICES[index]); servicePicker.setContentDescription("雨课堂版本："+RainApi.SERVICES[index]+"，点击切换"); controller.start(index); });
        });
        dialog.setOnDismissListener(d -> {
            controller.close(); if(qrController==controller) qrController=null; if(picker[0]!=null) picker[0].dismiss(); if(confirmation[0]!=null) confirmation[0].dismiss();
            if(loginDialog!=dialog) return; loginDialog=null; loginEpoch++;
            if(user==null) { vault.clear(); if(!isDestroyed()&&!isFinishing()) try { api=configureApi(new RainApi(prefs.getInt("service",0),vault.session())); } catch(Exception ignored) {} }
        });
        dialog.show(); if(dialog.getWindow()!=null) { dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(460)),-2); dialog.getWindow().setDimAmount(.32f); }
        controller.start(service[0]);
    }
    protected Dialog showHumanVerification(RainApi client,HumanVerification.Callback callback) {
        if(humanVerificationDialog!=null) humanVerificationDialog.dismiss(); long started=System.nanoTime(); client.trace("HUMAN","REQUEST",0,0,0);
        humanVerificationDialog=HumanVerification.show(this,client.host(),UserAgent.current(prefs),new HumanVerification.Callback() {
            @Override public void verified(HumanVerification.Proof proof) { client.trace("HUMAN","ACCEPTED",0,0,(System.nanoTime()-started)/1000000); callback.verified(proof); }
            @Override public void cancelled(String reason) { client.trace("HUMAN",reason!=null&&reason.startsWith("已取消")?"CANCELLED":"FAILED",0,0,(System.nanoTime()-started)/1000000); callback.cancelled(reason); }
        }); return humanVerificationDialog;
    }
    private android.widget.EditText loginField(String hint,boolean secret) {
        android.widget.EditText input=new android.widget.EditText(this); input.setHint(hint); input.setContentDescription(hint); input.setTextSize(14); input.setTextColor(INK); input.setHintTextColor(MUTED); input.setSingleLine(true); input.setPadding(dp(14),dp(12),dp(14),dp(12)); input.setBackground(bg(WHITE,12,LINE)); input.setSaveEnabled(false); input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); input.setInputType(secret?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS); return input;
    }
    private void openAccountLogin(boolean smsFirst) {
        if(user!=null||downloading||joining||(loginDialog!=null&&loginDialog.isShowing())) return;
        final Dialog dialog=new Dialog(this); loginDialog=dialog; accountEpoch++; busyCount=0; showLoading();
        LinearLayout form=column(); form.setPadding(dp(24),dp(26),dp(24),dp(20)); form.setBackground(bg(BG,24,0)); ScrollView scroll=new ScrollView(this); scroll.addView(form); dialog.setContentView(scroll);
        form.addView(text("登录雨课堂",25,INK,true)); gap(form,10); form.addView(text("账号仅提交到所选雨课堂；密码不在本地保存。",12,MUTED,false)); gap(form,18);
        Button server=button(RainApi.SERVICES[prefs.getInt("service",0)],false,() -> {}); form.addView(server); gap(form,14);
        LinearLayout modes=row(); Button passwordMode=button("账号密码",false,() -> {}),smsMode=button("短信验证码",false,() -> {}); LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(44),1); left.rightMargin=dp(5); modes.addView(passwordMode,left); LinearLayout.LayoutParams right=new LinearLayout.LayoutParams(0,dp(44),1); right.leftMargin=dp(5); modes.addView(smsMode,right); form.addView(modes); gap(form,18);
        android.widget.EditText identity=loginField("手机号或邮箱",false),secret=loginField("密码",true); form.addView(identity,lp(-1,50)); gap(form,12); form.addView(secret,lp(-1,50)); gap(form,12);
        Button send=button("获取验证码",false,() -> {}); form.addView(send); gap(form,10); TextView message=text("",12,0xffa35445,false); message.setTextIsSelectable(true); form.addView(message); gap(form,10);
        Button submit=button("登录",true,() -> {}); form.addView(submit); gap(form,10); Button qr=button("使用二维码登录",false,() -> { dialog.dismiss(); openLogin(); }); form.addView(qr); gap(form,10); form.addView(button("取消",false,dialog::dismiss));
        class CredentialFlow {
            boolean sms=smsFirst,busy,disposed; int service=prefs.getInt("service",0),run; RainApi client; Dialog challenge,picker;
            String sentPhone=""; HumanVerification.Proof sentProof;
            final java.util.Map<String,Long> cooldown=new java.util.HashMap<>();
            final Runnable tick=new Runnable() { @Override public void run() { if(disposed||!dialog.isShowing()) return; labels(); ui.postDelayed(this,1000); } };
            boolean active(int epoch,RainApi owner) { return !disposed&&dialog.isShowing()&&loginDialog==dialog&&run==epoch&&loginEpoch==epoch&&client==owner&&api==owner&&!owner.isClosed()&&user==null; }
            void fields() {
                identity.setHint(sms?"手机号":"手机号或邮箱"); identity.setContentDescription(sms?"手机号":"手机号或邮箱"); identity.setInputType(sms?android.text.InputType.TYPE_CLASS_PHONE:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                secret.setHint(sms?"短信验证码":"密码"); secret.setContentDescription(sms?"短信验证码":"密码"); secret.setInputType(sms?android.text.InputType.TYPE_CLASS_NUMBER:android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            void labels() {
                passwordMode.setSelected(!sms); smsMode.setSelected(sms); passwordMode.setTextColor(!sms?WHITE:GREEN); passwordMode.setBackground(bg(!sms?GREEN:WHITE,12,!sms?0:LINE)); smsMode.setTextColor(sms?WHITE:GREEN); smsMode.setBackground(bg(sms?GREEN:WHITE,12,sms?0:LINE)); send.setVisibility(sms?View.VISIBLE:View.GONE);
                long remaining=cooldown.getOrDefault(service+"|"+identity.getText().toString().trim(),0L)-android.os.SystemClock.elapsedRealtime(),seconds=Math.max(0,(remaining+999)/1000);
                send.setText(seconds>0?seconds+" 秒后重试":"获取验证码"); send.setEnabled(!busy&&seconds==0&&client!=null); submit.setEnabled(!busy&&client!=null); submit.setText(busy?"处理中…":"登录"); identity.setEnabled(!busy); secret.setEnabled(!busy); qr.setEnabled(!busy);
            }
            void start() {
                if(disposed) return; run=++loginEpoch; busy=false; sentPhone=""; sentProof=null; secret.setText(""); message.setText(""); fields(); if(challenge!=null) challenge.dismiss(); challenge=null;
                retire(client==null?api:client); vault.clear();
                try { prefs.edit().putInt("service",service).apply(); client=configureApi(createLoginClient(service,vault.session())); api=client; } catch(Exception e) { client=null; message.setText("登录服务无法连接，请重新选择版本"); }
                server.setText(RainApi.SERVICES[service]); labels();
            }
            void fail(int epoch,RainApi owner,String reason) { if(!active(epoch,owner)) return; busy=false; message.setText(reason); labels(); }
            void sendCode() {
                if(busy||client==null) return; final String phone;
                try { phone=LoginInput.phone(identity.getText().toString()); } catch(Exception e) { message.setText(e.getMessage()); return; }
                if(cooldown.getOrDefault(service+"|"+phone,0L)>android.os.SystemClock.elapsedRealtime()) return;
                final int epoch=run,selectedService=service; final RainApi owner=client; busy=true; message.setText(""); labels();
                challenge=showHumanVerification(owner,new HumanVerification.Callback() {
                    @Override public void verified(HumanVerification.Proof proof) {
                        if(!active(epoch,owner)) return;
                        work(() -> owner.sendLoginCode(phone,proof.ticket,proof.rand),ok -> { if(!active(epoch,owner)) return; sentPhone=phone; sentProof=proof; cooldown.put(selectedService+"|"+phone,android.os.SystemClock.elapsedRealtime()+60_000); busy=false; message.setText("验证码已发送，请查看短信"); labels(); secret.requestFocus(); },e -> fail(epoch,owner,"验证码发送失败："+error(e)));
                    }
                    @Override public void cancelled(String reason) { fail(epoch,owner,reason); }
                });
            }
            void login() {
                if(busy||client==null) return; final String account,value;
                try { account=sms?LoginInput.phone(identity.getText().toString()):LoginInput.account(identity.getText().toString()); value=sms?LoginInput.code(secret.getText().toString()):LoginInput.password(secret.getText().toString()); } catch(Exception e) { message.setText(e.getMessage()); return; }
                if(sms&&(sentProof==null||!account.equals(sentPhone))) { message.setText("请先为当前手机号获取验证码"); return; }
                final int epoch=run; final RainApi owner=client; final boolean useSms=sms; busy=true; message.setText(""); labels();
                HumanVerification.Callback action=new HumanVerification.Callback() {
                    @Override public void verified(HumanVerification.Proof proof) {
                        if(!active(epoch,owner)) return;
                        work(() -> useSms?owner.smsLogin(account,value,proof.ticket,proof.rand):owner.passwordLogin(account,value,proof.ticket,proof.rand),profile -> { if(!active(epoch,owner)) return; secret.setText(""); sentProof=null; if(picker!=null) picker.dismiss(); loginSuccess(profile,owner); },e -> fail(epoch,owner,"登录失败："+error(e)));
                    }
                    @Override public void cancelled(String reason) { fail(epoch,owner,reason); }
                };
                if(useSms) action.verified(sentProof); else challenge=showHumanVerification(owner,action);
            }
            void dispose() {
                disposed=true; ui.removeCallbacks(tick); secret.setText(""); sentProof=null; if(challenge!=null) challenge.dismiss(); if(picker!=null) picker.dismiss();
                if(loginDialog!=dialog) return; loginDialog=null; loginEpoch++;
                if(user==null) { vault.clear(); retire(client); if(!isDestroyed()&&!isFinishing()) try { api=configureApi(new RainApi(prefs.getInt("service",0),vault.session())); } catch(Exception ignored) {} }
            }
        }
        CredentialFlow flow=new CredentialFlow(); passwordMode.setOnClickListener(v -> { if(flow.sms) { flow.sms=false; flow.start(); } }); smsMode.setOnClickListener(v -> { if(!flow.sms) { flow.sms=true; flow.start(); } });
        server.setOnClickListener(v -> { if(flow.picker!=null&&flow.picker.isShowing()) return; flow.picker=chooseService(flow.service,index -> { if(flow.disposed||index==flow.service) return; flow.service=index; flow.start(); }); }); submit.setOnClickListener(v -> flow.login()); send.setOnClickListener(v -> flow.sendCode());
        dialog.setOnDismissListener(d -> flow.dispose()); dialog.show(); if(dialog.getWindow()!=null) { dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent); dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(32),dp(460)),-2); dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); } flow.start(); ui.post(flow.tick);
    }

    private RainApi configureApi(RainApi client) { client.configure(lessonCache.session()); client.setDiagnostics(diagnostics,"client-"+java.util.UUID.randomUUID()); client.setUserAgent(UserAgent.current(prefs)); client.setDeviceInfo(UserAgent.deviceBrand(),UserAgent.deviceVersion()); return client; }
    protected RainApi createLoginClient(int service,RainApi.CookieStorage storage) throws Exception { return new RainApi(service,storage); }

    private void loginSuccess(JSONObject profile,RainApi a) { try { a.rememberLogin(); } catch(Exception e) { retire(a); toast(error(e)); return; } api=a; user=profile; loginEpoch++; if(loginDialog!=null) loginDialog.dismiss(); tab=0; catalog=null; onlyLive=false; courseError=null; courseUpdated=0; render(); loadCourses(); if(a.storageWarning()!=null) toast(a.storageWarning()); else toast("登录成功，欢迎回来"); }
    private boolean checkingLocation;
    private int locationRevision;
    private Dialog locationDialog;
    private boolean activeLocation(RainApi owner,String lesson,int revision) { return revision==locationRevision&&owner==api&&!owner.isClosed()&&lesson.equals(owner.lesson())&&!isDestroyed()&&!isFinishing(); }
    private void cancelLocationFlow() { locationRevision++; checkingLocation=false; locationSubmitting=false; if(locationDialog!=null) locationDialog.dismiss(); locationDialog=null; }
    private void checkLocation() {
        if(!hasLesson()||joining||locationSubmitting||checkingLocation||(locationDialog!=null&&locationDialog.isShowing())) return;
        RainApi owner=api; String lesson=owner.lesson(); int revision=++locationRevision; checkingLocation=true;
        work(owner::locationInfo,info -> {
            if(revision==locationRevision) checkingLocation=false; if(!activeLocation(owner,lesson,revision)) return;
            if(info.signed) { toast("位置签到已由雨课堂确认"); return; }
            if(Boolean.FALSE.equals(info.needed)) { toast("当前位置签到尚未开始或已结束"); return; }
            try { info.requireAvailable(); } catch(Exception e) { toast(error(e)); return; }
            if(tab!=1||dynamicView!=LIVE_READER) return;
            Sheet sheet=createSheet(info.submitted?"等待签到确认":"位置签到",info.submitted?"本轮已提交位置，雨课堂尚未确认。刷新状态不会重复提交。":"将使用雨课堂返回的签到位置提交，不读取手机定位。"); locationDialog=sheet.dialog;
            LinearLayout position=compactCard(); position.addView(text(info.address.isEmpty()?"课堂签到位置":info.address,14,INK,true)); gap(position,8); position.addView(text(String.format(java.util.Locale.ROOT,"%.6f, %.6f",info.point.lat,info.point.lng),12,MUTED,false)); sheet.panel.addView(position); gap(sheet.panel,16);
            TextView result=text("",12,0xffa35445,false); sheet.panel.addView(result); gap(sheet.panel,10);
            Button confirm=button(info.submitted?"本轮已提交":"确认位置签到",true,() -> {}); confirm.setEnabled(!info.submitted); sheet.panel.addView(confirm); gap(sheet.panel,10);
            Button refresh=button("刷新状态",false,() -> { sheet.dialog.dismiss(); checkLocation(); }); sheet.panel.addView(refresh); gap(sheet.panel,10); Button cancel=button("关闭",false,sheet.dialog::dismiss); sheet.panel.addView(cancel);
            confirm.setOnClickListener(v -> {
                if(!activeLocation(owner,lesson,revision)||locationSubmitting) return;
                locationSubmitting=true; confirm.setEnabled(false); confirm.setText("正在提交…"); refresh.setEnabled(false); cancel.setEnabled(false); sheet.dialog.setCancelable(false);
                work(() -> owner.submitServerLocation(info),state -> {
                    if(revision==locationRevision) locationSubmitting=false;
                    if(!activeLocation(owner,lesson,revision)) { if(revision==locationRevision&&owner==api) sheet.dialog.dismiss(); return; }
                    if(state.signed) { sheet.dialog.dismiss(); toast("位置签到成功，雨课堂已确认"); return; }
                    confirm.setText("本轮已提交"); result.setText("位置已提交，雨课堂尚未确认。可刷新状态查看结果。"); refresh.setEnabled(true); cancel.setEnabled(true); sheet.dialog.setCancelable(true);
                },e -> {
                    if(revision==locationRevision) locationSubmitting=false;
                    if(!activeLocation(owner,lesson,revision)) { if(revision==locationRevision&&owner==api) sheet.dialog.dismiss(); return; }
                    result.setText(error(e)); confirm.setText("请重新查询状态"); refresh.setEnabled(true); cancel.setEnabled(true); sheet.dialog.setCancelable(true);
                });
            });
            sheet.dialog.setOnDismissListener(d -> { if(locationDialog==sheet.dialog) locationDialog=null; }); showSheet(sheet);
        },e -> { if(revision==locationRevision&&owner==api) { checkingLocation=false; toast(error(e)); } });
    }
    private void downloadOptions() {
        JSONObject shown=readerPresentation();
        if(shown==null||shown.optJSONArray("slides")==null||shown.optJSONArray("slides").length()==0) { toast("请先加载课件"); return; }
        if(downloading||pendingFormat!=null) { toast("已有保存任务，请先完成"); return; }
        final JSONObject selectedPresentation=shown; final RainApi owner=api; final int account=accountEpoch;
        Sheet sheet=createSheet("下载课件",shown.optString("title","当前课件"));
        String[] names={"PPTX 演示文稿","ZIP 页面图片"},details={"图片版幻灯片，适合展示和复习","按页保存图片，便于整理和查阅"},formats={"pptx","zip"},badges={"PPTX","ZIP"};
        final int[] selected={0}; LinearLayout[] rows=new LinearLayout[names.length]; FrameLayout[] radios=new FrameLayout[names.length];
        for(int i=0;i<names.length;i++) {
            final int index=i; LinearLayout option=row(); option.setPadding(dp(14),dp(16),dp(14),dp(16)); option.setMinimumHeight(dp(86)); option.setFocusable(true);
            TextView badge=text(badges[i],10,GREEN,true); badge.setGravity(Gravity.CENTER); badge.setBackground(bg(PALE,11,0)); option.addView(badge,lp(44,44));
            LinearLayout content=column(); content.setPadding(dp(12),0,dp(8),0); content.addView(text(names[i],14,INK,true)); gap(content,7); content.addView(text(details[i],11,MUTED,false)); option.addView(content,new LinearLayout.LayoutParams(0,-2,1));
            FrameLayout radio=new FrameLayout(this); option.addView(radio,lp(24,24)); radios[i]=radio; rows[i]=option; sheet.panel.addView(option,lp(-1,-2)); gap(sheet.panel,10);
            option.setOnClickListener(v -> { selected[0]=index; updateFormatRows(rows,radios,index); });
        }
        updateFormatRows(rows,radios,0); gap(sheet.panel,10); sheet.panel.addView(text("PPTX 由课件页面图片生成，不是原始可编辑 PPT。",11,MUTED,false)); gap(sheet.panel,20);
        sheet.panel.addView(button("选择保存位置",true,() -> {
            sheet.dialog.dismiss();
            if(account!=accountEpoch||api!=owner||owner==null||owner.isClosed()) { toast("账号已变更，请重新选择课件"); return; }
            String format=formats[selected[0]],mime=selected[0]==0?"application/vnd.openxmlformats-officedocument.presentationml.presentation":"application/zip";
            pendingFormat=format; exportPresentation=selectedPresentation; pendingExportApi=owner; pendingExportEpoch=account;
            String title=selectedPresentation.optString("title","雨课堂课件").replaceAll("[\\\\/:*?\"<>|]","_");
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime).putExtra(Intent.EXTRA_TITLE,title+"."+format);
            try { startActivityForResult(save,SAVE); } catch(Exception e) { pendingFormat=null; exportPresentation=null; pendingExportApi=null; toast("未找到系统文件保存程序"); }
        })); gap(sheet.panel,10); sheet.panel.addView(button("取消",false,sheet.dialog::dismiss)); showSheet(sheet);
    }
    private SingleImageFile pendingImage;
    private ImageSaveJob imageJob;
    private final class ImageSaveJob implements java.util.function.BooleanSupplier {
        final RainApi owner; final int epoch=accountEpoch; final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        ImageSaveJob(RainApi owner) { this.owner=owner; }
        @Override public boolean getAsBoolean() { return cancelled.get()||epoch!=accountEpoch||owner.isClosed()||Thread.currentThread().isInterrupted(); }
        void cancel() { cancelled.set(true); owner.cancelOperation(this); }
    }
    private void imageOptions() {
        if(slideImage==null||slideImage.getDrawable()==null) { toast("图片加载中，请稍候"); return; }
        JSONObject deck=readerPresentation(); JSONArray slides=deck==null?null:deck.optJSONArray("slides"); int index=readerIndex();
        if(slides==null||index<0||index>=slides.length()||slides.optJSONObject(index)==null) { toast("请先加载本页课件"); return; }
        if(downloading||pendingFormat!=null) { toast("已有保存任务，请先完成"); return; }
        final JSONObject slide;
        try { slide=new JSONObject(slides.getJSONObject(index).toString()); } catch(Exception e) { toast("本页数据不完整，请刷新课件"); return; }
        RainApi owner=api; int account=accountEpoch; String title=deck.optString("title","雨课堂课件");
        Sheet sheet=createSheet("本页图片",title+" · 第 "+(index+1)+" 页");
        sheet.panel.addView(button("保存此页图片",true,() -> {
            sheet.dialog.dismiss(); if(account!=accountEpoch||owner==null||api!=owner||owner.isClosed()) { toast("账号已变更，请重新选择图片"); return; }
            if(downloading||pendingFormat!=null) { toast("已有保存任务，请先完成"); return; }
            fetchSingleImage(owner,slide,title,index+1);
        })); gap(sheet.panel,10); sheet.panel.addView(button("取消",false,sheet.dialog::dismiss)); showSheet(sheet);
    }
    private void fetchSingleImage(RainApi owner,JSONObject slide,String title,int pageNumber) {
        ImageSaveJob job=new ImageSaveJob(owner); imageJob=job; downloading=true; pendingFormat="image";
        Sheet loading=createSheet("正在读取图片","读取完成后可选择图片保存位置。"); ProgressBar spinner=new ProgressBar(this); spinner.setIndeterminateTintList(ColorStateList.valueOf(GREEN)); LinearLayout.LayoutParams p=lp(28,28); p.gravity=Gravity.CENTER_HORIZONTAL; loading.panel.addView(spinner,p); gap(loading.panel,18); loading.panel.addView(button("取消保存",false,() -> { job.cancel(); loading.dialog.dismiss(); }));
        loading.dialog.setOnDismissListener(d -> { if(imageJob==job&&downloading) job.cancel(); }); showSheet(loading);
        work(() -> { SingleImageFile file=SingleImageFile.from(owner.slideImage(slide,job)); if(job.getAsBoolean()) throw new java.io.InterruptedIOException("保存已取消"); return file; },file -> {
            if(imageJob!=job) return; downloading=false; loading.dialog.dismiss();
            if(job.getAsBoolean()) { clearSingleImage(job); toast("已取消保存"); return; }
            pendingImage=file;
            String name=title.replace('\\','_').replace('/','_').replaceAll("[\\p{Cntrl}:*?\"<>|]","_").trim(); if(name.isEmpty()) name="雨课堂课件"; if(name.length()>80) name=name.substring(0,80);
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(file.mime).putExtra(Intent.EXTRA_TITLE,name+"-第"+pageNumber+"页."+file.extension);
            try { startActivityForResult(save,SAVE_IMAGE); } catch(Exception e) { clearSingleImage(job); toast("未找到系统文件保存程序"); }
        },e -> { if(imageJob!=job) return; downloading=false; loading.dialog.dismiss(); clearSingleImage(job); toast(job.getAsBoolean()?"已取消保存":"图片读取失败："+error(e)); });
    }
    private void clearSingleImage(ImageSaveJob job) {
        if(imageJob!=job) return; imageJob=null; pendingImage=null; if("image".equals(pendingFormat)) pendingFormat=null;
    }
    private void deleteImageDocument(Uri uri) {
        try { if("file".equals(uri.getScheme())) { new java.io.File(uri.getPath()).delete(); return; } if(!DocumentsContract.deleteDocument(getContentResolver(),uri)) getContentResolver().delete(uri,null,null); } catch(Exception ignored) {}
    }
    private void singleImageResult(int result,Intent data) {
        ImageSaveJob job=imageJob; SingleImageFile file=pendingImage; pendingImage=null; if("image".equals(pendingFormat)) pendingFormat=null;
        if(result!=RESULT_OK||data==null||data.getData()==null) { if(job!=null) { job.cancel(); clearSingleImage(job); } return; }
        Uri uri=data.getData();
        if(job==null||file==null||job.getAsBoolean()||job.owner!=api) { deleteImageDocument(uri); if(job!=null) clearSingleImage(job); toast("保存请求已失效，请重新选择图片"); return; }
        downloading=true; Sheet saving=createSheet("保存本页图片","正在保存到你选择的位置…"); ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(100); bar.setProgressTintList(ColorStateList.valueOf(GREEN)); saving.panel.addView(bar,lp(-1,8)); gap(saving.panel,16); TextView percent=text("0%",13,GREEN,true); percent.setGravity(Gravity.CENTER); saving.panel.addView(percent); gap(saving.panel,18); saving.panel.addView(button("取消保存",false,() -> { job.cancel(); saving.dialog.dismiss(); })); saving.dialog.setOnDismissListener(d -> { if(imageJob==job&&downloading) job.cancel(); }); showSheet(saving);
        work(() -> { try { file.save(this,uri,job,value -> ui.post(() -> { if(!isDestroyed()&&imageJob==job&&!job.getAsBoolean()) { bar.setProgress(value); percent.setText(getString(R.string.export_percent,value)); } })); return true; } catch(Exception e) { deleteImageDocument(uri); throw e; } },ok -> { downloading=false; clearSingleImage(job); saving.dialog.dismiss(); toast("本页图片已保存"); },e -> { downloading=false; clearSingleImage(job); saving.dialog.dismiss(); toast(job.getAsBoolean()?"已取消保存":"图片保存失败："+error(e)); });
    }

    private void updateFormatRows(LinearLayout[] rows,FrameLayout[] radios,int selected) {
        for(int i=0;i<rows.length;i++) {
            boolean active=i==selected; rows[i].setSelected(active); rows[i].setBackground(new RippleDrawable(ColorStateList.valueOf(0x142c5846),bg(active?PALE:WHITE,16,active?0:LINE),bg(WHITE,16,0)));
            radios[i].removeAllViews(); radios[i].addView(new Icon(this,active?5:6,active?GREEN:0xffbdcbb6),new FrameLayout.LayoutParams(-1,-1));
        }
    }
    private DownloadTask activeDownload;
    private final class DownloadTask implements java.util.function.BooleanSupplier {
        final RainApi owner; final int epoch; final java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        long lastUpdate; ExportProgress.Stage lastStage; DownloadPanel panel;
        DownloadTask(RainApi owner,int epoch) { this.owner=owner; this.epoch=epoch; }
        @Override public boolean getAsBoolean() { return cancelled.get()||owner.isClosed()||epoch!=accountEpoch||Thread.currentThread().isInterrupted(); }
        void cancel() {
            cancelled.set(true); owner.cancelOperation(this);
            if(panel!=null&&panel.sheet.dialog.isShowing()) { panel.headline.setText("正在取消…"); panel.description.setText("正在停止下载并清理未完成的文件"); panel.action.setEnabled(false); }
        }
        void publish(ExportProgress progress) {
            long now=android.os.SystemClock.elapsedRealtime();
            if(progress.stage==lastStage&&now-lastUpdate<100&&progress.percent()!=100) return;
            lastUpdate=now; lastStage=progress.stage;
            ui.post(() -> { if(!isDestroyed()&&activeDownload==this&&!getAsBoolean()) panel.update(progress); });
        }
    }
    private final class DownloadPanel {
        final Sheet sheet;
        final TextView headline,description,detail;
        final TextView[] stages=new TextView[3];
        final ProgressBar progress;
        final Button action;
        DownloadPanel(DownloadTask job,String title) {
            sheet=createSheet("保存课件",title);
            LinearLayout steps=row(); String[] labels={"1 获取页面","2 生成文件","3 保存文件"};
            for(int i=0;i<3;i++) { TextView step=text(labels[i],10,i==0?GREEN:MUTED,true); step.setGravity(Gravity.CENTER); step.setPadding(dp(4),dp(10),dp(4),dp(10)); step.setBackground(bg(i==0?PALE:BG,10,0)); stages[i]=step; steps.addView(step,new LinearLayout.LayoutParams(0,-2,1)); }
            sheet.panel.addView(steps); gap(sheet.panel,28);
            headline=text("准备下载",30,INK,true); headline.setGravity(Gravity.CENTER); sheet.panel.addView(headline); gap(sheet.panel,10);
            description=text("正在准备课件页面",13,MUTED,false); description.setGravity(Gravity.CENTER); sheet.panel.addView(description); gap(sheet.panel,24);
            progress=new ProgressBar(MainActivity.this,null,android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgressTintList(ColorStateList.valueOf(GREEN)); progress.setProgressBackgroundTintList(ColorStateList.valueOf(PALE)); progress.setIndeterminateTintList(ColorStateList.valueOf(GREEN)); sheet.panel.addView(progress,lp(-1,8)); gap(sheet.panel,12);
            detail=text("",11,MUTED,false); detail.setGravity(Gravity.CENTER); sheet.panel.addView(detail); gap(sheet.panel,26);
            action=button("取消保存",false,job::cancel); sheet.panel.addView(action);
            sheet.dialog.setCanceledOnTouchOutside(false); sheet.dialog.setOnCancelListener(d -> job.cancel()); showSheet(sheet);
        }
        void update(ExportProgress state) {
            int phase=state.stage==ExportProgress.Stage.FETCH?0:state.stage==ExportProgress.Stage.BUILD?1:2;
            for(int i=0;i<stages.length;i++) { stages[i].setTextColor(i<=phase?GREEN:MUTED); stages[i].setBackground(bg(i==phase?PALE:BG,10,0)); }
            progress.setIndeterminate(state.stage==ExportProgress.Stage.BUILD);
            if(state.stage!=ExportProgress.Stage.BUILD) progress.setProgress(state.percent());
            if(state.stage==ExportProgress.Stage.FETCH) { headline.setText(getString(R.string.page_number,state.pages,state.totalPages)); description.setText("页面已准备"); detail.setText("正在从雨课堂获取课件图片"); }
            else if(state.stage==ExportProgress.Stage.BUILD) { headline.setText("正在生成"); description.setText("正在整理课件文件"); detail.setText("图片已就绪，文件正在本机生成"); }
            else { headline.setText(getString(R.string.export_percent,state.percent())); description.setText("正在保存到所选位置"); detail.setText(getString(R.string.export_bytes,android.text.format.Formatter.formatShortFileSize(MainActivity.this,state.bytes),android.text.format.Formatter.formatShortFileSize(MainActivity.this,state.totalBytes))); }
        }
        void finish(boolean saved,boolean cancelled,String message) {
            progress.setIndeterminate(false); progress.setProgress(saved?100:0);
            headline.setText(saved?"保存完成":cancelled?"已取消保存":"保存失败"); description.setText(message); detail.setText(saved?"文件已保存到你选择的位置":cancelled?"未完成的文件已尝试清理":"可刷新课件后重新下载");
            for(TextView stage:stages) stage.setBackground(bg(saved?PALE:BG,10,0));
            action.setEnabled(true); action.setText(saved?"完成":"关闭"); action.setOnClickListener(v -> sheet.dialog.dismiss());
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==EXPORT_LOGS) {
            if(result==RESULT_OK&&data!=null&&data.getData()!=null) { Uri destination=data.getData(); work(() -> { byte[] bytes=diagnostics.today(); if(bytes.length==0) throw new java.io.IOException("暂无可导出的诊断日志"); try(java.io.OutputStream output=getContentResolver().openOutputStream(destination,"wt")) { if(output==null) throw new java.io.IOException("无法保存日志"); output.write(bytes); } return true; },ok -> toast("诊断日志已导出")); }
            return;
        } if(request==SAVE_IMAGE) { singleImageResult(result,data); return; } if(request!=SAVE) return;
        String format=pendingFormat; JSONObject p=exportPresentation; RainApi owner=pendingExportApi; int ownerEpoch=pendingExportEpoch; pendingFormat=null; exportPresentation=null; pendingExportApi=null;
        if(result!=RESULT_OK||data==null||data.getData()==null||format==null||p==null) return;
        Uri destination=data.getData();
        if(owner==null||owner.isClosed()||ownerEpoch!=accountEpoch) { try { DocumentsContract.deleteDocument(getContentResolver(),destination); } catch(Exception ignored) {} toast("账号已变更，请重新选择课件"); return; }
        RainApi a=owner; downloading=true; DownloadTask job=new DownloadTask(a,ownerEpoch); activeDownload=job;
        DownloadPanel panel=new DownloadPanel(job,p.optString("title","当前课件")); job.panel=panel;
        work(() -> { try { SlideExporter.save(this,s -> a.slideImage(s,job),p,format,destination,job::publish,job); return true; }
            catch(Exception e) { try { DocumentsContract.deleteDocument(getContentResolver(),destination); } catch(Exception ignored) {} throw e; }
        },ok -> { downloading=false; activeDownload=null; panel.finish(true,false,"课件已保存到所选位置"); },e -> { downloading=false; activeDownload=null; boolean cancelled=job.cancelled.get(); panel.finish(false,cancelled,cancelled?"保存任务已停止":error(e)); });
    }

    @Override public void onBackPressed() { if(exitingForUpdate) return; if(pendingUpdate!=null&&pendingUpdate.required(BuildConfig.VERSION_CODE)) { showPendingUpdate(); return; } if(checkingCredentials&&cancelEntryLookup!=null) { cancelEntryLookup.run(); return; } if(tab==1&&dynamicView==HISTORY_READER) { dynamicView=DETAIL; render(); } else if(tab==1&&dynamicView!=FEED) showFeed(); else if(tab!=0) { tab=0; render(); } else super.onBackPressed(); }
    @Override protected void onStart() { super.onStart(); checkForUpdates(false); }
    @Override protected void onStop() { flushActivityCache(); super.onStop(); }
    @Override protected void onDestroy() { if(humanVerificationDialog!=null) humanVerificationDialog.dismiss(); if(loginDialog!=null) loginDialog.dismiss(); if(updateChecker!=null) updateChecker.close(); updateIo.shutdownNow(); if(updateDialog!=null) updateDialog.dismiss(); if(qrController!=null) qrController.close(); if(diagnostics!=null) diagnostics.close(); if(cancelEntryLookup!=null) cancelEntryLookup.run(); flushActivityCache(); feedIo.shutdown(); loginEpoch++; accountEpoch++; cancelLocationFlow(); if(fullDialog!=null) fullDialog.dismiss(); if(imageJob!=null) imageJob.cancel(); pendingImage=null; if(api!=null) api.cancel(); workers.shutdownNow(); ui.removeCallbacksAndMessages(null); super.onDestroy(); }
    /** Consistent stroke icons, no font-dependent emoji or mismatched symbol baselines. */
    private static class Icon extends View {
        final int kind,color; final Paint paint=new Paint(3); final RectF rect=new RectF();
        Icon(android.content.Context c,int kind,int color) { super(c); this.kind=kind; this.color=color; setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save(); canvas.scale(getWidth()/24f,getHeight()/24f); paint.setColor(color); paint.setStrokeWidth(1.7f); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStyle(Paint.Style.STROKE);
            if(kind==0) { for(int y=3;y<=14;y+=11) for(int x=3;x<=14;x+=11) { rect.set(x,y,x+7,y+7); canvas.drawRoundRect(rect,1.5f,1.5f,paint); } }
            else if(kind==1) { canvas.drawLine(5,6,5,18,paint); for(int y=5;y<=19;y+=7) { canvas.drawCircle(5,y,1.8f,paint); canvas.drawLine(11,y,20,y,paint); } }
            else if(kind==2) { canvas.drawCircle(12,8,3.5f,paint); rect.set(5,13,19,26); canvas.drawArc(rect,185,170,false,paint); }
            else if(kind==5||kind==6) { canvas.drawCircle(12,12,8,paint); if(kind==5) { paint.setStyle(Paint.Style.FILL); canvas.drawCircle(12,12,4,paint); } }
            else { for(int i=0;i<8;i++) { canvas.save(); canvas.rotate(i*45,12,12); canvas.drawLine(12,3,12,9,paint); canvas.restore(); } canvas.drawCircle(12,12,2,paint); }
            canvas.restore();
        }
    }
}
