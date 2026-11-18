package ai.magpie.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/** Draws the lens and its rim under the dock's ordinary native children. */
final class GlassPanel extends FrameLayout {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private GlassFrame.Backdrop backdrop;
    private boolean dark, solid;

    GlassPanel(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setElevation(5 * density);
        setOutlineAmbientShadowColor(0x440d1520);
        setOutlineSpotShadowColor(0x660d1520);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline out) {
                out.setRoundRect(0, 0, getWidth(), getHeight(), radius());
            }
        });
        setClipToOutline(true);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, density));
    }

    boolean dark() { return dark; }

    void setMaterial(GlassFrame.Backdrop source, boolean night, boolean opaque) {
        if (source == backdrop && night == dark && opaque == solid) return;
        backdrop = source;
        dark = night;
        solid = opaque;
        updateRim();
        invalidate();
    }

    private float radius() {
        return Math.min(26 * density, Math.min(getWidth(), getHeight()) * 0.5f);
    }

    private void updateRim() {
        if (getWidth() == 0 || getHeight() == 0) return;
        stroke.setShader(new LinearGradient(0, 0, getWidth() * .15f, getHeight(),
                dark ? 0xbb939ba2 : 0xffffffff, dark ? 0xff41464b : 0xb5a9b3be, Shader.TileMode.CLAMP));
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateRim();
        invalidateOutline();
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float radius = radius();
        boolean live = backdrop != null && canvas.isHardwareAccelerated()
                && backdrop.drawLens(canvas, getLeft(), getTop(), getWidth(), getHeight(), radius);
        if (live) fill.setColor(dark ? 0xbd191d20 : 0xaeeef3f8);
        else if (solid || !canvas.isHardwareAccelerated()) fill.setColor(dark ? 0xff25282c : 0xfff4f6f8);
        else fill.setColor(dark ? 0xf025282c : 0xecf4f6f8);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, fill);
        float inset = stroke.getStrokeWidth() * .5f;
        float rim = Math.max(0, radius - inset);
        canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset, rim, rim, stroke);
    }
}
