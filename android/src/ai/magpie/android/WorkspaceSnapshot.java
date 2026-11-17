package ai.magpie.android;

import java.util.*;
import org.json.*;

/** Read-only catalog projection. Credentials and transport settings never enter the view model. */
final class WorkspaceSnapshot {
    final List<Provider> providers;
    final List<Group> groups;
    final String gatewayUrl;
    final boolean onNetwork;
    private WorkspaceSnapshot(List<Provider> providers,List<Group> groups,String gatewayUrl,boolean onNetwork){this.onNetwork=onNetwork;this.gatewayUrl=gatewayUrl;this.providers=Collections.unmodifiableList(providers);this.groups=Collections.unmodifiableList(groups);}
    static WorkspaceSnapshot parse(String providerJson,String groupJson)throws JSONException{
        List<Provider> providers=new ArrayList<>();List<Group> groups=new ArrayList<>();
        JSONArray ps=array(new JSONObject(providerJson),"providers"),gs=array(new JSONObject(groupJson),"groups");
        for(int i=0;i<ps.length();i++){
            JSONObject p=ps.getJSONObject(i);String id=p.getString("id");List<Model> models=new ArrayList<>();JSONArray ms=array(p,"models");
            for(int j=0;j<ms.length();j++){JSONObject m=ms.getJSONObject(j);models.add(new Model(id,m.getString("id"),m.optString("name",""),m.getBoolean("on")));}
            providers.add(new Provider(id,p.optString("name",id),p.optBoolean("off")||p.optBoolean("disabled"),models));
        }
        for(int i=0;i<gs.length();i++){
            JSONObject g=gs.getJSONObject(i);groups.add(new Group(g.getString("id"),g.getString("ref"),g.optString("name",""),g.optString("routing",""),strings(array(g,"members")),g.has("matched")?strings(array(g,"matched")):Collections.emptyList()));
        }
        JSONObject gateway=new JSONObject(providerJson).optJSONObject("gateway");
        return new WorkspaceSnapshot(providers,groups,gateway==null?"":gateway.optString("url",""),gateway!=null&&gateway.optBoolean("onNetwork"));
    }
    private static JSONArray array(JSONObject o,String key)throws JSONException{if(!o.has(key))throw new JSONException("Missing "+key);return o.isNull(key)?new JSONArray():o.getJSONArray(key);}
    private static List<String> strings(JSONArray a)throws JSONException{List<String> out=new ArrayList<>();for(int i=0;i<a.length();i++)out.add(a.getString(i));return Collections.unmodifiableList(out);}
    private static boolean contains(String value,String query){return value.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));}
    boolean sameContent(WorkspaceSnapshot other){
        if(other==null||onNetwork!=other.onNetwork||!gatewayUrl.equals(other.gatewayUrl)||providers.size()!=other.providers.size()||groups.size()!=other.groups.size())return false;
        for(int i=0;i<providers.size();i++){
            Provider a=providers.get(i),b=other.providers.get(i);
            if(!a.id.equals(b.id)||!a.name.equals(b.name)||a.off!=b.off||a.models.size()!=b.models.size())return false;
            for(int j=0;j<a.models.size();j++){Model x=a.models.get(j),y=b.models.get(j);if(!x.reference.equals(y.reference)||!x.name.equals(y.name)||x.on!=y.on)return false;}
        }
        for(int i=0;i<groups.size();i++){Group a=groups.get(i),b=other.groups.get(i);if(!a.id.equals(b.id)||!a.reference.equals(b.reference)||!a.name.equals(b.name)||!a.routing.equals(b.routing)||!a.members.equals(b.members)||!a.matched.equals(b.matched))return false;}
        return true;
    }
    static final class Model{
        final String id,name,reference;final boolean on;
        Model(String provider,String id,String name,boolean on){this.id=id;this.name=name.isEmpty()?id:name;reference=provider+"/"+id;this.on=on;}
    }
    static final class Provider{
        final String id,name;final boolean off;final List<Model> models;
        Provider(String id,String name,boolean off,List<Model> models){this.id=id;this.name=name.isEmpty()?id:name;this.off=off;this.models=Collections.unmodifiableList(models);}
        List<Model> matching(String query){if(contains(name,query)||contains(id,query))return models;List<Model> out=new ArrayList<>();for(Model m:models)if(contains(m.name,query)||contains(m.reference,query))out.add(m);return out;}
        boolean matches(String query){return contains(name,query)||contains(id,query)||!matching(query).isEmpty();}
    }
    static final class Group{
        final String id,reference,name,routing;final List<String> members,matched;
        Group(String id,String reference,String name,String routing,List<String> members,List<String> matched){this.id=id;this.reference=reference;this.name=name.isEmpty()?id:name;this.routing=routing;this.members=members;this.matched=matched;}
        boolean matches(String query){if(contains(name,query)||contains(id,query)||contains(reference,query))return true;for(String m:members)if(contains(m,query))return true;for(String m:matched)if(contains(m,query))return true;return false;}
        String strategy(){switch(routing){case "order":return "顺序备用";case "round-robin":return "轮询";case "random":return "随机";default:return routing.isEmpty()?"默认策略":routing;}}
    }
}
