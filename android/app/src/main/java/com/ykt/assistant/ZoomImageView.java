package com.ykt.assistant;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

/** Fit-centered slides with focal-point pinch zoom, bounded panning and long-press actions. */
final class ZoomImageView extends ImageView {
    private Matrix transform=new Matrix();
    private float fit=1,zoom=1,x,y,lastX,lastY;
    private int pointer=-1;
    private boolean multi,swipeAllowed;
    private float downX,downY;
    private java.util.function.IntConsumer pageSwipe;
    void setOnPageSwipeListener(java.util.function.IntConsumer listener) { pageSwipe=listener; }
    private Bitmap bitmap;
    private final ScaleGestureDetector pinch;
    private final GestureDetector gestures;
    ZoomImageView(Context context) {
        super(context); setScaleType(ScaleType.MATRIX);
        pinch=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) { zoomAt(zoom*detector.getScaleFactor(),detector.getFocusX(),detector.getFocusY()); return true; }
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { performClick(); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { swipeAllowed=false; zoomAt(zoom>1.01f?1:2.5f,e.getX(),e.getY()); return true; }
            @Override public void onLongPress(MotionEvent e) { swipeAllowed=false; if(!multi&&getDrawable()!=null) performLongClick(); }
        });
        setContentDescription("课件页面，左右滑动翻页，放大后可拖动；支持双指缩放、双击还原，长按保存图片");
    }
    @Override public void setImageBitmap(Bitmap value) { if(value==bitmap&&getDrawable()!=null) return; super.setImageBitmap(value); bitmap=value; reset(); }
    @Override public void setImageDrawable(Drawable value) { super.setImageDrawable(value); bitmap=value instanceof BitmapDrawable?((BitmapDrawable)value).getBitmap():null; reset(); }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        if(transform!=null&&oldw>0&&oldh>0&&zoom>1&&imageWidth()>0&&imageHeight()>0) {
            float centerX=(oldw/2f-x)/(fit*zoom),centerY=(oldh/2f-y)/(fit*zoom);
            fit=Math.min(w/imageWidth(),h/imageHeight()); x=w/2f-centerX*fit*zoom; y=h/2f-centerY*fit*zoom; apply();
        } else reset();
    }
    private float imageWidth() { return getDrawable()==null?0:getDrawable().getIntrinsicWidth(); }
    private float imageHeight() { return getDrawable()==null?0:getDrawable().getIntrinsicHeight(); }
    private void reset() {
        if(transform==null||getWidth()==0||getHeight()==0||imageWidth()<=0||imageHeight()<=0) return;
        fit=Math.min(getWidth()/imageWidth(),getHeight()/imageHeight()); zoom=1; x=(getWidth()-imageWidth()*fit)/2; y=(getHeight()-imageHeight()*fit)/2; apply();
    }
    void zoomAt(float value,float focusX,float focusY) {
        if(imageWidth()<=0||imageHeight()<=0||!Float.isFinite(value)) return;
        float next=Math.max(1,Math.min(6,value)),ratio=next/zoom; x=focusX-(focusX-x)*ratio; y=focusY-(focusY-y)*ratio; zoom=next; apply();
    }
    float zoomLevel() { return zoom; }
    private void apply() {
        float w=imageWidth()*fit*zoom,h=imageHeight()*fit*zoom;
        x=w<=getWidth()?(getWidth()-w)/2:Math.max(getWidth()-w,Math.min(0,x)); y=h<=getHeight()?(getHeight()-h)/2:Math.max(getHeight()-h,Math.min(0,y));
        transform.setScale(fit*zoom,fit*zoom); transform.postTranslate(x,y); setImageMatrix(transform);
    }
    // GestureDetector.onSingleTapConfirmed invokes performClick; the lint checker cannot follow that callback.
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN) { multi=false; swipeAllowed=zoom<=1.01f&&getDrawable()!=null; downX=e.getX(); downY=e.getY(); pointer=e.getPointerId(0); lastX=e.getX(); lastY=e.getY(); }
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN) { multi=true; swipeAllowed=false; MotionEvent cancel=MotionEvent.obtain(e); cancel.setAction(MotionEvent.ACTION_CANCEL); gestures.onTouchEvent(cancel); cancel.recycle(); }
        if(e.getActionMasked()==MotionEvent.ACTION_CANCEL) swipeAllowed=false;
        pinch.onTouchEvent(e); if(!multi) gestures.onTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&e.getPointerCount()==1&&!pinch.isInProgress()) {
            int index=e.findPointerIndex(pointer); if(index>=0) { float nx=e.getX(index),ny=e.getY(index); if(zoom>1) { x+=nx-lastX; y+=ny-lastY; apply(); } lastX=nx; lastY=ny; }
        }
        if(e.getActionMasked()==MotionEvent.ACTION_POINTER_UP) { int index=e.getActionIndex()==0?1:0; if(index<e.getPointerCount()) { pointer=e.getPointerId(index); lastX=e.getX(index); lastY=e.getY(index); } }
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL) { pointer=-1; if(getParent()!=null) getParent().requestDisallowInterceptTouchEvent(false); }
        else if(getParent()!=null) getParent().requestDisallowInterceptTouchEvent(true);
        if(e.getActionMasked()==MotionEvent.ACTION_UP&&swipeAllowed&&!multi&&zoom<=1.01f&&pageSwipe!=null) {
            float dx=e.getX()-downX,dy=e.getY()-downY; float threshold=Math.max(48*getResources().getDisplayMetrics().density,getWidth()*.15f);
            if(Math.abs(dx)>=threshold&&Math.abs(dx)>Math.abs(dy)*1.4f) { swipeAllowed=false; pageSwipe.accept(dx<0?1:-1); }
        }
        return true;
    }
    @Override public boolean performClick() { return super.performClick(); }
}
