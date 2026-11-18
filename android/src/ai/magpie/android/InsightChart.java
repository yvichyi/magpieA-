package ai.magpie.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Solid trend plot; companion controls expose the same points to accessibility. */
final class InsightChart extends View {
    private final Ui ui;
    private final List<LedgerSnapshot.Point> points;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int metric;
    private final IntConsumer selectPoint;
    private final double maximum;
    private final String[] ticks = new String[4];
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
        maximum = scaleTop(largest);
        for (int i = 0; i < ticks.length; i++) ticks[i] = axisLabel(maximum * ((3 - i) / 3d));
        paint.setTypeface(ui.font(true, 400));
        paint.setTextSize(ui.dp(11));
        setBackground(ui.shape(ui.card, 14, true));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setClickable(!points.isEmpty());
    }

    private double value(LedgerSnapshot.Point point) {
        if (metric == 1) return point.totals.tokens();
        if (metric == 2) return point.totals.cost;
        return point.totals.calls;
    }

    private double scaleTop(double largest) {
        if (largest <= 0) return metric == 2 ? .003 : 3;
        double rough = Math.max(largest / 3, 0.000001);
        double magnitude = Math.pow(10, Math.floor(Math.log10(rough)));
        double interval = magnitude;
        for (double step : new double[]{1, 2, 5, 10}) {
            interval = magnitude * step;
            if (interval >= rough) break;
        }
        if (metric != 2) interval = Math.max(1, interval);
        double top = interval * 3;
        return Double.isFinite(top) ? top : largest;
    }

    private String axisLabel(double amount) {
        if (amount == 0) return "0";
        if (metric == 2) return amount >= .01 && amount < 1000
                ? String.format(Locale.ROOT, "$%.2f", amount) : String.format(Locale.ROOT, "$%.1g", amount);
        if (amount >= 1e9) return String.format(Locale.ROOT, "%.1gB", amount / 1e9);
        if (amount >= 1e6) return String.format(Locale.ROOT, "%.1gM", amount / 1e6);
        if (amount >= 1e3) return String.format(Locale.ROOT, "%.1gK", amount / 1e3);
        return String.format(Locale.ROOT, "%.0f", amount);
    }

    void select(int index) {
        selected = index;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left = ui.dp(metric == 2 ? 57 : 42);
        float top = ui.dp(14);
        float width = getWidth() - left - ui.dp(14);
        float height = getHeight() - top - ui.dp(14);
        if (width <= 0 || height <= 0) return;
        for (int i = 0; i <= 3; i++) {
            float y = top + i * height / 3;
            paint.setColor(ui.line);
            paint.setStrokeWidth(ui.dp(1));
            canvas.drawLine(left, y, left + width, y, paint);
            String label = ticks[i];
            paint.setColor(ui.muted);
            canvas.drawText(label, Math.max(ui.dp(4), left - ui.dp(7) - paint.measureText(label)), y + ui.dp(3), paint);
        }
        if (points.isEmpty()) return;
        float slot = width / points.size();
        float maxBarWidth = ui.dp(20);
        float barWidth = Math.min(maxBarWidth, Math.max(slot * .66f, 1));
        for (int i = 0; i < points.size(); i++) {
            double value = value(points.get(i));
            float center = left + (i + .5f) * slot;
            if (i == selected) {
                paint.setColor(ui.accentSoft);
                canvas.drawRect(center - slot / 2, top, center + slot / 2, top + height, paint);
            }
            if (value <= 0) continue;
            float barHeight = Math.max(ui.dp(2), (float) (height * (value / maximum)));
            paint.setColor(i == selected ? ui.ink : ui.muted);
            canvas.drawRoundRect(center - barWidth / 2, top + height - barHeight,
                    center + barWidth / 2, top + height, 2, 2, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float left = ui.dp(metric == 2 ? 57 : 42);
            float width = Math.max(1, getWidth() - left - ui.dp(14));
            float ratio = (event.getX() - left) / width;
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
