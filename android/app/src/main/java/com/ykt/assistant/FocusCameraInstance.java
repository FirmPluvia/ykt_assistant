package com.ykt.assistant;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import com.journeyapps.barcodescanner.camera.CameraInstance;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** All hardware access is serialized by CameraInstance's camera thread. UI timers never touch Camera. */
@SuppressWarnings("deprecation")
class FocusCameraInstance extends CameraInstance {
    interface Feedback { void state(int state); } // 0 focusing, 1 focused, 2 retry, 3 unsupported
    private final Handler ui=new Handler(Looper.getMainLooper());
    private volatile long generation;
    private volatile boolean running;
    private boolean tapping;
    private String automaticMode;
    private final AtomicLong request=new AtomicLong();
    private List<Camera.Area> baselineFocus,baselineMeter;
    FocusCameraInstance(Context context) { super(context); }
    void startFocus() {
        ui.removeCallbacks(periodic); running=true; long token=++generation;
        changeCameraParameters(p-> {
            if(!valid(token)) return p;
            automaticMode=CameraFocus.automaticMode(p.getSupportedFocusModes());
            baselineFocus=p.getMaxNumFocusAreas()>0?p.getFocusAreas():null; baselineMeter=p.getMaxNumMeteringAreas()>0?p.getMeteringAreas():null;
            try { if(automaticMode!=null) p.setFocusMode(automaticMode); } catch(RuntimeException ignored) {}
            if("auto".equals(automaticMode)) ui.post(()-> { if(valid(token)) ui.postDelayed(periodic,2200); });
            return p;
        });
    }
    private boolean valid(long token) { return running&&token==generation&&isOpen(); }
    private final Runnable periodic=new Runnable() { @Override public void run() {
        if(!running) return; long token=generation;
        changeCameraParameters(p-> {
            if(valid(token)&&!tapping&&"auto".equals(automaticMode)) {
                Camera raw=getCameraManager().getCamera();
                new Handler(Looper.myLooper()).post(()-> { if(valid(token)&&!tapping) try { raw.autoFocus((success,c)->{}); } catch(RuntimeException ignored) {} });
            }
            return p;
        });
        ui.postDelayed(this,2200);
    }};
    void focus(Rect area,Feedback feedback) {
        if(!running||area==null) return; long token=generation,id=request.incrementAndGet();
        changeCameraParameters(p-> {
            if(!valid(token)||id!=request.get()) return p;
            Camera raw=getCameraManager().getCamera();
            List<String> modes=p.getSupportedFocusModes();
            if(modes==null||!modes.contains(Camera.Parameters.FOCUS_MODE_AUTO)||p.getMaxNumFocusAreas()==0) { report(token,id,feedback,3); return p; }
            String previous=automaticMode!=null?automaticMode:p.getFocusMode(); List<Camera.Area> oldFocus=baselineFocus,oldMeter=baselineMeter;
            tapping=true;
            try {
                raw.cancelAutoFocus(); p.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                if(p.getMaxNumFocusAreas()>0) p.setFocusAreas(Collections.singletonList(new Camera.Area(area,1000)));
                if(p.getMaxNumMeteringAreas()>0) p.setMeteringAreas(Collections.singletonList(new Camera.Area(area,1000)));
                raw.setParameters(p);
                report(token,id,feedback,0);
                new Handler(Looper.myLooper()).post(()-> {
                    if(!valid(token)||id!=request.get()) return;
                    try { raw.autoFocus((success,c)->report(token,id,feedback,success?1:2)); }
                    catch(RuntimeException ignored) { report(token,id,feedback,2); }
                });
            } catch(RuntimeException ignored) { report(token,id,feedback,2); }
            // Also restore after timeout if the driver never calls AutoFocusCallback.
            ui.post(()-> { if(valid(token)&&id==request.get()) ui.postDelayed(()->restore(token,id,previous,oldFocus,oldMeter),1800); });
            // CameraManager applies the returned parameters once more. Use the actual driver state.
            try { return raw.getParameters(); } catch(RuntimeException ignored) { return p; }
        });
    }
    private void restore(long token,long id,String mode,List<Camera.Area> focus,List<Camera.Area> meter) {
        if(!valid(token)||id!=request.get()) return;
        changeCameraParameters(p-> {
            if(!valid(token)||id!=request.get()) return p;
            try {
                getCameraManager().getCamera().cancelAutoFocus();
                if(mode!=null) p.setFocusMode(mode);
                if(p.getMaxNumFocusAreas()>0) p.setFocusAreas(focus);
                if(p.getMaxNumMeteringAreas()>0) p.setMeteringAreas(meter);
            } catch(RuntimeException ignored) {} finally { tapping=false; request.compareAndSet(id,id+1); }
            return p;
        });
    }
    private void report(long token,long id,Feedback feedback,int state) { ui.post(()-> { if(valid(token)&&id==request.get()) feedback.state(state); }); }
    void stopFocus() { running=false; generation++; request.incrementAndGet(); ui.removeCallbacksAndMessages(null); }
    @Override public void close() { stopFocus(); super.close(); }
    boolean mirrored() {
        int id=getCameraSettings().getRequestedCameraId(); Camera.CameraInfo info=new Camera.CameraInfo();
        try {
            if(id<0) { id=0; for(int i=0;i<Camera.getNumberOfCameras();i++) { Camera.getCameraInfo(i,info); if(info.facing==Camera.CameraInfo.CAMERA_FACING_BACK) { id=i; break; } } }
            Camera.getCameraInfo(id,info); return info.facing==Camera.CameraInfo.CAMERA_FACING_FRONT;
        } catch(RuntimeException ignored) { return false; }
    }
}
