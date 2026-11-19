package com.ykt.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class QrScanActivityTest {
    private ActivityController<QrScanActivity> controller; private QrScanActivity activity;
    @Before public void setup() { Shadows.shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.CAMERA); controller=Robolectric.buildActivity(QrScanActivity.class).setup(); activity=controller.get(); }
    @After public void cleanup() { controller.pause().stop().destroy(); }
    private boolean contains(View view,String text) { if(view instanceof TextView&&((TextView)view).getText().toString().contains(text)) return true; if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) if(contains(((ViewGroup)view).getChildAt(i),text)) return true; return false; }
    private View described(View view,String description) { if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())) return view; if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { View found=described(((ViewGroup)view).getChildAt(i),description); if(found!=null) return found; } return null; }
    private View label(View view,String text) { if(view instanceof TextView&&((TextView)view).getText().toString().equals(text)) return view; if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) { View found=label(((ViewGroup)view).getChildAt(i),text); if(found!=null) return found; } return null; }
    @Test public void themedCloseCancelsAndRejectsLateDecode() {
        View close=described(activity.getWindow().getDecorView(),"关闭扫码"); assertNotNull(close); assertFalse(close instanceof android.widget.Button); assertTrue(close.getLayoutParams().height>=Math.round(44*activity.getResources().getDisplayMetrics().density)); assertTrue(close.performClick()); activity.deliver("123456"); assertTrue(activity.isFinishing()); assertEquals(Activity.RESULT_CANCELED,Shadows.shadowOf(activity).getResultCode());
    }
    @Test public void supportedZoomControlsReflectActualRatiosAndReset() {
        activity.configureZoom(true,java.util.Arrays.asList(100,150,200,300),3,0); View root=activity.getWindow().getDecorView(); android.widget.SeekBar slider=(android.widget.SeekBar)described(root,"相机变焦滑杆"); assertEquals(View.VISIBLE,((View)slider.getParent().getParent()).getVisibility()); assertEquals(3,slider.getMax()); assertTrue(described(root,"放大相机画面").performClick()); assertNotNull(label(root,"1.5×")); assertEquals(1,slider.getProgress()); assertTrue(label(root,"1.5×").performClick()); assertEquals(0,slider.getProgress()); assertNotNull(label(root,"1.0×")); assertTrue(described(root,"缩小相机画面").performClick()); assertEquals(0,slider.getProgress());
    }
    @Test public void unsupportedCameraHidesZoomWithoutEndingScan() {
        activity.configureZoom(false,null,0,0); android.widget.SeekBar slider=(android.widget.SeekBar)described(activity.getWindow().getDecorView(),"相机变焦滑杆"); assertEquals(View.GONE,((View)slider.getParent().getParent()).getVisibility()); assertTrue(contains(activity.getWindow().getDecorView(),"不支持变焦")); assertFalse(activity.isFinishing()); assertEquals(Activity.RESULT_CANCELED,Shadows.shadowOf(activity).getResultCode());
    }
    @Test public void cameraPermissionIsRequestedFromScannerAndDenialDoesNotReturnAResult() {
        org.robolectric.shadows.ShadowActivity.PermissionsRequest request=Shadows.shadowOf(activity).getLastRequestedPermission(); assertNotNull(request); assertArrayEquals(new String[]{Manifest.permission.CAMERA},request.requestedPermissions); activity.onRequestPermissionsResult(request.requestCode,new String[]{Manifest.permission.CAMERA},new int[]{PackageManager.PERMISSION_DENIED}); assertTrue(contains(activity.getWindow().getDecorView(),"未获得相机权限")); assertEquals(Activity.RESULT_CANCELED,Shadows.shadowOf(activity).getResultCode()); assertFalse(activity.isFinishing());
    }
    @Test public void decodedPayloadIsReturnedExactlyOnceWithoutChangingItsContents() {
        String raw="https://www.yuketang.cn/api/v3/lesson/check-in/dynamic-qr-code?sig=a%2Fb%3D"; activity.deliver(raw); activity.deliver("different"); described(activity.getWindow().getDecorView(),"关闭扫码").performClick(); assertEquals(Activity.RESULT_OK,Shadows.shadowOf(activity).getResultCode()); assertEquals(raw,Shadows.shadowOf(activity).getResultIntent().getStringExtra(QrScanActivity.CONTENT)); assertTrue(activity.isFinishing());
    }
    @Test public void pausedScannerIgnoresLateDecodeAndCannotReturnAnEntryResult() {
        activity.configureZoom(true,java.util.Arrays.asList(100,150,200),2,0); described(activity.getWindow().getDecorView(),"放大相机画面").performClick(); controller.pause(); android.widget.SeekBar slider=(android.widget.SeekBar)described(activity.getWindow().getDecorView(),"相机变焦滑杆"); int before=slider.getProgress(); described(activity.getWindow().getDecorView(),"放大相机画面").performClick(); assertEquals(before,slider.getProgress()); assertEquals(View.GONE,((View)slider.getParent().getParent()).getVisibility()); activity.deliver("123456"); assertEquals(Activity.RESULT_CANCELED,Shadows.shadowOf(activity).getResultCode()); assertFalse(activity.isFinishing());
    }
}
