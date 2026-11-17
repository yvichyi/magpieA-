package ai.magpie.android;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicReference;

/** Isolated catalog fixture; actual core creation is covered by NativeSetupAcceptance. */
public final class NativeDailyAcceptance extends Instrumentation {
    private final AtomicReference<Activity> current=new AtomicReference<>();
    private Activity home;
    private boolean keyboard;
    @Override public void onCreate(Bundle b){super.onCreate(b);keyboard="true".equals(b.getString("keyboard"));start();}
    @Override public void onStart(){try{
        Context c=getTargetContext();c.getSharedPreferences("host",0).edit().putBoolean("launched",true).putBoolean("askedNotifications",true).putBoolean("remote",false).commit();
        c.getSharedPreferences("model-favorites",0).edit().clear().commit();
        ((Application)c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){public void onActivityResumed(Activity a){current.set(a);}public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}});
        home=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        ui(()->{GatewayService.ready=true;inject(home);});
        waitFor(()->find(root(),"仅可用")!=null,"Missing native catalog availability filter");
        click(root(),"仅可用");require(find(root(),"off/m0")==null&&find(root(),"one/hidden")==null,"Availability filter includes disabled/hidden models");
        visible(root(),"接入客户端");capture("01-models");
        click(root(),"接入客户端");Dialog picker=(Dialog)get(home,"modelPicker");require(picker.isShowing(),"Picker did not open");
        View pickerRoot=picker.getWindow().getDecorView();require(find(pickerRoot,"搜索接入模型") instanceof EditText,"Picker has no search input");
        ui(()->((EditText)find(pickerRoot,"搜索接入模型")).setText("one/m39"));
        click(pickerRoot,"接入模型 one/m39");waitFor(()->{Object d=get(home,"connectionSheet");return d!=null&&((Dialog)d).isShowing();},"Picker did not open exact model");
        Dialog connection=(Dialog)get(home,"connectionSheet");View sheet=connection.getWindow().getDecorView();
        require(find(sheet,"one/m39")!=null,"Wrong model reference after search");click(sheet,"收藏模型");require(find(sheet,"取消收藏")!=null,"Favorite action did not confirm state");capture("02-connection");ui(connection::dismiss);
        click(root(),"收藏");require(find(root(),"one/m39")!=null&&find(root(),"one/m0")==null,"Favorites filter did not isolate saved model");capture("03-favorites");
        Activity old=home;ui(old::recreate);waitFor(()->current.get()!=old&&current.get() instanceof MainActivity,"Recreation did not return");home=current.get();ui(()->inject(home));
        require(find(root(),"one/m39")!=null&&find(root(),"one/m0")==null,"Recreation lost favorites filter or persistence");
        click(root(),"搜索模型与分组");ui(()->((EditText)find(root(),"搜索输入框")).setText("nothing"));require(find(root(),"清除搜索")!=null,"No-result search has no recovery");ui(home::onBackPressed);require(find(root(),"one/m39")!=null,"Back lost filtered context");
        ui(()->invoke(home,"focusProvider",new Class[]{String.class,String.class},"two","第二服务商"));require(find(root(),"two/second")!=null,"Saved-provider focus remained hidden by favorites filter");ui(home::onBackPressed);require(find(root(),"one/m0")!=null,"Back did not clear provider focus");
        ui(()->invoke(home,"showConnection",new Class[]{String.class},"@qa"));Dialog route=(Dialog)get(home,"connectionSheet");require(route.isShowing(),"Route connection missing");require(find(route.getWindow().getDecorView(),"收藏模型")==null&&find(route.getWindow().getDecorView(),"取消收藏")==null,"Route offers an unusable model favorite");ui(route::dismiss);
        click(root(),"接入客户端");picker=(Dialog)get(home,"modelPicker");final View p=picker.getWindow().getDecorView();waitFor(()->find(p,"接入模型 one/m39")!=null,"Favorite not in picker");ui(()->((EditText)find(p,"搜索接入模型")).setText("no result"));require(find(p,"没有可接入的匹配模型\n清除或修改搜索条件")!=null||find(p,"没有可接入的匹配模型")!=null,"Picker empty state missing");ui(()->((EditText)find(p,"搜索接入模型")).setText("模型"));capture("04-search-picker");
        if(keyboard){ui(()->{EditText q=(EditText)find(p,"搜索接入模型");q.requestFocus();((android.view.inputmethod.InputMethodManager)home.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(q,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});waitFor(()->{Rect visible=new Rect();p.getWindowVisibleDisplayFrame(visible);return visible.bottom<home.getResources().getDisplayMetrics().heightPixels*.9f;},"Picker software keyboard did not become visible");visible(p,"搜索接入模型");capture("05-picker-keyboard");}
        ui(()->((EditText)find(p,"搜索接入模型")).setText("one/m39"));
        ui(()->{try{Field f=MainActivity.class.getDeclaredField("catalog");f.setAccessible(true);f.set(home,WorkspaceSnapshot.parse("{\"providers\":[]}","{\"groups\":[]}"));}catch(Exception e){throw new RuntimeException(e);}});
        click(p,"接入模型 one/m39");require(!((Dialog)get(home,"connectionSheet")).isShowing(),"Stale picker opened a removed model");
        Bundle ok=new Bundle();ok.putString("stream","PASS filters, fixed CTA, searchable exact-reference selection, favorite persistence, recreation, Back, empty recovery\n");finish(Activity.RESULT_OK,ok);
    }catch(Throwable e){Bundle out=new Bundle();out.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
    private View root(){return home.getWindow().getDecorView();}
    private void inject(Activity a){try{
        org.json.JSONArray models=new org.json.JSONArray();for(int i=0;i<40;i++)models.put(new org.json.JSONObject().put("id","m"+i).put("name","模型 "+i).put("on",true));models.put(new org.json.JSONObject().put("id","hidden").put("on",false));
        org.json.JSONArray ps=new org.json.JSONArray().put(new org.json.JSONObject().put("id","one").put("name","常用 API").put("models",models)).put(new org.json.JSONObject().put("id","off").put("off",true).put("models",models)).put(new org.json.JSONObject().put("id","two").put("name","第二服务商").put("models",new org.json.JSONArray().put(new org.json.JSONObject().put("id","second").put("on",true))));
        WorkspaceSnapshot s=WorkspaceSnapshot.parse(new org.json.JSONObject().put("providers",ps).put("gateway",new org.json.JSONObject().put("url","http://127.0.0.1:18789")).toString(),"{\"groups\":[{\"id\":\"qa\",\"ref\":\"@qa\",\"members\":[]}]}");
        Field f=MainActivity.class.getDeclaredField("catalog");f.setAccessible(true);f.set(a,s);((TextView)get(a,"metricsHint")).setText(s.providers.size()+" 个服务商 · "+s.groups.size()+" 个路由组");((TextView)get(a,"todayHint")).setText("用量暂未载入");invoke(a,"renderCatalog",new Class[0]);
    }catch(Exception e){throw new RuntimeException(e);}}
    private interface Check{boolean get()throws Exception;}
    private void waitFor(Check c,String why)throws Exception{long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AtomicReference<Boolean> value=new AtomicReference<>(false);ui(()->{try{value.set(c.get());}catch(Exception e){throw new RuntimeException(e);}});if(value.get())return;Thread.sleep(150);}throw new AssertionError(why);}
    private void ui(Runnable r){AtomicReference<Throwable> e=new AtomicReference<>();runOnMainSync(()->{try{r.run();}catch(Throwable x){e.set(x);}});if(e.get()!=null)throw new AssertionError(e.get());}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static void invoke(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private static View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||(v instanceof TextView&&label.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View x=find(((ViewGroup)v).getChildAt(i),label);if(x!=null)return x;}return null;}
    private void click(View root,String label)throws Exception{waitFor(()->find(root,label)!=null,"Missing "+label);ui(()->{View v=find(root,label);require(v!=null,"Missing "+label);v.performClick();});}
    private void visible(View root,String label)throws Exception{waitFor(()->{View v=find(root,label);Rect r=new Rect();return v!=null&&v.getGlobalVisibleRect(r)&&r.height()>=v.getHeight()-1&&r.height()>=44*home.getResources().getDisplayMetrics().density;},"Clipped "+label);}
    private static void require(boolean x,String why){if(!x)throw new AssertionError(why);}
    private void capture(String name)throws Exception{getUiAutomation().waitForIdle(250,10000);Thread.sleep(2200);Bitmap b=getUiAutomation().takeScreenshot();File dir=new File(getTargetContext().getFilesDir(),"daily-acceptance");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
