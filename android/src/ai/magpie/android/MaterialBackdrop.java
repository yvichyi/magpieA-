package ai.magpie.android;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A quiet workspace surface with a slight tonal shift under the toolbar and dock. */
final class MaterialBackdrop extends Drawable {
    private static final float[] STOPS = {0f, .25f, .75f, 1f};
    private final int[] tones;
    private final Paint paint = new Paint(Paint.DITHER_FLAG);

    MaterialBackdrop(int background, boolean dark) {
        tones = dark
                ? new int[]{0xff192029, background, background, 0xff202831}
                : new int[]{0xfff7f8f9, background, background, 0xffe9edf1};
        paint.setColor(background);
    }

    @Override protected void onBoundsChange(Rect bounds) {
        if (bounds.isEmpty()) {
            paint.setShader(null);
            return;
        }
        paint.setShader(new LinearGradient(bounds.left, bounds.top,
                bounds.left + bounds.width() * .12f, bounds.bottom,
                tones, STOPS, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), paint);
    }

    @Override public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public int getAlpha() { return paint.getAlpha(); }

    @Override public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return paint.getAlpha() == 255 && paint.getColorFilter() == null
                ? PixelFormat.OPAQUE : PixelFormat.TRANSLUCENT;
    }
}
