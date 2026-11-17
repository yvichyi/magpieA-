package ai.magpie.android;
import java.util.*;
public final class UsageModelsTest {
    private static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        UsageSnapshot s=UsageSnapshot.parse("{\"period\":\"today\",\"bucket\":\"hour\",\"calls\":11,\"errors\":0,\"input\":200,\"output\":100,\"cache_read\":0,\"cache_write\":0,\"unpriced\":1,\"cost\":0,\"series\":[],\"models\":[{\"name\":\"Alpha long\",\"sub\":\"供应商甲\",\"calls\":10,\"input\":10,\"output\":20,\"cost\":0,\"unpriced\":1},{\"name\":\"beta\",\"sub\":\"乙\",\"calls\":1,\"input\":200,\"output\":200,\"cost\":0,\"unpriced\":0}]}");
        check(UsageModels.select(s.models,"",0).get(0).name.equals("Alpha long"),"Calls order wrong");
        check(UsageModels.select(s.models,"",1).get(0).name.equals("beta"),"Token order wrong");
        check(UsageModels.select(s.models," ALPHA ",1).size()==1,"Name search failed");
        check(UsageModels.select(s.models,"甲",0).size()==1,"Provider search failed");
        check(UsageModels.select(s.models,"absent",0).isEmpty(),"Empty query recovery is impossible");
        check(s.calls==11&&s.models.size()==2&&s.models.get(0).name.equals("Alpha long"),"Search/sort mutated authoritative data");
        System.out.println("PASS model/provider search, calls/token sorting, empty results and immutable totals");
    }
}
