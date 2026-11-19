package com.ykt.assistant;

import android.graphics.Rect;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class CameraFocusTest {
    @Test public void cropAndCameraRotationMapToSensorArea() {
        Rect crop=new Rect(-100,0,500,600);
        assertEquals(new Rect(-120,-120,120,120),CameraFocus.area(200,300,crop,90,false));
        assertEquals(new Rect(-620,380,-380,620),CameraFocus.area(50,150,crop,90,false));
        assertEquals(new Rect(380,-620,620,-380),CameraFocus.area(50,150,crop,270,false));
        assertEquals(new Rect(380,380,620,620),CameraFocus.area(50,150,crop,180,false));
    }
    @Test public void frontMirrorAndEdgeBoundsAreRespected() {
        Rect full=new Rect(0,0,400,400);
        assertEquals(CameraFocus.area(100,200,full,0,false),CameraFocus.area(300,200,full,0,true));
        assertEquals(new Rect(380,380,620,620),CameraFocus.area(100,100,full,90,true));
        Rect edge=CameraFocus.area(0,0,full,0,false); assertEquals(new Rect(-1000,-1000,-760,-760),edge);
        assertEquals(new Rect(760,760,1000,1000),CameraFocus.area(399,399,full,0,false));
        assertNull(CameraFocus.area(20,20,new Rect(50,50,350,350),0,false));
        assertNull(CameraFocus.area(Float.NaN,20,full,0,false)); assertNull(CameraFocus.area(20,20,new Rect(),0,false));
    }
    @Test public void continuousPicturePreferredAndUnsupportedModesAreNotInvented() {
        assertEquals("continuous-picture",CameraFocus.automaticMode(Arrays.asList("auto","continuous-video","continuous-picture")));
        assertEquals("continuous-video",CameraFocus.automaticMode(Arrays.asList("auto","continuous-video")));
        assertEquals("auto",CameraFocus.automaticMode(Arrays.asList("fixed","auto")));
        assertNull(CameraFocus.automaticMode(Arrays.asList("fixed","infinity"))); assertNull(CameraFocus.automaticMode(null));
    }
}
