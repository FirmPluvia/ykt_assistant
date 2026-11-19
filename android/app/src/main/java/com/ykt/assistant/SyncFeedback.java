package com.ykt.assistant;
import java.util.List;
import java.util.function.Predicate;
final class SyncFeedback {
    static <T> String summary(String action,List<T> results,Predicate<T> succeeded){int success=0;for(T result:results)if(succeeded.test(result))success++;return "本次同步"+action+"成功 "+success+" 条，失败 "+(results.size()-success)+" 条";}
}
