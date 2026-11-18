package ai.magpie.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
import java.util.function.IntConsumer;

/** Solid trend plot; companion controls expose the same points to accessibility. */
final class InsightChart extends View {
    private final Ui ui;
    private final List<LedgerSnapshot.Point> points;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int metric;
    private final IntConsumer selectPoint;
    private final double max;
    private int selected;
    private int touched = -1;

    InsightChart(Ui ui, List<LedgerSnapshot.Point> points, int metric, int selected, IntConsumer selectPoint) {
        super(ui.a);
        this.ui = ui;
        this.points = points;
        this.metric = metric;
        this.selected = selected;
        this.selectPoint = selectPoint;
        double largest = 0;
        for (LedgerSnapshot.Point point : points) largest = Math.max(largest, value(point));
        max = largest;
        setBackground(ui.shape(ui.card, 14, true));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(!points.isEmpty());
    }

    private double value(LedgerSnapshot.Point point) {
        if (metric == 1) return point.totals.tokens();
        if (metric == 2) return point.totals.cost;
        return point.totals.calls;
    }

    void select(int index) {
        selected = index;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = ui.dp(14);
        float width = getWidth() - 2 * inset;
        float height = getHeight() - 2 * inset;
        if (width <= 0 || height <= 0) return;
        for (int i = 0; i <= 3; i++) {
            float y = inset + i * height / 3;
            paint.setColor(ui.line);
            paint.setStrokeWidth(ui.dp(1));
            canvas.drawLine(inset, y, inset + width, y, paint);
        }
        if (points.isEmpty()) return;
        float slot = width / points.size();
        float maxBarWidth = ui.dp(20);
        float barWidth = Math.min(maxBarWidth, Math.max(slot * .66f, 1));
        for (int i = 0; i < points.size(); i++) {
            double value = value(points.get(i));
            float center = inset + (i + .5f) * slot;
            if (i == selected) {
                paint.setColor(ui.accentSoft);
                canvas.drawRect(center - slot / 2, inset, center + slot / 2, inset + height, paint);
            }
            if (value <= 0 || max <= 0) continue;
            float barHeight = Math.max(ui.dp(2), (float) (height * (value / max)));
            paint.setColor(i == selected ? ui.ink : ui.muted);
            canvas.drawRoundRect(center - barWidth / 2, inset + height - barHeight,
                    center + barWidth / 2, inset + height, 2, 2, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float width = Math.max(1, getWidth() - 2 * ui.dp(14));
            float ratio = (event.getX() - ui.dp(14)) / width;
            touched = Math.max(0, Math.min(points.size() - 1, (int) (ratio * points.size())));
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (touched >= 0) selectPoint.accept(touched);
        return true;
    }
}
