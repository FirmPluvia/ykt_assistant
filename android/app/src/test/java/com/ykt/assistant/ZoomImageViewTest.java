package com.ykt.assistant;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
@LooperMode(LooperMode.Mode.PAUSED)
public class ZoomImageViewTest {
    private ZoomImageView view;
    @Before public void create() { view=new ZoomImageView(RuntimeEnvironment.getApplication()); view.layout(0,0,400,400); view.setImageBitmap(Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)); }
    private void touch(long start,long time,int action,float... coords) {
        int count=coords.length/2; MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count]; MotionEvent.PointerCoords[] points=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++) { properties[i]=new MotionEvent.PointerProperties(); properties[i].id=i; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER; points[i]=new MotionEvent.PointerCoords(); points[i].x=coords[i*2]; points[i].y=coords[i*2+1]; points[i].pressure=1; points[i].size=1; }
        MotionEvent event=MotionEvent.obtain(start,time,action,count,properties,points,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0); view.onTouchEvent(event); event.recycle();
    }
    @Test public void realPinchEventsZoomAndPanningStaysWithinImageBounds() {
        long t=SystemClock.uptimeMillis(); touch(t,t,MotionEvent.ACTION_DOWN,100,200); touch(t,t+10,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),100,200,300,200); touch(t,t+30,MotionEvent.ACTION_MOVE,60,200,340,200); touch(t,t+60,MotionEvent.ACTION_MOVE,0,200,400,200); assertTrue(view.zoomLevel()>1);
        touch(t,t+70,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),0,200,400,200); touch(t,t+90,MotionEvent.ACTION_MOVE,2000,2000); touch(t,t+100,MotionEvent.ACTION_UP,2000,2000);
        RectF image=new RectF(0,0,view.getDrawable().getIntrinsicWidth(),view.getDrawable().getIntrinsicHeight()); view.getImageMatrix().mapRect(image); assertTrue(image.left<=.01f); assertTrue(image.top<=.01f); assertTrue(image.right>=399.99f); assertTrue(image.bottom>=399.99f);
    }
    @Test public void sameBitmapKeepsZoomWhileNewPageResetsAndScaleIsBounded() {
        Bitmap bitmap=Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888); view.setImageBitmap(bitmap); view.zoomAt(3,200,200); view.setImageBitmap(bitmap); assertEquals(3,view.zoomLevel(),.001f); view.layout(0,0,600,300); assertEquals(3,view.zoomLevel(),.001f); view.setImageBitmap(Bitmap.createBitmap(400,400,Bitmap.Config.ARGB_8888)); assertEquals(1,view.zoomLevel(),.001f); view.zoomAt(100,200,200); assertEquals(6,view.zoomLevel(),.001f); view.zoomAt(0,200,200); assertEquals(1,view.zoomLevel(),.001f);
    }
    @Test public void holdingImageTriggersLongPressAction() {
        AtomicInteger clicks=new AtomicInteger(); view.setOnLongClickListener(v -> { clicks.incrementAndGet(); return true; }); long t=SystemClock.uptimeMillis(); touch(t,t,MotionEvent.ACTION_DOWN,200,200); ShadowLooper.idleMainLooper(1000,java.util.concurrent.TimeUnit.MILLISECONDS); touch(t,t+1000,MotionEvent.ACTION_UP,200,200); assertEquals(1,clicks.get());
    }    @Test public void horizontalSwipesTurnPagesButVerticalAndZoomedDragsDoNot() {
        java.util.List<Integer> pages=new java.util.ArrayList<>(); view.setOnPageSwipeListener(pages::add); long t=SystemClock.uptimeMillis();
        touch(t,t,MotionEvent.ACTION_DOWN,320,200); touch(t,t+40,MotionEvent.ACTION_MOVE,80,205); touch(t,t+80,MotionEvent.ACTION_UP,80,205); assertEquals(java.util.Arrays.asList(1),pages);
        t+=1000; touch(t,t,MotionEvent.ACTION_DOWN,80,200); touch(t,t+40,MotionEvent.ACTION_MOVE,320,200); touch(t,t+80,MotionEvent.ACTION_UP,320,200); assertEquals(java.util.Arrays.asList(1,-1),pages);
        t+=1000; touch(t,t,MotionEvent.ACTION_DOWN,200,80); touch(t,t+40,MotionEvent.ACTION_MOVE,205,320); touch(t,t+80,MotionEvent.ACTION_UP,205,320); assertEquals(2,pages.size());
        view.zoomAt(3,200,200); t+=1000; touch(t,t,MotionEvent.ACTION_DOWN,320,200); touch(t,t+40,MotionEvent.ACTION_MOVE,80,200); touch(t,t+80,MotionEvent.ACTION_UP,80,200); assertEquals(2,pages.size()); assertEquals(3,view.zoomLevel(),.001f);
    }
    @Test public void pinchThenRemainingFingerSwipeDoesNotAccidentallyTurnPage() {
        java.util.concurrent.atomic.AtomicInteger pages=new java.util.concurrent.atomic.AtomicInteger(); view.setOnPageSwipeListener(p -> pages.incrementAndGet()); long t=SystemClock.uptimeMillis(); touch(t,t,MotionEvent.ACTION_DOWN,100,200); touch(t,t+10,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),100,200,300,200); touch(t,t+30,MotionEvent.ACTION_MOVE,0,200,400,200); touch(t,t+50,MotionEvent.ACTION_MOVE,130,200,270,200); touch(t,t+70,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),130,200,270,200); view.zoomAt(1,200,200); touch(t,t+100,MotionEvent.ACTION_MOVE,-250,200); touch(t,t+120,MotionEvent.ACTION_UP,-250,200); assertEquals(0,pages.get());
    }

}
