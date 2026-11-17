package ai.magpie.android;
import android.view.*;
import android.widget.*;

/** A heading/value and its action get separate rows when their natural widths do not fit. */
final class AdaptivePair extends LinearLayout {
    private final View leading,trailing;private final int gap;
    AdaptivePair(Ui ui,View leading,View trailing,int spacing){super(ui.a);this.leading=leading;this.trailing=trailing;gap=ui.dp(spacing);setGravity(Gravity.CENTER_VERTICAL);addView(leading);addView(trailing);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=Math.max(0,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());
        int loose=MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        leading.measure(loose,free);trailing.measure(loose,free);boolean stack=leading.getMeasuredWidth()+trailing.getMeasuredWidth()+gap>width;
        int orientation=stack?VERTICAL:HORIZONTAL;if(getOrientation()!=orientation)setOrientation(orientation);
        LayoutParams lp=(LayoutParams)leading.getLayoutParams(),rp=(LayoutParams)trailing.getLayoutParams();
        lp.width=stack?LayoutParams.MATCH_PARENT:0;lp.height=LayoutParams.WRAP_CONTENT;lp.weight=stack?0:1;
        rp.width=stack?LayoutParams.MATCH_PARENT:LayoutParams.WRAP_CONTENT;rp.height=LayoutParams.WRAP_CONTENT;rp.weight=0;rp.leftMargin=stack?0:gap;rp.topMargin=stack?gap:0;
        super.onMeasure(widthSpec,heightSpec);
    }
}
