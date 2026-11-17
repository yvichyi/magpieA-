package ai.magpie.android;

import org.json.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The upstream daily ledger, including its cache tokens and partial prices. */
final class UsageHeatmap {
    final LocalDate from,to;
    private final Map<LocalDate,Day> days;
    final String signature;

    static final class Day {
        final LocalDate date;
        final long calls,tokens,unpriced;
        final double cost;
        Day(LocalDate date,long calls,long tokens,long unpriced,double cost){
            this.date=date;this.calls=calls;this.tokens=tokens;this.unpriced=unpriced;this.cost=cost;
        }
        String costLabel(){return cost==0&&unpriced>0?"待定价":UsageSnapshot.money(cost);}
        double value(int metric){return metric==1?tokens:metric==2?cost:calls;}
    }
    private UsageHeatmap(JSONObject j)throws JSONException{
        from=date(j.getString("from"));to=date(j.getString("to"));
        long span=ChronoUnit.DAYS.between(from,to);
        if(span<0||span>371)throw new JSONException("每日统计范围无效");
        Map<LocalDate,Day> at=new TreeMap<>();
        if(!j.has("days"))throw new JSONException("每日统计列表缺失");
        JSONArray rows=j.isNull("days")?new JSONArray():j.getJSONArray("days");
        for(int i=0;i<rows.length();i++){
            JSONObject r=rows.getJSONObject(i);LocalDate day=date(r.getString("date"));
            if(day.isBefore(from)||day.isAfter(to)||at.containsKey(day))throw new JSONException("每日统计日期无效或重复");
            long calls=UsageSnapshot.count(r,"calls",true),tokens=UsageSnapshot.count(r,"tokens",true),unpriced=UsageSnapshot.count(r,"unpriced",false);
            at.put(day,new Day(day,calls,tokens,unpriced,UsageSnapshot.amount(r,true)));
        }
        days=Collections.unmodifiableMap(at);signature=j.toString();
    }
    private static LocalDate date(String s)throws JSONException{
        try{if(s.length()!=10)throw new IllegalArgumentException();return LocalDate.parse(s);}
        catch(RuntimeException e){throw new JSONException("每日统计日期无效");}
    }
    static UsageHeatmap parse(String json)throws JSONException{return new UsageHeatmap(new JSONObject(json));}
    List<Day> recent(int weeks){
        if(weeks<1||weeks>53)throw new IllegalArgumentException("Invalid week count");
        LocalDate first=to.minusDays(to.getDayOfWeek().getValue()-1L).minusWeeks(weeks-1L);
        if(first.isBefore(from))first=from;
        ArrayList<Day> result=new ArrayList<>();
        for(LocalDate d=first;!d.isAfter(to);d=d.plusDays(1))result.add(days.getOrDefault(d,new Day(d,0,0,0,0)));
        return Collections.unmodifiableList(result);
    }
    static String path(String zone){
        ZoneId.of(zone);
        try{return "/api/usage/heatmap?tz="+URLEncoder.encode(zone,StandardCharsets.UTF_8.name());}
        catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
    }
}
