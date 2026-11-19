package ai.magpie.android;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

final class InsightsViews {
    private InsightsViews() {}

    static LinearLayout content(Ui ui, ScrollView scroll) {
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(96));
        content.setFocusableInTouchMode(true);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        return content;
    }

    static TextView title(Ui ui, String title, int size) {
        TextView text = ui.text(title, size >= 24 ? Ui.Type.PAGE : size >= 18 ? Ui.Type.SECTION : Ui.Type.ROW);
        text.setAccessibilityHeading(true);
        return text;
    }

    static TextView note(Ui ui, String text) {
        return ui.text(text, Ui.Type.SUPPORT);
    }

    static TextView action(Ui ui, String label, Runnable click) {
        TextView text = ui.text(label, Ui.Type.ACTION);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setMinimumHeight(ui.dp(48));
        text.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT, 12));
        text.setOnClickListener(view -> click.run());
        return text;
    }

    static void pair(Ui ui, LinearLayout root, View first, View second) {
        root.addView(new AdaptivePair(ui, first, second, 14, true));
    }

    static void field(Ui ui, LinearLayout layout, String label, String value) {
        if (value.isEmpty()) return;
        LinearLayout field = ui.column();
        field.setPadding(0, ui.dp(9), 0, ui.dp(9));
        field.addView(ui.text(label, Ui.Type.LABEL));
        ui.space(field, 4);
        TextView text = ui.text(value, Ui.Type.BODY);
        text.setTextIsSelectable(true);
        field.addView(text);
        layout.addView(field);
    }

    static String count(long count) {
        return String.format(Locale.ROOT, "%,d", count);
    }

    static String price(double knownUsd, long unpriced) {
        if (unpriced == 0) return UsageSnapshot.money(knownUsd) + " USD";
        if (knownUsd == 0) return "待定价 · " + count(unpriced) + " 次未定价";
        return UsageSnapshot.money(knownUsd) + " USD · " + count(unpriced) + " 次未定价";
    }

    static String time(Number duration) {
        return duration == null ? "无样本" : String.format(Locale.ROOT, "%,.0f ms", duration.doubleValue());
    }

    static String speed(Double rate) {
        return rate == null ? "无样本" : String.format(Locale.ROOT, "%,.1f Token/s", rate);
    }
}
