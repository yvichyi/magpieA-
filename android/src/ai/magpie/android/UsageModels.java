package ai.magpie.android;
import java.util.*;
/** Local exploration never changes the period's authoritative totals. */
final class UsageModels {
    static List<UsageSnapshot.Model> select(List<UsageSnapshot.Model> models,String query,int sort){
        String q=query.trim().toLowerCase(Locale.ROOT);List<UsageSnapshot.Model> out=new ArrayList<>();
        for(UsageSnapshot.Model model:models)if((model.name+" "+model.provider).toLowerCase(Locale.ROOT).contains(q))out.add(model);
        out.sort((a,b)->Long.compare(sort==1?b.tokens:b.calls,sort==1?a.tokens:a.calls));return out;
    }
}
