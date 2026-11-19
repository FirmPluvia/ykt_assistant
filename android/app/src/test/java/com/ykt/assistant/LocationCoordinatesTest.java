package com.ykt.assistant;
import org.junit.Test;
import static org.junit.Assert.*;
public class LocationCoordinatesTest {
    @Test public void validatesSignedBoundsAndRejectsNonFiniteAndEmpty() {
        LocationCheckin.Point p=LocationCoordinates.parse(" -90 ","180"); assertEquals(-90,p.lat,0); assertEquals(180,p.lng,0);
        for(String[] pair:new String[][]{{"","1"},{"NaN","1"},{"Infinity","1"},{"91","1"},{"1","-181"},{"true","1"}}) try {LocationCoordinates.parse(pair[0],pair[1]); fail();} catch(IllegalArgumentException expected) {}
    }
    @Test public void randomPointsStayWithinFiftyMetresAtEquatorPolesAndDateLine() {
        for(LocationCheckin.Point center:new LocationCheckin.Point[]{new LocationCheckin.Point(31.25,121.5),new LocationCheckin.Point(0,179.99999),new LocationCheckin.Point(89.999,0),new LocationCheckin.Point(-89.999,0),new LocationCheckin.Point(90,0)}) {
            for(int i=0;i<=100;i++) { LocationCheckin.Point q=LocationCoordinates.random(center,i/100.0,(i%17)/17.0); LocationCoordinates.validate(q); assertTrue(distance(center,q)<=50.0001); }
        }
    }
    @Test public void zeroRadiusAndRootLocaleFormattingPreserveCoordinates() {
        LocationCheckin.Point center=new LocationCheckin.Point(-31.25,-121.5),q=LocationCoordinates.random(center,0,.5); assertEquals(center.lat,q.lat,1e-10); assertEquals(center.lng,q.lng,1e-10);
        java.util.Locale before=java.util.Locale.getDefault(); try {java.util.Locale.setDefault(java.util.Locale.GERMANY);assertEquals("-31.250000",LocationCoordinates.format(center.lat));} finally {java.util.Locale.setDefault(before);}
    }
    static double distance(LocationCheckin.Point a,LocationCheckin.Point b) { double p=Math.toRadians(b.lat-a.lat),q=Math.toRadians(b.lng-a.lng); double h=Math.sin(p/2)*Math.sin(p/2)+Math.cos(Math.toRadians(a.lat))*Math.cos(Math.toRadians(b.lat))*Math.sin(q/2)*Math.sin(q/2);return 2*LocationCoordinates.EARTH_METRES*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h)))); }
    @Test public void configurableRadiusSupportsBoundsAndSerializedPointsStayInsideRange() {
        assertEquals(50.5,LocationCoordinates.parseRadius("50.5"),0);assertEquals("500",LocationCoordinates.radiusLabel(500));
        for(String invalid:new String[]{"0","-1","10001","NaN",""})try{LocationCoordinates.parseRadius(invalid);fail();}catch(IllegalArgumentException expected){}
        LocationCheckin.Point center=new LocationCheckin.Point(31.25,179.9999);
        for(double radius:new double[]{1,50,500,10000})for(int i=0;i<=30;i++){LocationCheckin.Point q=LocationCoordinates.random(center,radius,i/30.0,i/31.0);q=LocationCoordinates.parse(LocationCoordinates.format(q.lat),LocationCoordinates.format(q.lng));assertTrue(distance(center,q)<=radius);}
    }
}
