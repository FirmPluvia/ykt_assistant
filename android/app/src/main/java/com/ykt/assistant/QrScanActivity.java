package com.ykt.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.Uri;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;
import com.journeyapps.barcodescanner.CameraPreview;
import com.journeyapps.barcodescanner.Size;
import com.journeyapps.barcodescanner.camera.CameraSettings;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;
import com.journeyapps.barcodescanner.camera.CameraInstance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local QR camera capture, with driver-backed manual zoom. */
public final class QrScanActivity extends Activity {
    static final String CONTENT="classroom_qr_content";
    private static final int CAMERA_REQUEST=101,GREEN=0xff2c5846,PALE=0xffedf3e9;
    private FocusMark focusMark; private long focusUiGeneration;
    private BarcodeView camera; private TextView status,zoomValue; private Button access;
    private LinearLayout zoomPanel; private SeekBar zoomSlider;
    private final CameraZoom zoom=new CameraZoom(); private double preferredZoom=1;
    private final Handler ui=new Handler(Looper.getMainLooper()); private boolean zoomScheduled;
    private volatile long cameraEpoch;
    private boolean resumed,requested; private final AtomicBoolean finished=new AtomicBoolean();
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private TextView text(String value,int size) { TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(GREEN); view.setIncludeFontPadding(false); return view; }
    private GradientDrawable background(int color,int radius) { GradientDrawable value=new GradientDrawable(); value.setColor(color); value.setCornerRadius(dp(radius)); return value; }
    private TextView control(String label,String description,Runnable action) {
        TextView view=text(label,13); view.setGravity(Gravity.CENTER); view.setTypeface(null,Typeface.BOLD); view.setFocusable(true); view.setContentDescription(description); view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x182c5846),background(PALE,12),background(0xffffffff,12))); view.setOnClickListener(v->action.run()); return view;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(state!=null) { requested=state.getBoolean("camera_requested",false); double saved=state.getDouble("zoom_factor",1); if(Double.isFinite(saved)&&saved>=1) preferredZoom=saved; }
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(0xfff7f9f5); root.setPadding(dp(20),dp(16),dp(20),dp(16));
        root.setOnApplyWindowInsetsListener((view,insets)-> { root.setPadding(dp(20)+insets.getSystemWindowInsetLeft(),dp(16)+insets.getSystemWindowInsetTop(),dp(20)+insets.getSystemWindowInsetRight(),dp(16)+insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout toolbar=new LinearLayout(this); toolbar.setGravity(Gravity.CENTER_VERTICAL); TextView title=text("扫码入课",21); title.setTypeface(null,Typeface.BOLD); title.setGravity(Gravity.CENTER_VERTICAL); toolbar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1)); TextView close=control("关闭","关闭扫码",this::closeScanner); toolbar.addView(close,new LinearLayout.LayoutParams(dp(72),dp(44))); root.addView(toolbar);
        TextView guide=text("将课堂二维码置于取景区域内",13); guide.setPadding(0,dp(10),0,dp(16)); root.addView(guide);
        camera=new BarcodeView(this) { @Override protected CameraInstance createCameraInstance() { FocusCameraInstance instance=new FocusCameraInstance(getContext()); instance.setCameraSettings(getCameraSettings()); return instance; } };
        CameraSettings settings=camera.getCameraSettings(); settings.setAutoFocusEnabled(false); settings.setFocusMode(CameraSettings.FocusMode.CONTINUOUS);
        camera.setDecoderFactory(new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.QR_CODE))); camera.setContentDescription("课堂二维码相机取景区域，可双指缩放、点击对焦");
        FrameLayout preview=new FrameLayout(this); preview.addView(camera,new FrameLayout.LayoutParams(-1,-1)); focusMark=new FocusMark(); preview.addView(focusMark,new FrameLayout.LayoutParams(-1,-1)); root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        ScaleGestureDetector gestures=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) { zoom.beginGesture(); return resumed&&zoom.supported()&&!finished.get(); }
            @Override public boolean onScale(ScaleGestureDetector detector) { if(!resumed||finished.get()||!zoom.supported()) return false; zoom.scale(detector.getScaleFactor()); updateZoom(); scheduleZoom(); return true; }
        });
        GestureDetector taps=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onSingleTapUp(MotionEvent event) { camera.performClick(); tapFocus(event.getX(),event.getY()); return true; }
        });
        camera.setOnTouchListener((view,event)-> { gestures.onTouchEvent(event); taps.onTouchEvent(event); return true; });
        zoomPanel=new LinearLayout(this); zoomPanel.setOrientation(LinearLayout.VERTICAL); zoomPanel.setPadding(0,dp(10),0,0); zoomPanel.setVisibility(View.GONE);
        LinearLayout tools=new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL); tools.addView(control("−","缩小相机画面",()->nudgeZoom(-1)),new LinearLayout.LayoutParams(dp(44),dp(44)));
        zoomSlider=new SeekBar(this); zoomSlider.setContentDescription("相机变焦滑杆"); zoomSlider.setProgressTintList(ColorStateList.valueOf(GREEN)); zoomSlider.setThumbTintList(ColorStateList.valueOf(GREEN)); LinearLayout.LayoutParams sliderParams=new LinearLayout.LayoutParams(0,dp(44),1); sliderParams.leftMargin=dp(6); sliderParams.rightMargin=dp(6); tools.addView(zoomSlider,sliderParams);
        tools.addView(control("+","放大相机画面",()->nudgeZoom(1)),new LinearLayout.LayoutParams(dp(44),dp(44))); zoomValue=control("1.0×","恢复默认倍率",()-> { zoom.reset(); updateZoom(); scheduleZoom(); }); LinearLayout.LayoutParams resetParams=new LinearLayout.LayoutParams(dp(60),dp(44)); resetParams.leftMargin=dp(8); tools.addView(zoomValue,resetParams); zoomPanel.addView(tools);
        TextView zoomHint=text("双指或滑杆缩放，点击倍率复位",11); zoomHint.setGravity(Gravity.CENTER); zoomHint.setPadding(0,dp(6),0,0); zoomPanel.addView(zoomHint); root.addView(zoomPanel);
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onStartTrackingTouch(SeekBar bar) {} public void onStopTrackingTouch(SeekBar bar) {} public void onProgressChanged(SeekBar bar,int value,boolean fromUser) { if(fromUser&&zoom.supported()&&resumed&&!finished.get()) { zoom.setIndex(value); updateZoom(); scheduleZoom(); } } });
        status=text("正在准备相机…",12); status.setGravity(Gravity.CENTER); status.setPadding(0,dp(16),0,dp(12)); root.addView(status); access=new FlatButton(this); access.setText("允许使用相机"); access.setAllCaps(false); access.setTextSize(13); access.setTextColor(0xffffffff); access.setBackground(background(GREEN,12)); access.setOnClickListener(v-> {
            if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) { startCamera(); return; }
            if(requested&&!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); else requestCamera();
        }); root.addView(access,new LinearLayout.LayoutParams(-1,dp(48))); setContentView(root);
        camera.addStateListener(new CameraPreview.StateListener() {
            public void previewSized() {} public void previewStarted() { status.setText("点击画面对焦，识别二维码后自动返回"); if(camera.getCameraInstance() instanceof FocusCameraInstance) ((FocusCameraInstance)camera.getCameraInstance()).startFocus(); loadZoomCapabilities(); } public void previewStopped() { stopZoom(); } public void cameraClosed() {}
            public void cameraError(Exception error) { stopZoom(); status.setText("相机暂不可用，请检查权限或相机占用后重试"); access.setText("重试打开相机"); access.setVisibility(View.VISIBLE); }
        });
    }
    private void tapFocus(float x,float y) {
        if(!resumed||finished.get()||!camera.isPreviewActive()||!(camera.getCameraInstance() instanceof FocusCameraInstance)) return;
        Size size=camera.getPreviewSize(); if(size==null) return;
        Rect preview=camera.getPreviewScalingStrategy().scalePreview(size,new Size(camera.getWidth(),camera.getHeight()));
        FocusCameraInstance instance=(FocusCameraInstance)camera.getCameraInstance();
        Rect area=CameraFocus.area(x,y,preview,instance.getCameraRotation(),instance.mirrored()); if(area==null) return;
        long epoch=cameraEpoch,id=++focusUiGeneration;
        focusMark.show(x,y,0); instance.focus(area,state-> {
            if(!activeCamera(epoch,instance)||id!=focusUiGeneration) return;
            if(state==3) { focusMark.setVisibility(View.GONE); status.setText("当前相机不支持点击对焦，继续自动识别"); }
            else focusMark.show(x,y,state);
        });
        ui.postDelayed(()-> { if(id==focusUiGeneration) focusMark.setVisibility(View.GONE); },1800);
    }
    private final class FocusMark extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG); private float x,y; private int state;
        FocusMark() { super(QrScanActivity.this); setVisibility(View.GONE); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); setClickable(false); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); }
        void show(float px,float py,int value) { float half=dp(32)+dp(2); x=Math.max(half,Math.min(getWidth()-half,px)); y=Math.max(half,Math.min(getHeight()-half,py)); state=value; setVisibility(View.VISIBLE); invalidate(); }
        @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); paint.setColor(state==1?0xffa8d5a0:state==2?0xffffcc80:0xffffffff); canvas.drawRoundRect(x-dp(30),y-dp(30),x+dp(30),y+dp(30),dp(10),dp(10),paint); canvas.drawLine(x-dp(5),y,x+dp(5),y,paint); canvas.drawLine(x,y-dp(5),x,y+dp(5),paint); }
    }
    private void requestCamera() { requested=true; requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST); }
    private void startCamera() {
        if(!resumed||isFinishing()||finished.get()) return;
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) { status.setText("此设备没有可用相机，可返回使用课堂 ID 入课"); access.setVisibility(View.GONE); return; }
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) { status.setText("需要相机权限才能扫描课堂二维码"); access.setText("允许使用相机"); access.setVisibility(View.VISIBLE); return; }
        if(camera.getCameraInstance()!=null) return;
        cameraEpoch++; access.setVisibility(View.GONE); camera.decodeSingle(new BarcodeCallback() { @Override public void barcodeResult(BarcodeResult result) { deliver(result==null?null:result.getText()); } @Override public void possibleResultPoints(List<com.google.zxing.ResultPoint> points) {} }); camera.resume();
    }
    private boolean activeCamera(long epoch,CameraInstance instance) { return resumed&&!finished.get()&&!isFinishing()&&epoch==cameraEpoch&&instance==camera.getCameraInstance(); }
    private void loadZoomCapabilities() {
        CameraInstance instance=camera.getCameraInstance(); if(instance==null) return; long epoch=cameraEpoch;
        instance.changeCameraParameters(parameters-> {
            try { boolean supported=parameters.isZoomSupported(); List<Integer> ratios=supported&&parameters.getZoomRatios()!=null?new ArrayList<>(parameters.getZoomRatios()):null; int maximum=supported?parameters.getMaxZoom():0,current=supported?parameters.getZoom():0; ui.post(()-> { if(activeCamera(epoch,instance)) configureZoom(supported,ratios,maximum,current); }); }
            catch(RuntimeException ignored) { ui.post(()-> { if(activeCamera(epoch,instance)) configureZoom(false,null,0,0); }); }
            return parameters;
        });
    }
    void configureZoom(boolean supported,List<Integer> ratios,int maximum,int current) {
        zoom.configure(supported,ratios,maximum,current); zoomPanel.setVisibility(zoom.supported()?View.VISIBLE:View.GONE);
        if(zoom.supported()) { zoom.setFactor(preferredZoom); updateZoom(); scheduleZoom(); } else status.setText("对准课堂二维码，当前相机不支持变焦");
    }
    private void nudgeZoom(int direction) { if(!resumed||finished.get()||!zoom.supported()) return; zoom.nudge(direction); updateZoom(); scheduleZoom(); }
    private void updateZoom() { preferredZoom=zoom.factor(); zoomSlider.setMax(zoom.maximum()); zoomSlider.setProgress(zoom.index()); zoomValue.setText(zoom.label()); zoomValue.setContentDescription("当前 "+zoom.label()+"，点击恢复默认倍率"); }
    private void scheduleZoom() { if(!resumed||finished.get()||!zoom.supported()||zoomScheduled) return; zoomScheduled=true; ui.postDelayed(applyZoom,32); }
    private final Runnable applyZoom=()-> {
        zoomScheduled=false; CameraInstance instance=camera.getCameraInstance(); if(instance==null||!resumed||finished.get()||!zoom.supported()) return; long epoch=cameraEpoch; int index=zoom.index();
        instance.changeCameraParameters(parameters-> { if(epoch!=cameraEpoch) return parameters; try { if(parameters.isZoomSupported()) parameters.setZoom(Math.max(0,Math.min(index,parameters.getMaxZoom()))); } catch(RuntimeException ignored) { ui.post(()-> { if(activeCamera(epoch,instance)) configureZoom(false,null,0,0); }); } return parameters; });
    };
    private void stopZoom() { focusUiGeneration++; if(focusMark!=null) focusMark.setVisibility(View.GONE); if(camera!=null&&camera.getCameraInstance() instanceof FocusCameraInstance) ((FocusCameraInstance)camera.getCameraInstance()).stopFocus(); cameraEpoch++; ui.removeCallbacks(applyZoom); zoomScheduled=false; if(zoomPanel!=null) zoomPanel.setVisibility(View.GONE); }
    private void closeScanner() { if(!finished.compareAndSet(false,true)) return; stopZoom(); if(camera!=null) camera.pause(); setResult(RESULT_CANCELED); finish(); }
    void deliver(String value) {
        if(value==null||value.isEmpty()||isFinishing()||!resumed||!finished.compareAndSet(false,true)) return;
        stopZoom(); camera.pause(); setResult(RESULT_OK,new Intent().putExtra(CONTENT,value)); finish();
    }
    @Override protected void onResume() { super.onResume(); resumed=true; if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera(); else if(!requested) requestCamera(); else startCamera(); }
    @Override protected void onPause() { resumed=false; stopZoom(); if(camera!=null) camera.pause(); super.onPause(); }
    @Override protected void onDestroy() { stopZoom(); ui.removeCallbacksAndMessages(null); super.onDestroy(); }
    @Override public void onBackPressed() { closeScanner(); }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) { super.onRequestPermissionsResult(request,permissions,results); if(request==CAMERA_REQUEST) { if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED) startCamera(); else { status.setText("未获得相机权限，可授权后重试或返回使用课堂 ID"); access.setText("允许使用相机"); access.setVisibility(View.VISIBLE); } } }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("camera_requested",requested); state.putDouble("zoom_factor",preferredZoom); super.onSaveInstanceState(state); }
}
