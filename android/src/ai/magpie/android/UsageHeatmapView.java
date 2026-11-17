package ai.magpie.android;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

/** A stationary calendar; day controls below provide full-size touch targets. */
final class UsageHeatmapView extends View {
    private final Ui ui;
    private final List<UsageHeatmap.Day> days;
    private final Consumer<UsageHeatmap.Day> select;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int[] COLORS={0xffeeeeed,0xffd2d2d0,0xffa1a1a0,0xff666665,0xff292928};
    private int metric,selected;
    private float left,top,cell,gap;
    UsageHeatmapView(Context context,Ui ui,List<UsageHeatmap.Day> days,Consumer<UsageHeatmap.Day> select){
        super(context);this.ui=ui;this.days=days;this.select=select;selected=days.size()-1;
        setFocusable(true);setClickable(true);paint.setTypeface(ui.font(false,400));describe();
    }
    void metric(int metric){this.metric=metric;invalidate();}
    void day(int index){if(days.isEmpty())return;selected=Math.max(0,Math.min(days.size()-1,index));describe();select.accept(days.get(selected));invalidate();}
    int day(){return selected;}
    private void describe(){setContentDescription(days.isEmpty()?"每日用量，无日期数据":"每日用量，已选 "+days.get(selected).date+"；左右方向键切换日期，下方按钮也可切换");}
    private float sp(float value){return value*getResources().getDisplayMetrics().scaledDensity;}
    private void measureGrid(int width){
        paint.setTextSize(sp(11));left=paint.measureText("日")+ui.dp(9);gap=ui.dp(3);top=sp(13)+ui.dp(10);
        cell=Math.max(1,(width-left-gap*11)/12f);
    }
    @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);measureGrid(width);setMeasuredDimension(width,(int)Math.ceil(top+7*cell+6*gap+ui.dp(4)));}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);measureGrid(getWidth());paint.setTypeface(ui.font(false,400));paint.setColor(ui.muted);paint.setTextSize(sp(11));
        for(int row:new int[]{0,2,4,6})canvas.drawText(new String[]{"一","二","三","四","五","六","日"}[row],0,top+row*(cell+gap)+cell*.5f-paint.ascent()*.45f,paint);
        double max=0;for(UsageHeatmap.Day d:days)max=Math.max(max,d.value(metric));float lastLabel=-10000;int lastMonth=-1;
        for(int i=0;i<days.size();i++){
            UsageHeatmap.Day d=days.get(i);int row=d.date.getDayOfWeek().getValue()-1;
            int offset=days.get(0).date.getDayOfWeek().getValue()-1;int col=(i+offset)/7;
            float x=left+col*(cell+gap),y=top+row*(cell+gap);double value=d.value(metric);
            int level=value<=0?0:Math.max(1,Math.min(4,(int)Math.ceil(4*Math.log1p(value)/Math.log1p(max))));
            paint.setStyle(Paint.Style.FILL);paint.setColor(COLORS[level]);canvas.drawRoundRect(x,y,x+cell,y+cell,ui.dp(2),ui.dp(2),paint);
            if(i==selected){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(ui.dp(1));paint.setColor(ui.ink);canvas.drawRoundRect(x-ui.dp(1),y-ui.dp(1),x+cell+ui.dp(1),y+cell+ui.dp(1),ui.dp(2),ui.dp(2),paint);paint.setStyle(Paint.Style.FILL);}
            if(i==0||row==0){
                java.time.LocalDate anchor=d.date.plusDays(3);if(anchor.isAfter(days.get(days.size()-1).date))anchor=days.get(days.size()-1).date;
                if(anchor.getMonthValue()!=lastMonth&&x>=lastLabel+ui.dp(7)){
                    String label=anchor.getMonthValue()+"月";paint.setColor(ui.muted);paint.setTextSize(sp(11));
                    if(x+paint.measureText(label)<=getWidth()){canvas.drawText(label,x,sp(11),paint);lastLabel=x+paint.measureText(label);lastMonth=anchor.getMonthValue();}
                }
            }
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_UP){
            int col=Math.max(0,Math.min(11,(int)((event.getX()-left)/(cell+gap))));
            int row=Math.max(0,Math.min(6,(int)((event.getY()-top)/(cell+gap))));
            if(!days.isEmpty()&&event.getX()>=left&&event.getY()>=top){int index=col*7+row-days.get(0).date.getDayOfWeek().getValue()+1;if(index>=0&&index<days.size())day(index);}
            performClick();return true;
        }
        return event.getAction()==MotionEvent.ACTION_DOWN||super.onTouchEvent(event);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){day(selected+(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1));return true;}
        return super.onKeyDown(code,event);
    }
}
