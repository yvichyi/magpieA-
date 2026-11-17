package ai.magpie.android;

import android.app.Activity;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** One searchable native directory; host controls operate here instead of opening Main. */
public final class ToolsActivity extends Activity {
    private Ui ui;private HostActions actions;private LinearLayout rows;private TextView workspace,status;
    private EditText search;private ScrollView scroll;private android.widget.Button start;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean active,busy,wasReady;private int generation;private String scope="";
    private List<Page> pages=new ArrayList<>();
    private static final class Page{final String id,title;Page(String id,String title){this.id=id;this.title=title;}}
    private final Runnable poll=new Runnable(){public void run(){updateWorkspace();main.postDelayed(this,1000);}};
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new Ui(this);actions=new HostActions(this,ui,()->{generation++;busy=false;updateWorkspace();loadPages();});
        LinearLayout root=ui.column();root.setBackgroundColor(ui.bg);
        LinearLayout header=ui.row();header.setPadding(ui.dp(16),ui.dp(8),ui.dp(16),ui.dp(8));header.addView(ui.iconButton("back","返回模型",()->AppNavigation.primary(this,"home")));ui.grow(header,ui.text("工具",22,ui.ink,true));root.addView(header);
        LinearLayout content=ui.column();content.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(24));scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        workspace=ui.text("",13,ui.muted,false);workspace.setMinHeight(ui.dp(32));workspace.setOnClickListener(v->actions.workspace());content.addView(workspace);
        start=ui.button("启动本机网关",true,actions::start);content.addView(start);ui.space(content,12);
        search=new EditText(this);search.setSingleLine();search.setTextSize(16);search.setTypeface(ui.font(false,400));search.setTextColor(ui.ink);search.setHintTextColor(ui.muted);search.setHint("搜索功能、网关、资料库、备份…");search.setContentDescription("搜索工具");search.setMinHeight(ui.dp(48));search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.line));content.addView(search,new LinearLayout.LayoutParams(-1,-2));
        scope=AppNavigation.workspace(this);search.setText(saved!=null?saved.getString("query",""):getSharedPreferences("navigation",0).getString("toolsQuery:"+scope,""));rows=ui.column();content.addView(rows);status=ui.text("",12,ui.muted,false);status.setPadding(0,ui.dp(16),0,0);content.addView(status);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}public void afterTextChanged(Editable e){}});
        View bottom=ui.bottomNav("tools",id->{if(id.equals("tools"))scroll.smoothScrollTo(0,0);else AppNavigation.primary(this,id);});root.addView(bottom);ui.viewport(root,header,bottom);setContentView(root);try{pages=parse(getSharedPreferences("navigation",0).getString(scope,"[]"),false);}catch(Exception ignored){}root.setFocusableInTouchMode(true);root.requestFocus();render();
    }
    @Override protected void onResume(){super.onResume();UiMotion.enter(this);active=true;updateWorkspace();loadPages();main.post(poll);}
    @Override protected void onPause(){getSharedPreferences("navigation",0).edit().putString("toolsQuery:"+scope,search.getText().toString()).apply();active=false;generation++;busy=false;main.removeCallbacks(poll);UiMotion.pause(this);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("query",search.getText().toString());super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(search.hasFocus()){search.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);return;}AppNavigation.primary(this,"home");}
    private void updateWorkspace(){
        String next=AppNavigation.workspace(this);if(!next.equals(scope)){generation++;busy=false;wasReady=false;status.setText("");status.setOnClickListener(null);if(!scope.isEmpty())getSharedPreferences("navigation",0).edit().putString("toolsQuery:"+scope,search.getText().toString()).apply();scope=next;search.setText(getSharedPreferences("navigation",0).getString("toolsQuery:"+scope,""));pages=new ArrayList<>();try{pages=parse(getSharedPreferences("navigation",0).getString(scope,"[]"),false);}catch(Exception ignored){}render();}
        boolean remote=AppNavigation.remote(this),ready=AppNavigation.ready(this);String label=remote?"远程 · "+getSharedPreferences("host",0).getString("remoteAddress",""):"本机 · "+GatewayService.state;
        if(!label.contentEquals(workspace.getText()))workspace.setText(label);
        start.setVisibility(remote||ready?View.GONE:View.VISIBLE);boolean launching=GatewayService.state.startsWith("正在启动");start.setEnabled(!launching);start.setText(launching?"正在启动…":"启动本机网关");
        if(ready&&!wasReady&&active)loadPages();wasReady=ready;
    }
    private void render(){
        if(rows==null)return;rows.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;
        count+=section("接入与路由",q,new String[][]{{"subscription","订阅登录与高级添加","登录订阅，或使用完整服务商参数"},{"gateway","网关与访问密钥","接入地址、共享、密钥和调用配额"},{"routing","请求路由","查看请求如何分配、回退与转换"},{"masking","隐私脱敏","配置请求内容的隐私保护"}});
        count+=section("扩展与资料",q,new String[][]{{"library","资料库","指令、MCP、技能、子 Agent 与资源"},{"agents","Agent 配置",AppNavigation.remote(this)?"管理远程电脑的 Agent 接入":"连接远程工作空间后管理电脑 Agent"},{"sessions","会话",AppNavigation.remote(this)?"查看远程工作空间的会话":"连接远程工作空间后查看会话"}});
        if(!pages.isEmpty()){List<String[]> entries=new ArrayList<>();for(Page p:pages)entries.add(new String[]{p.id,p.title,"已安装插件提供的功能页面"});count+=section("插件页面",q,entries.toArray(new String[0][]));}
        count+=section("应用与数据",q,new String[][]{{"settings","设置与备份","模型行为、偏好、加密备份与恢复"},{"workspace","工作空间","切换本机或远程连接，启动与停止网关"},{"diagnostics","运行诊断","本机核心状态与经过脱敏的日志"},{"about","关于与开源许可","Android "+HostActions.APP_VERSION+" · 核心 v"+HostActions.CORE_VERSION}});
        if(count==0){ui.space(rows,24);rows.addView(ui.text("没有匹配的功能",18,ui.ink,true));Button clear=ui.button("清除搜索",false,()->search.setText(""));rows.addView(clear);}
    }
    private int section(String title,String q,String[][] entries){
        List<String[]> found=new ArrayList<>();for(String[] item:entries)if(q.isEmpty()||(title+" "+item[1]+" "+item[2]).toLowerCase(Locale.ROOT).contains(q))found.add(item);if(found.isEmpty())return 0;
        ui.space(rows,24);rows.addView(ui.text(title,12,ui.muted,true));ui.space(rows,8);
        for(String[] item:found){LinearLayout row=ui.row();row.setPadding(0,ui.dp(14),0,ui.dp(14));row.setMinimumHeight(ui.dp(64));LinearLayout text=ui.column();text.addView(ui.text(item[1],17,ui.ink,true));ui.space(text,5);text.addView(ui.text(item[2],13,ui.muted,false));ui.grow(row,text);row.addView(new LineIcon(this,"chevron",ui.muted),new LinearLayout.LayoutParams(ui.dp(20),ui.dp(20)));row.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT,0));row.setContentDescription("打开"+item[1]);row.setOnClickListener(v->open(item[0],item[1]));rows.addView(row,new LinearLayout.LayoutParams(-1,-2));ui.rule(rows,0);}
        return found.size();
    }
    private void open(String id,String title){
        switch(id){case"workspace":actions.workspace();return;case"diagnostics":actions.diagnostics();return;case"about":actions.about();return;
            case"agents":case"sessions":if(!AppNavigation.remote(this)){actions.remote();return;}break;
            case"subscription":AppNavigation.detail(this,"providers","addProvider","true");return;
        }
        AppNavigation.detail(this,id,"pageTitle",title);
    }
    private static List<Page> parse(String raw,boolean api)throws JSONException{
        JSONArray values=new JSONArray(raw);List<Page> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        if(values.length()>256)throw new JSONException("Too many plugin pages");
        for(int i=0;i<values.length();i++){JSONObject o=values.getJSONObject(i);String id=o.getString(api?"key":"id"),title=o.getString("title");JSONObject words=o.optJSONObject("titles");if(words!=null)title=words.optString("zh",title);
            if(id.isEmpty()||id.length()>160||title.trim().isEmpty()||title.length()>180||id.indexOf('\n')>=0)throw new JSONException("Invalid plugin page");if(!seen.add(id))continue;result.add(new Page(id,title));}
        return result;
    }
    private void loadPages(){
        if(!active||busy||!AppNavigation.ready(this))return;busy=true;int request=++generation;String space=scope,address=AppNavigation.origin(this),key=AppNavigation.key(this);status.setText("正在更新插件页面…");
        new Thread(()->{try{List<Page> list=parse(new CoreApi(address,key).get("/api/plugin-pages"),true);JSONArray cache=new JSONArray();for(Page p:list)cache.put(new JSONObject().put("id",p.id).put("title",p.title));
                main.post(()->{if(!active||request!=generation||!space.equals(AppNavigation.workspace(this))||!address.equals(AppNavigation.origin(this))||!key.equals(AppNavigation.key(this)))return;busy=false;pages=list;getSharedPreferences("navigation",0).edit().putString(space,cache.toString()).apply();status.setText(list.isEmpty()?"安装插件提供的独立页面会显示在这里。":"插件页面已更新");render();});
            }catch(Exception e){main.post(()->{if(!active||request!=generation)return;busy=false;status.setText("插件页面未更新，保留本工作空间的上次记录。点此重试。");status.setOnClickListener(v->loadPages());});}
        },"magpie-navigation-pages").start();
    }
}
