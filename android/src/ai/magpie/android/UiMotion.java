package ai.magpie.android;

import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import java.util.WeakHashMap;

/** Cancellable transitions and touch feedback that respect reduced motion. */
final class UiMotion {
    static final PathInterpolator EASE=new PathInterpolator(.2f,0,0,1);
    private static final PathInterpolator PAGE_EASE = new PathInterpolator(.23f, .72f, .22f, 1f);
    private static final OvershootInterpolator RELEASE = new OvershootInterpolator(.75f);
    private static final WeakHashMap<View,Integer> epochs=new WeakHashMap<>();
    private static final WeakHashMap<View,ValueAnimator> sizes=new WeakHashMap<>();
    private static final WeakHashMap<Activity,Integer> routes=new WeakHashMap<>();
    private static View surface(Activity a){View v=a.findViewById(android.R.id.content);return v instanceof ViewGroup&&((ViewGroup)v).getChildCount()>0?((ViewGroup)v).getChildAt(0):v;}
    static void enter(Activity a){
        routes.put(a,routes.getOrDefault(a,0)+1);View root=surface(a);if(root==null)return;reset(root);
    }
    static void stay(Activity a){pause(a);}
    static void pause(Activity a){routes.put(a,routes.getOrDefault(a,0)+1);View root=surface(a);if(root!=null)reset(root);}
    static void go(Activity a,Intent intent,boolean finish){leave(a,()->{a.startActivity(intent);if(finish)a.finish();window(a,true);});}
    static void result(Activity a,Intent intent,int request){leave(a,()->{a.startActivityForResult(intent,request);window(a,true);});}
    static void close(Activity a){leave(a,()->{a.finish();window(a,false);});}
    private static void window(Activity activity, boolean opening) {
        if (enabled(activity)) activity.overridePendingTransition(
                opening ? R.anim.page_enter : R.anim.page_return,
                opening ? R.anim.page_underlay : R.anim.page_exit);
        else activity.overridePendingTransition(0, 0);
    }
    private static void leave(Activity a,Runnable action){
        int epoch=routes.getOrDefault(a,0)+1;routes.put(a,epoch);View root=surface(a);
        if(root==null){action.run();return;}
        reset(root);root.postOnAnimation(()->{if(routes.getOrDefault(a,0)==epoch&&!a.isDestroyed()&&!a.isFinishing())action.run();});
    }
    static boolean enabled(){return ValueAnimator.areAnimatorsEnabled();}
    static boolean enabled(android.content.Context context){return !GlassOptions.stillMotion(context);}
    static void reset(View v){epochs.put(v,epochs.getOrDefault(v,0)+1);ValueAnimator size=sizes.remove(v);if(size!=null)size.cancel();v.animate().cancel();v.animate().withEndAction(null);v.setAlpha(1);v.setTranslationX(0);v.setTranslationY(0);}
    static void page(View view, float distance, long duration) {
        reset(view);
        if (distance == 0 || !enabled(view.getContext())) return;
        int epoch = epochs.get(view);
        view.setTranslationX(distance);
        ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                else view.getViewTreeObserver().removeOnPreDrawListener(this);
                if (view.isAttachedToWindow() && epochs.getOrDefault(view, 0) == epoch) {
                    if (enabled(view.getContext())) view.animate().translationX(0)
                            .setDuration(duration).setInterpolator(PAGE_EASE).start();
                    else view.setTranslationX(0);
                }
                return true;
            }
        });
    }

    static void press(View view) {
        view.setOnTouchListener((target, event) -> {
            int action = event.getActionMasked();
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP
                    && action != MotionEvent.ACTION_CANCEL) return false;
            if (!enabled(target.getContext())) {
                target.animate().cancel();
                target.setScaleX(1);
                target.setScaleY(1);
                return false;
            }
            boolean down = action == MotionEvent.ACTION_DOWN;
            target.animate().cancel();
            target.animate().scaleX(down ? .94f : 1f).scaleY(down ? .94f : 1f)
                    .setDuration(down ? 85 : 210).setInterpolator(down ? EASE : RELEASE).start();
            return false;
        });
    }
    static void swap(View v,Runnable update){
        reset(v);update.run();
    }

    static void reveal(View view,boolean show){
        int from=view.getVisibility()==View.VISIBLE?view.getHeight():0;reset(view);int epoch=epochs.get(view);
        ViewGroup.LayoutParams layout=view.getLayoutParams();
        if(!enabled(view.getContext())||!view.isAttachedToWindow()){layout.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(layout);view.setVisibility(show?View.VISIBLE:View.GONE);return;}
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
        boolean animate=enabled(box.getContext());
        copy.setAlpha(0);check.setAlpha(1);check.setScaleX(animate?.8f:1);check.setScaleY(animate?.8f:1);
        if(animate)check.animate().scaleX(1).scaleY(1).setDuration(160).setInterpolator(EASE).start();
        int token=epochs.getOrDefault(box,0)+1;epochs.put(box,token);
        box.postDelayed(()->{if(epochs.getOrDefault(box,0)!=token)return;copy.setAlpha(1);check.setAlpha(0);},1300);
    }
}
