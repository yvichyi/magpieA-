package ai.magpie.android;

import android.app.*;
import android.content.*;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native common setup. Preview does not save; only an explicit Save creates a provider. */
public final class ProviderSetupActivity extends Activity {
    private static final String[] PROTOCOLS={"OpenAI 兼容","OpenAI Responses","Anthropic","Gemini"};
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final class State {
        final ProviderDraft draft;
        State(String id){draft=new ProviderDraft(id);}
        String origin,key,error="";
        boolean remote,busy,saving,uncertain,saved,revealModels;
        Runnable observer;
    }
    private State state;
    private Ui ui;
    private EditText address,key,name,manual,query;
    private Button fetch,save,protocol;
    private TextView message,count;
    private LinearLayout models;
    private LinearLayout modelSearch;
    private ScrollView scroll;
    private final ArrayList<View> controls=new ArrayList<>();
    private Runnable observer;
    private boolean resumed;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);ui=new Ui(this);
        // Draft secrets remain only in the current Activity/nonconfiguration memory.
        state=(State)getLastNonConfigurationInstance();
        if(state==null){state=new State(saved==null?null:saved.getString("id"));android.content.SharedPreferences p=getSharedPreferences("host",MODE_PRIVATE);state.remote=getIntent().getBooleanExtra("remote",false);
            state.origin=state.remote?p.getString("remoteAddress",""):GatewayService.managementOrigin;
            state.key=state.remote?p.getString("remoteKey",""):GatewayService.key(this);
            if(saved!=null){state.draft.url=saved.getString("url","");state.draft.name=saved.getString("name","");state.draft.manual=saved.getString("manual","");state.draft.protocol=saved.getInt("protocol");
                state.uncertain=saved.getBoolean("saving");state.error=state.uncertain?"上次保存结果待确认，请返回模型列表查看。不会重复提交。":"应用已重新启动，请重新填写 API Key。";}
        }
        build();observer=this::render;state.observer=observer;render();
    }
    @Override protected void onResume(){super.onResume();resumed=true;render();}
    @Override protected void onPause(){resumed=false;capture();super.onPause();}
    @Override protected void onDestroy(){if(state.observer==observer)state.observer=null;super.onDestroy();}
    @Override public Object onRetainNonConfigurationInstance(){capture();return state;}
    @Override protected void onSaveInstanceState(Bundle out){capture();out.putString("id",state.draft.id);out.putString("url",state.draft.url);out.putString("name",state.draft.name);out.putString("manual",state.draft.manual);out.putInt("protocol",state.draft.protocol);out.putBoolean("saving",state.saving||state.uncertain);super.onSaveInstanceState(out);}
    private EditText field(LinearLayout box,String label,String hint,String value,boolean secret){
        box.addView(ui.text(label,13,ui.muted,false));ui.space(box,6);
        EditText e=new EditText(this);e.setTextSize(16);e.setTypeface(ui.font(false,400));e.setTextColor(ui.ink);e.setHintTextColor(ui.muted);e.setHint(hint);e.setContentDescription(label);e.setSingleLine();e.setMinHeight(ui.dp(48));e.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));e.setBackground(ui.field(ui.card,12));e.setSaveEnabled(false);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|(secret?android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD:android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));e.setTypeface(ui.font(false,400));e.setIncludeFontPadding(false);e.setText(value);box.addView(e,new LinearLayout.LayoutParams(-1,-2));ui.space(box,18);controls.add(e);return e;
    }
    private void build(){
        LinearLayout root=ui.column();root.setBackgroundColor(ui.bg);root.setFocusableInTouchMode(true);
        LinearLayout header=ui.row();header.setPadding(ui.dp(8),ui.dp(6),ui.dp(16),ui.dp(6));header.addView(ui.iconButton("back","返回",this::onBackPressed));ui.grow(header,ui.text("添加服务商",20,ui.ink,true));root.addView(header);ui.rule(root,0);
        scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout body=ui.column();body.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(20));scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        body.addView(ui.text("填写地址和密钥，读取模型后即可接入。",14,ui.muted,false));ui.space(body,20);
        body.addView(ui.text("接口协议",13,ui.muted,false));ui.space(body,6);protocol=ui.button(PROTOCOLS[state.draft.protocol]+"  ⌄",false,()->new AlertDialog.Builder(this,ui.dialogTheme).setTitle("接口协议").setSingleChoiceItems(PROTOCOLS,state.draft.protocol,(d,w)->{if(state.draft.protocol!=w){state.draft.protocol=w;invalidateModels();}d.dismiss();render();}).show());body.addView(protocol);controls.add(protocol);ui.space(body,18);
        address=field(body,"服务商地址","https://api.example.com/v1",state.draft.url,false);address.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
        key=field(body,"API Key","粘贴 Key；自托管可留空",state.draft.key,true);
        name=field(body,"名称（可选）","默认使用域名",state.draft.name,false);
        TextView advanced=ui.text("改用订阅 / 高级添加 →",14,ui.ink,true);advanced.setMinHeight(ui.dp(44));advanced.setGravity(Gravity.CENTER_VERTICAL);advanced.setOnClickListener(v->{if(!state.busy){capture();boolean empty=state.draft.url.trim().isEmpty()&&state.draft.key.trim().isEmpty()&&state.draft.name.trim().isEmpty()&&state.draft.manual.trim().isEmpty();Intent advancedIntent=new Intent(this,ManagementActivity.class).putExtra("view","providers").putExtra("addProvider",true).putExtra("remote",state.remote).putExtra("returnToSetup",!empty);if(empty)UiMotion.go(this,advancedIntent,true);else UiMotion.result(this,advancedIntent,38);}});body.addView(advanced);controls.add(advanced);ui.space(body,18);ui.rule(body,0);ui.space(body,20);
        TextView modelHeading=ui.text("选择模型",20,ui.ink,true);fetch=ui.button("读取模型",false,()->perform(false));controls.add(fetch);body.addView(new AdaptivePair(ui,modelHeading,fetch,12));ui.space(body,8);
        count=ui.text("读取只用于预览，不会保存服务商。",13,ui.muted,false);body.addView(count);ui.space(body,12);
        modelSearch=ui.column();body.addView(modelSearch);query=field(modelSearch,"筛选模型","输入模型名称或 ID","",false);query.addTextChangedListener(watcher(this::renderModels));
        models=ui.column();body.addView(models);ui.space(body,14);
        manual=field(body,"手动模型 ID（可选）","例如 gpt-5，多个 ID 用逗号或换行分隔",state.draft.manual,false);manual.setSingleLine(false);manual.setMinLines(2);manual.setMaxLines(5);manual.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        TextWatcher source=watcher(()->{capture();invalidateModels();renderModels();});address.addTextChangedListener(source);key.addTextChangedListener(source);
        manual.addTextChangedListener(watcher(()->{capture();updateCount();}));
        LinearLayout footer=ui.column();footer.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(16));ui.rule(root,0);message=ui.text("",13,ui.muted,false);message.setMaxLines(4);message.setEllipsize(android.text.TextUtils.TruncateAt.END);message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(message,0);save=ui.button("保存并查看模型",true,()->{if(state.uncertain)finishToModels();else perform(true);});footer.addView(save);root.addView(footer);ui.viewport(root,header);setContentView(root);root.requestFocus();
    }
    private static TextWatcher watcher(Runnable action){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){action.run();}public void afterTextChanged(Editable e){}};}
    private void capture(){if(address==null)return;ProviderDraft d=state.draft;d.url=address.getText().toString();d.key=key.getText().toString();d.name=name.getText().toString();d.manual=manual.getText().toString();}
    private void invalidateModels(){state.draft.listed.clear();state.draft.selected.clear();state.error="";}
    private void updateCount(){try{count.setText(state.draft.listed.isEmpty()?"可读取模型列表，也可直接填写模型 ID。":"已选 "+state.draft.modelIds().size()+" 项 · 共 "+state.draft.listed.size()+" 个模型");}catch(IllegalArgumentException e){count.setText(e.getMessage());}}
    private void renderModels(){
        if(models==null)return;models.removeAllViews();updateCount();boolean has=!state.draft.listed.isEmpty();modelSearch.setVisibility(has?View.VISIBLE:View.GONE);
        if(!has)return;LinearLayout tools=ui.row();Button all=ui.button("全选",false,()->{state.draft.selected.addAll(state.draft.listed.keySet());renderModels();});Button none=ui.button("清空选择",false,()->{state.draft.selected.clear();renderModels();});all.setEnabled(!state.busy&&!state.uncertain);none.setEnabled(all.isEnabled());tools.addView(all);ui.gap(tools,12);tools.addView(none);models.addView(tools);ui.space(models,8);
        String filter=query.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0,total=0;
        for(Map.Entry<String,String> m:state.draft.listed.entrySet()){if(!(m.getKey()+" "+m.getValue()).toLowerCase(Locale.ROOT).contains(filter))continue;total++;if(shown++>=100)continue;
            CheckBox row=new CheckBox(this);row.setText(m.getValue().equals(m.getKey())?m.getKey():m.getValue()+"\n"+m.getKey());row.setTextSize(15);row.setTextColor(ui.ink);row.setTypeface(ui.font(false,400));row.setMinHeight(ui.dp(48));row.setChecked(state.draft.selected.contains(m.getKey()));row.setEnabled(!state.busy&&!state.uncertain);row.setButtonTintList(android.content.res.ColorStateList.valueOf(ui.ink));row.setOnCheckedChangeListener((b,on)->{if(on)state.draft.selected.add(m.getKey());else state.draft.selected.remove(m.getKey());updateCount();});models.addView(row);ui.rule(models,0);
        }
        if(total>100)models.addView(ui.text("显示前100项；筛选可找到其余模型，全选包含完整列表。",13,ui.muted,false));
        if(total==0)models.addView(ui.text("没有匹配的模型",14,ui.muted,false));
    }
    private boolean currentWorkspace(){android.content.SharedPreferences p=getSharedPreferences("host",MODE_PRIVATE);return state.remote==p.getBoolean("remote",false)&&state.origin!=null&&state.origin.equals(state.remote?p.getString("remoteAddress",""):GatewayService.managementOrigin)&&(state.remote||GatewayService.ready);}
    private void perform(boolean saving){
        if(state.busy||state.uncertain)return;capture();if(!currentWorkspace()){state.error="工作空间已变化或网关未就绪，请返回模型页后重试。";render();return;}
        String body;try{body=(saving?state.draft.save():state.draft.preview()).toString();}catch(IllegalArgumentException e){state.error=e.getMessage();render();return;}
        View focus=getCurrentFocus();if(focus!=null)focus.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(scroll.getWindowToken(),0);
        state.busy=true;state.saving=saving;state.error="";render();run(state,body,saving);
    }
    private static void run(State s,String body,boolean saving){
        new Thread(()->{
            ProviderOutcome result=ProviderOutcome.call(new CoreApi(s.origin,s.key),s.draft,saving,body);
            MAIN.post(()->{s.busy=false;s.saving=false;s.error=result.error;s.uncertain=result.uncertain;if(result.success){if(saving)s.saved=true;else{s.draft.listed.clear();s.draft.listed.putAll(result.rows);s.draft.selected.clear();s.draft.selected.addAll(result.rows.keySet());s.revealModels=true;if(result.rows.isEmpty())s.error="服务商没有返回可选模型，可在下方填写模型 ID。";}}if(s.observer!=null)s.observer.run();});
        },saving?"magpie-setup-save":"magpie-setup-preview").start();
    }
    private void render(){
        if(state.saved&&resumed){finishToModels();return;}if(save==null)return;
        for(View v:controls)v.setEnabled(!state.busy&&!state.uncertain);fetch.setText(state.busy&&!state.saving?"正在读取…":"读取模型");protocol.setText(PROTOCOLS[state.draft.protocol]+"  ⌄");
        save.setEnabled(!state.busy);save.setText(state.busy?(state.saving?"正在保存…":"正在读取模型…"):state.uncertain?"返回模型列表确认":"保存并查看模型");
        String status=state.uncertain?"保存结果待确认。请返回列表查看，不会自动重复提交。":state.error;
        boolean newError=!status.isEmpty()&&!status.contentEquals(message.getText());
        message.setText(status);message.setMaxLines(Integer.MAX_VALUE);message.setEllipsize(null);message.setVisibility(status.isEmpty()?View.GONE:View.VISIBLE);renderModels();
        if(newError)message.post(()->{if(!isFinishing()&&message.isShown())message.requestRectangleOnScreen(new android.graphics.Rect(0,0,message.getWidth(),message.getHeight()),true);});
        if(resumed&&state.revealModels){state.revealModels=false;View target=state.draft.listed.isEmpty()?manual:models;target.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){if(target.getViewTreeObserver().isAlive())target.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(isFinishing()||isDestroyed())return;if(!resumed){state.revealModels=true;return;}int height=Math.min(target.getHeight(),Math.max(ui.dp(100),scroll.getHeight()-ui.dp(120)));target.requestRectangleOnScreen(new android.graphics.Rect(0,0,target.getWidth(),height),true);}});}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==38&&result==RESULT_FIRST_USER&&data!=null){String primary=data.getStringExtra("primary");for(String id:NavigationModel.ROOTS)if(id.equals(primary)){AppNavigation.primary(this,id);return;}}}
    private void finishToModels(){setResult(RESULT_OK,new Intent().putExtra("providerId",state.draft.id).putExtra("providerName",state.draft.displayName()));UiMotion.close(this);}
    @Override public void onBackPressed(){
        if(state.busy&&state.saving){Toast.makeText(this,"正在保存，请等待结果",Toast.LENGTH_SHORT).show();return;}
        if(state.uncertain){finishToModels();return;}capture();if(state.draft.url.isEmpty()&&state.draft.key.isEmpty()&&state.draft.name.isEmpty()&&state.draft.manual.isEmpty()){UiMotion.close(this);return;}
        new AlertDialog.Builder(this,ui.dialogTheme).setTitle("放弃未保存的添加？").setMessage("地址、密钥和模型选择尚未保存。").setNegativeButton("继续填写",null).setPositiveButton("放弃",(d,w)->UiMotion.close(this)).show();
    }
}
