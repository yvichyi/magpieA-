package ai.magpie.android;
import java.util.*;

public final class NavigationModelTest {
    private static void same(String expected,String actual,String why){if(!expected.equals(actual))throw new AssertionError(why+": expected "+expected+", got "+actual);}
    public static void main(String[] args){
        same("groups",NavigationModel.section("groups"),"Model groups must keep their own primary destination");
        same("tools",NavigationModel.section("routing"),"Request routing must not masquerade as model groups");
        same("home",NavigationModel.section("providers"),"Provider editing belongs to models");
        for(String page:new String[]{"library","settings","gateway","masking","agents","sessions","plugin-example"})same("tools",NavigationModel.section(page),"Advanced pages use the same Tools destination");
        same("groups",NavigationModel.parent("groups"),"Group editor returns to native groups");
        same("home",NavigationModel.parent("providers"),"Provider editor returns to models");
        same("tools",NavigationModel.parent("settings"),"Settings returns to Tools, not an arbitrary caller");
        same("home",NavigationModel.parent("plugins"),"Primary plugin page returns to models");
        same("usage",NavigationModel.parent("usage"),"Request ledger returns to the native overview");
        same("模型分组",NavigationModel.title("groups"),"Groups and rules have different user-facing names");
        same("请求路由",NavigationModel.title("routing"),"Request routing title is explicit");
        if(new HashSet<>(Arrays.asList(NavigationModel.ROOTS)).size()!=5)throw new AssertionError("Primary destinations must be unique");
        System.out.println("PASS stable five roots, explicit group/request trace distinction and deterministic parents");
    }
}
