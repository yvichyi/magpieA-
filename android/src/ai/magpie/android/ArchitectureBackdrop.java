package ai.magpie.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Edge crops stay in the live backdrop while the workspace keeps an even reading surface. */
final class ArchitectureBackdrop extends Drawable {
    private static Bitmap sharedPhoto;
    private final Bitmap photo;
    private final int background;
    private final boolean dark;
    private final float density;
    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint wash = new Paint();
    private final Rect top = new Rect(), bottom = new Rect();
    private final Rect topPhoto = new Rect(), bottomPhoto = new Rect();
    private Shader topWash, bottomWash;

    ArchitectureBackdrop(Context context, int background, boolean dark) {
        this.background = background;
        this.dark = dark;
        density = context.getResources().getDisplayMetrics().density;
        photo = photo(context);
    }

    private static synchronized Bitmap photo(Context context) {
        if (sharedPhoto == null) {
            sharedPhoto = BitmapFactory.decodeResource(context.getResources(), R.drawable.architectural_light);
        }
        return sharedPhoto;
    }

    @Override protected void onBoundsChange(Rect bounds) {
        int cap = bounds.height() / 3;
        top.set(bounds.left, bounds.top, bounds.right, bounds.top + Math.min(cap, Math.round(152 * density)));
        bottom.set(bounds.left, bounds.bottom - Math.min(cap, Math.round(122 * density)), bounds.right, bounds.bottom);
        int rgb = background & 0xffffff;
        topWash = tint(top, new int[]{rgb | (dark ? 0x92000000 : 0x48000000), rgb | 0xc4000000, background});
        bottomWash = tint(bottom, new int[]{background, rgb | 0xba000000, rgb | (dark ? 0x98000000 : 0x65000000)});
        if (photo != null) {
            coverPhoto(top, topPhoto, .22f);
            coverPhoto(bottom, bottomPhoto, .77f);
        }
    }

    private Shader tint(Rect area, int[] colors) {
        return new LinearGradient(0, area.top, 0, Math.max(area.top + 1, area.bottom),
                colors, new float[]{0f, .57f, 1f}, Shader.TileMode.CLAMP);
    }

    private void coverPhoto(Rect area, Rect crop, float verticalFocus) {
        float scale = Math.max(area.width() / (float) photo.getWidth(), area.height() / (float) photo.getHeight());
        if (scale <= 0) return;
        int width = Math.min(photo.getWidth(), Math.max(1, Math.round(area.width() / scale)));
        int height = Math.min(photo.getHeight(), Math.max(1, Math.round(area.height() / scale)));
        int left = (photo.getWidth() - width) / 2;
        int top = Math.round((photo.getHeight() - height) * verticalFocus);
        crop.set(left, top, left + width, top + height);
    }

    @Override public void draw(Canvas canvas) {
        wash.setShader(null);
        wash.setColor(background);
        canvas.drawRect(getBounds(), wash);
        if (photo == null || top.isEmpty() || bottom.isEmpty()) return;
        drawBand(canvas, topPhoto, top, topWash);
        drawBand(canvas, bottomPhoto, bottom, bottomWash);
    }

    private void drawBand(Canvas canvas, Rect crop, Rect area, Shader tint) {
        canvas.drawBitmap(photo, crop, area, image);
        wash.setShader(tint);
        canvas.drawRect(area, wash);
    }

    @Override public void setAlpha(int alpha) { image.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { image.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
