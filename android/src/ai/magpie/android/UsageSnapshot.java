package ai.magpie.android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The upstream summary's accounting rules; never infer zero from an unreadable field. */
final class UsageSnapshot {
    final String period, bucket;
    final long calls, errors, canceled, input, output, cacheRead, cacheWrite, cacheUnknown, unpriced;
    final double cost;
    final List<Point> points;
    final List<Model> models;

    static final class Point {
        final String label, time;
        final long calls, tokens;
        final double cost;
        Point(JSONObject j) throws JSONException {
            label = j.getString("label"); time = j.getString("time");
            calls = count(j, "calls", true);
            tokens = Math.addExact(count(j, "input", true), count(j, "output", true));
            cost = amount(j, false);
        }
    }
    static final class Model {
        final String name, provider;
        final long calls, tokens, unpriced;
        final double cost;
        Model(JSONObject j) throws JSONException {
            name = j.optString("name", j.optString("model", j.optString("id", "未命名模型")));
            provider = j.optString("sub", j.optString("provider", ""));
            calls = count(j, "calls", true);
            tokens = Math.addExact(count(j, "input", true), count(j, "output", true));
            unpriced = count(j, "unpriced", true); cost = amount(j, true);
        }
    }
    private UsageSnapshot(JSONObject j) throws JSONException {
        period = j.getString("period"); validatePeriod(period);
        bucket = j.getString("bucket");
        if(!Arrays.asList("hour","day","week").contains(bucket))throw new JSONException("统计时间粒度无效");
        calls = count(j,"calls",true); errors = count(j,"errors",true); canceled = count(j,"canceled",false);
        input = count(j,"input",true); output = count(j,"output",true);
        cacheRead = count(j,"cache_read",true); cacheWrite = count(j,"cache_write",true);
        cacheUnknown = count(j,"cache_unknown",false); unpriced = count(j,"unpriced",true); cost = amount(j,true);
        if (errors > calls || canceled > calls - errors) throw new JSONException("调用状态数量不一致");
        Math.addExact(input,output);
        Math.addExact(Math.addExact(input,cacheRead),cacheWrite);
        ArrayList<Point> ps = new ArrayList<>();
        JSONArray series = array(j,"series");
        for (int i=0;i<series.length();i++) ps.add(new Point(series.getJSONObject(i)));
        points = Collections.unmodifiableList(ps);
        ArrayList<Model> ms = new ArrayList<>();
        JSONArray groups = array(j,"models");
        for (int i=0;i<groups.length();i++) ms.add(new Model(groups.getJSONObject(i)));
        ms.sort((a,b)->Long.compare(b.calls,a.calls)); models = Collections.unmodifiableList(ms);
    }
    String displaySignature(){
        org.json.JSONArray values=new org.json.JSONArray();values.put(period).put(bucket).put(calls).put(errors).put(canceled).put(input).put(output).put(cacheRead).put(cacheWrite).put(cacheUnknown).put(unpriced).put((Object)Double.valueOf(cost));
        for(Point p:points)values.put(new org.json.JSONArray().put(p.label).put(p.time).put(p.calls).put(p.tokens));
        for(Model m:models)values.put(new org.json.JSONArray().put(m.name).put(m.provider).put(m.calls).put(m.tokens).put(m.unpriced).put((Object)Double.valueOf(m.cost)));
        return values.toString();
    }
    static UsageSnapshot parse(String json) throws JSONException { return new UsageSnapshot(new JSONObject(json)); }
    private static JSONArray array(JSONObject j,String key) throws JSONException {
        if(!j.has(key))throw new JSONException("统计列表缺失："+key);
        if(j.isNull(key)) return new JSONArray();
        return j.getJSONArray(key);
    }
    static long count(JSONObject j,String key,boolean required) throws JSONException {
        if (!required&&!j.has(key)) return 0;
        Object value = j.get(key);
        if (!(value instanceof Number)) throw new JSONException("统计字段无效："+key);
        double d=((Number)value).doubleValue();
        if (!Double.isFinite(d)||d<0||d>9007199254740991L||d!=Math.floor(d)) throw new JSONException("统计数值无效："+key);
        return ((Number)value).longValue();
    }
    static double amount(JSONObject j,boolean required) throws JSONException {
        if (!required&&!j.has("cost")) return 0;
        Object value=j.get("cost");
        if (!(value instanceof Number)) throw new JSONException("费用字段无效");
        double d=((Number)value).doubleValue();
        if (!Double.isFinite(d)||d<0) throw new JSONException("费用数值无效");
        return d;
    }
    long tokens(){return input+output;}
    long completed(){return calls-errors-canceled;}
    String completionRate(){return calls==0?"—":percent(completed(),calls);}
    String cacheRate(){
        long prompt=input+cacheRead+cacheWrite;
        long known=prompt-Math.min(prompt,cacheUnknown);
        return known==0?"—":percent(Math.min(cacheRead,known),known);
    }
    String costLabel(){return cost==0&&unpriced>0?"待定价":money(cost);}
    String priceNote(){return unpriced>0?number(unpriced)+" 次未定价，未计入费用":"USD · 按已知价格统计";}
    static String percent(long numerator,long denominator){return String.format(Locale.ROOT,"%.1f%%",100d*numerator/denominator);}
    static String number(long n){
        if(n>=100000000)return compact(n/100000000d)+"亿";
        if(n>=10000)return compact(n/10000d)+"万";
        return String.format(Locale.ROOT,"%,d",n);
    }
    private static String compact(double n){return String.format(Locale.ROOT,n>=100||n==Math.floor(n)?"%.0f":"%.1f",n);}
    static String money(double n){
        if(n>0&&n<.0001)return "<$0.0001";
        return String.format(Locale.ROOT,n>0&&n<.01?"$%.4f":"$%,.2f",n);
    }
    static void validatePeriod(String period){
        if(!Arrays.asList("today","7d","30d","all").contains(period))throw new IllegalArgumentException("不支持的统计范围");
    }
    static String path(String period,String zone){
        validatePeriod(period);
        try{return "/api/usage?period="+period+"&tz="+URLEncoder.encode(zone,StandardCharsets.UTF_8.name());}
        catch(java.io.UnsupportedEncodingException impossible){throw new AssertionError(impossible);}
    }
}
