package ai.magpie.android;
import java.util.*;
public final class CatalogSelectionTest {
    private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        WorkspaceSnapshot s=WorkspaceSnapshot.parse("{\"providers\":[{\"id\":\"a\",\"name\":\"供应商\",\"models\":[{\"id\":\"x\",\"name\":\"Same\",\"on\":true},{\"id\":\"hidden\",\"on\":false}]},{\"id\":\"b\",\"off\":true,\"models\":[{\"id\":\"x\",\"on\":true}]},{\"id\":\"c\",\"models\":[{\"id\":\"z\",\"name\":\"Same\",\"on\":true}]}]}","{\"groups\":[]}");
        Set<String> favorites=new HashSet<>(Arrays.asList("a/hidden","b/x","c/z","missing/ref"));
        check(CatalogSelection.choices(s,"",favorites).size()==2,"Picker includes disabled/hidden favorite");
        check(CatalogSelection.choices(s,"",favorites).get(0).model.reference.equals("c/z"),"Favorites not ordered first");
        check(CatalogSelection.choices(s," A/X ",favorites).size()==1,"Exact-reference search or case folding failed");
        check(CatalogSelection.choices(s,"供应商",favorites).size()==1,"Provider search failed");
        check(CatalogSelection.models(s.providers.get(0),"",2,favorites).size()==1,"Favorites catalog loses hidden item management");
        check(CatalogSelection.models(s.providers.get(1),"",1,favorites).isEmpty(),"Available catalog exposes disabled provider");
        check(CatalogSelection.models(s.providers.get(0),"",0,favorites).size()==2,"All filter loses hidden model");
        check(CatalogSelection.choices(s,"unmatched",favorites).isEmpty(),"Empty search is not empty");
        check(!ModelFavorites.scope(false,"").equals(ModelFavorites.scope(true,"https://host:8080")),"Local and remote scope collide");
        check(ModelFavorites.scope(true,"https://host:8080/").equals(ModelFavorites.scope(true,"https://host:8080")),"Trailing-slash scopes differ");
        check(!ModelFavorites.scope(true,"https://host:8080").equals(ModelFavorites.scope(true,"https://other:8080")),"Remote scopes collide");
        check(CatalogSelection.available(s,"a/x"),"Available reference rejected");
        check(!CatalogSelection.available(s,"b/x")&&!CatalogSelection.available(s,"a/hidden")&&!CatalogSelection.available(s,"missing/ref")&&!CatalogSelection.available(null,"a/x"),"Stale unavailable reference accepted");
        System.out.println("PASS catalog selection, availability, search, exact references, favorites order and workspace scopes");
    }
}
