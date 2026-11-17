package ai.magpie.android;

import android.app.Activity;
import android.content.*;
import android.widget.Toast;

/** The five roots and explicit detail parents, shared by native and web screens. */
final class AppNavigation {
    static boolean remote(Context c){return c.getSharedPreferences("host",0).getBoolean("remote",false);}
    static String origin(Context c){return remote(c)?c.getSharedPreferences("host",0).getString("remoteAddress",""):GatewayService.managementOrigin;}
    static String key(Context c){return remote(c)?c.getSharedPreferences("host",0).getString("remoteKey",""):GatewayService.key(c);}
    static String workspace(Context c){return ModelFavorites.scope(remote(c),c.getSharedPreferences("host",0).getString("remoteAddress",""));}
    static boolean ready(Context c){return remote(c)||GatewayService.ready;}
    static String currentRoot(Activity a){
        if(a instanceof ToolsActivity)return "tools";
        if(a instanceof UsageActivity)return "usage";
        if(a instanceof MainActivity)return ((MainActivity)a).catalogSection();
        return a.getIntent().getStringExtra("parentRoot")==null?"tools":a.getIntent().getStringExtra("parentRoot");
    }
    static void primary(Activity a,String id){
        Intent target;
        if("home".equals(id)||"groups".equals(id))target=new Intent(a,MainActivity.class).putExtra("catalogTab","groups".equals(id)?1:0);
        else if("usage".equals(id))target=new Intent(a,UsageActivity.class).putExtra("remote",remote(a));
        else if("tools".equals(id))target=new Intent(a,ToolsActivity.class);
        else if("plugins".equals(id)){
            if(!ready(a)){Toast.makeText(a,"请先启动网关或连接远程工作空间",Toast.LENGTH_SHORT).show();return;}
            target=new Intent(a,ManagementActivity.class).putExtra("view","plugins").putExtra("remote",remote(a)).putExtra("parentRoot","home");
        }else throw new IllegalArgumentException("Unknown primary destination");
        // A new web root discards its approved old page; stateful native roots are reused.
        target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if(!"plugins".equals(id))target.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        UiMotion.go(a,target,!(a instanceof MainActivity));
    }
    static Intent detailIntent(Activity a,String view){
        return new Intent(a,ManagementActivity.class).putExtra("view",view).putExtra("remote",remote(a)).putExtra("parentRoot",currentRoot(a));
    }
    static void detail(Activity a,String view){detail(a,view,null,null);}
    static void detail(Activity a,String view,String extra,String value){
        if(!ready(a)){Toast.makeText(a,"请先启动网关或连接远程工作空间",Toast.LENGTH_SHORT).show();return;}
        Intent target=detailIntent(a,view);if(extra!=null){if(extra.equals("addProvider")||extra.equals("addGroup"))target.putExtra(extra,Boolean.parseBoolean(value));else target.putExtra(extra,value);}
        UiMotion.go(a,target,false);
    }
    static String parent(Activity a,String view){
        String requested=a.getIntent().getStringExtra("parentRoot");
        for(String root:NavigationModel.ROOTS)if(root.equals(requested))return root;
        return NavigationModel.parent(view);
    }
}
