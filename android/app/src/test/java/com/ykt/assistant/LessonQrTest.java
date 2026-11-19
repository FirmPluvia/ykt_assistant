package com.ykt.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class LessonQrTest {
    @Test public void directAndQueryIdsPreserveLongNumbersAsStrings() {
        String id="1801803512884878080"; assertEquals(id,LessonQr.parse(id,0).lessonId);
        for(String key:new String[]{"lessonId","lesson_id","lessonid"}) assertEquals(id,LessonQr.parse("https://www.yuketang.cn/lesson/view?"+key+"="+id,0).lessonId);
    }
    @Test public void dynamicQrAlwaysUsesServerValidationAndPreservesCompleteSignedUrl() {
        String raw="https://changjiang.yuketang.cn/api/v3/lesson/check-in/dynamic-qr-code?lessonId=123&sig=a%2Fb%3D&time=1700000000"; LessonQr.Payload result=LessonQr.parse(raw,2); assertNull(result.lessonId); assertEquals(raw,result.url);
    }
    @Test public void rejectsOtherHostsServersLoginCodesAndUnsafeSchemes() {
        for(String raw:new String[]{"https://evil.example/lesson?lessonId=123","http://www.yuketang.cn/lesson?lessonId=123","https://name:secret@www.yuketang.cn/lesson?lessonId=123","https://www.yuketang.cn:444/lesson?lessonId=123","https://pro.yuketang.cn/lesson?lessonId=123","https://www.yuketang.cn/login?lessonId=123","https://www.yuketang.cn/authorize?lessonId=123","javascript:alert(1)"}) reject(raw);
    }
    @Test public void rejectsCourseIdsAmbiguousIdsAndMalformedContents() {
        for(String raw:new String[]{"","0","000","123456789012345678901","https://www.yuketang.cn/?classroomId=123","https://www.yuketang.cn/?courseId=123","https://www.yuketang.cn/lesson?lessonId=123&lesson_id=456","https://www.yuketang.cn/lesson?lessonId=123&lessonId=123","https://www.yuketang.cn/lesson?lessonId=abc","https://www.yuketang.cn/lesson?lessonId=0","https://www.yuketang.cn/lesson\n?lessonId=123"}) reject(raw);
    }
    private void reject(String raw) { try { LessonQr.parse(raw,0); fail(raw); } catch(IllegalArgumentException expected) {} }
}
