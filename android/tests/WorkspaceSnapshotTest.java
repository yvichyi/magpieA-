package ai.magpie.android;
public final class WorkspaceSnapshotTest {
    public static void main(String[] args)throws Exception{
        String providers="{\"providers\":[{\"id\":\"relay\",\"name\":\"工作服务\",\"off\":false,\"exposed\":1,\"key\":\"must-not-render\",\"models\":[{\"id\":\"alpha\",\"name\":\"Alpha 模型\",\"on\":true},{\"id\":\"beta\",\"name\":\"Beta\",\"on\":false}]}]}";
        String groups="{\"groups\":[{\"id\":\"fallback\",\"ref\":\"auto/fallback\",\"name\":\"备用组\",\"routing\":\"order\",\"members\":[\"relay/alpha\",\"relay/beta\"],\"matched\":[\"relay/gamma\"]}]}";
        WorkspaceSnapshot s=WorkspaceSnapshot.parse(providers,groups);
        check(s.sameContent(WorkspaceSnapshot.parse(providers,groups)),"Unchanged catalog cannot retain its Views");
        check(!s.sameContent(WorkspaceSnapshot.parse(providers.replace("Alpha 模型","改名模型"),groups)),"Renamed model was not detected");
        check(!s.sameContent(WorkspaceSnapshot.parse(providers,groups.replace("relay/gamma","relay/delta"))),"Changed route members were not detected");
        check(WorkspaceSnapshot.parse(providers.replace("{\"providers\"","{\"gateway\":{\"url\":\"http://127.0.0.1:41237\",\"onNetwork\":true},\"providers\""),groups).onNetwork,"Network-bound gateway flag was dropped");
        check(s.providers.size()==1&&s.groups.size()==1,"Actual entities missing");
        check(s.providers.get(0).models.get(0).reference.equals("relay/alpha"),"Copy reference is not provider/model");
        check(s.providers.get(0).models.get(0).on&&!s.providers.get(0).models.get(1).on,"Enabled state invented");
        check(s.providers.get(0).matching("工作").size()==2,"Provider-name search lost models");
        check(s.providers.get(0).matching("ALPHA").size()==1,"Model search is not case insensitive");
        check(s.providers.get(0).matching("beta").get(0).reference.equals("relay/beta"),"Filtered copy target changed");
        check(s.groups.get(0).members.size()==2&&s.groups.get(0).matched.size()==1,"Fixed and matched members merged misleadingly");
        check(s.groups.get(0).reference.equals("auto/fallback"),"Auto route reference was invented");
        check(s.groups.get(0).matches("auto/fallback"),"Advertised route reference search failed");
        check(s.groups.get(0).matches("alpha"),"Routing member search failed");
        check(s.groups.get(0).strategy().equals("顺序备用"),"Routing strategy mislabeled");
        check(WorkspaceSnapshot.parse("{\"providers\":null}","{\"groups\":null}").providers.isEmpty(),"Go null slices rejected");
        bad("{}",groups);bad(providers.replace("\"on\":true","\"on\":null"),groups);
        System.out.println("WorkspaceSnapshot: actual entities, exact model IDs, explicit enabled state, provider/model/member search, distinct fixed/matched members and invalid-read handling passed");
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void bad(String p,String g)throws Exception{try{WorkspaceSnapshot.parse(p,g);throw new AssertionError("Incomplete data became an empty catalog");}catch(org.json.JSONException expected){}}
}
