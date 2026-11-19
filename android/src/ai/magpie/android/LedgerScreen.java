package ai.magpie.android;

import android.widget.Button;
import android.widget.LinearLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.LongConsumer;

/** Request-list content backed by one current, server-paginated ledger page. */
final class LedgerScreen {
    static void render(ScreenHost host, LinearLayout target, InsightsScope scope, LedgerQuery query,
                       LedgerSnapshot page, boolean busy, LongConsumer setOffset) {
        Ui ui = host.ui();
        ui.space(target, 6);
        if (page.total == 0) {
            target.addView(InsightsViews.note(ui, "当前筛选没有匹配的请求。"));
            return;
        }
        target.addView(InsightsViews.note(ui,
                InsightsViews.count(page.total) + " 条匹配记录 · " + InsightsViews.count(page.totals.calls) + " 次调用"));
        if (query.value("failed").equals("1")) {
            target.addView(InsightsViews.note(ui, "含最终失败和重试后成功的请求。"));
        }
        ui.space(target, 14);
        Button export = ui.button("导出筛选结果 CSV", false, () -> LedgerExport.start(ui.a, scope, query));
        export.setEnabled(scope.ready && !busy);
        target.addView(export);
        ui.space(target, 14);
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
            entry.addView(ui.text(model, Ui.Type.ROW));
            ui.space(entry, 4);
            String context = request.providerName.isEmpty() ? request.agentName : request.providerName;
            entry.addView(InsightsViews.note(ui, context.isEmpty() ? time : time + " · " + context));
            String amounts = InsightsViews.count(request.tokens()) + " Token";
            if (request.priced) amounts += " · " + request.priceLabel();
            else if (request.tokens() > 0) amounts += " · 待定价";
            entry.addView(InsightsViews.note(ui, request.statusLabel() + " · " + amounts));
            entry.setOnClickListener(view -> {
                if (scope.isCurrent(ui.a)) host.openDetail("请求明细", new RequestDetailScreen(host, scope, request));
            });
            target.addView(entry);
            ui.rule(target, 0);
        }
        ui.space(target, 14);
        target.addView(InsightsViews.note(ui, InsightsViews.count(page.offset + 1) + "–"
                + InsightsViews.count(page.nextOffset()) + " / " + InsightsViews.count(page.total)));
        ui.space(target, 6);
        Button previous = ui.button("上一页", false, () -> setOffset.accept(Math.max(0, page.offset - LedgerQuery.PAGE_SIZE)));
        Button next = ui.button("下一页", false, () -> setOffset.accept(page.nextOffset()));
        previous.setEnabled(!busy && page.offset > 0);
        next.setEnabled(!busy && page.hasNext());
        InsightsViews.pair(ui, target, previous, next);
    }
}
