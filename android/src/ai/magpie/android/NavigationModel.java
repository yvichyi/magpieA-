package ai.magpie.android;

/** Destination contract shared by all Android surfaces; independent of Android APIs. */
final class NavigationModel {
    static final String[] ROOTS={"home","groups","usage","plugins","tools"};
    static final String[] LABELS={"模型","分组","用量","插件","工具"};
    static String section(String view){
        if("home".equals(view)||"providers".equals(view))return "home";
        if("groups".equals(view)||"usage".equals(view)||"plugins".equals(view))return view;
        return "tools";
    }
    static String parent(String view){
        if("groups".equals(view)||"usage".equals(view))return view;
        if("providers".equals(view)||"plugins".equals(view)||"tools".equals(view))return "home";
        return "tools";
    }
    static String title(String view){
        switch(view){
            case"home":return"模型";case"groups":return"模型分组";case"usage":return"用量";case"plugins":return"插件";case"tools":return"工具";
            case"providers":return"服务商与订阅";case"routing":return"请求路由";case"gateway":return"网关与密钥";case"library":return"资料库";
            case"settings":return"设置与备份";case"masking":return"隐私脱敏";case"agents":return"Agent";case"sessions":return"会话";default:return"扩展页面";
        }
    }
}
