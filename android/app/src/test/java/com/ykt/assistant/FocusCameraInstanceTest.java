package com.ykt.assistant;

import android.graphics.Rect;
import android.hardware.Camera;
import com.journeyapps.barcodescanner.camera.CameraManager;
import com.journeyapps.barcodescanner.camera.CameraParametersCallback;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@SuppressWarnings("deprecation")
@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @LooperMode(LooperMode.Mode.PAUSED)
public class FocusCameraInstanceTest {
    private CameraManager manager; private Camera raw; private FocusCameraInstance focus;
    @Before public void setup() {
        Camera.CameraInfo info=new Camera.CameraInfo(); info.facing=Camera.CameraInfo.CAMERA_FACING_BACK; info.orientation=90; org.robolectric.shadows.ShadowCamera.addCameraInfo(0,info);
        manager=new CameraManager(RuntimeEnvironment.getApplication()); manager.open(); raw=manager.getCamera();
        Camera.Parameters p=raw.getParameters(); Shadows.shadowOf(p).setSupportedFocusModes("auto","continuous-picture"); Shadows.shadowOf(p).setMaxNumFocusAreas(1); Shadows.shadowOf(p).setMaxNumMeteringAreas(1); raw.setParameters(p);
        focus=new FocusCameraInstance(RuntimeEnvironment.getApplication()) {
            @Override protected CameraManager getCameraManager() { return manager; }
            @Override public boolean isOpen() { return true; }
            @Override public void changeCameraParameters(CameraParametersCallback callback) { raw.setParameters(callback.changeCameraParameters(raw.getParameters())); }
        }; focus.startFocus();
    }
    @After public void cleanup() { focus.stopFocus(); manager.close(); }
    private void idle() { Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); }
    @Test public void tapTargetsRequestedAreaAndRestoresContinuousFocusAfterTimeout() {
        List<Integer> states=new ArrayList<>(); Rect area=new Rect(200,-400,440,-160); focus.focus(area,states::add); idle();
        assertEquals("auto",raw.getParameters().getFocusMode()); assertEquals(area,raw.getParameters().getFocusAreas().get(0).rect); assertTrue(Shadows.shadowOf(raw).hasRequestedAutoFocus());
        Shadows.shadowOf(raw).invokeAutoFocusCallback(true,raw); idle(); assertEquals(java.util.Arrays.asList(0,1),states);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(1800));
        assertEquals("continuous-picture",raw.getParameters().getFocusMode()); assertNull(raw.getParameters().getFocusAreas()); assertNull(raw.getParameters().getMeteringAreas());
    }
    @Test public void successiveTapsRestoreOriginalModeAndOldTimeoutCannotInterruptNewTap() {
        focus.focus(new Rect(-120,-120,120,120),s->{}); idle(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(900));
        Rect latest=new Rect(300,300,540,540); focus.focus(latest,s->{}); idle(); Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(900));
        assertEquals("auto",raw.getParameters().getFocusMode()); assertEquals(latest,raw.getParameters().getFocusAreas().get(0).rect);
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(900)); assertEquals("continuous-picture",raw.getParameters().getFocusMode()); assertNull(raw.getParameters().getFocusAreas());
    }
    @Test public void stopDropsLateFocusResultsAndRestorationTasks() {
        List<Integer> states=new ArrayList<>(); focus.focus(new Rect(-120,-120,120,120),states::add); idle(); focus.stopFocus();
        Shadows.shadowOf(raw).invokeAutoFocusCallback(true,raw); Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        assertEquals(java.util.Arrays.asList(0),states); assertEquals("auto",raw.getParameters().getFocusMode());
    }
    @Test public void fixedLensSkipsTapAndDoesNotReportFalseSuccess() {
        Camera.Parameters p=raw.getParameters(); Shadows.shadowOf(p).setSupportedFocusModes("fixed"); p.setFocusMode("fixed"); raw.setParameters(p);
        List<Integer> states=new ArrayList<>(); focus.focus(new Rect(-120,-120,120,120),states::add); idle(); assertEquals(java.util.Arrays.asList(3),states); assertEquals("fixed",raw.getParameters().getFocusMode()); assertFalse(Shadows.shadowOf(raw).hasRequestedAutoFocus());
    }
    @Test public void autoOnlyLensKeepsPeriodicAutoFocus() {
        focus.stopFocus(); Camera.Parameters p=raw.getParameters(); Shadows.shadowOf(p).setSupportedFocusModes("auto"); raw.setParameters(p); focus.startFocus();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(2200)); assertTrue(Shadows.shadowOf(raw).hasRequestedAutoFocus());
    }
}
