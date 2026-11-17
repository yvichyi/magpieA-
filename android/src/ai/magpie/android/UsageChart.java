package ai.magpie.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
import java.util.function.Consumer;

/** Interactive chart of the upstream's time buckets, without re-bucketing or filling invented values. */
final class UsageChart extends View {
    private final Ui ui;
    private final List<UsageSnapshot.Point> points;
    private final Consumer<UsageSnapshot.Point> select;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int selected = -1;
    private float reveal=1;
    private android.animation.ValueAnimator animator;
    UsageChart(Context context, Ui ui, List<UsageSnapshot.Point> points, Consumer<UsageSnapshot.Point> select) {
        this(context,ui,points,select,true);
    }
    UsageChart(Context context,Ui ui,List<UsageSnapshot.Point> points,Consumer<UsageSnapshot.Point> select,boolean animate){
        super(context); this.ui=ui;this.points=points;this.select=select;
        setFocusable(true);setClickable(true);
        if(animate&&UiMotion.enabled()){reveal=0;animator=android.animation.ValueAnimator.ofFloat(0,1);animator.setDuration(280);animator.setInterpolator(UiMotion.EASE);animator.addUpdateListener(a->{reveal=(float)a.getAnimatedValue();invalidate();});animator.start();}
        setContentDescription("调用趋势，共 "+points.size()+" 个时间段；点击查看数值");
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float font=12*getResources().getDisplayMetrics().scaledDensity;
        float top=Math.max(ui.dp(20),font+ui.dp(8)), bottom=getHeight()-Math.max(ui.dp(27),font+ui.dp(10)), width=getWidth(), height=bottom-top;
        long max=0;for(UsageSnapshot.Point p:points)max=Math.max(max,p.calls);
        paint.setTypeface(ui.font(true,400));paint.setColor(ui.line);paint.setStrokeWidth(ui.dp(1));
        for(int i=0;i<3;i++){float y=top+height*i/2;c.drawLine(0,y,width,y,paint);}
        paint.setTextSize(font);paint.setColor(ui.muted);
        c.drawText(max==0?"0":"峰值 "+UsageSnapshot.number(max),0,font,paint);
        if(points.isEmpty())return;
        float step=width/points.size(),gap=Math.min(ui.dp(5),step*.35f);
        for(int i=0;i<points.size();i++){
            float h=max==0?0:(float)((double)points.get(i).calls/max)*height*reveal;
            if(h<=0)continue;
            paint.setColor(i==selected?ui.ink:ui.muted);paint.setShader(null);
            c.drawRoundRect(new RectF(i*step+gap/2,bottom-Math.max(h,ui.dp(2)),(i+1)*step-gap/2,bottom),0,0,paint);
        }
        paint.setShader(null);paint.setColor(ui.muted);paint.setTextSize(font);
        String first=points.get(0).label,last=points.get(points.size()-1).label;
        c.drawText(first,0,getHeight()-ui.dp(5),paint);
        c.drawText(last,Math.max(0,width-paint.measureText(last)),getHeight()-ui.dp(5),paint);
    }
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_UP){
            if(!points.isEmpty())selected=Math.max(0,Math.min(points.size()-1,(int)(e.getX()/Math.max(1,getWidth())*points.size())));
            performClick();return true;
        }
        return e.getAction()==MotionEvent.ACTION_DOWN||super.onTouchEvent(e);
    }
    @Override public boolean performClick(){
        super.performClick();if(points.isEmpty())return true;
        selected=Math.max(0,selected);UsageSnapshot.Point p=points.get(selected);select.accept(p);
        setContentDescription(p.label+"，"+p.calls+" 次调用，"+p.tokens+" Token；左右方向键切换时间段");invalidate();return true;
    }
    @Override public boolean onKeyDown(int code,android.view.KeyEvent event){
        if(!points.isEmpty()&&(code==android.view.KeyEvent.KEYCODE_DPAD_LEFT||code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)){
            selected=Math.max(0,Math.min(points.size()-1,selected+(code==android.view.KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)));performClick();return true;
        }
        return super.onKeyDown(code,event);
    }
}
