package ai.magpie.android;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/** Records the native content beneath the two independent glass surfaces. */
final class GlassFrame extends FrameLayout {
    interface Backdrop {
        Canvas beginScene(int width, int height);
        void endScene();
        void drawScene(Canvas canvas);
        boolean drawLens(Canvas canvas, boolean topSurface, int left, int top, int width, int height, float radius);
        void release();
    }

    private final GlassPanel panel, toolbar;
    private final int gutter;
    private final boolean dark;
    private final Appearance appearance;
    private final Drawable plainBackground, photoBackground;
    private View scene;
    private GlassPolicy.Mode mode = GlassPolicy.Mode.SOLID;
    private Backdrop backdrop;
    private boolean imeVisible, opaque;

    GlassFrame(Activity activity) {
        super(activity);
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        appearance = Ui.appearance(activity);
        int background = appearance.palette(dark).bg();
        plainBackground = new ColorDrawable(background);
        photoBackground = appearance == Appearance.GLASS ? new ArchitectureBackdrop(activity, background, dark) : plainBackground;
        gutter = appearance == Appearance.SWISS ? 0 : Math.round(16 * getResources().getDisplayMetrics().density);
        panel = new GlassPanel(activity, false, appearance);
        panel.setMinimumHeight(Math.round(64 * getResources().getDisplayMetrics().density));
        LayoutParams dockLayout = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        dockLayout.setMargins(gutter, 0, gutter, gutter);
        addView(panel, dockLayout);
        panel.setVisibility(GONE);
        toolbar = new GlassPanel(activity, true, appearance);
        toolbar.setClickable(true);
        LayoutParams toolbarLayout = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.TOP);
        toolbarLayout.setMargins(gutter, topGap(), gutter, 0);
        addView(toolbar, toolbarLayout);
        toolbar.setVisibility(GONE);
        refreshOptions();
    }

    int topGap() { return appearance == Appearance.SWISS ? 0 : Math.round(6 * getResources().getDisplayMetrics().density); }

    void setToolbar(View view) {
        toolbar.addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        toolbar.setVisibility(VISIBLE);
    }

    void setScene(View view) {
        if (view == scene) return;
        if (scene != null) removeView(scene);
        scene = view;
        if (view != null) addView(view, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        panel.setMaterial(view != null ? backdrop : null, panel.dark(), opaque);
        toolbar.setMaterial(view != null ? backdrop : null, toolbar.dark(), opaque);
        panel.invalidate();
    }

    void setDock(View view) {
        if (panel.getChildAt(0) != view) {
            panel.removeAllViews();
            if (view != null) panel.addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        updateDockVisibility();
    }

    void setGlassEnabled(boolean enabled) {
        GlassOptions.setGlassEnabled(getContext(), enabled);
        refreshOptions();
    }

    void refreshOptions() {
        opaque = appearance == Appearance.SWISS || GlassOptions.solidSurface(getContext());
        Drawable background = opaque ? plainBackground : photoBackground;
        if (getBackground() != background) setBackground(background);
        GlassPolicy.Mode selected = GlassPolicy.choose(Build.VERSION.SDK_INT, isHardwareAccelerated(),
                GlassOptions.glassEnabled(getContext()), opaque);
        if (selected != mode) {
            releaseBackdrop();
            mode = selected;
            if (selected != GlassPolicy.Mode.SOLID && Build.VERSION.SDK_INT >= 31) {
                backdrop = new GlassBlur31(selected, getResources().getDisplayMetrics().density);
            }
            invalidate();
        }
        panel.setMaterial(scene != null ? backdrop : null, dark, opaque);
        toolbar.setMaterial(scene != null ? backdrop : null, dark, opaque);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshOptions();
        requestApplyInsets();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) refreshOptions();
    }

    @Override protected void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        refreshOptions();
    }

    @Override protected void onDetachedFromWindow() {
        releaseBackdrop();
        mode = GlassPolicy.Mode.SOLID;
        panel.setMaterial(null, panel.dark(), opaque);
        toolbar.setMaterial(null, toolbar.dark(), opaque);
        super.onDetachedFromWindow();
    }

    private void releaseBackdrop() {
        if (backdrop != null) backdrop.release();
        backdrop = null;
    }

    @Override protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child != scene || backdrop == null || !canvas.isHardwareAccelerated()
                || (panel.getVisibility() != VISIBLE && toolbar.getVisibility() != VISIBLE)
                || getWidth() == 0 || getHeight() == 0) {
            return super.drawChild(canvas, child, drawingTime);
        }
        // drawChild records the platform child's live display list. Nested View invalidations
        // still traverse the normal hierarchy. The dock is a sibling and is never recorded here.
        Canvas source = backdrop.beginScene(getWidth(), getHeight());
        boolean animate;
        try {
            getBackground().draw(source);
            animate = super.drawChild(source, child, drawingTime);
        } finally {
            backdrop.endScene();
        }
        backdrop.drawScene(canvas);
        return animate;
    }

    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            Insets30.apply(this, insets);
        } else {
            int bottom = Math.min(insets.getStableInsetBottom(), insets.getSystemWindowInsetBottom());
            boolean keyboard = insets.getSystemWindowInsetBottom() > bottom + gutter * 4;
            applyDockInsets(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetRight(), bottom, keyboard);
        }
        return super.onApplyWindowInsets(insets);
    }

    private void applyDockInsets(int left, int right, int bottom, boolean keyboard) {
        imeVisible = keyboard;
        LayoutParams layout = (LayoutParams) panel.getLayoutParams();
        if (layout.leftMargin != gutter + left || layout.rightMargin != gutter + right
                || layout.bottomMargin != gutter + bottom) {
            layout.setMargins(gutter + left, 0, gutter + right, gutter + bottom);
            panel.setLayoutParams(layout);
        }
        LayoutParams header = (LayoutParams) toolbar.getLayoutParams();
        if (header.leftMargin != gutter + left || header.rightMargin != gutter + right) {
            header.setMargins(gutter + left, topGap(), gutter + right, 0);
            toolbar.setLayoutParams(header);
        }
        updateDockVisibility();
    }

    private void updateDockVisibility() {
        int visibility = panel.getChildCount() == 0 || imeVisible ? GONE : VISIBLE;
        if (visibility != panel.getVisibility()) {
            panel.setVisibility(visibility);
            invalidate();
        }
    }

    private static final class Insets30 {
        static void apply(GlassFrame frame, WindowInsets insets) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            frame.applyDockInsets(bars.left, bars.right, bars.bottom, insets.isVisible(WindowInsets.Type.ime()));
        }
    }
}
