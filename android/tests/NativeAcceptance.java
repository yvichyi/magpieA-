package ai.magpie.android;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.*;

/** Lab-only genuine callbacks, normal motion, actual View captures and system-recording checkpoints. */
public final class NativeAcceptance extends Instrumentation {
    private final AtomicReference<Activity> current=new AtomicReference<>();
    private File evidence,proceed;
    private boolean layoutOnly,skipPluginWebView;
    private final Handler main=new Handler(Looper.getMainLooper());
    @Override public void onCreate(Bundle args){super.onCreate(args);layoutOnly=args!=null&&"true".equals(args.getString("layoutOnly"));skipPluginWebView=args!=null&&"true".equals(args.getString("skipPluginWebView"));start();}
    @Override public void onStart(){
        try{
            Context target=getTargetContext();target.getSharedPreferences("host",Context.MODE_PRIVATE).edit().putBoolean("launched",true).putBoolean("askedNotifications",true).putBoolean("remote",false).putString("usagePeriod","today").commit();
            evidence=new File(target.getFilesDir(),"native-acceptance");evidence.mkdirs();proceed=new File(target.getFilesDir(),"native-qa-continue");proceed.delete();
            ((Application)target.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                public void onActivityResumed(Activity a){current.set(a);}public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
            });
            scales(1);shell("settings put system font_scale 1.0");
            Activity home=startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));settle();
            if(!GatewayService.ready)click(home,"启动本机网关");await(180000,()->GatewayService.ready,"Gateway did not become ready");
            java.lang.Process runtime=new ProcessBuilder(new File(target.getApplicationInfo().nativeLibraryDir,"libmagpie-bun.so").getAbsolutePath(),"--version").redirectErrorStream(true).start();require(runtime.waitFor(45,java.util.concurrent.TimeUnit.SECONDS),"Packaged Bun did not exit");String runtimeVersion=new String(runtime.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim();require(runtime.exitValue()==0&&runtimeVersion.equals("1.4.2"),"Packaged Bun cannot run under app UID: "+runtimeVersion);stage("PACKAGED_BUN_APP_UID_PASSED");stage("GATEWAY_READY");
            if(!layoutOnly)await(600000,()->proceed.exists(),"External actual core checks did not finish");
            awaitText(home,"android-echo");settle();focused(home.getWindow().getDecorView());
            await(60000,()->!boolField(home,"todayBusy"),"Initial today summary did not finish");
            runOnMainSync(()->((TextView)field(home,"todayHint")).performClick());
            await(60000,()->!boolField(home,"todayBusy"),"Today summary refresh did not finish");
            navigationAlignment(home);save(home,"home.png");stage("HOME_RENDERED_NORMAL_MOTION");
            ViewGroup list=(ViewGroup)field(home,"catalogList");View retained=list.getChildAt(0);int y=((ScrollView)field(home,"homeScroll")).getScrollY();
            runOnMainSync(()->((TextView)field(home,"metricsHint")).performClick());
            await(60000,()->!boolField(home,"metricsBusy"),"Catalog refresh did not finish");
            require(list.getChildAt(0)==retained,"Unchanged catalog replaced its Views");require(((ScrollView)field(home,"homeScroll")).getScrollY()==y,"Unchanged refresh lost scroll");stage("UNCHANGED_CATALOG_RETAINED");
            click(home,"搜索模型与分组");EditText search=(EditText)field(home,"search");runOnMainSync(()->{search.setText("android-echo");search.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);});settle();
            require(find(home.getWindow().getDecorView(),"android-limited",true)==null,"Search retained a non-matching model");save(home,"search.png");
            click(home,"复制模型标识 android-device-verified/android-echo");require(clipboard(target).equals("android-device-verified/android-echo"),"Copied model identifier changed");stage("SEARCH_AND_COPY_PASSED");
            click(home,"搜索模型与分组");settle();click(home,"接入模型 android-device-verified/android-echo");
            await(30000,()->{Object d=field(home,"connectionSheet");return d instanceof Dialog&&((Dialog)d).isShowing();},"Connection sheet did not open");
            Dialog sheet=(Dialog)field(home,"connectionSheet");settle();periodAlignment(sheet.getWindow().getDecorView());saveView(sheet.getWindow().getDecorView(),"connection.png");saveSystem("connection.png");
            clickView(sheet.getWindow().getDecorView(),"Anthropic");clickView(sheet.getWindow().getDecorView(),"Gemini");clickView(sheet.getWindow().getDecorView(),"OpenAI");settle();
            View copy=findClickable(sheet.getWindow().getDecorView(),"复制接入信息");require(copy instanceof Button,"Connection copy button missing");
            AtomicReference<String> feedback=new AtomicReference<>();
            runOnMainSync(()->{copy.performClick();main.postDelayed(copy::performClick,800);main.postDelayed(()->feedback.set(((Button)copy).getText().toString()),1500);});
            await(30000,()->feedback.get()!=null,"Copy feedback timer did not finish");require(feedback.get().equals("已复制接入信息"),"Repeated copy reset feedback too early");
            String config=clipboard(target);require(config.contains("http://127.0.0.1:3425/v1")&&config.contains("android-device-verified/android-echo")&&config.contains("API Key: magpie"),"Connection payload is not usable local configuration");
            clickView(sheet.getWindow().getDecorView(),"关闭接入面板");settle();stage("CONNECTION_AND_REPEAT_FEEDBACK_PASSED");
            runOnMainSync(()->{findClickable(home.getWindow().getDecorView(),"用量").performClick();findClickable(home.getWindow().getDecorView(),"分组").performClick();});settle();
            require(current.get()==home&&((Integer)field(home,"catalogTab"))==1,"Pending usage launch overrode the latest routing selection");awaitText(home,"android-device-fallback");save(home,"routes.png");
            click(home,"复制分组标识 group/android-device-fallback");require(clipboard(target).equals("group/android-device-fallback"),"Copied route reference changed");
            runOnMainSync(()->{findClickable(home.getWindow().getDecorView(),"模型").performClick();findClickable(home.getWindow().getDecorView(),"分组").performClick();findClickable(home.getWindow().getDecorView(),"模型").performClick();});settle();require(((Integer)field(home,"catalogTab"))==0,"Rapid catalog tabs did not settle to the last selection");stage("RAPID_NAVIGATION_PASSED");
            if(!skipPluginWebView){
            click(home,"插件");await(120000,()->current.get() instanceof ManagementActivity,"Primary plugin destination did not open");Activity plugins=current.get();await(120000,()->boolField(plugins,"pageLoaded"),"Plugin WebView did not load");android.webkit.WebView pluginWeb=(android.webkit.WebView)field(plugins,"web");
            awaitScript(pluginWeb,"!!document.querySelector('#view-plugins .pm-head') && window.magpieAndroidInstalled===true && getComputedStyle(document.querySelector('#view-plugins .lib-tabs')).display!=='none' && getComputedStyle(document.querySelector('#view-plugins .pm-head')).position!=='fixed'");settle();navigationAlignment(plugins);save(plugins,"plugins.png");stage("PRIMARY_PLUGIN_PAGE_PASSED");
            click(plugins,"模型");await(60000,()->current.get() instanceof MainActivity,"Plugin navigation did not return to models");require(current.get()==home,"Plugin return replaced the existing model workspace");awaitText(home,"android-echo");settle();
            }else stage("PLUGIN_WEBVIEW_SKIPPED_EXPLICIT_LAB_FLAG: confirmed WebView124 renderer SIGTRAP; browser layout and actual API installation tested separately");
            click(home,"用量");await(60000,()->current.get() instanceof UsageActivity,"Usage destination did not open");Activity usage=current.get();awaitText(usage,"更新于");settle();navigationAlignment(usage);periodAlignment(usage.getWindow().getDecorView());save(usage,"statistics.png");stage("SUMMARY_RENDERED");
            View chart=findClass(usage.getWindow().getDecorView(),"UsageChart");require(chart!=null,"Chart missing");runOnMainSync(chart::performClick);require(chart.getContentDescription().toString().contains("次调用"),"Chart did not select a real bucket");
            click(usage,"近 7 天");await(60000,()->{Object s=field(usage,"snapshot");return s instanceof UsageSnapshot&&((UsageSnapshot)s).period.equals("7d");},"Selected range did not load");settle();save(usage,"week.png");
            runOnMainSync(()->{findClickable(usage.getWindow().getDecorView(),"近 30 天").performClick();findClickable(usage.getWindow().getDecorView(),"今天").performClick();findClickable(usage.getWindow().getDecorView(),"近 7 天").performClick();});
            await(60000,()->{Object s=field(usage,"snapshot");return s instanceof UsageSnapshot&&((UsageSnapshot)s).period.equals("7d");},"Rapid ranges accepted an old response");settle();stage("CHART_AND_RANGE_RACES_PASSED");
            ScrollView scroll=(ScrollView)field(usage,"scroll");runOnMainSync(()->{scroll.setSmoothScrollingEnabled(false);scroll.fullScroll(View.FOCUS_DOWN);});settle();save(usage,"models.png");modelWidths(usage);stage("MODEL_VALUE_WIDTH_PASSED");
            click(usage,"Token 与计费明细");settle();shell("input keyevent 4");settle();
            final Activity normalUsage=usage;final ScrollView normalScroll=scroll;runOnMainSync(()->normalScroll.fullScroll(View.FOCUS_UP));
            Activity previous=usage;shell("settings put system font_scale 1.5");await(60000,()->current.get()!=previous&&current.get() instanceof UsageActivity&&current.get().getResources().getConfiguration().fontScale>1.4f,"Large font recreation failed");
            Activity large=current.get();awaitText(large,"更新于");settle();save(large,"large-font.png");ScrollView largeScroll=(ScrollView)field(large,"scroll");runOnMainSync(()->{largeScroll.setSmoothScrollingEnabled(false);largeScroll.fullScroll(View.FOCUS_DOWN);});settle();modelWidths(large);save(large,"large-font-models.png");stage("LARGE_FONT_PASSED");
            shell("settings put system font_scale 1.0");await(60000,()->current.get()!=large&&current.get() instanceof UsageActivity&&current.get().getResources().getConfiguration().fontScale<1.1f,"Font restore failed");
            Activity restored=current.get();awaitText(restored,"更新于");scales(0);settle();click(restored,"模型");await(60000,()->current.get() instanceof MainActivity,"Reduced-motion home did not open");Activity reduced=current.get();
            runOnMainSync(()->{findClickable(reduced.getWindow().getDecorView(),"分组").performClick();findClickable(reduced.getWindow().getDecorView(),"模型").performClick();});settle();
            View root=((ViewGroup)reduced.findViewById(android.R.id.content)).getChildAt(0);require(root.getAlpha()==1&&root.getTranslationY()==0,"Reduced motion left content hidden");require(((Integer)field(reduced,"catalogTab"))==0,"Reduced motion did not settle");save(reduced,"reduced-motion.png");stage("REDUCED_MOTION_PASSED");
            scales(1);click(reduced,"用量");await(60000,()->current.get() instanceof UsageActivity,"Usage return failed");Activity last=current.get();awaitText(last,"更新于");target.stopService(new Intent(target,GatewayService.class));await(20000,()->!GatewayService.ready,"Gateway stop failed");click(last,"刷新统计");awaitText(last,"统计暂不可用");settle();save(last,"unavailable.png");stage("UNAVAILABLE_PASSED");
            Bundle result=new Bundle();result.putString("stream","Native1.6 native callback checks passed (plugin WebView scope is recorded in stages): real start, retained refresh, search/copy, connection/repeated feedback, latest navigation, range races, chart, readable model widths, large fonts, reduced motion and unavailable state.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){try{scales(1);shell("settings put system font_scale 1.0");}catch(Exception ignored){}Bundle result=new Bundle();StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));result.putString("stream",trace.toString());finish(Activity.RESULT_CANCELED,result);}
    }
    private void navigationAlignment(Activity a){runOnMainSync(()->{for(String name:new String[]{"模型","分组","用量","插件"}){View cell=findClickable(a.getWindow().getDecorView(),name);require(cell instanceof ViewGroup,"Navigation cell missing");ViewGroup group=(ViewGroup)cell;TextView label=null;for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof TextView)label=(TextView)group.getChildAt(i);require(label!=null&&(label.getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK)==Gravity.CENTER_HORIZONTAL,"Navigation label is not centered under its icon: "+name);}});}
    private void periodAlignment(View root){runOnMainSync(()->{View tabs=findClass(root,"PeriodTabs");require(tabs!=null,"Period control missing");View indicator=(View)field(tabs,"indicator");TextView[] labels=(TextView[])field(tabs,"labels");require(indicator.getWidth()==tabs.getWidth()/labels.length&&indicator.getWidth()>10,"Initial selected-period underline has not been measured");});}
    private void modelWidths(Activity a){View model=find(a.getWindow().getDecorView(),"android-echo",true);require(model!=null,"Actual model missing");Rect rect=new Rect();require(model.getGlobalVisibleRect(rect),"Model cannot be reached by scrolling");ViewGroup row=(ViewGroup)model.getParent().getParent();ViewGroup values=(ViewGroup)row.getChildAt(row.getChildCount()-1);require(values.getWidth()>10,"Model values collapsed");require(((View)model.getParent()).getWidth()>60,"Value column collapsed the model label");for(int i=0;i<values.getChildCount();i++){View v=values.getChildAt(i);if(v instanceof TextView)require(v.getWidth()>10&&v.getHeight()>0,"Model value not laid out");}}
    private void awaitScript(android.webkit.WebView web,String condition)throws Exception{long until=SystemClock.elapsedRealtime()+120000;while(SystemClock.elapsedRealtime()<until){AtomicReference<String> result=new AtomicReference<>();runOnMainSync(()->web.evaluateJavascript("Boolean("+condition+")",result::set));await(15000,()->result.get()!=null,"WebView evaluation stalled");if("true".equals(result.get()))return;Thread.sleep(200);}throw new AssertionError("Plugin DOM did not become usable");}
    private void stage(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(1,b);}
    private interface Condition{boolean check();}
    private void await(long timeout,Condition c,String error)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){if(c.check())return;Thread.sleep(150);}throw new AssertionError(error);}
    private void awaitText(Activity a,String text)throws Exception{await(60000,()->{AtomicBoolean result=new AtomicBoolean();runOnMainSync(()->result.set(find(a.getWindow().getDecorView(),text,true)!=null));return result.get();},"Native text did not appear: "+text);}
    private void settle(){try{Thread.sleep(550);}catch(InterruptedException e){Thread.currentThread().interrupt();}waitForIdleSync();}
    private void focused(View root){long end=SystemClock.elapsedRealtime()+60000;while(!root.hasWindowFocus()&&SystemClock.elapsedRealtime()<end){try{Thread.sleep(150);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("Interrupted while waiting for the application window");}}require(root.hasWindowFocus(),"System overlay or another app owns focus; native interaction is not valid");}
    private void click(Activity a,String s){clickView(a.getWindow().getDecorView(),s);}
    private void clickView(View root,String text){focused(root);AtomicBoolean found=new AtomicBoolean();runOnMainSync(()->{View v=findClickable(root,text);if(v!=null){found.set(true);v.performClick();}});require(found.get(),"Clickable native control missing: "+text);waitForIdleSync();}
    private View find(View v,String text,boolean contains){String desc=v.getContentDescription()==null?"":v.getContentDescription().toString(),value=v instanceof TextView?((TextView)v).getText().toString():"";if(contains?(value.contains(text)||desc.contains(text)):(value.equals(text)||desc.equals(text)))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text,contains);if(found!=null)return found;}}return null;}
    private View findClickable(View v,String text){if(v.isClickable()){String desc=v.getContentDescription()==null?"":v.getContentDescription().toString(),value=v instanceof TextView?((TextView)v).getText().toString():"";if(desc.equals(text)||value.equals(text))return v;}if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View found=findClickable(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private View findClass(View v,String name){if(v.getClass().getSimpleName().equals(name))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View result=findClass(g.getChildAt(i),name);if(result!=null)return result;}}return null;}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new RuntimeException(e);}}
    private static boolean boolField(Object o,String name){return Boolean.TRUE.equals(field(o,name));}
    private static void setField(Object o,String name,Object value)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private String clipboard(Context c){AtomicReference<String> text=new AtomicReference<>();runOnMainSync(()->{android.content.ClipboardManager cb=(android.content.ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE);text.set(cb.getPrimaryClip()==null?"":cb.getPrimaryClip().getItemAt(0).getText().toString());});return text.get();}
    private void save(Activity a,String name){saveView(a.findViewById(android.R.id.content),name);saveSystem(name);}
    private void saveSystem(String name){Bitmap screen=getUiAutomation().takeScreenshot();require(screen!=null,"System screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(evidence,name.replace(".png",".screen.png")))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}catch(IOException e){throw new RuntimeException(e);}finally{screen.recycle();}}
    private void saveView(View view,String name){runOnMainSync(()->{try{require(view.getWidth()>0&&view.getHeight()>0,"Native view not laid out");Bitmap b=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);view.draw(c);try(FileOutputStream out=new FileOutputStream(new File(evidence,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}try(FileWriter out=new FileWriter(new File(evidence,name+".txt"))){dump(view,out,0);}b.recycle();}catch(IOException e){throw new RuntimeException(e);}});}
    private void dump(View v,Writer out,int depth)throws IOException{int[] xy=new int[2];v.getLocationOnScreen(xy);String text=v instanceof TextView?((TextView)v).getText().toString():"";out.write(" ".repeat(depth)+v.getClass().getSimpleName()+" "+xy[0]+","+xy[1]+" "+v.getWidth()+"x"+v.getHeight()+" "+text.replace('\n',' ')+"\n");if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)dump(g.getChildAt(i),out,depth+1);}}
    private void scales(int value)throws IOException{shell("settings put global window_animation_scale "+value);shell("settings put global transition_animation_scale "+value);shell("settings put global animator_duration_scale "+value);}
    private void shell(String s)throws IOException{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(s);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}}
    private static void require(boolean b,String s){if(!b)throw new AssertionError(s);}
}
