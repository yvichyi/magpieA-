package ai.magpie.android;
import android.view.*;import android.widget.*;import java.util.*;
/** Equal action columns where labels fit; full-width actions otherwise. */
final class ActionButtons extends LinearLayout {
    private final int gap;private final List<String> original=new ArrayList<>();
    ActionButtons(Ui ui){super(ui.a);gap=ui.dp(10);}
    void addAction(Button button){original.add(button.getText().toString());addView(button,new LayoutParams(0,-2,1));}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int available=Math.max(0,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());float widest=0;
        for(int i=0;i<getChildCount();i++){TextView b=(TextView)getChildAt(i);float text=Math.max(b.getPaint().measureText(original.get(i)),b.getPaint().measureText(b.getText().toString()));widest=Math.max(widest,Math.max(b.getMinimumWidth(),text+b.getPaddingLeft()+b.getPaddingRight()));}
        boolean stack=widest*getChildCount()+gap*Math.max(0,getChildCount()-1)>available;int orientation=stack?VERTICAL:HORIZONTAL;if(getOrientation()!=orientation)setOrientation(orientation);
        for(int i=0;i<getChildCount();i++){LayoutParams p=(LayoutParams)getChildAt(i).getLayoutParams();p.width=stack?-1:0;p.height=-2;p.weight=stack?0:1;p.leftMargin=stack||i==0?0:gap;p.topMargin=stack&&i>0?gap:0;}
        super.onMeasure(widthSpec,heightSpec);
    }
}
