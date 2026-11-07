package com.ykt.assistant;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocationCheckinTest {
    private LocationCheckin parse(String value,String event) throws Exception { return LocationCheckin.parse(new JSONObject(value),event==null?null:new JSONObject(event),"lesson",1,false); }
    @Test public void nestedServerPairWinsAndNumbersMayBeStrings() throws Exception { LocationCheckin info=parse("{\"data\":{\"needLocationSignIn\":true,\"session\":{\"id\":7,\"centerLat\":\"31.25\",\"centerLng\":\"121.5\"},\"lat\":1,\"lng\":2,\"address\":\"教学楼\"}}","{\"centerLat\":3,\"centerLng\":4}"); assertEquals(31.25,info.point.lat,0); assertEquals(121.5,info.point.lng,0); assertEquals("7",info.sessionId); assertEquals("教学楼",info.address); info.requireAvailable(); }
    @Test public void coordinatesNeverMixAcrossDifferentSources() throws Exception { LocationCheckin info=parse("{\"needLocationSignIn\":true,\"session\":{\"centerLat\":31},\"lng\":120}","{\"centerLat\":3,\"centerLng\":4,\"address\":\"事件位置\"}"); assertEquals(3,info.point.lat,0); assertEquals(4,info.point.lng,0); assertEquals("事件位置",info.address); assertNull(parse("{\"session\":{\"centerLat\":31},\"lng\":120}",null).point); }
    @Test public void invalidNumbersAreNotConvertedIntoLocations() throws Exception { for(String raw:new String[]{"{\"lat\":91,\"lng\":120}","{\"lat\":31,\"lng\":181}","{\"lat\":\"NaN\",\"lng\":120}","{\"lat\":\"Infinity\",\"lng\":120}","{\"lat\":true,\"lng\":120}"}) assertNull(parse(raw,null).point); }
    @Test public void unavailableOrIncompleteStateHasExplicitError() throws Exception { for(String raw:new String[]{"{\"needLocationSignIn\":true}","{\"needLocationSignIn\":false,\"lat\":1,\"lng\":2}","{\"lat\":1,\"lng\":2}"}) try { parse(raw,null).requireAvailable(); fail(); } catch(RainApi.Failure e) { assertTrue(e.getMessage().length()>5); } }
    @Test public void signedVariantsAndTargetIdentityAreSupported() throws Exception { assertTrue(parse("{\"signed\":1}",null).signed); assertTrue(parse("{\"signed\":\"true\"}",null).signed); LocationCheckin a=parse("{\"lat\":1,\"lng\":2}",null),b=parse("{\"lat\":1,\"lng\":3}",null); assertTrue(a.sameTarget(a)); assertFalse(a.sameTarget(b)); assertTrue(a.submitted(true).submitted); }
}
