package com.ykt.assistant;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class CameraZoomTest {
    @Test public void nonlinearDriverRatiosUseNearestIndexAndClampAtDeviceLimits() {
        CameraZoom zoom=new CameraZoom(); zoom.configure(true,Arrays.asList(100,115,140,220,300),4,0); assertTrue(zoom.supported()); zoom.setFactor(2.1); assertEquals(3,zoom.index()); assertEquals("2.2×",zoom.label()); zoom.setIndex(99); assertEquals(3,zoom.factor(),0); zoom.setIndex(-8); assertEquals(1,zoom.factor(),0); zoom.setFactor(100); assertEquals(4,zoom.index()); zoom.reset(); assertEquals(0,zoom.index());
    }
    @Test public void repeatedSmallPinchesAccumulateAcrossCoarseZoomSteps() {
        CameraZoom zoom=new CameraZoom(); zoom.configure(true,Arrays.asList(100,150,200),2,0); zoom.beginGesture(); for(int n=0;n<10;n++) zoom.scale(1.03); assertEquals(1.5,zoom.factor(),0); zoom.beginGesture(); for(int n=0;n<20;n++) zoom.scale(.95); assertEquals(1,zoom.factor(),0); zoom.scale(100); assertEquals(2,zoom.factor(),0);
    }
    @Test public void plusAndMinusAlwaysAdvanceAtLeastOneSupportedStep() {
        CameraZoom zoom=new CameraZoom(); zoom.configure(true,Arrays.asList(100,200,400),2,0); zoom.nudge(1); assertEquals(2,zoom.factor(),0); zoom.nudge(-1); assertEquals(1,zoom.factor(),0); zoom.nudge(-1); assertEquals(1,zoom.factor(),0); zoom.setIndex(2); zoom.nudge(1); assertEquals(4,zoom.factor(),0);
    }
    @Test public void unsupportedOrInvalidDriverCapabilitiesDoNotInventZoomValues() {
        CameraZoom zoom=new CameraZoom(); zoom.configure(false,Arrays.asList(100,200),1,0); assertFalse(zoom.supported()); zoom.configure(true,null,1,0); assertFalse(zoom.supported()); zoom.configure(true,Arrays.asList(100,200),5,0); assertFalse(zoom.supported()); zoom.configure(true,Arrays.asList(100,90,200),2,0); assertFalse(zoom.supported()); zoom.configure(true,Arrays.asList(100,100),1,0); assertFalse(zoom.supported()); assertEquals(0,zoom.maximum()); assertEquals("1.0×",zoom.label());
    }
    @Test public void invalidGestureValuesAreIgnoredAndRestoredFactorRespectsNewDeviceRange() {
        CameraZoom zoom=new CameraZoom(); zoom.configure(true,Arrays.asList(100,150,200),2,1); for(double value:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY}) { zoom.scale(value); zoom.setFactor(value); } assertEquals(1.5,zoom.factor(),0); zoom.setFactor(4); assertEquals(2,zoom.factor(),0);
    }
}
