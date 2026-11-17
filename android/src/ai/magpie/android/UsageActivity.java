package ai.magpie.android;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

/** Native, read-only usage summary. The original full ledger remains one tap away. */
public final class UsageActivity extends Activity {
    private Ui ui;
    private SharedPreferences prefs;
    private LinearLayout content, periods,summary,modelArea,modelRows;
    private EditText modelSearch;
    private TextView modelCount;
    private int modelSort;
    private String restoredQuery="",filterScope="";
    private ScrollView scroll;
    private String renderedPeriod="",summarySignature="";
    private TextView freshness,workspaceLabel;
    private View refreshButton;
    private final Handler main=new Handler(Looper.getMainLooper());
    private String period="today", loadedPeriod="", loadedOrigin="";
    private UsageSnapshot snapshot;
    private boolean active,busy,remote;
    private int generation,modelLimit=30;
    private long lastRead;
    private LinearLayout heatArea,heatBody;
    private TextView heatStatus,heatDay,heatValues;
    private View heatPrevious,heatNext;
    private UsageHeatmap heatmap;
    private UsageHeatmapView heatView;
    private java.util.List<UsageHeatmap.Day> heatDays=java.util.Collections.emptyList();
    private int heatGeneration,heatMetric;
    private boolean heatBusy;
    private long heatReadAt;
    private String heatReadScope="",heatResultScope="";
    private final Runnable poll=new Runnable(){@Override public void run(){refresh(false);main.postDelayed(this,30000);}};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new Ui(this);prefs=getSharedPreferences("host",MODE_PRIVATE);
        remote=getIntent().getBooleanExtra("remote",prefs.getBoolean("remote",false));filterScope=AppNavigation.workspace(this);android.content.SharedPreferences navigation=getSharedPreferences("navigation",0);modelSort=navigation.getInt("usageSort:"+filterScope,0);restoredQuery=navigation.getString("usageQuery:"+filterScope,"");if(saved!=null){modelSort=saved.getInt("modelSort",0);restoredQuery=saved.getString("modelQuery","");}
        {String value=saved!=null?saved.getString("period","today"):prefs.getString("usagePeriod","today");try{UsageSnapshot.validatePeriod(value);period=value;}catch(IllegalArgumentException ignored){}}
        LinearLayout root=ui.column();root.setBackgroundColor(ui.bg);
        LinearLayout toolbar=ui.row();toolbar.setPadding(ui.dp(24),ui.dp(8),ui.dp(12),ui.dp(8));
        ui.grow(toolbar,ui.text("用量",22,ui.ink,true));
        refreshButton=ui.iconButton("refresh","刷新统计",()->{refresh(true);refreshHeat(true);});toolbar.addView(refreshButton);TextView details=action("明细",()->openManagement("usage"));details.setContentDescription("查看请求明细与导出");toolbar.addView(details);root.addView(toolbar);
        scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=ui.column();body.setPadding(ui.dp(24),ui.dp(14),ui.dp(24),ui.dp(24));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        workspaceLabel=ui.text("",13,ui.muted,false);body.addView(workspaceLabel);ui.space(body,18);
        periods=ui.row();body.addView(periods);renderPeriods();ui.space(body,12);
        freshness=ui.text("正在读取统计…",11,ui.muted,false);freshness.setMinHeight(ui.dp(24));freshness.setOnClickListener(v->refresh(true));body.addView(freshness);ui.space(body,8);
        content=ui.column();body.addView(content);summary=ui.column();content.addView(summary);buildHeatArea();buildModelArea();
        View bottom=ui.bottomNav("usage",this::navigate);root.addView(bottom);ui.viewport(root,toolbar,bottom);setContentView(root);renderMessage("正在读取统计", "正在连接当前工作空间，请稍候。",false);
    }
    @Override protected void onResume(){super.onResume();UiMotion.enter(this);remote=AppNavigation.remote(this);workspaceLabel.setText((remote?"远程空间":"本机空间")+" · "+TimeZone.getDefault().getDisplayName(false,TimeZone.SHORT,Locale.CHINA));active=true;refresh(true);main.postDelayed(poll,30000);}
    @Override protected void onPause(){getSharedPreferences("navigation",0).edit().putInt("usageSort:"+filterScope,modelSort).putString("usageQuery:"+filterScope,modelSearch.getText().toString()).apply();UiMotion.pause(this);active=false;generation++;heatGeneration++;busy=false;heatBusy=false;main.removeCallbacks(poll);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("period",period);out.putInt("modelSort",modelSort);out.putString("modelQuery",modelSearch.getText().toString());super.onSaveInstanceState(out);}
    private String origin(){return remote?prefs.getString("remoteAddress",""):GatewayService.managementOrigin;}
    private void renderPeriods(){
        periods.removeAllViews();String[] ids={"today","7d","30d","all"};int selected=java.util.Arrays.asList(ids).indexOf(period);
        PeriodTabs tabs=new PeriodTabs(ui,new String[]{"今天","7 天","30 天","全部"},selected,index->{
            modelSearch.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(modelSearch.getWindowToken(),0);period=ids[index];prefs.edit().putString("usagePeriod",period).apply();modelLimit=30;generation++;busy=false;snapshot=null;loadedPeriod="";lastRead=0;renderedPeriod="";summarySignature="";refresh(true);
        });periods.addView(tabs,new LinearLayout.LayoutParams(-1,-2));
    }
    private void refresh(boolean force){
        if(!active)return;refreshHeat(false);if(busy)return;
        if(!force&&SystemClock.elapsedRealtime()-lastRead<25000)return;
        String address=origin();
        if((!remote&&!GatewayService.ready)||address.isEmpty()){
            generation++;snapshot=null;loadedPeriod="";refreshButton.setEnabled(true);refreshButton.setAlpha(1);freshness.setText("网关未连接");renderMessage("统计暂不可用","在工具中启动网关，再刷新统计。",true);return;
        }
        if(!address.equals(loadedOrigin)){snapshot=null;loadedPeriod="";}
        if(snapshot==null||!period.equals(loadedPeriod))renderMessage("正在读取统计","正在汇总所选时间范围内的真实调用。",false);
        busy=true;refreshButton.setEnabled(false);refreshButton.setAlpha(.4f);freshness.setText("正在更新…");
        String selected=period,key=remote?prefs.getString("remoteKey",""):GatewayService.key(this);
        int request=++generation;
        new Thread(()->{
            try{
                UsageSnapshot result=UsageSnapshot.parse(new CoreApi(address,key).get(UsageSnapshot.path(selected,TimeZone.getDefault().getID())));
                if(!selected.equals(result.period))throw new IllegalArgumentException("统计范围不匹配");
                main.post(()->{
                    if(!accept(request,address,selected)){recoverChangedOrigin(request);return;}
                    busy=false;refreshButton.setEnabled(true);refreshButton.setAlpha(1);snapshot=result;loadedOrigin=address;loadedPeriod=selected;lastRead=SystemClock.elapsedRealtime();
                    freshness.setText("更新于 "+new java.text.SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date())+" · 每 30 秒刷新");String signature=result.displaySignature();if(!signature.equals(summarySignature)){summarySignature=signature;renderSummary(result);}
                });
            }catch(Exception error){main.post(()->{
                if(!accept(request,address,selected)){recoverChangedOrigin(request);return;}
                busy=false;refreshButton.setEnabled(true);refreshButton.setAlpha(1);lastRead=SystemClock.elapsedRealtime();
                if(snapshot!=null&&loadedPeriod.equals(selected)&&loadedOrigin.equals(address))freshness.setText("更新失败 · 保留上次数据，点此重试");
                else{freshness.setText("读取失败 · 点此重试");renderMessage("未能读取统计","请检查网关、网络及管理密钥后重试；当前没有可展示的统计结果。",true);}
            });}
        },"magpie-usage").start();
    }
    private boolean accept(int request,String address,String selected){return active&&!isFinishing()&&!isDestroyed()&&request==generation&&selected.equals(period)&&address.equals(origin())&&(remote||GatewayService.ready);}
    private void recoverChangedOrigin(int request){if(active&&request==generation){busy=false;refresh(true);}}
    private void renderMessage(String title,String text,boolean retry){
        modelArea.setVisibility(View.GONE);UiMotion.reset(summary);summarySignature="";renderedPeriod="";summary.removeAllViews();ui.space(summary,28);summary.addView(ui.text(title,20,ui.ink,true));ui.space(summary,12);summary.addView(ui.text(text,13,ui.muted,false));
        if(retry){ui.space(summary,18);summary.addView(ui.button("重新读取",true,()->refresh(true)));}advanced();
    }
    private void renderSummary(UsageSnapshot s){
        int previousY=scroll.getScrollY();boolean animateChart=!s.period.equals(renderedPeriod);renderedPeriod=s.period;summary.removeAllViews();
        TextView call=ui.digits(UsageSnapshot.number(s.calls),44);call.setContentDescription("调用次数 "+s.calls);
        TextView price=ui.text(s.costLabel(),16,ui.ink,true);
        float width=getResources().getDisplayMetrics().widthPixels-ui.dp(48);
        boolean stacked=getResources().getConfiguration().fontScale>1.3f||call.getPaint().measureText(call.getText().toString())>width*.48f||price.getPaint().measureText(price.getText().toString())>width*.46f;
        LinearLayout hero=stacked?ui.column():ui.row();if(!stacked)hero.setGravity(Gravity.BOTTOM);
        LinearLayout calls=ui.column();calls.addView(ui.text("调用次数",14,ui.muted,false));ui.space(calls,9);calls.addView(call);
        if(stacked){hero.addView(calls);ui.space(hero,18);}else ui.grow(hero,calls);
        LinearLayout other=ui.column();other.setGravity(stacked?Gravity.LEFT:Gravity.RIGHT);
        LinearLayout token=ui.column();token.setGravity(stacked?Gravity.LEFT:Gravity.RIGHT);token.addView(ui.text("Token 总量",12,ui.muted,false),new LinearLayout.LayoutParams(-2,-2));ui.space(token,4);token.addView(ui.digits(UsageSnapshot.number(s.tokens()),24),new LinearLayout.LayoutParams(-2,-2));other.addView(token,new LinearLayout.LayoutParams(-2,-2));ui.space(other,9);other.addView(price,new LinearLayout.LayoutParams(-2,-2));ui.space(other,5);other.addView(ui.text(s.unpriced>0?s.unpriced+" 次未定价":"USD · 已知价格",12,ui.muted,false),new LinearLayout.LayoutParams(-2,-2));hero.addView(other,new LinearLayout.LayoutParams(stacked?-1:-2,-2));summary.addView(hero);ui.space(summary,16);
        LinearLayout completion=ui.column();completion.addView(ui.text("完成率 "+s.completionRate(),13,ui.muted,false));completion.addView(ui.text("失败 "+s.errors+" · 取消 "+s.canceled,13,ui.muted,false));summary.addView(completion);ui.space(summary,22);
        LinearLayout chartHead=ui.row();ui.grow(chartHead,ui.text("调用趋势",16,ui.ink,true));chartHead.addView(ui.text(s.bucket.equals("hour")?"按小时":s.bucket.equals("week")?"按周":"按天",12,ui.muted,false));summary.addView(chartHead);ui.space(summary,10);
        TextView selected=ui.text(s.calls==0?"这个时段还没有调用":"轻点柱形查看时段数值",12,ui.muted,false);selected.setMinHeight(ui.dp(28));
        summary.addView(new UsageChart(this,ui,s.points,p->{
            String when=p.label;try{when=java.time.Instant.parse(p.time).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern(s.bucket.equals("hour")?"MM-dd HH:mm":"yyyy-MM-dd"));}catch(Exception ignored){}
            selected.setText(when+" · "+UsageSnapshot.number(p.calls)+" 次 · "+UsageSnapshot.number(p.tokens)+" Token");selected.announceForAccessibility(selected.getText());
        },animateChart),new LinearLayout.LayoutParams(-1,ui.dp(145)));ui.space(summary,8);summary.addView(selected);ui.space(summary,24);
        modelArea.setVisibility(View.VISIBLE);renderModels();
        if(!modelSearch.hasFocus())scroll.post(()->scroll.scrollTo(0,animateChart?0:previousY));
    }
    private void buildHeatArea(){
        heatArea=ui.column();content.addView(heatArea);ui.rule(heatArea,0);ui.space(heatArea,24);
        heatArea.addView(ui.text("每日用量",20,ui.ink,true));ui.space(heatArea,7);
        heatArea.addView(ui.text("近 12 周 · 不受筛选影响",12,ui.muted,false));ui.space(heatArea,8);
        heatStatus=ui.text("正在读取每日用量…",12,ui.muted,false);heatStatus.setMinHeight(ui.dp(44));heatStatus.setOnClickListener(v->refreshHeat(true));heatArea.addView(heatStatus);
        heatBody=ui.column();heatArea.addView(heatBody);ui.space(heatArea,24);ui.rule(heatArea,0);ui.space(heatArea,24);
        heatMetric=Math.max(0,Math.min(2,getSharedPreferences("navigation",0).getInt("usageHeatMetric:"+filterScope,0)));
    }
    private String heatScope(){return AppNavigation.remote(this)+"\n"+origin()+"\n"+(remote?prefs.getString("remoteKey",""):GatewayService.key(this))+"\n"+TimeZone.getDefault().getID();}
    private void refreshHeat(boolean force){
        if(!active)return;remote=AppNavigation.remote(this);String address=origin(),scope=heatScope();
        if(!scope.equals(heatReadScope)){
            heatGeneration++;heatBusy=false;heatReadAt=0;heatReadScope=scope;heatResultScope="";heatmap=null;heatView=null;heatBody.removeAllViews();
        }
        if(address.isEmpty()||(!remote&&!GatewayService.ready)){heatStatus.setText("网关未连接 · 每日统计暂不可用");return;}
        if(heatBusy||(!force&&heatReadAt>0&&SystemClock.elapsedRealtime()-heatReadAt<60000))return;
        heatBusy=true;int request=++heatGeneration;
        heatStatus.setText(heatmap==null?"正在读取每日用量…":"正在更新每日用量…");
        String key=remote?prefs.getString("remoteKey",""):GatewayService.key(this),zone=TimeZone.getDefault().getID();
        new Thread(()->{
            try{
                UsageHeatmap result=UsageHeatmap.parse(new CoreApi(address,key).get(UsageHeatmap.path(zone)));
                main.post(()->{
                    if(!acceptHeat(request,scope)){recoverHeat(request);return;}
                    heatBusy=false;heatReadAt=SystemClock.elapsedRealtime();
                    boolean changed=heatmap==null||!result.signature.equals(heatmap.signature);heatmap=result;heatResultScope=scope;
                    heatStatus.setText("更新于 "+new java.text.SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date())+" · 每分钟");
                    if(changed)renderHeat();
                });
            }catch(Exception error){main.post(()->{
                if(!acceptHeat(request,scope)){recoverHeat(request);return;}
                heatBusy=false;heatReadAt=SystemClock.elapsedRealtime();
                heatStatus.setText(heatmap!=null&&scope.equals(heatResultScope)?"更新失败 · 保留上次日历，点此重试":"每日统计读取失败 · 点此重试");
            });}
        },"magpie-heatmap").start();
    }
    private boolean acceptHeat(int request,String scope){return active&&!isFinishing()&&!isDestroyed()&&request==heatGeneration&&scope.equals(heatScope())&&(remote||GatewayService.ready);}
    private void recoverHeat(int request){if(active&&request==heatGeneration){heatBusy=false;refreshHeat(true);}}
    private void renderHeat(){
        String selected=heatDays.isEmpty()||heatView==null?"":heatDays.get(heatView.day()).date.toString();
        heatDays=heatmap.recent(12);heatBody.removeAllViews();
        heatView=new UsageHeatmapView(this,ui,heatDays,d->updateHeatDay());heatView.metric(heatMetric);
        heatBody.addView(new PeriodTabs(ui,new String[]{"调用","Token","费用"},heatMetric,index->{
            heatMetric=index;getSharedPreferences("navigation",0).edit().putInt("usageHeatMetric:"+filterScope,index).apply();heatView.metric(index);updateHeatDay();
        }),new LinearLayout.LayoutParams(-1,-2));ui.space(heatBody,14);
        heatBody.addView(heatView,new LinearLayout.LayoutParams(-1,-2));ui.space(heatBody,10);
        heatBody.addView(ui.text("浅 → 深表示较少 → 较多；Token 包含缓存；费用仅含已知价格。",12,ui.muted,false));ui.space(heatBody,14);
        heatDay=ui.text("",15,ui.ink,true);heatDay.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);heatBody.addView(heatDay);ui.space(heatBody,6);
        heatValues=ui.text("",13,ui.muted,false);heatValues.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);heatValues.setLineSpacing(ui.dp(4),1);heatBody.addView(heatValues);ui.space(heatBody,8);
        LinearLayout controls=ui.row();heatPrevious=action("← 上一天",()->heatView.day(heatView.day()-1));heatNext=action("下一天 →",()->heatView.day(heatView.day()+1));
        ui.grow(controls,heatPrevious);controls.addView(heatNext);heatBody.addView(controls);
        for(int i=0;i<heatDays.size();i++)if(heatDays.get(i).date.toString().equals(selected)){heatView.day(i);break;}
        updateHeatDay();
    }
    private void updateHeatDay(){
        if(heatView==null||heatDay==null||heatDays.isEmpty())return;
        int index=heatView.day();UsageHeatmap.Day d=heatDays.get(index);
        heatDay.setText(d.date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd · EEEE",Locale.CHINA)));
        String calls=UsageSnapshot.number(d.calls)+" 次调用",tokens=UsageSnapshot.number(d.tokens)+" Token（含缓存）",cost=d.costLabel()+(d.unpriced>0?" · "+d.unpriced+" 次未定价":" USD");
        heatValues.setText(heatMetric==1?tokens+"\n"+calls+" · "+cost:heatMetric==2?cost+"\n"+calls+" · "+tokens:calls+"\n"+tokens+"\n"+cost);
        heatPrevious.setEnabled(index>0);heatPrevious.setAlpha(index>0?1:.35f);heatNext.setEnabled(index<heatDays.size()-1);heatNext.setAlpha(index<heatDays.size()-1?1:.35f);
    }
    private void buildModelArea(){
        modelArea=ui.column();content.addView(modelArea);modelArea.setVisibility(View.GONE);
        LinearLayout head=ui.row();ui.grow(head,ui.text("模型用量",20,ui.ink,true));modelCount=ui.text("",12,ui.muted,false);head.addView(modelCount);modelArea.addView(head);ui.space(modelArea,10);
        modelSearch=new EditText(this);modelSearch.setSingleLine();modelSearch.setTextSize(15);modelSearch.setTypeface(ui.font(false,400));modelSearch.setTextColor(ui.ink);modelSearch.setHintTextColor(ui.muted);modelSearch.setHint("搜索模型或服务商");modelSearch.setContentDescription("搜索模型用量");modelSearch.setMinHeight(ui.dp(48));modelSearch.setPadding(0,ui.dp(8),0,ui.dp(8));modelSearch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.line));modelSearch.setSaveEnabled(false);modelSearch.setText(restoredQuery);modelArea.addView(modelSearch,new LinearLayout.LayoutParams(-1,-2));
        modelSearch.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);modelSearch.setOnEditorActionListener((v,action,event)->{if(action!=android.view.inputmethod.EditorInfo.IME_ACTION_DONE)return false;modelSearch.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(modelSearch.getWindowToken(),0);return true;});
        modelSearch.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){modelLimit=30;renderModels();}public void afterTextChanged(android.text.Editable e){}});ui.space(modelArea,8);
        modelArea.addView(new PeriodTabs(ui,new String[]{"按调用次数","按 Token"},modelSort,index->{modelSort=index;modelLimit=30;renderModels();}),new LinearLayout.LayoutParams(-1,-2));ui.space(modelArea,8);
        modelRows=ui.column();modelArea.addView(modelRows);ui.space(modelArea,20);modelArea.addView(ui.button("查看请求明细",true,()->openManagement("usage")));ui.space(modelArea,10);
        LinearLayout extras=ui.row();ui.grow(extras,action("Token 与计费明细",()->{if(snapshot!=null)showTokenDetails(snapshot);}));extras.addView(action("管理模型 →",()->openManagement("providers")));modelArea.addView(extras);
    }
    private void renderModels(){
        if(modelRows==null||snapshot==null)return;modelRows.removeAllViews();List<UsageSnapshot.Model> rows=UsageModels.select(snapshot.models,modelSearch.getText().toString(),modelSort);modelCount.setText(rows.size()+" / "+snapshot.models.size()+" 项");
        if(rows.isEmpty()){ui.space(modelRows,20);modelRows.addView(ui.text(modelSearch.getText().toString().trim().isEmpty()?"暂无模型记录":"没有匹配的用量记录",16,ui.ink,true));if(!modelSearch.getText().toString().isEmpty())modelRows.addView(action("清除搜索",()->modelSearch.setText("")));return;}
        float available=getResources().getDisplayMetrics().widthPixels-ui.dp(48);
        for(UsageSnapshot.Model m:rows.subList(0,Math.min(modelLimit,rows.size()))){
            LinearLayout entry=ui.column();entry.setPadding(0,ui.dp(16),0,ui.dp(16));TextView name=ui.text(m.name,16,ui.ink,true);name.setLineSpacing(ui.dp(3),1);entry.addView(name);ui.space(entry,5);entry.addView(ui.text(m.provider.isEmpty()?"未知服务商":m.provider,12,ui.muted,false));ui.space(entry,13);
            String price=m.cost==0&&m.unpriced>0?"待定价":UsageSnapshot.money(m.cost);String[] labels={"调用","Token","已计价"},values={UsageSnapshot.number(m.calls),UsageSnapshot.number(m.tokens),price};
            boolean stacked=getResources().getConfiguration().fontScale>1.3f;for(String value:values)if(ui.text(value,16,ui.ink,false).getPaint().measureText(value)>available/3-ui.dp(12))stacked=true;
            LinearLayout metrics=stacked?ui.column():ui.row();
            for(int i=0;i<3;i++){if(stacked){LinearLayout line=ui.column();line.setPadding(0,0,0,ui.dp(10));line.addView(ui.text(labels[i],12,ui.muted,false));ui.space(line,5);line.addView(ui.text(values[i],16,ui.ink,true),new LinearLayout.LayoutParams(-1,-2));metrics.addView(line);}else{LinearLayout cell=ui.column();cell.addView(ui.text(labels[i],12,ui.muted,false));ui.space(cell,5);cell.addView(ui.text(values[i],16,ui.ink,true));metrics.addView(cell,new LinearLayout.LayoutParams(0,-2,1));}}
            entry.addView(metrics);if(m.unpriced>0){ui.space(entry,9);entry.addView(ui.text(m.unpriced+" 次未定价，未计入费用",12,ui.muted,false));}modelRows.addView(entry);ui.rule(modelRows,0);
        }
        if(rows.size()>modelLimit)modelRows.addView(ui.button("显示更多模型 · 还有 "+(rows.size()-modelLimit)+" 项",false,()->{modelLimit+=30;renderModels();}));
    }
    private TextView action(String text,Runnable action){TextView v=ui.text(text,13,ui.ink,true);v.setMinHeight(ui.dp(44));v.setGravity(Gravity.CENTER_VERTICAL);v.setOnClickListener(w->action.run());return v;}
    private void showTokenDetails(UsageSnapshot s){
        UiMotion.stay(this);
        LinearLayout box=ui.column();box.setPadding(ui.dp(24),ui.dp(16),ui.dp(24),ui.dp(16));
        detail(box,"输入",UsageSnapshot.number(s.input));detail(box,"输出",UsageSnapshot.number(s.output));detail(box,"缓存读取",UsageSnapshot.number(s.cacheRead));detail(box,"缓存写入",UsageSnapshot.number(s.cacheWrite));detail(box,"已知缓存命中率",s.cacheRate());ui.space(box,16);
        box.addView(ui.text("Token 总量为输入 + 输出，缓存另计。取消不算完成。\n\n"+s.priceNote()+"，以厂商最终账单为准。",13,ui.muted,false));ScrollView scroll=new ScrollView(this);scroll.addView(box);
        new android.app.AlertDialog.Builder(this,ui.dialogTheme).setTitle("Token 与计费明细").setView(scroll).setPositiveButton("完成",null).show();
    }
    private void detail(LinearLayout box,String label,String value){LinearLayout row=ui.row();row.setMinimumHeight(ui.dp(42));ui.grow(row,ui.text(label,14,ui.muted,false));row.addView(ui.digits(value,15));box.addView(row);}
    private void advanced(){ui.space(summary,24);summary.addView(ui.button("查看请求明细",false,()->openManagement("usage")));}
    private void openManagement(String view){AppNavigation.detail(this,view);}
    private void navigate(String id){if(id.equals("usage")){scroll.smoothScrollTo(0,0);return;}AppNavigation.primary(this,id);}
    @Override public void onBackPressed(){AppNavigation.primary(this,"home");}
}
