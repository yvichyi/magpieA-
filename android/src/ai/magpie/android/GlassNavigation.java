package ai.magpie.android;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

/** The tabs and their sliding selection share one retained navigation surface. */
final class GlassNavigation extends LinearLayout {
    private final Ui ui;
    private final View[] icons = new View[NavigationModel.ROOTS.length];
    private final TextView[] labels = new TextView[icons.length];
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Shader finish;
    private ValueAnimator move;
    private int selected = -1;
    private float position, stretch;

    GlassNavigation(Ui ui, Consumer<String> action) {
        super(ui.a);
        this.ui = ui;
        setWillNotDraw(false);
        setPadding(ui.dp(5), ui.dp(5), ui.dp(5), ui.dp(5));
        setMinimumHeight(ui.dp(64));
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(ui.dp(1));
        rim.setColor(ui.dark ? 0xb4a6c7d6 : 0xfffcfeff);
        for (int i = 0; i < icons.length; i++) {
            String id = NavigationModel.ROOTS[i];
            String label = NavigationModel.LABELS[i];
            LinearLayout cell = ui.column();
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(ui.dp(4), ui.dp(5), ui.dp(4), ui.dp(5));
            cell.setMinimumHeight(ui.dp(54));
            cell.setContentDescription(label);
            cell.setBackground(ui.ripple(Color.TRANSPARENT, 26));
            icons[i] = new LineIcon(ui.a, id.equals("connect") ? "network" : id.equals("home") ? "providers" : id, ui.ink);
            cell.addView(icons[i], new LayoutParams(ui.dp(21), ui.dp(21)));
            ui.space(cell, 3);
            labels[i] = ui.text(label, 12, ui.ink, false);
            labels[i].setGravity(Gravity.CENTER);
            cell.addView(labels[i]);
            cell.setOnClickListener(view -> action.accept(id));
            UiMotion.press(cell);
            addView(cell, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        }
    }

    void select(String root, boolean animate) {
        int next = 0;
        while (next < icons.length - 1 && !root.equals(NavigationModel.ROOTS[next])) next++;
        boolean canMove = animate && selected >= 0 && isShown() && getWidth() > 0 && UiMotion.enabled(getContext());
        if (next == selected && canMove) return;
        if (move != null) move.cancel();
        selected = next;
        for (int i = 0; i < icons.length; i++) {
            boolean active = selected == i;
            getChildAt(i).setSelected(active);
            icons[i].setAlpha(active ? 1f : .64f);
            labels[i].setTextColor(active ? ui.ink : ui.muted);
            labels[i].setTypeface(ui.font(false, active ? 600 : 400));
        }
        if (!canMove || position == selected) {
            position = selected;
            stretch = 0;
            invalidate();
            return;
        }
        float start = position;
        move = ValueAnimator.ofFloat(0f, 1f);
        move.setDuration(300);
        move.addUpdateListener(animation -> {
            float elapsed = animation.getAnimatedFraction();
            float eased = UiMotion.EASE.getInterpolation(elapsed);
            position = start + (selected - start) * eased;
            stretch = .12f * (float) Math.sin(Math.PI * elapsed);
            invalidate();
        });
        move.setInterpolator(new android.view.animation.LinearInterpolator());
        move.start();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        finish = new LinearGradient(0, 0, 0, Math.max(1, height), ui.dark
                ? new int[]{0x929cb3c3, 0x66486a81, 0x9c5b798b}
                : new int[]{0xfaffffff, 0xbbd8eaf4, 0xd4bed4e2}, null, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (selected < 0) return;
        float slot = (getWidth() - getPaddingLeft() - getPaddingRight()) / (float) icons.length;
        float visualPosition = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                ? icons.length - 1 - position : position;
        float center = getPaddingLeft() + (visualPosition + .5f) * slot;
        float half = (slot * (1f + stretch) - ui.dp(4)) / 2f;
        float top = getPaddingTop();
        float bottom = getHeight() - getPaddingBottom();
        float radius = (bottom - top) / 2f;
        boolean opaque = GlassOptions.reduceTransparency(getContext()) || !GlassOptions.glassEnabled(getContext());
        fill.setShader(opaque ? null : finish);
        fill.setColor(opaque ? ui.soft : Color.WHITE);
        canvas.drawRoundRect(center - half, top, center + half, bottom, radius, radius, fill);
        if (!opaque) canvas.drawRoundRect(center - half, top, center + half, bottom, radius, radius, rim);
    }

    @Override protected void onDetachedFromWindow() {
        if (move != null) move.cancel();
        position = selected;
        stretch = 0;
        super.onDetachedFromWindow();
    }
}
