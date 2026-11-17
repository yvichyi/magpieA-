package ai.magpie.android;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.concurrent.*;
/** Lab only: run a verified runtime using the actual app UID/domain. */
public final class PluginRuntimeProbe extends Instrumentation {
 public void onCreate(Bundle args){super.onCreate(args);start();}
 public void onStart(){Bundle out=new Bundle();try{
  File cached=new File(getTargetContext().getFilesDir(),"bun-probe");if(!cached.isFile())throw new AssertionError("Verified Android Bun probe file missing");
  try{java.lang.Process p=new ProcessBuilder(cached.getAbsolutePath(),"--version").redirectErrorStream(true).start();boolean ended=p.waitFor(15,TimeUnit.SECONDS);String text=ended?new String(p.getInputStream().readAllBytes()):"timed out";p.destroy();throw new AssertionError("Writable app-data execution unexpectedly started: "+text);}catch(IOException expected){if(!expected.getMessage().toLowerCase().contains("permission denied"))throw expected;out.putString("stream","CONFIRMED: verified Android Bun in writable app files is denied by Android execution policy under the app UID: "+expected.getMessage()+"\n");}
  finish(Activity.RESULT_OK,out);
 }catch(Throwable e){out.putString("stream",e.toString());finish(Activity.RESULT_CANCELED,out);}}
}
