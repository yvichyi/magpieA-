package ai.magpie.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
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
        LinearLayout row = ui.row();
        row.setMinimumHeight(ui.dp(56));
        row.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12));
        LinearLayout text = ui.column();
        text.addView(ui.text(title, 16, ui.ink, true));
        if (!description.isEmpty()) {
            ui.space(text, 3);
            text.addView(ui.text(description, 14, ui.muted, false));
        }
        ui.grow(row, text);
        if (open != null) {
            ui.gap(row, 12);
            View chevron = new LineIcon(ui.a, "chevron", ui.muted);
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(chevron, new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
            row.setBackground(ui.ripple(Color.TRANSPARENT, 8));
            row.setFocusable(true);
            row.setOnClickListener(v -> open.run());
            row.setContentDescription(description.isEmpty() ? title : title + "，" + description);
            text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        return row;
    }

    static TextView action(Ui ui, String label, Runnable action) {
        TextView button = ui.text(label, 15, ui.ink, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(ui.dp(48));
        button.setPadding(ui.dp(8), ui.dp(6), ui.dp(8), ui.dp(6));
        button.setBackground(ui.ripple(Color.TRANSPARENT, 8));
        button.setFocusable(true);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    static void copy(Ui ui, String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) ui.a.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(ui.a, "已复制" + label, Toast.LENGTH_SHORT).show();
    }
}
