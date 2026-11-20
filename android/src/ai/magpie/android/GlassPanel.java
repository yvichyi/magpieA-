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
    private final Paint bevel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final boolean topSurface;
    private final Appearance appearance;
    private GlassFrame.Backdrop backdrop;
    private Shader film, readableFilm;
    private boolean dark, solid;

    GlassPanel(Context context, boolean topSurface, Appearance appearance) {
        super(context);
        this.topSurface = topSurface;
        this.appearance = appearance;
        density = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setElevation(appearance == Appearance.SWISS ? 0 : (topSurface ? 5 : 8) * density);
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
        bevel.setStyle(Paint.Style.STROKE);
        bevel.setStrokeWidth(Math.max(1f, density));
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
        if (appearance == Appearance.SWISS) return 0;
        return Math.min(26 * density, Math.min(getWidth(), getHeight()) * 0.5f);
    }

    private void updateRim() {
        if (getWidth() == 0 || getHeight() == 0) return;
        stroke.setShader(new LinearGradient(0, 0, getWidth() * .18f, getHeight(), dark
                ? new int[]{0xffa4b6bf, 0x99728590, 0xff52636e}
                : new int[]{0xffffffff, 0x997a9baa, 0xd17d98a8},
                new float[]{0, .52f, 1}, Shader.TileMode.CLAMP));
        bevel.setShader(new LinearGradient(0, 0, 0, getHeight(), dark
                ? new int[]{0x4fe4f3f8, 0x00ffffff, 0x32768c98}
                : new int[]{0xd9ffffff, 0x00ffffff, 0xa8ffffff},
                new float[]{0, .58f, 1}, Shader.TileMode.CLAMP));
        film = new LinearGradient(0, 0, 0, getHeight(), dark
                ? new int[]{0xb32a3943, 0xa019242c, 0xc026343e}
                : new int[]{0xaaffffff, 0x70f6fbfd, 0x94e9f3f7},
                new float[]{0, .55f, 1}, Shader.TileMode.CLAMP);
        readableFilm = new LinearGradient(0, 0, 0, getHeight(),
                dark ? 0xe12b3c46 : 0xe1f8fcfe, dark ? 0xe1233039 : 0xe1eaf2f6, Shader.TileMode.CLAMP);
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
        if (appearance == Appearance.SWISS) {
            Appearance.Palette palette = appearance.palette(dark);
            fill.setColor(palette.card());
            canvas.drawRect(0, 0, getWidth(), getHeight(), fill);
            fill.setColor(palette.line());
            float edge = topSurface ? getHeight() - density : 0;
            canvas.drawRect(0, edge, getWidth(), edge + density, fill);
            return;
        }
        float radius = radius();
        boolean live = backdrop != null && canvas.isHardwareAccelerated()
                && backdrop.drawLens(canvas, topSurface, getLeft(), getTop(), getWidth(), getHeight(), radius);
        if (live) {
            fill.setColor(0xffffffff);
            fill.setShader(!topSurface || getResources().getConfiguration().fontScale >= 1.5f ? readableFilm : film);
        } else if (solid || !canvas.isHardwareAccelerated()) {
            fill.setShader(null);
            fill.setColor(dark ? 0xff25282c : 0xfff4f6f8);
        } else {
            fill.setShader(null);
            fill.setColor(dark ? 0xf025282c : 0xecf4f6f8);
        }
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), radius, radius, fill);
        float inset = stroke.getStrokeWidth() * .5f;
        float rim = Math.max(0, radius - inset);
        canvas.drawRoundRect(inset, inset, getWidth() - inset, getHeight() - inset, rim, rim, stroke);
        if (!solid) {
            float inner = inset + density * 1.5f;
            canvas.drawRoundRect(inner, inner, getWidth() - inner, getHeight() - inner,
                    Math.max(0, radius - inner), Math.max(0, radius - inner), bevel);
        }
    }
}
