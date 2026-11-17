package ai.magpie.android;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

/** Android control centre. All configuration screens use the real upstream UI. */
public final class MainActivity extends Activity {
    private Ui ui;
    private TextView status, caption;
    private Button primary;
    private TextView endpoint,metricsHint;
    private LinearLayout catalogList,homeNavigation;
    private ScrollView homeScroll;
    private TextView catalogTitle;
    private View catalogAction;
    private Dialog connectionSheet,modelPicker;
    private LinearLayout catalogFilters;
    private int catalogFilter;
    private java.util.Set<String> favorites=new java.util.HashSet<>();
    private EditText search;
    private boolean searchExpanded;
    private WorkspaceSnapshot catalog;
    private int catalogTab,catalogLimit=30,pendingScroll;
    private String focusedProvider="",focusedName="";
    private static final int SETUP_PROVIDER=17;
    private android.view.View statusDot;
    private TextView todaySummary;
    private TextView todayHint;
    private boolean todayBusy;
    private long todayAt;
    private long metricsAt;
    private boolean metricsBusy,remoteConnected,homeActive;
    private int homeGeneration;
    private String observedOrigin="",homeWorkspace="";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private android.content.SharedPreferences prefs;
    private final Runnable poll = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs = getSharedPreferences("host", MODE_PRIVATE);catalogTab=getIntent().getIntExtra("catalogTab",0)==1?1:0;if(state!=null){catalogTab=state.getInt("catalogTab",0);pendingScroll=state.getInt("scrollY",0);}showHome();if(state!=null){catalogFilter=state.getInt("catalogFilter",0);focusedProvider=state.getString("focusedProvider","");focusedName=state.getString("focusedName","");renderCatalogFilters();}if(state!=null&&!state.getString("query","").isEmpty()){searchExpanded=true;search.setVisibility(View.VISIBLE);search.setText(state.getString("query",""));}
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && !prefs.getBoolean("askedNotifications", false)) {
            prefs.edit().putBoolean("askedNotifications", true).apply(); requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (!remote() && !prefs.getBoolean("launched", false)) {
            prefs.edit().putBoolean("launched", true).apply(); startCore();
        }
        if(state==null&&getIntent().getBooleanExtra("showFunctions",false))handler.post(this::moreDialog);
    }
    @Override protected void onResume() { super.onResume();UiMotion.enter(this);if(!homeWorkspace.equals(AppNavigation.workspace(this)))showHome();homeActive=true;homeGeneration++;metricsAt=0;todayAt=0;metricsBusy=false;todayBusy=false;handler.post(poll); }
    @Override protected void onPause() { UiMotion.pause(this);homeActive=false;homeGeneration++;handler.removeCallbacks(poll);super.onPause(); }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("showFunctions",false)){moreDialog();return;}selectCatalog(intent.getIntExtra("catalogTab",0),false);if(intent.hasExtra("focusProvider"))focusProvider(intent.getStringExtra("focusProvider"),intent.getStringExtra("focusName"));}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==SETUP_PROVIDER&&result==RESULT_OK&&data!=null)focusProvider(data.getStringExtra("providerId"),data.getStringExtra("providerName"));}
    private void focusProvider(String id,String name){if(id==null||id.isEmpty()||id.length()>160)return;selectCatalog(0,false);search.setText("");search.clearFocus();searchExpanded=false;UiMotion.reveal(search,false);((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);catalogFilter=0;renderCatalogFilters();focusedProvider=id;focusedName=name==null?id:name;catalogLimit=30;metricsAt=0;renderCatalog();homeScroll.scrollTo(0,0);metricsHint.setText("查看 "+focusedName+" · 点此显示全部");}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("catalogFilter",catalogFilter);out.putString("focusedProvider",focusedProvider);out.putString("focusedName",focusedName);out.putInt("catalogTab",catalogTab);out.putInt("scrollY",homeScroll==null?0:homeScroll.getScrollY());out.putString("query",search==null?"":search.getText().toString());super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(search!=null&&searchExpanded){toggleSearch();return;}if(!focusedProvider.isEmpty()){clearProviderFocus();return;}if(catalogTab==1){selectCatalog(0,true);return;}UiMotion.close(this);}
    private boolean remote() { return prefs.getBoolean("remote", false); }

    private void showHome() {
        homeWorkspace=AppNavigation.workspace(this);focusedProvider="";focusedName="";catalogFilter=0;loadFavorites();ui=new Ui(this);homeGeneration++;metricsAt=0;todayAt=0;metricsBusy=false;todayBusy=false;remoteConnected=false;catalog=null;observedOrigin="";searchExpanded=false;
        LinearLayout root=ui.column();root.setBackgroundColor(ui.bg);root.setFocusableInTouchMode(true);
        LinearLayout header=ui.row();header.setPadding(ui.dp(24),ui.dp(8),ui.dp(12),ui.dp(8));
        header.addView(new LineIcon(this,"brand",ui.ink),new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)));ui.gap(header,9);ui.grow(header,ui.digits("Magpie",22));
        TextView workspace=ui.text(remote()?"远程 ⌄":"本机 ⌄",14,ui.ink,false);workspace.setMinHeight(ui.dp(44));workspace.setGravity(Gravity.CENTER);workspace.setContentDescription("选择工作空间");workspace.setOnClickListener(v->modeDialog());header.addView(workspace);root.addView(header);
        homeScroll=new ScrollView(this);homeScroll.setVerticalScrollBarEnabled(false);homeScroll.setFillViewport(true);
        LinearLayout body=ui.column();body.setPadding(ui.dp(24),0,ui.dp(24),ui.dp(24));homeScroll.addView(body);root.addView(homeScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout gateway=ui.row();gateway.setPadding(0,ui.dp(8),0,ui.dp(12));
        LinearLayout info=ui.column();LinearLayout state=ui.row();statusDot=new View(this);statusDot.setBackground(ui.shape(ui.muted,4,false));state.addView(statusDot,new LinearLayout.LayoutParams(ui.dp(6),ui.dp(6)));ui.gap(state,8);status=ui.text("已停止",13,ui.muted,false);ui.grow(state,status);endpoint=ui.text("本机网关",13,ui.muted,false);endpoint.setMaxLines(2);info.addView(state,new LinearLayout.LayoutParams(-2,-2));ui.space(info,4);info.addView(endpoint,new LinearLayout.LayoutParams(-2,-2));caption=ui.text("",12,ui.muted,false);caption.setVisibility(View.GONE);info.addView(caption,new LinearLayout.LayoutParams(-2,-2));
        primary=ui.button("启动本机网关",false,()->{if(remote()||GatewayService.ready)open("gateway");else startCore();});primary.setTextSize(13);primary.setPadding(ui.dp(10),0,ui.dp(10),0);primary.setMinWidth(0);primary.setMinimumWidth(0);primary.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT,6));body.addView(new AdaptivePair(ui,info,primary,8));ui.rule(body,0);ui.space(body,22);
        LinearLayout heading=ui.row();catalogTitle=ui.text(catalogTab==1?"模型分组":"模型",28,ui.ink,true);ui.grow(heading,catalogTitle);
        catalogAction=ui.iconButton("add",catalogTab==1?"新建分组":"添加服务商",()->{if(catalogTab==0)providerEditor(null);else groupEditor(null);});heading.addView(catalogAction);heading.addView(ui.iconButton("search","搜索模型与分组",this::toggleSearch));body.addView(heading);
        search=new EditText(this);search.setSingleLine();search.setTextSize(15);search.setTypeface(ui.font(false,400));search.setTextColor(ui.ink);search.setHintTextColor(ui.muted);search.setHint("搜索名称或完整标识");search.setContentDescription("搜索输入框");search.setPadding(0,ui.dp(12),0,ui.dp(12));search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.line));search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);search.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();return true;}return false;});search.setVisibility(View.GONE);body.addView(search,new LinearLayout.LayoutParams(-1,-2));
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){if(s.length()>0&&!focusedProvider.isEmpty()){focusedProvider="";focusedName="";if(catalog!=null)metricsHint.setText(catalog.providers.size()+" 个服务商 · "+catalog.groups.size()+" 个模型组");}catalogLimit=30;renderCatalog();}public void afterTextChanged(android.text.Editable e){}});
        catalogFilters=ui.row();body.addView(catalogFilters);renderCatalogFilters();
        metricsHint=ui.text("启动网关后读取模型",13,ui.muted,false);metricsHint.setPadding(0,ui.dp(8),0,ui.dp(12));metricsHint.setMinHeight(ui.dp(44));metricsHint.setOnClickListener(v->{if(!focusedProvider.isEmpty())clearProviderFocus();metricsAt=0;refreshMetrics();});body.addView(metricsHint);
        catalogList=ui.column();body.addView(catalogList);renderCatalog();ui.space(body,24);
        LinearLayout dailyHead=ui.row();ui.grow(dailyHead,ui.text("今日用量",14,ui.muted,false));dailyHead.addView(textAction("查看用量 →",()->open("usage")));body.addView(dailyHead);
        todaySummary=ui.text("— 次调用  ·  — Token",15,ui.ink,false);todaySummary.setTypeface(ui.font(false,500));todaySummary.setLineSpacing(ui.dp(5),1.05f);body.addView(todaySummary);
        todayHint=ui.text("网关就绪后读取今日用量",12,ui.muted,false);todayHint.setPadding(0,ui.dp(8),0,ui.dp(20));todayHint.setOnClickListener(v->{todayAt=0;refreshToday();});body.addView(todayHint);
        LinearLayout dock=ui.column();dock.setPadding(ui.dp(24),ui.dp(10),ui.dp(24),ui.dp(8));ui.rule(root,0);dock.addView(ui.button("接入客户端",true,this::connectDialog));root.addView(dock);
        homeNavigation=ui.column();root.addView(homeNavigation);renderHomeNavigation();ui.viewport(root,header,dock,homeNavigation);setContentView(root);root.requestFocus();updateStatus();
    }
    private TextView textAction(String label,Runnable action){TextView v=ui.text(label,14,ui.ink,true);v.setMinHeight(ui.dp(44));v.setGravity(Gravity.CENTER_VERTICAL);v.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT,4));v.setOnClickListener(w->action.run());return v;}
    private void toggleSearch(){
        UiMotion.stay(this);
        boolean show=!searchExpanded;searchExpanded=show;UiMotion.reveal(search,show);
        android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(show){search.requestFocus();search.postDelayed(()->{if(searchExpanded&&search.hasFocus()&&homeActive)ime.showSoftInput(search,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);},180);}
        else{search.setText("");search.clearFocus();ime.hideSoftInputFromWindow(search.getWindowToken(),0);}
    }
    String catalogSection(){return catalogTab==1?"groups":"home";}
    private void renderHomeNavigation(){homeNavigation.removeAllViews();homeNavigation.addView(ui.bottomNav(catalogSection(),id->{if(id.equals("home")||id.equals("groups"))selectCatalog(id.equals("groups")?1:0,true);else AppNavigation.primary(this,id);}));}
    private void selectCatalog(int tab,boolean animate){
        UiMotion.stay(this);
        if(catalogTitle==null){catalogTab=tab;return;}boolean changed=catalogTab!=tab;catalogTab=tab;catalogLimit=30;catalogTitle.setText(tab==1?"模型分组":"模型");catalogAction.setContentDescription(tab==1?"新建分组":"添加服务商");
        if(changed){search.setText("");search.clearFocus();searchExpanded=false;UiMotion.reveal(search,false);((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);if(animate)UiMotion.swap(catalogList,this::renderCatalog);else renderCatalog();homeScroll.smoothScrollTo(0,0);}
        renderHomeNavigation();renderCatalogFilters();
    }
    private void renderCatalog(){
        if(catalogList==null)return;catalogList.removeAllViews();String query=search==null?"":search.getText().toString();
        if(catalog==null){ui.space(catalogList,28);catalogList.addView(ui.text(remote()||GatewayService.ready?"正在连接工作空间…":"从一个服务商开始",18,ui.ink,true));ui.space(catalogList,10);catalogList.addView(ui.text(remote()||GatewayService.ready?"模型与分组会在连接后显示。":"启动网关，添加你的服务商与模型。",14,ui.muted,false));ui.space(catalogList,28);return;}
        int shown=0,rendered=0,total=0;
        if(catalogTab==0){
            for(WorkspaceSnapshot.Provider p:catalog.providers)if((focusedProvider.isEmpty()||focusedProvider.equals(p.id))&&p.matches(query))total+=catalogModels(p,query).size();
            for(WorkspaceSnapshot.Provider p:catalog.providers){if((!focusedProvider.isEmpty()&&!focusedProvider.equals(p.id))||!p.matches(query)||rendered>=catalogLimit)continue;shown++;
                java.util.List<WorkspaceSnapshot.Model> models=catalogModels(p,query);if(models.isEmpty()&&catalogFilter!=0){shown--;continue;}
                LinearLayout head=ui.column();head.setPadding(0,ui.dp(12),0,ui.dp(2));head.addView(ui.text(p.name,16,ui.ink,true));ui.space(head,4);LinearLayout details=ui.row();ui.grow(details,ui.text(p.off?"服务商已停用":((catalogFilter!=0||!query.trim().isEmpty())?models.size()+" / "+p.models.size():String.valueOf(focusedProvider.isEmpty()?p.models.size():models.size()))+" 个模型",12,ui.muted,false));ui.gap(details,12);details.addView(textAction("编辑",()->providerEditor(p.id)));head.addView(details);catalogList.addView(head);
                if(models.isEmpty()){ui.space(catalogList,12);catalogList.addView(ui.text("进入编辑获取或添加模型",14,ui.muted,false));ui.space(catalogList,20);}
                for(WorkspaceSnapshot.Model m:models){if(rendered>=catalogLimit)break;rendered++;LinearLayout row=ui.row();row.setPadding(0,ui.dp(14),0,ui.dp(14));LinearLayout text=ui.column();TextView name=ui.text(m.name,16,ui.ink,false);name.setTypeface(ui.font(false,500));text.addView(name);ui.space(text,7);TextView ref=ui.text(m.reference,12,ui.muted,false);text.addView(ref);ui.grow(row,text);ui.gap(row,10);
                    if(p.off||!m.on){row.addView(ui.text(p.off?"停用":"隐藏",12,ui.muted,false));ui.gap(row,6);}row.addView(ui.copyButton("复制模型标识 "+m.reference,()->copyReference(m.reference)));row.addView(ui.iconButton("more","模型操作 "+m.reference,()->modelActions(p,m)));
                    row.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT,0));row.setContentDescription("接入模型 "+m.reference);row.setOnClickListener(v->{if(!p.off&&m.on)showConnection(m.reference);else providerEditor(p.id);});catalogList.addView(row);ui.rule(catalogList,0);
                }
                ui.space(catalogList,12);
            }
        }else{
            for(WorkspaceSnapshot.Group g:catalog.groups)if(g.matches(query))total++;
            for(WorkspaceSnapshot.Group g:catalog.groups){if(!g.matches(query)||rendered>=catalogLimit)continue;shown++;rendered++;LinearLayout head=ui.row();head.setPadding(0,ui.dp(12),0,ui.dp(8));ui.grow(head,ui.text(g.name,17,ui.ink,true));head.addView(ui.text(g.strategy(),13,ui.muted,false));ui.gap(head,8);head.addView(ui.copyButton("复制分组标识 "+g.reference,()->copyReference(g.reference)));catalogList.addView(head);catalogList.addView(ui.text(g.reference,13,ui.muted,false));ui.space(catalogList,14);
                catalogList.addView(ui.text("固定成员",12,ui.muted,false));for(String member:g.members.subList(0,Math.min(20,g.members.size()))){TextView ref=ui.text(member,14,ui.ink,false);ref.setPadding(0,ui.dp(7),0,ui.dp(7));catalogList.addView(ref);}if(g.members.isEmpty())catalogList.addView(ui.text("无固定成员",14,ui.muted,false));if(g.members.size()>20)catalogList.addView(ui.text("另有 "+(g.members.size()-20)+" 项，在管理页查看",13,ui.muted,false));
                if(!g.matched.isEmpty()){ui.space(catalogList,10);catalogList.addView(ui.text("动态匹配成员",12,ui.muted,false));for(String member:g.matched.subList(0,Math.min(20,g.matched.size()))){catalogList.addView(ui.text(member,14,ui.ink,false));}if(g.matched.size()>20)catalogList.addView(ui.text("另有 "+(g.matched.size()-20)+" 项，在管理页查看",13,ui.muted,false));}
                LinearLayout actions=ui.row();ui.grow(actions,textAction("接入此分组",()->showConnection(g.reference)));actions.addView(textAction("编辑分组 →",()->groupEditor(g.id)));catalogList.addView(actions);ui.rule(catalogList,0);ui.space(catalogList,12);
            }
        }
        if(rendered<total)catalogList.addView(textAction("显示更多 · 还有 "+(total-rendered)+" 项",()->{catalogLimit+=30;renderCatalog();}));
        if(shown==0&&!focusedProvider.isEmpty()){ui.space(catalogList,24);catalogList.addView(ui.text(metricsBusy?"正在刷新刚添加的服务商…":"未找到该服务商",18,ui.ink,true));ui.space(catalogList,12);catalogList.addView(ui.text("可点击上方显示全部，并确认保存结果。",14,ui.muted,false));return;}
        if(shown==0&&(!query.trim().isEmpty()||(catalogTab==0&&catalogFilter!=0))){ui.space(catalogList,24);catalogList.addView(ui.text(!query.trim().isEmpty()?"没有匹配的模型":catalogFilter==2?"还没有收藏模型":"暂无可用模型",18,ui.ink,true));ui.space(catalogList,10);catalogList.addView(ui.text(catalogFilter==2?"打开模型接入面板，点收藏即可保存在这里。":"可清除筛选，查看全部模型与服务商。",14,ui.muted,false));catalogList.addView(textAction(!query.trim().isEmpty()?"清除搜索":"查看全部模型",()->{search.setText("");catalogFilter=0;renderCatalogFilters();renderCatalog();}));return;}
        if(shown==0){ui.space(catalogList,24);catalogList.addView(ui.text(query.isEmpty()?(catalogTab==0?"添加你的第一个服务商":"创建一个模型分组"):"没有匹配的结果",18,ui.ink,true));ui.space(catalogList,10);catalogList.addView(ui.text(query.isEmpty()?(catalogTab==0?"使用 API Key 连接服务商，再获取可调用模型。":"将多个模型组成一组，按策略分流与备用切换。"):"尝试名称或完整模型标识。",14,ui.muted,false));ui.space(catalogList,16);catalogList.addView(ui.button(catalogTab==0?"添加服务商":"新建分组",false,()->{if(catalogTab==0)providerEditor(null);else groupEditor(null);}));}
    }
    private void modelActions(WorkspaceSnapshot.Provider provider,WorkspaceSnapshot.Model model){new AlertDialog.Builder(this,ui.dialogTheme).setTitle(model.name).setItems(new String[]{favorites.contains(model.reference)?"取消收藏":"收藏模型","复制模型标识","编辑服务商"},(d,w)->{if(w==0){boolean on=toggleFavorite(model.reference);Toast.makeText(this,on?"已收藏模型":"已取消收藏",Toast.LENGTH_SHORT).show();}else if(w==1){copyReference(model.reference);Toast.makeText(this,"已复制模型标识",Toast.LENGTH_SHORT).show();}else providerEditor(provider.id);}).show();}
    private void loadFavorites(){String scope=ModelFavorites.scope(remote(),prefs.getString("remoteAddress",""));favorites=new java.util.HashSet<>(getSharedPreferences("model-favorites",MODE_PRIVATE).getStringSet(scope,java.util.Collections.emptySet()));}
    private boolean toggleFavorite(String reference){boolean on=!favorites.remove(reference);if(on)favorites.add(reference);getSharedPreferences("model-favorites",MODE_PRIVATE).edit().putStringSet(ModelFavorites.scope(remote(),prefs.getString("remoteAddress","")),new java.util.HashSet<>(favorites)).apply();renderCatalog();return on;}
    private void clearProviderFocus(){focusedProvider="";focusedName="";renderCatalog();if(catalog!=null)metricsHint.setText(catalog.providers.size()+" 个服务商 · "+catalog.groups.size()+" 个模型组");}
    private void renderCatalogFilters(){if(catalogFilters==null)return;catalogFilters.removeAllViews();catalogFilters.setVisibility(catalogTab==0?View.VISIBLE:View.GONE);String[] labels={"全部","仅可用","收藏"};for(int i=0;i<labels.length;i++){final int selected=i;TextView t=textAction(labels[i],()->{catalogFilter=selected;catalogLimit=30;renderCatalogFilters();renderCatalog();});t.setGravity(Gravity.CENTER);t.setTextColor(i==catalogFilter?ui.ink:ui.muted);t.setTypeface(ui.font(false,i==catalogFilter?600:400));t.setBackground(ui.ripple(i==catalogFilter?ui.soft:android.graphics.Color.TRANSPARENT,4));t.setSelected(i==catalogFilter);catalogFilters.addView(t,new LinearLayout.LayoutParams(0,ui.dp(48),1));}}
    @Override protected void onDestroy(){if(connectionSheet!=null)connectionSheet.dismiss();if(modelPicker!=null)modelPicker.dismiss();super.onDestroy();}
    private void copyReference(String value){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Magpie 模型标识",value));}
    private java.util.List<WorkspaceSnapshot.Model> catalogModels(WorkspaceSnapshot.Provider provider,String query){java.util.List<WorkspaceSnapshot.Model> all=CatalogSelection.models(provider,query,catalogFilter,favorites);if(focusedProvider.isEmpty())return all;java.util.List<WorkspaceSnapshot.Model> available=new java.util.ArrayList<>();for(WorkspaceSnapshot.Model model:all)if(!provider.off&&model.on)available.add(model);return available;}
    private void advancedProvider(){if(!remote()&&!GatewayService.ready){Toast.makeText(this,"请先启动本机网关",Toast.LENGTH_SHORT).show();return;}UiMotion.go(this,AppNavigation.detailIntent(this,"providers").putExtra("view","providers").putExtra("remote",remote()).putExtra("addProvider",true),false);}
    private void providerEditor(String id){if(!remote()&&!GatewayService.ready){Toast.makeText(this,"请先启动本机网关",Toast.LENGTH_SHORT).show();return;}if(id==null){UiMotion.result(this,new Intent(this,ProviderSetupActivity.class).putExtra("remote",remote()),SETUP_PROVIDER);return;}Intent intent=AppNavigation.detailIntent(this,"providers").putExtra("view","providers").putExtra("remote",remote()).putExtra("providerId",id);UiMotion.go(this,intent,false);}
    private void showConnection(String reference){
        UiMotion.stay(this);
        if(remote()||(catalog!=null&&catalog.onNetwork)){open("gateway");return;}
        if(!GatewayService.ready||catalog==null){Toast.makeText(this,"网关就绪后再接入模型",Toast.LENGTH_SHORT).show();return;}
        if(!CatalogSelection.available(catalog,reference)){metricsAt=0;Toast.makeText(this,"此模型已不可用，请刷新后重试",Toast.LENGTH_SHORT).show();return;}
        if(connectionSheet!=null&&connectionSheet.isShowing())connectionSheet.dismiss();
        String gatewayAtOpen=catalog.gatewayUrl;
        boolean model=CatalogSelection.choices(catalog,"",favorites).stream().anyMatch(choice->choice.model.reference.equals(reference));
        connectionSheet=ConnectionSheet.show(this,ui,catalog.gatewayUrl,reference,()->open("gateway"),favorites.contains(reference),model?()->toggleFavorite(reference):null,()->!remote()&&GatewayService.ready&&catalog!=null&&!catalog.onNetwork&&catalog.gatewayUrl.equals(gatewayAtOpen)&&CatalogSelection.available(catalog,reference));
    }
    private void updateStatus(){
        if(status==null)return;
        String currentOrigin=remote()?prefs.getString("remoteAddress",""):GatewayService.ready?GatewayService.managementOrigin:"";
        if(!currentOrigin.equals(observedOrigin)){observedOrigin=currentOrigin;if(modelPicker!=null)modelPicker.dismiss();if(connectionSheet!=null)connectionSheet.dismiss();homeGeneration++;metricsBusy=false;todayBusy=false;metricsAt=0;todayAt=0;remoteConnected=false;catalog=null;renderCatalog();}
        if(remote()){
            status.setText(remoteConnected?"已连接":"远程");statusDot.setBackground(ui.shape(remoteConnected?ui.ink:ui.muted,8,false));
            String address=prefs.getString("remoteAddress","");try{endpoint.setText(new java.net.URI(address).getAuthority());}catch(Exception e){endpoint.setText("远程工作空间");}
            caption.setText("");caption.setVisibility(View.GONE);primary.setText("管理");primary.setEnabled(true);primary.setAlpha(1);refreshMetrics();refreshToday();
        }else{
            boolean ready=GatewayService.ready,starting="正在启动".equals(GatewayService.state);
            status.setText(ready?"运行中":starting?"启动中":"已停止");statusDot.setBackground(ui.shape(ready?ui.ink:ui.muted,8,false));endpoint.setText(catalog!=null&&!catalog.gatewayUrl.isEmpty()?catalog.gatewayUrl.replaceFirst("^https?://",""):"本机网关");
            boolean issue=GatewayService.state.startsWith("启动失败")||GatewayService.state.startsWith("核心已退出");caption.setText(issue?GatewayService.state:"");caption.setVisibility(issue?View.VISIBLE:View.GONE);
            primary.setText(ready?"管理":starting?"正在启动…":"启动本机网关");primary.setEnabled(!starting);primary.setAlpha(starting?.6f:1f);
            if(ready){refreshMetrics();refreshToday();}else{metricsHint.setText("启动网关后添加模型");if(todaySummary!=null)todaySummary.setText("— 次调用  ·  — Token");todayHint.setText("网关就绪后读取今日统计");todayAt=0;}
        }
    }
    private void refreshToday(){
        if(!homeActive||todayHint==null||todayBusy||(!remote()&&!GatewayService.ready))return;
        long now=SystemClock.elapsedRealtime();if(todayAt!=0&&now-todayAt<30000)return;
        String origin=remote()?prefs.getString("remoteAddress",""):GatewayService.managementOrigin;
        if(origin.isEmpty())return;
        String key=remote()?prefs.getString("remoteKey",""):GatewayService.key(this);int generation=homeGeneration;
        todayBusy=true;todayAt=now;
        new Thread(()->{try{
            UsageSnapshot s=UsageSnapshot.parse(new CoreApi(origin,key).get(UsageSnapshot.path("today",java.util.TimeZone.getDefault().getID())));
            if(!"today".equals(s.period))throw new IllegalArgumentException("统计范围不匹配");
            handler.post(()->{if(!todayCurrent(generation,origin))return;todayBusy=false;todaySummary.setText(UsageSnapshot.number(s.calls)+" 次调用  ·  "+UsageSnapshot.number(s.tokens())+" Token  ·  "+(s.costLabel().equals("待定价")?"费用待定价":"已计价 "+s.costLabel()));todayHint.setText(s.calls==0?"今日暂无调用 · 发起请求后自动更新":s.unpriced>0?s.priceNote():"USD · 缓存另列，详见统计页");});
        }catch(Exception e){handler.post(()->{if(!todayCurrent(generation,origin))return;todayBusy=false;todaySummary.setText("— 次调用  ·  — Token");todayHint.setText("今日统计读取失败 · 点此重试");});}},"magpie-today").start();
    }
    private boolean todayCurrent(int generation,String origin){return homeActive&&!isDestroyed()&&generation==homeGeneration&&(remote()||GatewayService.ready)&&origin.equals(remote()?prefs.getString("remoteAddress",""):GatewayService.managementOrigin);}
    private void refreshMetrics(){
        if(!homeActive)return;
        long now=SystemClock.elapsedRealtime();if(metricsBusy||(metricsAt!=0&&now-metricsAt<20000))return;
        String origin=remote()?prefs.getString("remoteAddress",""):GatewayService.managementOrigin;
        if(origin.isEmpty())return;String key=remote()?prefs.getString("remoteKey",""):GatewayService.key(this);int generation=homeGeneration;
        metricsBusy=true;metricsAt=now;if(catalog==null)metricsHint.setText("正在读取模型与分组…");
        new Thread(()->{try{
            CoreApi api=new CoreApi(origin,key);WorkspaceSnapshot result=WorkspaceSnapshot.parse(api.get("/api/providers"),api.get("/api/groups"));
            handler.post(()->{if(!todayCurrent(generation,origin))return;metricsBusy=false;boolean changed=!result.sameContent(catalog);catalog=result;metricsHint.setText(focusedProvider.isEmpty()?result.providers.size()+" 个服务商 · "+result.groups.size()+" 个模型组":"查看 "+focusedName+" · 点此显示全部");remoteConnected=true;if(changed){if(modelPicker!=null)modelPicker.dismiss();int y=pendingScroll>0?pendingScroll:homeScroll.getScrollY();pendingScroll=0;renderCatalog();homeScroll.post(()->homeScroll.scrollTo(0,y));}});
        }catch(Exception e){handler.post(()->{if(!todayCurrent(generation,origin))return;metricsBusy=false;metricsHint.setText(catalog==null?"读取失败 · 点此重试，或进入管理页": "更新失败 · 保留上次列表，点此重试");if(catalog==null){catalogList.removeAllViews();ui.space(catalogList,24);catalogList.addView(ui.text("工作空间暂不可用",14,ui.ink,false));}remoteConnected=false;});}},"magpie-catalog").start();
    }
    private void startCore() {
        try { GatewayService.state = "正在启动"; startForegroundService(new Intent(this, GatewayService.class)); updateStatus(); }
        catch (Exception e) { GatewayService.state = "启动失败：" + e.getMessage(); updateStatus(); }
    }
    private void open(String view){if("usage".equals(view)){AppNavigation.primary(this,"usage");return;}AppNavigation.detail(this,view);}
    private void groupEditor(String id){AppNavigation.detail(this,"groups",id==null?"addGroup":"groupId",id==null?"true":id);}
    private void modeDialog(){new HostActions(this,ui,()->{showHome();updateStatus();}).workspace();}
    private void moreDialog(){AppNavigation.primary(this,"tools");}
    private void connectDialog() {
        UiMotion.stay(this);
        if(remote()){open("gateway");return;}
        if(catalog==null){Toast.makeText(this,"请先启动网关并添加服务商",Toast.LENGTH_SHORT).show();return;}
        java.util.List<CatalogSelection.Choice> choices=CatalogSelection.choices(catalog,"",favorites);
        if(choices.isEmpty()){new AlertDialog.Builder(this,ui.dialogTheme).setTitle("暂无可接入的模型").setMessage("先添加服务商，或在编辑中启用已有模型。").setPositiveButton("添加服务商",(d,w)->providerEditor(null)).setNegativeButton("返回",null).show();return;}
        if(choices.size()==1){showConnection(choices.get(0).model.reference);return;}
        if(modelPicker!=null&&modelPicker.isShowing())return;
        modelPicker=ModelPicker.show(this,ui,catalog,new java.util.HashSet<>(favorites),this::showConnection);
    }
}
