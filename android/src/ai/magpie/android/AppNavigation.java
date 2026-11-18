package ai.magpie.android;
import android.app.Activity;
import android.content.*;
import android.widget.Toast;
/** Canonical root ownership and secure advanced entry points. */
final class AppNavigation {
 static boolean remote(Context c){return c.getSharedPreferences("host",0).getBoolean("remote",false);}
 static String origin(Context c){return remote(c)?c.getSharedPreferences("host",0).getString("remoteAddress",""):GatewayService.managementOrigin;}
 static String key(Context c){return remote(c)?c.getSharedPreferences("host",0).getString("remoteKey",""):GatewayService.key(c);}
 static String workspace(Context c){
  try{return ModelFavorites.scope(remote(c),c.getSharedPreferences("host",0).getString("remoteAddress",""));}
  catch(IllegalArgumentException invalid){return "remote:unconfigured";}
 }
 static boolean ready(Context c){return remote(c)||GatewayService.ready;}
 static String currentRoot(Activity a){if(a instanceof MainActivity)return ((MainActivity)a).currentRootId();if(a instanceof UsageActivity)return "usage";String p=a.getIntent().getStringExtra("parentRoot");return p==null?"connect":NavigationModel.section(p);}
 static void primary(Activity a,String id){
  if(a instanceof MainActivity){((MainActivity)a).openRoot(id);return;}
  if("tools".equals(id)){UiMotion.go(a,new Intent(a,ToolsActivity.class).putExtra("parentRoot",currentRoot(a)),false);return;}
  Intent i=new Intent(a,MainActivity.class).putExtra("root",NavigationModel.section(id)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);if("groups".equals(id))i.putExtra("catalogTab",1);UiMotion.go(a,i,true);
 }
 static Intent detailIntent(Activity a,String view){return new Intent(a,ManagementActivity.class).putExtra("view",view).putExtra("remote",remote(a)).putExtra("parentRoot",currentRoot(a));}
 static void detail(Activity a,String view){detail(a,view,null,null);}
 static void detail(Activity a,String view,String extra,String value){
  if(!ready(a)){Toast.makeText(a,"请先启动网关或连接远程工作空间",Toast.LENGTH_SHORT).show();return;}
  Intent i=detailIntent(a,view);if(extra!=null){if(extra.equals("addProvider")||extra.equals("addGroup"))i.putExtra(extra,Boolean.parseBoolean(value));else i.putExtra(extra,value);}UiMotion.go(a,i,false);
 }
 static String parent(Activity a,String view){String p=a.getIntent().getStringExtra("parentRoot");for(String root:NavigationModel.ROOTS)if(root.equals(p))return root;return NavigationModel.parent(view);}
}
