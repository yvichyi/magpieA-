package ai.magpie.android;

public final class UsageSnapshotTest {
    private static final String BASE="\"period\":\"today\",\"bucket\":\"hour\",\"calls\":10,\"errors\":2,\"canceled\":1,\"input\":1000,\"output\":500,\"cache_read\":600,\"cache_write\":400,\"cache_unknown\":500,\"cost\":0.000012,\"unpriced\":3";
    private static final String ARRAYS=",\"models\":null,\"series\":null";
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void invalid(String json)throws Exception{try{UsageSnapshot.parse(json);throw new AssertionError("invalid data accepted: "+json);}catch(org.json.JSONException|IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        UsageSnapshot s=UsageSnapshot.parse("{"+BASE+",\"models\":[{\"name\":\"first\",\"cost\":0,\"unpriced\":2,\"calls\":2,\"input\":12,\"output\":4},{\"name\":\"second\",\"cost\":0,\"unpriced\":7,\"calls\":7,\"input\":20,\"output\":5}],\"series\":[{\"label\":\"09\",\"time\":\"2026-11-09T09:00:00Z\",\"calls\":3,\"input\":12,\"output\":4,\"cost\":0.01}]}");
        check(s.displaySignature().equals(s.displaySignature()),"Unchanged usage display is unstable");
        check(!s.displaySignature().equals(UsageSnapshot.parse("{"+BASE+ARRAYS+"}").displaySignature()),"Changed model/series data failed to invalidate the display");
        check(s.tokens()==1500,"cache or reasoning counted as input/output");
        check(s.completed()==7&&s.completionRate().equals("70.0%"),"cancellation counted as success");
        check(s.cacheRate().equals("40.0%"),"unknown-cache tokens included in denominator");
        check(s.costLabel().equals("<$0.0001"),"tiny nonzero cost hidden as zero");
        check(s.priceNote().contains("3"),"unpriced calls hidden");
        check(s.models.get(0).name.equals("second"),"models not ranked by calls");
        check(s.points.get(0).calls==3,"series ignored");
        UsageSnapshot empty=UsageSnapshot.parse("{"+BASE.replace("\"calls\":10,\"errors\":2,\"canceled\":1","\"calls\":0,\"errors\":0,\"canceled\":0")+",\"models\":null,\"series\":null}");
        check(empty.completionRate().equals("—"),"empty data claims 100 percent");
        check(empty.models.isEmpty()&&empty.points.isEmpty(),"nullable Go slices rejected");
        UsageSnapshot unknown=UsageSnapshot.parse("{"+BASE.replace("\"cost\":0.000012","\"cost\":0").replace("\"unpriced\":3","\"unpriced\":10")+ARRAYS+"}");
        check(unknown.costLabel().equals("待定价"),"unknown price shown as free");
        check(UsageSnapshot.number(3000000000L).equals("30亿"),"large count overflows");
        check(UsageSnapshot.path("7d","Asia/Shanghai").equals("/api/usage?period=7d&tz=Asia%2FShanghai"),"wrong period/timezone contract");
        invalid("{}");invalid("{"+BASE.replace("\"calls\":10","\"calls\":-1")+ARRAYS+"}");
        invalid("{"+BASE.replace("\"calls\":10","\"calls\":null")+ARRAYS+"}");
        invalid("{"+BASE.replace("\"cost\":0.000012","\"cost\":\"NaN\"")+ARRAYS+"}");
        invalid("{"+BASE.replace("\"calls\":10","\"calls\":1.5")+ARRAYS+"}");
        invalid("{"+BASE+"}");
        invalid("{"+BASE+",\"series\":[{\"label\":\"09\",\"time\":\"2026-11-09T09:00:00Z\",\"calls\":1}],\"models\":null}");
        invalid("{"+BASE+",\"models\":[{\"name\":\"model\",\"calls\":1}],\"series\":null}");
        invalid("{"+BASE+",\"models\":[{\"name\":\"unread-price\",\"calls\":1,\"input\":0,\"output\":0}],\"series\":null}");
        check(s.points.size()==1,"test data mutated");
        for(String file:args){
            UsageSnapshot actual=UsageSnapshot.parse(java.nio.file.Files.readString(java.nio.file.Path.of(file)));
            check(actual.calls==4&&actual.errors==0&&actual.completed()==4,"real ledger status changed");
            check(actual.tokens()==64&&actual.unpriced==4,"real token/price contract changed");
            check(actual.models.size()>0&&!actual.points.isEmpty(),"real groups/series not parsed");
            check(actual.points.stream().mapToLong(p->p.calls).sum()==4,"real chart not equal to total");
        }
        System.out.println("UsageSnapshot: cancellations, cache semantics, partial/tiny prices, nullable slices, ranking, large counts, strict errors and timezone passed");
    }
}
