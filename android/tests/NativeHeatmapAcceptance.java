package ai.magpie.android;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Installed calendar controls, independent reads, errors, and cross-workspace races. */
public final class NativeHeatmapAcceptance extends Instrumentation {
    private String address,addressB,mode;
    private Activity activity;
    private File evidence;
    @Override public void onCreate(Bundle args){super.onCreate(args);address=args.getString("address");addressB=args.getString("addressB");mode=args.getString("mode","flow");start();}
    @Override public void onStart(){
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("host",0);
        boolean oldRemote=prefs.getBoolean("remote",false);String oldAddress=prefs.getString("remoteAddress",""),oldKey=prefs.getString("remoteKey",""),oldPeriod=prefs.getString("usagePeriod","today");
        try{
            prefs.edit().putBoolean("remote",true).putString("remoteAddress",address).putString("remoteKey","heatmap-fixture-key-1234567890").putString("usagePeriod","today").commit();
            evidence=new File(getTargetContext().getFilesDir(),"heatmap-evidence");evidence.mkdirs();
            activity=startActivitySync(new Intent(getTargetContext(),UsageActivity.class).putExtra("remote",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitFor(()->find(root(),"每日用量")!=null,"Native Usage has no daily heatmap section");
            if(mode.equals("present")){pass("Daily heatmap section is present");return;}
            waitFor(()->calls()==17,"Daily heatmap did not read fixture ledger");
            long reads=state(address);int generations=(Integer)get(activity,"heatGeneration");
            click(root(),"7 天");waitFor(()->"7d".equals(get(activity,"loadedPeriod")),"Summary period failed to update");
            require(calls()==17,"Summary period replaced independent heatmap data");
            require(state(address)==reads&&(Integer)get(activity,"heatGeneration")==generations,"Period switch bypassed the heatmap minute cache");
            EditText search=(EditText)get(activity,"modelSearch");ui(()->search.setText("no-matching-model"));
            require(calls()==17,"Model search filtered the independent heatmap");ui(()->search.setText(""));
            View heat=(View)get(activity,"heatBody");click(heat,"Token");require((Integer)get(activity,"heatMetric")==1,"Token metric not selected");
            require(((TextView)get(activity,"heatValues")).getText().toString().startsWith("2,400")||((TextView)get(activity,"heatValues")).getText().toString().startsWith("2400"),"Actual cache-inclusive Token value missing");
            click(heat,"费用");require(((TextView)get(activity,"heatValues")).getText().toString().startsWith("待定价"),"Unknown price displayed as free");
            Object chart=get(activity,"heatView");int day=(Integer)invoke(chart,"day",new Class<?>[0]);
            click(heat,"← 上一天");require((Integer)invoke(chart,"day",new Class<?>[0])==day-1,"Previous day control failed");
            click(heat,"下一天 →");require((Integer)invoke(chart,"day",new Class<?>[0])==day,"Next day control failed");
            show((View)get(activity,"heatArea"));capture("heatmap-"+mode);checkLayout((View)get(activity,"heatArea"));
            show((View)((View)get(activity,"heatPrevious")).getParent());capture("heatmap-"+mode+"-details");
            control(address,"fail");ui(()->invoke(activity,"refreshHeat",new Class<?>[]{boolean.class},true));
            waitFor(()->!((Boolean)get(activity,"heatBusy"))&&((TextView)get(activity,"heatStatus")).getText().toString().contains("保留上次"),"Failed read did not retain known calendar");require(calls()==17,"Failed read became zero");
            control(address,"ok");control(address,"hold");long before=state(address);
            ui(()->invoke(activity,"refreshHeat",new Class<?>[]{boolean.class},true));waitState(address,before+1);
            prefs.edit().putString("remoteAddress",addressB).commit();ui(()->invoke(activity,"refresh",new Class<?>[]{boolean.class},true));
            waitFor(()->calls()==29,"New workspace calendar blocked by old pending request");control(address,"release");
            long deadline=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<deadline&&heatThreads())Thread.sleep(100);waitForIdleSync();
            require(calls()==29,"Old workspace response replaced the new calendar");
            require(((TextView)get(activity,"heatStatus")).getText().toString().startsWith("更新于"),"Old response changed the new workspace status");
            pass("Native heatmap: independent period/search, minute cache, metrics, day controls, unknown prices, failed-read retention, workspace race and measured layout");
        }catch(Throwable error){Bundle b=new Bundle();b.putString("stream",android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,b);}
        finally{try{control(address,"release");}catch(Exception ignored){}prefs.edit().putBoolean("remote",oldRemote).putString("remoteAddress",oldAddress).putString("remoteKey",oldKey).putString("usagePeriod",oldPeriod).commit();}
    }
    private void pass(String message){Bundle b=new Bundle();b.putString("stream","PASS "+message+"\n");finish(Activity.RESULT_OK,b);}
    private View root(){return activity.getWindow().getDecorView();}
    private static Object get(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static Object invoke(Object o,String name,Class<?>[] types,Object... args){try{Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,args);}catch(Exception e){throw new RuntimeException(e);}}
    private long calls()throws Exception{Object heat=get(activity,"heatmap");if(heat==null)return -1;List<?> days=(List<?>)get(activity,"heatDays");return days.isEmpty()?-1:((Number)get(days.get(days.size()-1),"calls")).longValue();}
    private interface Check{boolean ok()throws Exception;}
    private void waitFor(Check check,String message)throws Exception{long end=SystemClock.elapsedRealtime()+45000;while(SystemClock.elapsedRealtime()<end){AtomicReference<Boolean> value=new AtomicReference<>(false);ui(()->{try{value.set(check.ok());}catch(Exception e){throw new RuntimeException(e);}});if(value.get())return;Thread.sleep(100);}throw new AssertionError(message);}
    private void ui(Runnable run){AtomicReference<Throwable> error=new AtomicReference<>();runOnMainSync(()->{try{run.run();}catch(Throwable t){error.set(t);}});if(error.get()!=null)throw new AssertionError(error.get());}
    private static View find(View v,String label){if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void click(View root,String label)throws Exception{waitFor(()->find(root,label)!=null,"Missing control "+label);ui(()->{View v=find(root,label);while(!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();v.performClick();});}
    private void show(View v)throws Exception{ui(()->v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),Math.min(v.getHeight(),350)),true));waitForIdleSync();Thread.sleep(800);}
    private void capture(String name)throws Exception{getUiAutomation().waitForIdle(250,10000);Thread.sleep(900);Bitmap image=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(evidence,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private void checkLayout(View view){ui(()->{
        require(view.getWidth()>0,"Calendar has no measured width");checkText(view);
        View previous=find(view,"← 上一天"),next=find(view,"下一天 →");Rect a=new Rect(),b=new Rect();previous.getHitRect(a);next.getHitRect(b);
        require(!Rect.intersects(a,b),"Day controls overlap");require(previous.getHeight()>=44&&next.getHeight()>=44,"Day controls lack full-size touch targets");
    });}
    private void checkText(View v){if(v instanceof TextView){TextView t=(TextView)v;if(t.getLayout()!=null){for(int i=0;i<t.getLineCount();i++)require(t.getLayout().getEllipsisCount(i)==0,"Truncated heatmap text");require(t.getLayout().getLineBottom(t.getLineCount()-1)<=t.getHeight()-t.getPaddingBottom()+1,"Heatmap text is vertically clipped");}}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)checkText(((ViewGroup)v).getChildAt(i));}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private String request(String base,String path,boolean post)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(5000);if(post){c.setRequestMethod("POST");c.setDoOutput(true);c.getOutputStream().close();}try(InputStream in=c.getInputStream()){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}finally{c.disconnect();}}
    private void control(String base,String command)throws Exception{request(base,"/_test/"+command,true);}
    private long state(String base)throws Exception{return Long.parseLong(request(base,"/_test/state",false));}
    private void waitState(String base,long target)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<end){if(state(base)>=target)return;Thread.sleep(50);}throw new AssertionError("Pending heatmap request did not start");}
    private static boolean heatThreads(){for(Thread t:Thread.getAllStackTraces().keySet())if(t.isAlive()&&t.getName().equals("magpie-heatmap"))return true;return false;}
}
