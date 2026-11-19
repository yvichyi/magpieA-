package ai.magpie.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

final class CatalogViews {
    private CatalogViews() {}

    static ScrollView scroll(Ui ui) {
        ScrollView scroll = new InsetScrollView(ui.a);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusableInTouchMode(true);
        return scroll;
    }

    static LinearLayout body(Ui ui, ScrollView scroll, int bottom) {
        LinearLayout body = ui.column();
        body.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(bottom));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        return body;
    }

    static View row(Ui ui, String title, String description, Runnable open) {
        return row(ui, title, "", description, open);
    }

    static View row(Ui ui, String title, String reference, String description, Runnable open) {
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(56));
        row.setPadding(0, ui.dp(13), ui.dp(2), ui.dp(13));
        LinearLayout text = ui.column();
        TextView name = ui.text(title, Ui.Type.ROW);
        if (open != null) limit(name, 2);
        text.addView(name);
        if (!reference.isEmpty()) {
            ui.space(text, 3);
            TextView identifier = ui.text(reference, Ui.Type.IDENTIFIER);
            identifier.setTextColor(ui.muted);
            if (open != null) limit(identifier, 1);
            text.addView(identifier);
        }
        if (!description.isEmpty()) {
            ui.space(text, 4);
            text.addView(ui.text(description, Ui.Type.SUPPORT));
        }
        ui.grow(row, text);
        if (open != null) {
            ui.gap(row, 8);
            View chevron = new LineIcon(ui.a, "chevron", ui.muted);
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(chevron, new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
            row.setBackground(ui.ripple(Color.TRANSPARENT, 8));
            row.setFocusable(true);
            row.setOnClickListener(v -> open.run());
            row.setContentDescription(title + (reference.isEmpty() ? "" : "，" + reference)
                    + (description.isEmpty() ? "" : "，" + description));
            text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        return row;
    }

    private static void limit(TextView view, int lines) {
        view.setMaxLines(lines);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
    }

    static TextView action(Ui ui, String label, Runnable action) {
        TextView button = ui.text(label, Ui.Type.ACTION);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(ui.dp(48));
        button.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6));
        button.setBackground(ui.ripple(Color.TRANSPARENT, 8));
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    static TextView tab(Ui ui, String label, Runnable action) {
        TextView tab = action(ui, label, action);
        ui.typography(tab, Ui.Type.LABEL);
        tab.setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(6));
        return tab;
    }

    static void selectTab(Ui ui, TextView tab, boolean selected) {
        tab.setSelected(selected);
        tab.setTextColor(selected ? ui.accent : ui.muted);
        tab.setTypeface(ui.font(false, selected ? 650 : 450));
        LayerDrawable background = new LayerDrawable(new Drawable[]{
                ui.ripple(Color.TRANSPARENT, 0), new ColorDrawable(selected ? ui.accent : Color.TRANSPARENT)
        });
        background.setLayerHeight(1, ui.dp(2));
        background.setLayerGravity(1, Gravity.BOTTOM);
        tab.setBackground(background);
    }

    static void copy(Ui ui, String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) ui.a.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(ui.a, "已复制" + label, Toast.LENGTH_SHORT).show();
    }
}
