package ai.magpie.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** One visual proportion bar. Adjacent labels expose exact values to accessibility. */
final class DistributionBar extends View {
    private final Ui ui;
    private final double[] portions;
    private final int[] colors;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();

    DistributionBar(Ui ui, double[] portions, int[] colors) {
        super(ui.a);
        this.ui = ui;
        this.portions = portions;
        this.colors = colors;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        outline.rewind();
        outline.addRoundRect(0, 0, width, height, height / 2f, height / 2f, Path.Direction.CW);
    }

    @Override protected void onDraw(Canvas canvas) {
        int layer = canvas.save();
        canvas.clipPath(outline);
        canvas.drawColor(ui.line);
        float position = 0;
        for (int i = 0; i < portions.length; i++) {
            float end = Math.min(getWidth(), position + (float) (portions[i] * getWidth()));
            paint.setColor(colors[i]);
            canvas.drawRect(position, 0, end, getHeight(), paint);
            position = end;
        }
        canvas.restoreToCount(layer);
    }
}
