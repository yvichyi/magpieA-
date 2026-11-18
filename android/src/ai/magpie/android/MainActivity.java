package ai.magpie.android;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
/** Native roots and details share one host and retain their own context. */
public final class MainActivity extends Activity implements ScreenHost {
 private Ui ui; private GlassFrame glass; private FrameLayout content; private LinearLayout header;
 private final Map<String,Root> roots=new LinkedHashMap<>();
 private String rootId="connect",observedScope=""; private boolean resumed;
 private final Handler main=new Handler(Looper.getMainLooper());
 private static final class Entry { final AppScreen page;final String title;Entry(AppScreen p,String t){page=p;title=t;} }
 private static final class Root { final AppScreen base;AppScreen page;String title="";final ArrayDeque<Entry> stack=new ArrayDeque<>();Root(AppScreen p){base=page=p;} }
 private final Runnable poll=new Runnable(){public void run(){if(!resumed)return;if(!observedScope.equals(scope()))resetWorkspace();main.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle state){
  super.onCreate(state);ui=new Ui(this);if(state!=null)rootId=state.getString("root","connect");
  if(getIntent().hasExtra("root"))rootId=normalize(getIntent().getStringExtra("root"));else if(getIntent().hasExtra("catalogTab"))rootId="home";
  glass=new GlassFrame(this);LinearLayout scene=ui.column();scene.setBackgroundColor(ui.bg);
  header=ui.row();header.setMinimumHeight(ui.dp(60));header.setPadding(ui.dp(14),ui.dp(4),ui.dp(12),ui.dp(4));scene.addView(header);
  content=new FrameLayout(this);scene.addView(content,new LinearLayout.LayoutParams(-1,0,1));glass.setScene(scene);setContentView(glass);
  observedScope=scope();openRoot(rootId);
  android.content.SharedPreferences p=getSharedPreferences("host",0);
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!p.getBoolean("askedNotifications",false)){p.edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);}
  if(!AppNavigation.remote(this)&&!p.getBoolean("launched",false)){p.edit().putBoolean("launched",true).apply();new HostActions(this,ui,this::refresh).start();}
  if(getIntent().getBooleanExtra("showFunctions",false))main.post(this::settings);
 }
 @Override protected void onResume(){super.onResume();resumed=true;glass.refreshOptions();if(!observedScope.equals(scope()))resetWorkspace();else if(current()!=null)current().page.onResume();main.removeCallbacks(poll);main.post(poll);}
 @Override protected void onPause(){resumed=false;main.removeCallbacks(poll);if(current()!=null)current().page.onPause();super.onPause();}
 @Override protected void onDestroy(){for(Root r:roots.values())dispose(r);roots.clear();super.onDestroy();}
 @Override protected void onSaveInstanceState(Bundle b){b.putString("root",rootId);super.onSaveInstanceState(b);}
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);if(i.getBooleanExtra("showFunctions",false)){settings();return;}openRoot(i.hasExtra("root")?i.getStringExtra("root"):i.hasExtra("catalogTab")?"home":rootId);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(LedgerExport.onActivityResult(this,request,result,data))return;if(request==17&&result==RESULT_OK){openRoot("home");Root r=roots.get("home");if(r!=null&&r.base instanceof CatalogScreen&&data!=null)((CatalogScreen)r.base).focusProvider(data.getStringExtra("providerId"));refresh();}}
 @Override public void onBackPressed(){Root r=current();if(r==null){finish();return;}if(r.page.onBack())return;if(!r.stack.isEmpty()){r.page.dispose();Entry e=r.stack.pop();r.page=e.page;r.title=e.title;attach();}else if(!rootId.equals("connect"))openRoot("connect");else super.onBackPressed();}
 private String normalize(String id){return "groups".equals(id)?"home":Arrays.asList(NavigationModel.ROOTS).contains(id)?id:"connect";}
 private Root current(){return roots.get(rootId);}
 private String scope(){return AppNavigation.workspace(this)+"\n"+AppNavigation.origin(this)+"\n"+AppNavigation.key(this);}
 private void dispose(Root r){Set<AppScreen> pages=new HashSet<>();pages.add(r.page);pages.add(r.base);for(Entry e:r.stack)pages.add(e.page);for(AppScreen p:pages)p.dispose();}
 private void resetWorkspace(){for(Root r:roots.values())dispose(r);roots.clear();observedScope=scope();openRoot(rootId);}
 private AppScreen create(String id){switch(id){case"home":return new CatalogScreen(this);case"plugins":return new PluginsScreen(this);case"usage":return new InsightsScreen(this);default:return new ConnectScreen(this);}}
 @Override public Activity activity(){return this;}
 @Override public Ui ui(){return ui;}
 public String currentRootId(){return rootId;}
 String catalogSection(){return rootId;}
 @Override public void openRoot(String id){if("tools".equals(id)){settings();return;}if(current()!=null)current().page.onPause();rootId=normalize(id);if(!roots.containsKey(rootId))roots.put(rootId,new Root(create(rootId)));attach();}
 private void attach(){Root r=current();if(r==null)return;View v=r.page.view();if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);content.removeAllViews();content.addView(v,new FrameLayout.LayoutParams(-1,-1));updateHeader();glass.setDock(r.stack.isEmpty()?ui.bottomNav(rootId,this::openRoot):null);if(resumed)r.page.onResume();}
 private void updateHeader(){
  if(current()==null)return;header.removeAllViews();boolean detail=!current().stack.isEmpty();
  if(detail)header.addView(ui.iconButton("back","返回上一页",this::onBackPressed));else{LineIcon bird=new LineIcon(this,"brand",ui.ink);header.addView(bird,new LinearLayout.LayoutParams(ui.dp(26),ui.dp(26)));ui.gap(header,10);}
  TextView title=ui.text(detail?current().title:"Magpie",detail?19:20,ui.ink,true);ui.grow(header,title);
  if(!detail){TextView workspace=ui.text(AppNavigation.remote(this)?"远程 ⌄":"本机 ⌄",13,ui.ink,true);workspace.setGravity(Gravity.CENTER);workspace.setPadding(ui.dp(13),ui.dp(8),ui.dp(13),ui.dp(8));workspace.setMinHeight(ui.dp(42));workspace.setBackground(ui.ripple(ui.dark?0x18FFFFFF:0xA8FFFFFF,22));workspace.setContentDescription("选择工作空间");workspace.setOnClickListener(v->new HostActions(this,ui,this::refresh).workspace());header.addView(workspace);ui.gap(header,4);header.addView(ui.iconButton("settings","打开设置",this::settings));}
 }
 private void settings(){startActivity(new Intent(this,ToolsActivity.class).putExtra("parentRoot",rootId));overridePendingTransition(0,0);}
 @Override public void openDetail(String name,AppScreen page){Root r=current();if(r==null)return;r.page.onPause();r.stack.push(new Entry(r.page,r.title));r.page=page;r.title=name;attach();}
 @Override public void advanced(String view){AppNavigation.detail(this,view);}
 @Override public void advanced(String view,String key,String value){AppNavigation.detail(this,view,key,value);}
 @Override public void connect(String reference){openRoot("connect");Root r=current();while(!r.stack.isEmpty()){r.page.dispose();Entry e=r.stack.pop();r.page=e.page;r.title=e.title;}if(r.base instanceof ConnectScreen)((ConnectScreen)r.base).setTarget(reference);attach();}
 @Override public void refresh(){if(!observedScope.equals(scope()))resetWorkspace();else if(current()!=null){current().page.onPause();if(resumed)current().page.onResume();updateHeader();glass.refreshOptions();}}
}
