package ai.magpie.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;
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
    private float plotLeft, plotTop, plotWidth, plotHeight, labelOffset;
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
        paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                getResources().getDisplayMetrics()));
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
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        float labelWidth = 0;
        for (String label : ticks) labelWidth = Math.max(labelWidth, paint.measureText(label));
        Paint.FontMetrics font = paint.getFontMetrics();
        labelOffset = -(font.ascent + font.descent) / 2;
        plotLeft = (float) Math.ceil(labelWidth) + ui.dp(17);
        plotTop = Math.max(ui.dp(14), (font.descent - font.ascent) / 2 + ui.dp(6));
        plotWidth = width - plotLeft - ui.dp(12);
        plotHeight = height - 2 * plotTop;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (plotWidth <= 0 || plotHeight <= 0) return;
        float slot = points.isEmpty() ? 0 : plotWidth / points.size();
        if (selected >= 0 && selected < points.size()) {
            paint.setColor(ui.accentSoft);
            canvas.drawRect(plotLeft + selected * slot, plotTop,
                    plotLeft + (selected + 1) * slot, plotTop + plotHeight, paint);
        }
        for (int i = 0; i <= 3; i++) {
            float y = plotTop + i * plotHeight / 3;
            paint.setColor(ui.line);
            paint.setStrokeWidth(ui.dp(1));
            canvas.drawLine(plotLeft, y, plotLeft + plotWidth, y, paint);
            String label = ticks[i];
            paint.setColor(ui.muted);
            canvas.drawText(label, plotLeft - ui.dp(7) - paint.measureText(label), y + labelOffset, paint);
        }
        float maxBarWidth = ui.dp(20);
        float barWidth = Math.min(maxBarWidth, Math.max(slot * .66f, 1));
        float bottom = plotTop + plotHeight;
        for (int i = 0; i < points.size(); i++) {
            double value = value(points.get(i));
            if (value <= 0) continue;
            float center = plotLeft + (i + .5f) * slot;
            float barHeight = Math.max(ui.dp(2), (float) (plotHeight * (value / maximum)));
            paint.setColor(i == selected ? ui.accent : ui.muted);
            canvas.drawRoundRect(center - barWidth / 2, bottom - barHeight,
                    center + barWidth / 2, bottom, ui.dp(2), ui.dp(2), paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (points.isEmpty() || plotWidth <= 0 || plotHeight <= 0) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            float ratio = (event.getX() - plotLeft) / plotWidth;
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
