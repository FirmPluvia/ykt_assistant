package com.ykt.assistant;
import org.junit.Test;
import static org.junit.Assert.*;
public class ExportProgressTest {
    @Test public void pageAndByteProgressUseRealUnitsAndBuildHasNoPercentage() {
        assertEquals(30,ExportProgress.fetching(3,10).percent()); assertEquals(100,ExportProgress.fetching(20,10).percent());
        assertEquals(ExportProgress.Stage.BUILD,ExportProgress.building(10).stage); assertEquals(0,ExportProgress.building(10).percent());
        assertEquals(50,ExportProgress.saving(10,512,1024).percent()); assertEquals(100,ExportProgress.saving(10,2048,1024).percent()); assertEquals(0,ExportProgress.saving(0,0,0).percent());
    }
}
