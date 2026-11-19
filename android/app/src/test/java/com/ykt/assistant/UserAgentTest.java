package com.ykt.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class UserAgentTest {
    @Test public void exactRequestedTemplateUsesActualDeviceFields() {
        assertEquals("Mozilla/5.0 (Linux; Android 17; AAP-AN00 Build/HONORAAP-AN00; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/153.0.8010.36 Mobile Safari/537.36 flutterApp Mobile flutterApp",UserAgent.defaultFor("17","AAP-AN00","HONORAAP-AN00"));
    }
    @Test public void previewContainsOnlyAndroidVersionAndModel() {
        String value=UserAgent.defaultFor("17","AAP-AN00","HONORAAP-AN00"); assertEquals("Android 17; AAP-AN00",UserAgent.deviceSummary(value)); assertFalse(UserAgent.deviceSummary(value).contains("Build")); assertFalse(UserAgent.deviceSummary(value).contains("Mozilla")); assertFalse(UserAgent.deviceSummary(value).contains("Chrome"));
    }
    @Test public void previewPreservesModelSpacesAndOmitsBrowserOrCpuMarkers() {
        assertEquals("Android 16.0.1; Pixel 9 Pro",UserAgent.deviceSummary(UserAgent.defaultFor("16.0.1","Pixel 9 Pro","TEST"))); assertEquals("Android 17",UserAgent.deviceSummary("Mozilla/5.0 (Linux; Android 17; wv)")); assertEquals("Android 17",UserAgent.deviceSummary("Mozilla/5.0 (Linux; Android 17; arm64-v8a)")); assertEquals("Android 17",UserAgent.deviceSummary("Mozilla/5.0 (Linux; Android 17)"));
    }
    @Test public void nonAndroidCustomUaDoesNotPretendToDescribeTheDevice() {
        assertEquals("设备信息未提供",UserAgent.deviceSummary("StudyClient/1.0")); assertEquals("设备信息未提供",UserAgent.deviceSummary(null));
    }
    @Test public void missingOrUnsafeDeviceFieldsCannotProduceInvalidHeaders() {
        String value=UserAgent.defaultFor(null,"设备\r\n; (Model)",null); assertEquals(value,UserAgent.validate(value)); assertFalse(value.contains("\r")); assertFalse(value.contains("\n")); assertTrue(value.contains("Model")); assertTrue(value.contains("Build/unknown"));
    }
    @Test public void validatesCustomHeadersAndRejectsInjectionAndOverlongValues() {
        assertEquals("StudyClient/1.0",UserAgent.validate("  StudyClient/1.0  "));
        for(String value:new String[]{null,"","   ","UA\r\nAuthorization: injected","UA\tbad","中文",new String(new char[2049]).replace('\0','x')}) { try { UserAgent.validate(value); fail("accepted invalid User-Agent"); } catch(IllegalArgumentException expected) {} }
    }
}
