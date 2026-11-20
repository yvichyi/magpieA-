package ai.magpie.android;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.LongConsumer;

/** Request-list content backed by one current, server-paginated ledger page. */
final class LedgerScreen {
    static void render(ScreenHost host, LinearLayout target, InsightsScope scope, LedgerQuery query,
                       LedgerSnapshot page, boolean busy, LongConsumer setOffset) {
        Ui ui = host.ui();
        if (page.total == 0) {
            target.addView(InsightsViews.note(ui, "当前筛选没有匹配的请求。"));
            return;
        }
        LinearLayout summary = ui.column();
        summary.addView(ui.text(InsightsViews.count(page.total) + " 条匹配记录", Ui.Type.ROW));
        ui.space(summary, 3);
        summary.addView(InsightsViews.note(ui, InsightsViews.count(page.totals.calls) + " 次调用"));
        Button export = ui.textButton("导出 CSV", () -> LedgerExport.start(ui.a, scope, query));
        export.setContentDescription("导出筛选结果 CSV");
        export.setEnabled(scope.ready && !busy);
        target.addView(new AdaptivePair(ui, summary, export, 10));
        if (query.value("failed").equals("1")) {
            ui.space(target, 4);
            target.addView(InsightsViews.note(ui, "含最终失败和重试后成功的请求。"));
        }
        ui.space(target, 10);
        if (page.rows.isEmpty()) {
            target.addView(InsightsViews.note(ui, "这页已无记录。"));
            target.addView(ui.button("回到第一页", false, () -> setOffset.accept(0)));
            return;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.ROOT);
        ZoneId zone = ZoneId.of(scope.zone);
        ui.rule(target, 0);
        for (LedgerSnapshot.Row request : page.rows) {
            LinearLayout entry = ui.column();
            entry.setPadding(0, ui.dp(12), 0, ui.dp(12));
            entry.setMinimumHeight(ui.dp(48));
            entry.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT, 12));
            String model = request.model.isEmpty() ? "未记录模型" : request.model;
            String time = request.time.atZoneSameInstant(zone).format(formatter);
            LinearLayout title = ui.row();
            ui.grow(title, ui.text(model, Ui.Type.ROW));
            ui.gap(title, 8);
            title.addView(new LineIcon(ui.a, "chevron", ui.muted),
                    new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
            entry.addView(title);
            ui.space(entry, 4);
            String context = request.providerName.isEmpty() ? request.agentName : request.providerName;
            String meta = context.isEmpty() ? time : time + " · " + context;
            entry.addView(InsightsViews.note(ui, meta));
            String amounts = InsightsViews.count(request.tokens()) + " Token";
            if (request.priced) amounts += " · " + request.priceLabel();
            else if (request.tokens() > 0) amounts += " · 待定价";
            ui.space(entry, 6);
            entry.addView(new AdaptivePair(ui, ui.text(request.statusLabel(), Ui.Type.LABEL),
                    InsightsViews.note(ui, amounts), 8));
            entry.setOnClickListener(view -> {
                if (scope.isCurrent(ui.a)) host.openDetail("请求明细", new RequestDetailScreen(host, scope, request));
            });
            target.addView(entry);
            ui.rule(target, 0);
        }
        ui.space(target, 6);
        TextView count = InsightsViews.note(ui, InsightsViews.count(page.offset + 1) + "–"
                + InsightsViews.count(page.nextOffset()) + " / " + InsightsViews.count(page.total));
        LinearLayout actions = ui.row();
        Button previous = ui.textButton("上一页", () -> setOffset.accept(Math.max(0, page.offset - LedgerQuery.PAGE_SIZE)));
        Button next = ui.textButton("下一页", () -> setOffset.accept(page.nextOffset()));
        previous.setEnabled(!busy && page.offset > 0);
        next.setEnabled(!busy && page.hasNext());
        actions.addView(previous);
        actions.addView(next);
        target.addView(new AdaptivePair(ui, count, actions, 10));
    }
}
