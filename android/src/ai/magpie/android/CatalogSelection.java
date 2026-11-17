package ai.magpie.android;
import java.util.*;

/** Shared native filtering; favorites never change upstream availability. */
final class CatalogSelection {
    static final class Choice {
        final WorkspaceSnapshot.Provider provider;
        final WorkspaceSnapshot.Model model;
        Choice(WorkspaceSnapshot.Provider p,WorkspaceSnapshot.Model m){provider=p;model=m;}
    }
    static List<WorkspaceSnapshot.Model> models(WorkspaceSnapshot.Provider p,String query,int filter,Set<String> favorites){
        List<WorkspaceSnapshot.Model> out=new ArrayList<>();
        for(WorkspaceSnapshot.Model m:p.matching(query)){
            if(filter==1&&(p.off||!m.on))continue;
            if(filter==2&&!favorites.contains(m.reference))continue;
            out.add(m);
        }
        return out;
    }
    static boolean available(WorkspaceSnapshot snapshot,String reference){
        if(snapshot==null)return false;
        for(WorkspaceSnapshot.Provider p:snapshot.providers)if(!p.off)for(WorkspaceSnapshot.Model m:p.models)if(m.on&&m.reference.equals(reference))return true;
        for(WorkspaceSnapshot.Group g:snapshot.groups)if(g.reference.equals(reference))return true;
        return false;
    }
    static List<Choice> choices(WorkspaceSnapshot snapshot,String query,Set<String> favorites){
        List<Choice> out=new ArrayList<>();
        for(WorkspaceSnapshot.Provider p:snapshot.providers)for(WorkspaceSnapshot.Model m:models(p,query,1,favorites))out.add(new Choice(p,m));
        // Stable sort keeps each provider's original order within each section.
        out.sort(Comparator.comparingInt(c->favorites.contains(c.model.reference)?0:1));
        return out;
    }
}
