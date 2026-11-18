package ai.magpie.android;

import android.view.*;
import android.widget.*;

/** Stable period controls; indicator moves without replacing the control tree. */
final class PeriodTabs extends FrameLayout {
    private final Ui ui;private final TextView[] labels;private final View indicator;private int selected;
    PeriodTabs(Ui ui,String[] names,int selected,java.util.function.IntConsumer action){
        super(ui.a);this.ui=ui;this.selected=selected;labels=new TextView[names.length];
        setMinimumHeight(ui.dp(50));LinearLayout row=ui.row();row.setPadding(0,0,0,ui.dp(2));addView(row,new FrameLayout.LayoutParams(-1,-2));
        for(int i=0;i<names.length;i++){final int index=i;TextView label=ui.text(names[i],14,ui.muted,false);label.setGravity(Gravity.CENTER);label.setMinHeight(ui.dp(48));label.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT,0));label.setOnClickListener(v->{if(index==this.selected)return;select(index,true);action.accept(index);});labels[i]=label;row.addView(label,new LinearLayout.LayoutParams(0,-2,1));}
        View line=new View(ui.a);line.setBackgroundColor(ui.line);addView(line,new FrameLayout.LayoutParams(-1,ui.dp(1),Gravity.BOTTOM));
        indicator=new View(ui.a);indicator.setBackgroundColor(ui.ink);addView(indicator,new FrameLayout.LayoutParams(1,ui.dp(2),Gravity.BOTTOM));select(selected,false);
    }
    void select(int index,boolean animate){
        selected=index;
        for(int i=0;i<labels.length;i++){boolean on=i==index;labels[i].setTextColor(on?ui.ink:ui.muted);labels[i].setTypeface(ui.font(false,on?600:400));labels[i].setSelected(on);String name=labels[i].getText().toString();labels[i].setContentDescription((name.equals("7 天")?"近 7 天":name.equals("30 天")?"近 30 天":name)+(on?"，已选择":""));}
        if(getWidth()==0)return;int width=getWidth()/labels.length;indicator.animate().cancel();
        if(animate&&UiMotion.enabled(ui.a))indicator.animate().translationX(index*width).setDuration(170).setInterpolator(UiMotion.EASE).start();else indicator.setTranslationX(index*width);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        super.onMeasure(widthSpec,heightSpec);
        ViewGroup.LayoutParams p=indicator.getLayoutParams();int width=getMeasuredWidth()/labels.length;
        if(p.width!=width){p.width=width;super.onMeasure(widthSpec,heightSpec);}
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);select(selected,false);}
}
