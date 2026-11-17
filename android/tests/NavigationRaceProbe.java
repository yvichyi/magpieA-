package ai.magpie.android;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.util.concurrent.atomic.*;import java.lang.reflect.*;
/** Standalone regression for last-tap-wins with system animations disabled. */
public final class NavigationRaceProbe extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle result=new Bundle();try{
  try(ParcelFileDescriptor p=getUiAutomation().executeShellCommand("settings put global animator_duration_scale 0")){new ParcelFileDescriptor.AutoCloseInputStream(p).readAllBytes();}
  AtomicReference<Activity> current=new AtomicReference<>();((Application)getTargetContext().getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){public void onActivityResumed(Activity a){current.set(a);}public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}});
  Activity home=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();
  runOnMainSync(()->{find(home.getWindow().getDecorView(),"用量").performClick();find(home.getWindow().getDecorView(),"路由").performClick();});Thread.sleep(1000);waitForIdleSync();
  Field f=MainActivity.class.getDeclaredField("catalogTab");f.setAccessible(true);if(current.get()!=home||f.getInt(home)!=1)throw new AssertionError("Reduced-motion rapid navigation launched a superseded UsageActivity instead of the last Routing selection");
  result.putString("stream","PASS reduced-motion last-tap-wins navigation\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable e){result.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,result);}finally{try(ParcelFileDescriptor p=getUiAutomation().executeShellCommand("settings put global animator_duration_scale 1")){new ParcelFileDescriptor.AutoCloseInputStream(p).readAllBytes();}catch(Exception ignored){}}}
 private View find(View v,String name){if(v.isClickable()&&(name.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||v instanceof TextView&&name.contentEquals(((TextView)v).getText())))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View f=find(g.getChildAt(i),name);if(f!=null)return f;}}return null;}
}
