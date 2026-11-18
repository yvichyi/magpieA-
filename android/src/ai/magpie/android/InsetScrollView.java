package ai.magpie.android;

import android.content.Context;
import android.graphics.Rect;
import android.widget.ScrollView;

/** Lets content pass behind the toolbar while scrolling focused controls clear of it. */
final class InsetScrollView extends ScrollView {
    InsetScrollView(Context context) { super(context); }

    void setToolbarInset(int inset) {
        setClipToPadding(false);
        if (getPaddingTop() != inset) setPadding(getPaddingLeft(), inset, getPaddingRight(), getPaddingBottom());
    }

    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        int delta = super.computeScrollDeltaToGetChildRectOnScreen(rect);
        // Accessibility and keyboard navigation request the View rectangle in content coordinates.
        int toolbarDelta = rect.top - getScrollY() - getPaddingTop();
        if (getPaddingTop() > 0 && delta <= 0 && toolbarDelta < 0) {
            return Math.max(-getScrollY(), Math.min(delta, toolbarDelta));
        }
        return delta;
    }
}
