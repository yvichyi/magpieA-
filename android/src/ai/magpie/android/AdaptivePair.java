package ai.magpie.android;
import android.view.*;
import android.widget.*;

/** A heading/value and its action get separate rows when their natural widths do not fit. */
final class AdaptivePair extends LinearLayout {
    private final View leading,trailing;private final int gap;
    private final boolean equalColumns;
    AdaptivePair(Ui ui,View leading,View trailing,int spacing){this(ui, leading, trailing, spacing, false);}
    AdaptivePair(Ui ui, View leading, View trailing, int spacing, boolean equalColumns) {
        super(ui.a);
        this.leading = leading;
        this.trailing = trailing;
        this.equalColumns = equalColumns;
        gap = ui.dp(spacing);
        setGravity(equalColumns ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        addView(leading);
        addView(trailing);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=Math.max(0,MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight());
        int loose=MeasureSpec.makeMeasureSpec(width,MeasureSpec.AT_MOST),free=MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED);
        leading.measure(loose,free);trailing.measure(loose,free);
        int contentWidth = equalColumns ? 2 * Math.max(leading.getMeasuredWidth(), trailing.getMeasuredWidth())
                : leading.getMeasuredWidth() + trailing.getMeasuredWidth();
        boolean stack = contentWidth + gap > width;
        int orientation=stack?VERTICAL:HORIZONTAL;if(getOrientation()!=orientation)setOrientation(orientation);
        LayoutParams lp=(LayoutParams)leading.getLayoutParams(),rp=(LayoutParams)trailing.getLayoutParams();
        lp.width=stack?LayoutParams.MATCH_PARENT:0;lp.height=LayoutParams.WRAP_CONTENT;lp.weight=stack?0:1;
        rp.width=stack?LayoutParams.MATCH_PARENT:equalColumns?0:LayoutParams.WRAP_CONTENT;
        rp.height=LayoutParams.WRAP_CONTENT;rp.weight=!stack&&equalColumns?1:0;
        rp.setMarginStart(stack ? 0 : gap);
        rp.resolveLayoutDirection(getLayoutDirection());
        rp.topMargin=stack?gap:0;
        super.onMeasure(widthSpec,heightSpec);
    }
}
