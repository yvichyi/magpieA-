package ai.magpie.android;

import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** A Monday-first month, with ordinary focusable controls for the days. */
final class MonthHeatmap extends LinearLayout {
    MonthHeatmap(Ui ui, UsageHeatmap map, YearMonth month, LocalDate selected, int metric,
                 Consumer<LocalDate> select) {
        super(ui.a);
        setOrientation(VERTICAL);
        setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        setBackground(ui.shape(ui.card, 14, true));
        LinearLayout weekday = ui.row();
        for (String name : new String[]{"一", "二", "三", "四", "五", "六", "日"}) {
            TextView heading = ui.text(name, 14, ui.muted, false);
            heading.setGravity(Gravity.CENTER);
            heading.setMinHeight(ui.dp(36));
            weekday.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        }
        addView(weekday);

        Map<LocalDate, UsageHeatmap.Day> days = new HashMap<>();
        double largest = 0;
        List<UsageHeatmap.Day> available = map.recent(53);
        for (UsageHeatmap.Day day : available) {
            if (!YearMonth.from(day.date).equals(month)) continue;
            days.put(day.date, day);
            largest = Math.max(largest, day.value(metric));
        }

        LocalDate first = month.atDay(1);
        LocalDate monday = first.minusDays(first.getDayOfWeek().getValue() - 1L);
        LocalDate last = month.atEndOfMonth();
        for (LocalDate start = monday; !start.isAfter(last); start = start.plusWeeks(1)) {
            LinearLayout week = ui.row();
            for (int i = 0; i < 7; i++) {
                LocalDate date = start.plusDays(i);
                UsageHeatmap.Day day = days.get(date);
                boolean inMonth = !date.isBefore(first) && !date.isAfter(last);
                TextView cell = ui.text(inMonth ? Integer.toString(date.getDayOfMonth()) : "",
                        15, day == null ? ui.muted : ui.ink, date.equals(selected));
                cell.setGravity(Gravity.CENTER);
                cell.setMinimumHeight(ui.dp(48));
                if (day != null) {
                    boolean selectedDay = date.equals(selected);
                    double ratio = largest == 0 ? 0 : Math.sqrt(day.value(metric) / largest);
                    int fill = selectedDay ? ui.accent : shade(ui.soft, ui.ink, ratio * .23);
                    cell.setTextColor(selectedDay ? (ui.dark ? ui.bg : Color.WHITE) : ui.ink);
                    cell.setBackground(ui.ripple(fill, 10));
                    cell.setSelected(selectedDay);
                    cell.setContentDescription(date + "，" + stats(day) + (selectedDay ? "，已选择" : ""));
                    cell.setOnClickListener(view -> select.accept(date));
                } else if (inMonth) {
                    cell.setContentDescription(date + "，不在统计范围");
                } else {
                    cell.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                }
                LinearLayout.LayoutParams cellSize = new LinearLayout.LayoutParams(0, -2, 1);
                cellSize.setMargins(ui.dp(1), ui.dp(1), ui.dp(1), ui.dp(1));
                week.addView(cell, cellSize);
            }
            addView(week);
        }
    }

    static String stats(UsageHeatmap.Day day) {
        return InsightsViews.count(day.calls) + " 次 · " + InsightsViews.count(day.tokens) + " Token"
                + " · " + InsightsViews.price(day.cost, day.unpriced);
    }

    private static int shade(int base, int ink, double amount) {
        return Color.rgb(
                blend(Color.red(base), Color.red(ink), amount),
                blend(Color.green(base), Color.green(ink), amount),
                blend(Color.blue(base), Color.blue(ink), amount));
    }

    private static int blend(int base, int ink, double amount) {
        return (int) Math.round(base + (ink - base) * amount);
    }
}
