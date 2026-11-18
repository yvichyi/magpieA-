package ai.magpie.android;
/** Canonical ownership for four task roots, native and advanced detail pages. */
final class NavigationModel {
 static final String[] ROOTS={"connect","home","plugins","usage"};
 static final String[] LABELS={"接入","模型","插件","用量"};
 static String section(String view){
  if("home".equals(view)||"groups".equals(view)||"providers".equals(view)||"routing".equals(view))return "home";
  if("usage".equals(view))return "usage";
  if("connect".equals(view)||"gateway".equals(view)||"settings".equals(view)||"masking".equals(view)||"tools".equals(view))return "connect";
  return "plugins";
 }
 static String parent(String view){return section(view);}
 static String title(String view){switch(view){
  case "connect":return "接入";case "home":return "模型";case "groups":return "模型分组";case "usage":return "用量";case "plugins":return "插件";case "tools":return "设置";
  case "providers":return "服务商与订阅";case "routing":return "请求路由";case "gateway":return "网关与访问权限";case "library":return "资料库";case "settings":return "设置与备份";case "masking":return "隐私保护";case "agents":return "Agent";case "sessions":return "会话";default:return "扩展页面";
 }}
}
