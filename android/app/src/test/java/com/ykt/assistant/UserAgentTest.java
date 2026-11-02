package com.ykt.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class UserAgentTest {
    @Test public void exactRequestedTemplateUsesActualDeviceFields() {
        assertEquals("Mozilla/5.0 (Linux; Android 17; AAP-AN00 Build/HONORAAP-AN00; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/153.0.8010.36 Mobile Safari/537.36 flutterApp Mobile flutterApp",UserAgent.defaultFor("17","AAP-AN00","HONORAAP-AN00"));
    }
    @Test public void missingOrUnsafeDeviceFieldsCannotProduceInvalidHeaders() {
        String value=UserAgent.defaultFor(null,"设备\r\n; (Model)",null); assertEquals(value,UserAgent.validate(value)); assertFalse(value.contains("\r")); assertFalse(value.contains("\n")); assertTrue(value.contains("Model")); assertTrue(value.contains("Build/unknown"));
    }
    @Test public void validatesCustomHeadersAndRejectsInjectionAndOverlongValues() {
        assertEquals("StudyClient/1.0",UserAgent.validate("  StudyClient/1.0  "));
        for(String value:new String[]{null,"","   ","UA\r\nAuthorization: injected","UA\tbad","中文",new String(new char[2049]).replace('\0','x')}) { try { UserAgent.validate(value); fail("accepted invalid User-Agent"); } catch(IllegalArgumentException expected) {} }
    }
}
