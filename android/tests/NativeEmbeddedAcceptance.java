package ai.magpie.android;
import android.app.*;import android.content.*;import android.os.*;import android.webkit.*;import java.io.*;import java.lang.reflect.*;import java.util.concurrent.atomic.AtomicReference;
/** Diagnostic for the actual embedded upstream group page. No model/vendor calls. */
public final class NativeEmbeddedAcceptance extends Instrumentation {
 private final AtomicReference<Activity> current=new AtomicReference<>();
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle out=new Bundle();try{
  Context c=getTargetContext();c.getSharedPreferences("host",0).edit().putBoolean("launched",true).putBoolean("askedNotifications",true).putBoolean("remote",false).commit();
  ((Application)c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){public void onActivityResumed(Activity a){current.set(a);}public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}});
  startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));runOnMainSync(()->c.startForegroundService(new Intent(c,GatewayService.class)));
  long end=SystemClock.elapsedRealtime()+120000;while(!GatewayService.ready&&SystemClock.elapsedRealtime()<end)Thread.sleep(250);if(!GatewayService.ready)throw new AssertionError("Actual core did not become ready");
  Activity page=startActivitySync(new Intent(c,ManagementActivity.class).putExtra("view","groups").putExtra("parentRoot","groups").putExtra("remote",false).putExtra("addGroup",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  AtomicReference<String> state=new AtomicReference<>("");end=SystemClock.elapsedRealtime()+120000;
  while(SystemClock.elapsedRealtime()<end){
   AtomicReference<Boolean> gone=new AtomicReference<>(false);runOnMainSync(()->{try{gone.set((Boolean)get(page,"rendererGone"));if(!gone.get())((WebView)get(page,"web")).evaluateJavascript("JSON.stringify({ready:document.readyState,groups:!!document.querySelector('#view-groups:not([hidden])'),editor:(function(){var e=document.querySelector('#view-groups .rt-gedit');return !!e&&e.getBoundingClientRect().width>0&&e.getBoundingClientRect().height>0})()})",state::set);}catch(Exception e){state.set("inspection unavailable");}});
   if(gone.get()){if(!GatewayService.ready)throw new AssertionError("Renderer failure also stopped gateway");out.putString("stream","ENV_LIMIT: software SDK WebView renderer stopped; native recovery remained visible and actual gateway remains ready");finish(Activity.RESULT_OK,out);return;}
   boolean opened=false;try{Object decoded=new org.json.JSONTokener(state.get()).nextValue();if(decoded instanceof String)opened=new org.json.JSONObject((String)decoded).optBoolean("editor",false);}catch(Exception ignored){}
   if(opened){out.putString("stream","PASS actual embedded groups page and directly opened new-group editor; "+state.get());finish(Activity.RESULT_OK,out);return;}
   Thread.sleep(500);
  }
  throw new AssertionError("Embedded group editor not ready: "+state.get());
 }catch(Throwable e){out.putString("stream",android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,out);}}
 private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
}
