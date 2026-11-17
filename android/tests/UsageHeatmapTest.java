package ai.magpie.android;

import java.lang.reflect.*;
import java.time.LocalDate;
import java.util.*;

/** Uses reflection so the pre-feature APK/source can report a behavioral failure. */
public final class UsageHeatmapTest {
    static Class<?> type;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static Object parse(String s)throws Exception{
        try{return type.getDeclaredMethod("parse",String.class).invoke(null,s);}
        catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    @SuppressWarnings("unchecked") static List<Object> days(Object o,int n)throws Exception{return (List<Object>)type.getDeclaredMethod("recent",int.class).invoke(o,n);}
    static String json(String rows){return "{\"from\":\"2025-11-17\",\"to\":\"2026-11-17\",\"days\":"+rows+"}";}
    static void invalid(String s)throws Exception{try{parse(s);throw new AssertionError("Malformed heatmap became valid/zero: "+s);}catch(org.json.JSONException|IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        try{type=Class.forName("ai.magpie.android.UsageHeatmap");}catch(ClassNotFoundException e){throw new AssertionError("Native Usage cannot read the upstream daily heatmap yet");}
        Object h=parse(json("[{\"date\":\"2026-11-17\",\"calls\":2,\"tokens\":2400,\"cost\":0,\"unpriced\":2},{\"date\":\"2026-11-16\",\"calls\":3,\"tokens\":5000,\"cost\":0.125}]"));
        List<Object> recent=days(h,12);
        check(recent.size()==79,"12-week calendar must end on Tuesday, without future days");
        check(field(recent.get(0),"date").equals(LocalDate.of(2026,8,31)),"Calendar must start on Monday");
        check(((Number)field(recent.get(0),"calls")).longValue()==0,"A known sparse absent day must be empty");
        Object last=recent.get(recent.size()-1);
        check(((Number)field(last,"tokens")).longValue()==2400,"Use upstream tokens including cache, not summary input/output");
        check(last.getClass().getDeclaredMethod("costLabel").invoke(last).equals("待定价"),"Unknown prices must not be shown as free");
        check(((Number)field(recent.get(recent.size()-2),"calls")).longValue()==3,"Unsorted sparse days must retain real counts");
        check(days(parse(json("null")),12).size()==79,"Empty Go slice must retain known calendar range");
        check(type.getDeclaredMethod("path",String.class).invoke(null,"Asia/Shanghai").equals("/api/usage/heatmap?tz=Asia%2FShanghai"),"Heatmap must use phone zone, independently of period");
        Object retried=parse(json("[{\"date\":\"2026-11-17\",\"calls\":1,\"tokens\":2400,\"cost\":0,\"unpriced\":2}]"));
        Object retryDay=days(retried,12).get(78);
        check(((Number)field(retryDay,"calls")).longValue()==1&&((Number)field(retryDay,"unpriced")).longValue()==2,"Unknown-priced attempts must not be bounded by logical call count");
        Object midnight=parse(json("[{\"date\":\"2026-11-16\",\"calls\":0,\"tokens\":1000,\"cost\":0,\"unpriced\":1},{\"date\":\"2026-11-17\",\"calls\":1,\"tokens\":1400,\"cost\":0,\"unpriced\":1}]"));
        Object beforeMidnight=days(midnight,12).get(77);
        check(((Number)field(beforeMidnight,"calls")).longValue()==0&&((Number)field(beforeMidnight,"tokens")).longValue()==1000,"A handed-on try before midnight still contributes tokens and unknown price");
        check(days(parse(json("[{\"date\":\"2025-11-17\",\"calls\":1,\"tokens\":100,\"cost\":0,\"unpriced\":2}]")),12).size()==79,"Valid old retry outside recent weeks must not invalidate calendar");
        invalid("{}");invalid("{\"from\":\"2025-11-17\",\"to\":\"2026-11-17\"}");invalid(json("[]").replace("2026-11-17","2026-02-30"));
        invalid(json("[]").replace("2025-11-17","2027-11-17"));
        invalid(json("[{\"date\":\"2026-11-17\",\"calls\":-1,\"tokens\":2,\"cost\":0}]"));
        invalid(json("[{\"date\":\"2026-11-17\",\"calls\":1,\"tokens\":null,\"cost\":0}]"));
        invalid(json("[{\"date\":\"2026-11-17\",\"calls\":1,\"tokens\":2}]"));
        invalid(json("[{\"date\":\"2026-11-17\",\"calls\":1.5,\"tokens\":2,\"cost\":0}]"));
        invalid(json("[{\"date\":\"2026-11-18\",\"calls\":1,\"tokens\":2,\"cost\":0}]"));
        invalid(json("[{\"date\":\"2026-11-17\",\"calls\":1,\"tokens\":2,\"cost\":0},{\"date\":\"2026-11-17\",\"calls\":2,\"tokens\":3,\"cost\":0}]"));
        for(String path:args){Object live=parse(java.nio.file.Files.readString(java.nio.file.Path.of(path)));long calls=0;for(Object day:days(live,12))calls+=((Number)field(day,"calls")).longValue();check(calls==4,"Real Go heatmap differs from four recorded gateway calls");}
        System.out.println("UsageHeatmap: 12-week dates, sparse days, strict invalid reads, real cache tokens, unknown prices and phone timezone passed");
    }
}
