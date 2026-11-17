package ai.magpie.android;

import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import java.util.WeakHashMap;

/** Small, cancellable state transitions. System animation accessibility settings remain authoritative. */
final class UiMotion {
    static final PathInterpolator EASE=new PathInterpolator(.2f,0,0,1);
    private static final WeakHashMap<View,Integer> epochs=new WeakHashMap<>();
    private static final WeakHashMap<View,ValueAnimator> sizes=new WeakHashMap<>();
    private static final WeakHashMap<Activity,Integer> routes=new WeakHashMap<>();
    private static View surface(Activity a){View v=a.findViewById(android.R.id.content);return v instanceof ViewGroup&&((ViewGroup)v).getChildCount()>0?((ViewGroup)v).getChildAt(0):v;}
    static void enter(Activity a){
        routes.put(a,routes.getOrDefault(a,0)+1);View root=surface(a);if(root==null)return;reset(root);
        // Primary destinations keep a stable window; no whole-page fade or displacement.
    }
    static void stay(Activity a){pause(a);}
    static void pause(Activity a){routes.put(a,routes.getOrDefault(a,0)+1);View root=surface(a);if(root!=null)reset(root);}
    static void go(Activity a,Intent intent,boolean finish){leave(a,()->{a.startActivity(intent);if(finish)a.finish();a.overridePendingTransition(0,0);});}
    static void result(Activity a,Intent intent,int request){leave(a,()->{a.startActivityForResult(intent,request);a.overridePendingTransition(0,0);});}
    static void close(Activity a){leave(a,()->{a.finish();a.overridePendingTransition(0,0);});}
    private static void leave(Activity a,Runnable action){
        int epoch=routes.getOrDefault(a,0)+1;routes.put(a,epoch);View root=surface(a);
        if(root==null){action.run();return;}
        reset(root);root.postOnAnimation(()->{if(routes.getOrDefault(a,0)==epoch&&!a.isDestroyed()&&!a.isFinishing())action.run();});
    }
    static boolean enabled(){return ValueAnimator.areAnimatorsEnabled();}
    static void reset(View v){epochs.put(v,epochs.getOrDefault(v,0)+1);ValueAnimator size=sizes.remove(v);if(size!=null)size.cancel();v.animate().cancel();v.animate().withEndAction(null);v.setAlpha(1);v.setTranslationY(0);}
    static void swap(View v,Runnable update){
        reset(v);update.run();
    }

    static void reveal(View view,boolean show){
        int from=view.getVisibility()==View.VISIBLE?view.getHeight():0;reset(view);int epoch=epochs.get(view);
        ViewGroup.LayoutParams layout=view.getLayoutParams();
        if(!enabled()||!view.isAttachedToWindow()){layout.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(layout);view.setVisibility(show?View.VISIBLE:View.GONE);return;}
        int width=view.getWidth();if(width<=0&&view.getParent() instanceof View)width=((View)view.getParent()).getWidth();
        view.measure(View.MeasureSpec.makeMeasureSpec(Math.max(1,width),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int target=show?view.getMeasuredHeight():0;view.setVisibility(View.VISIBLE);layout.height=from;view.setLayoutParams(layout);
        ValueAnimator animator=ValueAnimator.ofInt(from,target);sizes.put(view,animator);animator.setDuration(180);animator.setInterpolator(EASE);
        animator.addUpdateListener(a->{if(epochs.getOrDefault(view,0)!=epoch)return;ViewGroup.LayoutParams p=view.getLayoutParams();p.height=(int)a.getAnimatedValue();view.setLayoutParams(p);view.setAlpha(show?a.getAnimatedFraction():1-a.getAnimatedFraction());});
        animator.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){if(epochs.getOrDefault(view,0)!=epoch)return;sizes.remove(view);ViewGroup.LayoutParams p=view.getLayoutParams();p.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(p);view.setAlpha(1);view.setVisibility(show?View.VISIBLE:View.GONE);}});animator.start();
    }
    static void copyFeedback(View target){
        if(!(target instanceof ViewGroup))return;ViewGroup box=(ViewGroup)target;
        if(box.getChildCount()!=2)return;
        View copy=box.getChildAt(0),check=box.getChildAt(1);copy.animate().cancel();check.animate().cancel();
        copy.setAlpha(0);check.setAlpha(1);check.setScaleX(enabled()?.8f:1);check.setScaleY(enabled()?.8f:1);
        if(enabled())check.animate().scaleX(1).scaleY(1).setDuration(160).setInterpolator(EASE).start();
        int token=epochs.getOrDefault(box,0)+1;epochs.put(box,token);
        box.postDelayed(()->{if(epochs.getOrDefault(box,0)!=token)return;copy.setAlpha(1);check.setAlpha(0);},1300);
    }
}
