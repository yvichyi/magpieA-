package ai.magpie.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Installed-app creation, actual core I/O and callback/layout acceptance; no vendor account. */
public final class NativeSetupAcceptance extends Instrumentation {
    private final AtomicReference<Activity> current=new AtomicReference<>();
    private String upstream;
    private boolean probeFocus;
    private boolean formOnly;
    private boolean probeFormLayout;
    private boolean probeVisibleModels;
    private boolean probeReadReveal;
    private File evidence;
    @Override public void onCreate(Bundle args){super.onCreate(args);upstream=args.getString("upstream");probeFocus="true".equals(args.getString("probeFocusOnly"));formOnly="true".equals(args.getString("formOnly"));probeFormLayout="true".equals(args.getString("probeFormLayoutOnly"));probeVisibleModels="true".equals(args.getString("probeVisibleModelsOnly"));probeReadReveal="true".equals(args.getString("probeReadRevealOnly"));start();}
    @Override public void onStart(){try{
        require(upstream!=null&&upstream.startsWith("http://127.0.0.1:"),"Lab fixture upstream argument required");
        Context target=getTargetContext();target.getSharedPreferences("host",Context.MODE_PRIVATE).edit().putBoolean("launched",true).putBoolean("askedNotifications",true).putBoolean("remote",false).commit();
        evidence=new File(target.getFilesDir(),"native-setup-acceptance");evidence.mkdirs();
        ((Application)target.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){public void onActivityResumed(Activity a){current.set(a);}public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}});
        Activity home=startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        if(!GatewayService.ready)click(home,"启动本机网关");await(180000,()->GatewayService.ready,"Gateway did not become ready");stage("SETUP_GATEWAY_READY");
        CoreApi api=new CoreApi(GatewayService.managementOrigin,GatewayService.key(target));
        JSONObject existing=new JSONObject().put("id","existing-setup-qa").put("name","已有配置保留").put("chat",upstream).put("key","existing-fixture").put("models",new JSONArray().put("echo"));
        api.post("/api/provider/save",existing.toString());int before=new JSONObject(api.get("/api/providers")).getJSONArray("providers").length();
        if(probeVisibleModels){JSONArray ps=new JSONObject(api.get("/api/providers")).getJSONArray("providers");String id="";for(int i=0;i<ps.length();i++){JSONObject p=ps.getJSONObject(i);if(p.getString("id").startsWith("api-")&&p.getString("name").startsWith("手机快捷添加")){id=p.getString("id");break;}}require(!id.isEmpty(),"Previous native setup fixture required");final Activity h=home;final String wanted=id;runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("focusProvider",String.class,String.class);m.setAccessible(true);m.invoke(h,wanted,"手机快捷添加");}catch(Exception e){throw new RuntimeException(e);}});await(60000,()->find(h.getWindow().getDecorView(),wanted+"/echo")!=null,"Focused fixture missing");require(find(home.getWindow().getDecorView(),"limited")==null,"Focused handoff still lists unselected hidden models");Bundle ok=new Bundle();ok.putString("stream","PASS focused handoff only shows selected available models");finish(Activity.RESULT_OK,ok);return;}
        if(probeFocus){
            click(home,"搜索模型与分组");fill(home,"搜索输入框","unrelated-old-search");final Activity h=home;
            runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("focusProvider",String.class,String.class);m.setAccessible(true);m.invoke(h,"existing-setup-qa","已有配置保留");}catch(Exception e){throw new RuntimeException(e);}});
            require(text(home,"搜索输入框").isEmpty(),"Saved-provider handoff retains the unrelated old search query");
            runOnMainSync(()->{try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("showHome");m.setAccessible(true);m.invoke(h);}catch(Exception e){throw new RuntimeException(e);}});
            require("".equals(field(home,"focusedProvider")),"Workspace rebuild retains old provider focus");Bundle ok=new Bundle();ok.putString("stream","PASS saved-provider clears old query; workspace rebuild clears old focus");finish(Activity.RESULT_OK,ok);return;
        }
        click(home,"添加服务商");await(60000,()->current.get()!=null&&current.get().getClass().getSimpleName().equals("ProviderSetupActivity"),"Add did not open the native setup flow");
        Activity setup=current.get();require(!hasWebView(setup.getWindow().getDecorView()),"Common setup still embeds a desktop WebView");capture(setup,formOnly?"05-large-font-add":"01-native-add");visibleSave(setup);stage("NATIVE_SETUP_WITHOUT_WEBVIEW");
        if(probeFormLayout){final Activity a=setup;int[] heights=new int[2];runOnMainSync(()->{heights[0]=input(a.getWindow().getDecorView(),"服务商地址").getHeight();heights[1]=input(a.getWindow().getDecorView(),"API Key").getHeight();});require(Math.abs(heights[0]-heights[1])<=2*a.getResources().getDisplayMetrics().density,"Single-line setup input heights are inconsistent");visibleControl(setup.getWindow().getDecorView(),"读取模型");Bundle ok=new Bundle();ok.putString("stream","PASS consistent native input heights and fully visible first-read action");finish(Activity.RESULT_OK,ok);return;}
        if(formOnly){
            require(setup.getResources().getConfiguration().fontScale>=1.4f,"Large-font scenario did not receive system font scale");fill(setup,"API Key","keyboard-fixture");final Activity keyboardForm=setup;
            runOnMainSync(()->{EditText input=input(keyboardForm.getWindow().getDecorView(),"API Key");input.requestFocus();((android.view.inputmethod.InputMethodManager)keyboardForm.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
            await(30000,()->{Rect r=new Rect();keyboardForm.getWindow().getDecorView().getWindowVisibleDisplayFrame(r);return r.bottom<keyboardForm.getResources().getDisplayMetrics().heightPixels*.9f;},"Software keyboard did not become visible");
            visibleSave(setup);capture(setup,"06-large-font-keyboard");Bundle ok=new Bundle();ok.putString("stream","PASS fontScale1.5 native form and real keyboard keep a fully visible44dp+ Save control");finish(Activity.RESULT_OK,ok);return;
        }
        String fixtureKey="native-setup-fixture-"+System.currentTimeMillis();fill(setup,"服务商地址",upstream);fill(setup,"API Key",fixtureKey);fill(setup,"名称（可选）","手机快捷添加");
        Activity old=setup;runOnMainSync(()->{findClickable(old.getWindow().getDecorView(),"读取模型").performClick();old.recreate();});
        await(60000,()->current.get()!=old&&current.get()!=null&&current.get().getClass().getSimpleName().equals("ProviderSetupActivity"),"Setup recreation did not return");setup=current.get();final Activity recreated=setup;
        require(text(setup,"API Key").equals(fixtureKey),"In-memory draft key was lost on recreation");
        await(90000,()->find(recreated.getWindow().getDecorView(),"echo")!=null,"Actual vendor models did not appear after recreation");
        visibleControl(recreated.getWindow().getDecorView(),"echo");
        if(probeReadReveal){Bundle ok=new Bundle();ok.putString("stream","PASS fetched model choices become visible without manual scrolling");finish(Activity.RESULT_OK,ok);return;}
        require(new JSONObject(api.get("/api/providers")).getJSONArray("providers").length()==before,"Preview persisted a provider");
        stage("PREVIEW_AND_RECREATION_PASSED");
        fill(setup,"筛选模型","echo");require(find(setup.getWindow().getDecorView(),"limited")==null,"Native model filter did not filter");fill(setup,"筛选模型","");
        View limited=find(setup.getWindow().getDecorView(),"limited");if(limited instanceof CheckBox)runOnMainSync(()->((CheckBox)limited).setChecked(false));
        String id=((ProviderDraft)field(field(setup,"state"),"draft")).id;
        capture(setup,"02-model-selection");final Activity form=setup;runOnMainSync(()->{View button=findClickable(form.getWindow().getDecorView(),"保存并查看模型");require(button!=null,"Save control missing");button.performClick();button.performClick();});
        await(90000,()->current.get() instanceof MainActivity,"Confirmed save did not return directly to native models");home=current.get();
        JSONObject data=new JSONObject(api.get("/api/providers"));JSONArray rows=data.getJSONArray("providers");require(rows.length()==before+1,"Save duplicated or overwrote provider configuration");boolean saved=false;
        for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))saved=true;
        require(saved,"Saved provider missing");final String ref=id+"/echo";final Activity returnedHome=home;
        await(60000,()->find(returnedHome.getWindow().getDecorView(),ref)!=null,"Home did not focus the saved provider models");
        require(find(home.getWindow().getDecorView(),"limited")==null,"Focused handoff still lists unselected hidden models");
        require(find(home.getWindow().getDecorView(),"已有配置保留")==null,"New-provider focus still shows unrelated providers");capture(home,"03-saved-models");stage("CONFIRMED_SAVE_HANDOFF_AND_SINGLE_MUTATION_PASSED");
        String gateway=data.getJSONObject("gateway").getString("url");JSONObject reply=gateway(gateway,ref);require(!reply.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").isEmpty(),"Saved model did not answer a real gateway request");stage("SAVED_MODEL_REAL_GATEWAY_PASSED");
        click(home,"接入模型 "+ref);Dialog dialog=(Dialog)field(home,"connectionSheet");require(dialog!=null&&dialog.isShowing(),"Connection sheet missing");View sheet=dialog.getWindow().getDecorView();
        copy(sheet,"复制 API Key");require(clipboard().equals("magpie"),"Key copy not exact");copy(sheet,"复制 Base URL");require(clipboard().equals(gateway+"/v1"),"Base URL copy not exact");copy(sheet,"复制接入模型标识");require(clipboard().equals(ref),"Model copy not exact");
        copy(sheet,"复制 OpenAI JSON 配置");JSONObject client=new JSONObject(clipboard());require(client.getString("model").equals(ref)&&client.getString("api_key").equals("magpie")&&client.getString("base_url").equals(gateway+"/v1"),"Client JSON does not match actual configured model");captureView(sheet,"04-client-config");stage("INDIVIDUAL_COPIES_AND_VALID_CLIENT_JSON_PASSED");runOnMainSync(dialog::dismiss);
        click(home,"查看 手机快捷添加 · 点此显示全部");await(60000,()->find(returnedHome.getWindow().getDecorView(),"已有配置保留")!=null,"Show all did not restore the full model catalog");
        click(home,"添加服务商");await(60000,()->current.get()!=returnedHome&&current.get().getClass().getSimpleName().equals("ProviderSetupActivity"),"Second setup did not open");setup=current.get();fill(setup,"服务商地址","https://unsaved.example/v1");final Activity dirty=setup;runOnMainSync(dirty::onBackPressed);await(15000,()->current.get()==dirty,"Unsaved exit unexpectedly left form");
        Bundle result=new Bundle();result.putString("stream","Native setup passed: no WebView, actual preview, recreation, no preview persistence, single confirmed save, direct model handoff, actual gateway request, exact copies and valid JSON; software-emulator callback/layout scope only.");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){Bundle out=new Bundle();out.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
    private interface Condition{boolean get()throws Exception;}
    private void await(long millis,Condition c,String error)throws Exception{long end=SystemClock.elapsedRealtime()+millis;while(SystemClock.elapsedRealtime()<end){AtomicReference<Throwable> failure=new AtomicReference<>();AtomicReference<Boolean> ok=new AtomicReference<>(false);runOnMainSync(()->{try{ok.set(c.get());}catch(Throwable e){failure.set(e);}});if(failure.get()!=null)throw new Exception(failure.get());if(ok.get())return;Thread.sleep(150);}throw new AssertionError(error);}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static View find(View v,String label){if(label.equals(v.getContentDescription())||(v instanceof TextView&&label.equals(((TextView)v).getText().toString())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=find(((ViewGroup)v).getChildAt(i),label);if(f!=null)return f;}return null;}
    private static View findClickable(View v,String label){View f=find(v,label);while(f!=null&&!f.isClickable()&&f.getParent() instanceof View)f=(View)f.getParent();return f;}
    private void visibleSave(Activity a){runOnMainSync(()->{View v=findClickable(a.getWindow().getDecorView(),"保存并查看模型");require(v!=null,"Save missing");Rect rect=new Rect(),visible=new Rect();require(v.getGlobalVisibleRect(rect),"Save is offscreen");a.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);require(rect.height()>=44*a.getResources().getDisplayMetrics().density&&rect.height()>=v.getHeight()-1&&rect.bottom<=visible.bottom,"Save is clipped or hidden by keyboard");});}
    private void click(Activity a,String label){runOnMainSync(()->{View v=findClickable(a.getWindow().getDecorView(),label);require(v!=null,"Missing control: "+label);v.performClick();});}
    private static EditText input(View v,String label){if(v instanceof EditText&&label.equals(v.getContentDescription()))return(EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText f=input(((ViewGroup)v).getChildAt(i),label);if(f!=null)return f;}return null;}
    private void fill(Activity a,String label,String value){runOnMainSync(()->{EditText v=input(a.getWindow().getDecorView(),label);require(v!=null,"Missing input: "+label);v.setText(value);});}
    private String text(Activity a,String label){AtomicReference<String> out=new AtomicReference<>();runOnMainSync(()->out.set(input(a.getWindow().getDecorView(),label).getText().toString()));return out.get();}
    private static boolean hasWebView(View v){if(v instanceof android.webkit.WebView)return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(hasWebView(((ViewGroup)v).getChildAt(i)))return true;return false;}
    private static Object field(Object target,String name)throws Exception{java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void visibleControl(View root,String label)throws Exception{await(15000,()->{View v=findClickable(root,label);if(v==null||v.getHeight()==0)return false;Rect r=new Rect();return v.getGlobalVisibleRect(r)&&r.height()>=v.getHeight()-1;},label+" is clipped");}
    private void copy(View root,String label)throws Exception{await(15000,()->{View v=findClickable(root,label);return v!=null&&v.getWidth()>0&&v.getHeight()>0;},"Copy control has not laid out: "+label);runOnMainSync(()->{View v=findClickable(root,label);v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);});visibleControl(root,label);runOnMainSync(()->findClickable(root,label).performClick());}
    private String clipboard(){AtomicReference<String> out=new AtomicReference<>();runOnMainSync(()->{ClipData clip=((android.content.ClipboardManager)getTargetContext().getSystemService(Context.CLIPBOARD_SERVICE)).getPrimaryClip();require(clip!=null,"App has no focused clipboard access");out.set(clip.getItemAt(0).coerceToText(getTargetContext()).toString());});return out.get();}
    private JSONObject gateway(String base,String ref)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(base+"/v1/chat/completions").openConnection();c.setConnectTimeout(10000);c.setReadTimeout(30000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer magpie");c.setRequestProperty("Content-Type","application/json");try{c.getOutputStream().write(new JSONObject().put("model",ref).put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content","native flow verification"))).toString().getBytes(StandardCharsets.UTF_8));return new JSONObject(new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8));}finally{c.disconnect();}}
    private void capture(Activity a,String name)throws Exception{captureView(a.getWindow().getDecorView(),name);}
    private void captureView(View root,String name)throws Exception{await(15000,()->root.getWidth()>0&&root.getHeight()>0,"Capture view has not laid out");runOnMainSync(()->{try{Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));try(FileOutputStream out=new FileOutputStream(new File(evidence,name+".canvas.png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}catch(IOException e){throw new RuntimeException(e);}});Bitmap screen=getUiAutomation().takeScreenshot();if(screen!=null){try(FileOutputStream out=new FileOutputStream(new File(evidence,name+".screen.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}screen.recycle();}}
    private void stage(String name){Bundle b=new Bundle();b.putString("stream",name+"\n");sendStatus(1,b);}
}
