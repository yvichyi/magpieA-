package ai.magpie.android;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;

/** Native usage views backed by the authenticated, workspace-scoped ledger. */
final class InsightsScreen implements AppScreen {
    private static final int OVERVIEW = 0, ANALYSIS = 1, CALENDAR = 2, REQUESTS = 3;
    private static final String[] TABS = {"概览", "分析", "日历", "请求"};
    private static final String[] PERIODS = {"today", "7d", "30d", "all"};
    private static final String[] PERIOD_LABELS = {"今天", "最近 7 天", "最近 30 天", "全部时间"};
    private static final String[] METRICS = {"调用", "Token", "费用"};
    private static final String[] FACETS = {"provider", "model", "agent", "purpose", "account", "callerKey", "computer"};
    private static final String[] FACET_LABELS = {"服务商", "模型", "客户端", "用途", "账号", "网关密钥", "设备"};
    private static final String[] SOURCES = {"", "through", "direct", "reported"};
    private static final String[] SOURCE_LABELS = {"全部来源", "通过网关", "会话日志", "客户端上报"};
    private static final int BREAKDOWN_PAGE = 8;
    private static final int CACHE_LIMIT = 6;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy 年 M 月", Locale.ROOT);

    private final ScreenHost host;
    private final Ui ui;
    private final ScrollView scroll;
    private final LinearLayout body;
    private final TextView period, filters, status;
    private final View refresh;
    private final TextView[] tabs = new TextView[TABS.length];
    private final int[] positions = new int[TABS.length];
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor reads = new ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), task -> new Thread(task, "magpie-insights"));
    private final Map<String, LedgerSnapshot> ledgerCache = new LinkedHashMap<>(8, .75f, true);
    private final Map<String, UsageHeatmap> heatCache = new LinkedHashMap<>(8, .75f, true);

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!active) return;
            if (updateScope()) read();
            main.postDelayed(this, 1000);
        }
    };

    private InsightsScope scope;
    private LedgerQuery query = new LedgerQuery();
    private long offset;
    private int tab = OVERVIEW, trendMetric, calendarMetric, groupBy, groupLimit = BREAKDOWN_PAGE;
    private YearMonth month;
    private LocalDate calendarDay;
    private Instant trendTime;
    private AlertDialog dialog;
    private Future<?> reading;
    private int generation, renderGeneration;
    private boolean active, disposed, loading, failed, showingData;
    private ReturnTo returnTo;

    private InsightChart chart;
    private LedgerSnapshot trend;
    private TextView pointDetails;
    private Button earlierPoint, laterPoint;
    private int pointIndex;

    private static final class ReturnTo {
        final int tab;
        final LedgerQuery query;
        final long offset;

        ReturnTo(int tab, LedgerQuery query, long offset) {
            this.tab = tab;
            this.query = query;
            this.offset = offset;
        }
    }

    InsightsScreen(ScreenHost host) {
        this.host = host;
        ui = host.ui();
        reads.allowCoreThreadTimeOut(true);
        scroll = new ScrollView(ui.a);
        scroll.setSaveEnabled(false);
        LinearLayout content = InsightsViews.content(ui, scroll);
        LinearLayout sections = ui.row();
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            tabs[i] = segment(TABS[i], () -> selectTab(index, false));
            ui.grow(sections, tabs[i]);
        }
        content.addView(sections);
        ui.space(content, 12);

        LinearLayout tools = ui.row();
        period = segment("", this::choosePeriod);
        period.setGravity(Gravity.CENTER_VERTICAL);
        ui.grow(tools, period);
        filters = segment("", this::chooseFilter);
        ui.grow(tools, filters);
        refresh = ui.iconButton("refresh", "刷新用量", this::refresh);
        tools.addView(refresh);
        content.addView(tools);
        status = InsightsViews.note(ui, "");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(status);
        ui.space(content, 12);
        body = ui.column();
        content.addView(body);
        updateScope();
        render(0);
    }

    @Override public View view() { return scroll; }

    @Override public void onResume() {
        if (active || disposed) return;
        active = true;
        updateScope();
        read();
        main.postDelayed(poll, 1000);
    }

    @Override public void onPause() {
        if (!active) return;
        rememberPosition();
        active = false;
        stopRead();
        main.removeCallbacks(poll);
        closeDialog();
    }

    @Override public void dispose() {
        onPause();
        disposed = true;
        reads.shutdownNow();
        ledgerCache.clear();
        heatCache.clear();
        body.removeAllViews();
    }

    @Override public boolean onBack() {
        if (tab == REQUESTS && returnTo != null) {
            ReturnTo destination = returnTo;
            query = destination.query;
            offset = destination.offset;
            selectTab(destination.tab, false);
            return true;
        }
        if (tab != OVERVIEW) {
            selectTab(OVERVIEW, false);
            return true;
        }
        return false;
    }

    private boolean updateScope() {
        InsightsScope now = InsightsScope.current(ui.a);
        boolean changed = !now.equals(scope);
        boolean readinessChanged = scope == null || scope.ready != now.ready;
        if (changed || readinessChanged) {
            stopRead();
            closeDialog();
            if (changed) {
                ledgerCache.clear();
                heatCache.clear();
                if (scope != null && !now.sameWorkspace(scope)) {
                    query = new LedgerQuery();
                    offset = 0;
                    returnTo = null;
                    java.util.Arrays.fill(positions, 0);
                }
                month = null;
                calendarDay = null;
                trendTime = null;
            }
            scope = now;
        }
        return changed || readinessChanged;
    }

    private void refresh() {
        if (!active) return;
        rememberPosition();
        updateScope();
        read();
    }

    private void rememberPosition() {
        if (showingData) positions[tab] = scroll.getScrollY();
    }

    private void selectTab(int index, boolean reset) {
        if (!active || (tab == index && !reset)) return;
        rememberPosition();
        updateScope();
        if (index != REQUESTS) returnTo = null;
        tab = index;
        if (reset) positions[index] = 0;
        read();
    }

    private String path() {
        if (tab == CALENDAR) return query.heatmap(scope.zone);
        if (tab == REQUESTS) return query.requests(scope.zone, offset);
        return summaryPath();
    }

    private String summaryPath() { return query.day(null).requests(scope.zone, 0); }

    private void stopRead() {
        generation++;
        if (reading != null) reading.cancel(true);
        reads.getQueue().clear();
        reading = null;
        loading = false;
        failed = false;
    }

    private void read() {
        stopRead();
        final String path = path();
        final InsightsScope wanted = scope;
        final int stamp = generation;
        final boolean heat = tab == CALENDAR;
        loading = wanted.ready;
        render(positions[tab]);
        if (!wanted.ready) return;

        reading = reads.submit(() -> {
            try {
                String json = new CoreApi(wanted.origin, wanted.key).get(path);
                if (heat) {
                    UsageHeatmap result = UsageHeatmap.parse(json);
                    main.post(() -> accept(stamp, wanted, () -> cache(heatCache, path, result)));
                } else {
                    LedgerSnapshot result = LedgerSnapshot.parse(json);
                    main.post(() -> accept(stamp, wanted, () -> cache(ledgerCache, path, result)));
                }
            } catch (IOException | JSONException | IllegalArgumentException error) {
                main.post(() -> accept(stamp, wanted, null));
            }
        });
    }

    private void accept(int stamp, InsightsScope wanted, Runnable store) {
        if (!active || stamp != generation || ui.a.isFinishing() || ui.a.isDestroyed()) return;
        if (!wanted.isCurrent(ui.a)) {
            if (updateScope()) read();
            return;
        }
        loading = false;
        failed = store == null;
        if (store != null) store.run();
        rememberPosition();
        render(positions[tab]);
    }

    private static <T> void cache(Map<String, T> cache, String path, T result) {
        cache.put(path, result);
        if (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
    }

    private void render(int position) {
        int stamp = ++renderGeneration;
        for (int i = 0; i < tabs.length; i++) selected(tabs[i], tab == i);
        boolean calendar = tab == CALENDAR;
        period.setVisibility(calendar ? View.GONE : View.VISIBLE);
        period.setText(tab == REQUESTS && query.day != null ? query.day.toString()
                : PERIOD_LABELS[indexOf(PERIODS, query.period)] + "  ⌄");
        period.setEnabled(active);
        int filterCount = (tab == REQUESTS ? query : query.day(null)).filterCount();
        filters.setText(filterCount == 0 ? "筛选  ⌄" : "筛选（" + filterCount + "）  ⌄");
        filters.setEnabled(active && scope.ready);
        refresh.setEnabled(active && scope.ready && !loading);
        refresh.setAlpha(refresh.isEnabled() ? 1f : .45f);
        refresh.setContentDescription(failed ? "重试读取用量" : "刷新用量");

        body.removeAllViews();
        chart = null;
        trend = null;
        LedgerSnapshot ledger = calendar ? null : ledgerCache.get(path());
        UsageHeatmap heat = calendar ? heatCache.get(path()) : null;
        boolean shown = ledger != null || heat != null;
        showingData = shown;
        String state = !scope.ready ? (shown ? "网关未就绪 · 显示上次读取" : "工作空间未就绪")
                : failed ? (shown ? "刷新失败 · 显示上次读取" : "读取用量失败")
                : loading ? (shown ? "正在刷新 · 显示上次读取" : "正在读取用量…") : "";
        status.setText(state);
        status.setVisibility(state.isEmpty() ? View.GONE : View.VISIBLE);

        if (!shown && !loading) {
            if (failed) {
                body.addView(InsightsViews.note(ui, "请检查当前工作空间的连接后重试。"));
                body.addView(ui.button("重试", false, this::refresh));
            } else if (!scope.ready) {
                body.addView(InsightsViews.note(ui, "启动本机网关或连接远程工作空间后，即可查看用量。"));
                body.addView(InsightsViews.action(ui, "前往接入", () -> host.openRoot("connect")));
            }
        } else if (heat != null) {
            renderCalendar(heat);
        } else if (ledger != null) {
            if (tab == OVERVIEW) renderOverview(ledger);
            else if (tab == ANALYSIS) renderAnalysis(ledger);
            else LedgerScreen.render(host, body, scope, query, ledger, loading || !scope.ready, this::page);
        }
        scroll.post(() -> {
            if (stamp == renderGeneration) scroll.scrollTo(0, position);
        });
    }

    private void renderOverview(LedgerSnapshot data) {
        LedgerSnapshot.Totals totals = data.totals;
        body.addView(InsightsViews.title(ui, "用量概览", 26));
        ui.space(body, 14);
        InsightsViews.pair(ui, body, measure("Token 总量", InsightsViews.count(totals.tokens())),
                measure("调用", InsightsViews.count(totals.calls)));
        ui.space(body, 10);
        body.addView(InsightsViews.note(ui, "费用 · " + InsightsViews.price(totals.cost, totals.unpriced)));
        body.addView(InsightsViews.note(ui, InsightsViews.count(totals.errors) + " 次最终失败 · "
                + InsightsViews.count(totals.canceled) + " 次取消"));
        renderTrend(data);
        section("调用健康");
        InsightsViews.pair(ui, body,
                measure("完成率", totals.calls == 0 ? "—" : UsageSnapshot.percent(totals.completed(), totals.calls)),
                measure("首 Token 平均耗时", InsightsViews.time(totals.meanFirstTokenMs())));
        ui.space(body, 8);
        body.addView(InsightsViews.note(ui, InsightsViews.count(totals.completed()) + " 次完成 · "
                + InsightsViews.count(totals.errors) + " 次最终失败 · " + InsightsViews.count(totals.canceled) + " 次取消"));
        body.addView(InsightsViews.note(ui, InsightsViews.count(totals.retried) + " 次在重试后成功"));
        if (totals.errors > 0 || totals.retried > 0) {
            body.addView(InsightsViews.action(ui, "查看有失败尝试的请求", () ->
                    drilldown(query.day(null).with("failed", "1").with("retried", ""))));
        }
    }

    private LinearLayout measure(String name, String value) {
        LinearLayout group = ui.column();
        group.addView(InsightsViews.note(ui, name));
        ui.space(group, 4);
        TextView amount = ui.text(value, 20, ui.ink, true);
        amount.setTypeface(ui.font(true, 500));
        amount.setFontFeatureSettings("tnum");
        group.addView(amount);
        return group;
    }

    private void section(String title) {
        ui.space(body, 18);
        ui.rule(body, 0);
        ui.space(body, 14);
        body.addView(InsightsViews.title(ui, title, 20));
        ui.space(body, 8);
    }

    private void renderTrend(LedgerSnapshot data) {
        section("用量趋势");
        if (data.series.isEmpty() || data.totals.calls == 0 && data.totals.tokens() == 0 && data.totals.cost == 0) {
            body.addView(InsightsViews.note(ui, "当前范围暂无趋势数据。"));
            return;
        }
        body.addView(segments(METRICS, trendMetric, value -> {
            trendMetric = value;
            render(scroll.getScrollY());
        }));
        ui.space(body, 6);
        trend = data;
        int selectedPoint = data.latestActivePoint();
        for (int i = 0; i < data.series.size(); i++) {
            if (data.series.get(i).time.equals(trendTime)) {
                selectedPoint = i;
                break;
            }
        }
        chart = new InsightChart(ui, data.series, trendMetric, selectedPoint, this::movePoint);
        body.addView(chart, new LinearLayout.LayoutParams(-1, ui.dp(136)));
        ui.space(body, 8);
        pointDetails = InsightsViews.note(ui, "");
        pointDetails.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(pointDetails);
        ui.space(body, 8);
        earlierPoint = ui.button("上一个点", false, () -> movePoint(pointIndex - 1));
        laterPoint = ui.button("下一个点", false, () -> movePoint(pointIndex + 1));
        InsightsViews.pair(ui, body, earlierPoint, laterPoint);
        if (!data.bucket.equals("week")) {
            body.addView(InsightsViews.action(ui, "查看当天请求", () -> {
                if (trend == null) return;
                LocalDate date = trend.series.get(pointIndex).time.atZone(ZoneId.of(scope.zone)).toLocalDate();
                drilldown(query.day(date));
            }));
        }
        movePoint(selectedPoint);
    }

    private void movePoint(int index) {
        if (trend == null || index < 0 || index >= trend.series.size()) return;
        pointIndex = index;
        LedgerSnapshot.Point point = trend.series.get(index);
        trendTime = point.time;
        chart.select(index);
        earlierPoint.setEnabled(index > 0);
        laterPoint.setEnabled(index + 1 < trend.series.size());
        earlierPoint.setAlpha(earlierPoint.isEnabled() ? 1f : .45f);
        laterPoint.setAlpha(laterPoint.isEnabled() ? 1f : .45f);
        LocalDate date = point.time.atZone(ZoneId.of(scope.zone)).toLocalDate();
        String time = trend.bucket.equals("hour") ? date + " · " + point.label
                : trend.bucket.equals("week") ? date + " 起的一周" : date.toString();
        LedgerSnapshot.Totals totals = point.totals;
        pointDetails.setText(time + " · " + (index + 1) + " / " + trend.series.size() + "\n"
                + InsightsViews.count(totals.calls) + " 次 · " + InsightsViews.count(totals.tokens())
                + " Token\n" + InsightsViews.price(totals.cost, totals.unpriced));
    }

    private void renderAnalysis(LedgerSnapshot data) {
        LedgerSnapshot.Totals totals = data.totals;
        body.addView(InsightsViews.title(ui, "用量分析", 26));
        section("Token 构成");
        String[] names = {"输入", "输出", "缓存读取", "缓存写入"};
        long[] values = {totals.input, totals.output, totals.cacheRead, totals.cacheWrite};
        int[] colors = ui.dark ? new int[]{0xffedf2f7, 0xffb5c0cc, 0xff8998a7, 0xff65717c}
                : new int[]{0xff202830, 0xff62717f, 0xff97a7b5, 0xffc5d0da};
        if (totals.tokens() > 0) {
            double[] portions = new double[values.length];
            for (int i = 0; i < values.length; i++) portions[i] = (double) values[i] / totals.tokens();
            body.addView(new DistributionBar(ui, portions, colors), new LinearLayout.LayoutParams(-1, ui.dp(12)));
            ui.space(body, 8);
        }
        for (int i = 0; i < names.length; i++) statLine(names[i], InsightsViews.count(values[i]), colors[i]);
        Double cacheRate = totals.cacheRate();
        statLine("已知缓存命中率", cacheRate == null ? "无样本" : String.format(Locale.ROOT, "%.1f%%", 100 * cacheRate));
        if (totals.cacheUnknownCalls > 0) {
            body.addView(InsightsViews.note(ui, InsightsViews.count(totals.cacheUnknownCalls) + " 次计量未知，未计入命中率。"));
        }

        section("调用分布");
        body.addView(segments(new String[]{"按模型", "按服务商"}, groupBy, value -> {
            groupBy = value;
            groupLimit = BREAKDOWN_PAGE;
            render(scroll.getScrollY());
        }));
        breakdown(data, groupBy == 0 ? "model" : "provider");

        section("响应速度");
        statLine("平均首 Token 耗时", InsightsViews.time(totals.meanFirstTokenMs()));
        if (totals.timed > 0) body.addView(InsightsViews.note(ui, InsightsViews.count(totals.timed) + " 条首 Token 计时样本"));
        statLine("平均生成速度", InsightsViews.speed(totals.decodeRate()));
        if (totals.decodeN > 0) body.addView(InsightsViews.note(ui, InsightsViews.count(totals.decodeN) + " 条生成速度样本"));
        if (data.speeds.isEmpty()) return;
        ui.space(body, 12);
        body.addView(InsightsViews.title(ui, "模型速度中位数", 16));
        if (data.speedMin > 0) body.addView(InsightsViews.note(ui, "含输出至少 " + data.speedMin + " Token 且记录首 Token 的成功请求。"));
        for (LedgerSnapshot.Speed speed : data.speeds) {
            ui.space(body, 12);
            body.addView(ui.text(speed.name, 16, ui.ink, true));
            body.addView(InsightsViews.note(ui, "首 Token " + InsightsViews.time(speed.firstTokenMs)
                    + " · " + InsightsViews.count(speed.requests) + " 条样本"));
            body.addView(InsightsViews.note(ui, "生成速度 " + InsightsViews.speed(speed.tokensPerSecond)
                    + (speed.speedSamples == 0 ? "" : " · " + InsightsViews.count(speed.speedSamples) + " 条样本")));
        }
    }

    private void statLine(String label, String value) {
        statLine(label, value, Color.TRANSPARENT);
    }

    private void statLine(String label, String value, int marker) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(6), 0, ui.dp(6));
        if (marker != Color.TRANSPARENT) {
            View dot = new View(ui.a);
            dot.setBackground(ui.shape(marker, 3, false));
            row.addView(dot, new LinearLayout.LayoutParams(ui.dp(9), ui.dp(9)));
            ui.gap(row, 9);
        }
        ui.grow(row, InsightsViews.note(ui, label));
        TextView amount = ui.text(value, 16, ui.ink, true);
        amount.setGravity(Gravity.END);
        ui.grow(row, amount);
        body.addView(row);
    }

    private void breakdown(LedgerSnapshot data, String dimension) {
        try {
            List<LedgerSnapshot.Share> groups = data.by(dimension);
            if (groups.isEmpty()) {
                body.addView(InsightsViews.note(ui, "当前范围暂无匹配的用量。"));
                return;
            }
            if (!query.value(dimension).isEmpty()) {
                body.addView(InsightsViews.note(ui, "可点按其他分组切换筛选。"));
            }
            long maximum = 0;
            for (LedgerSnapshot.Share group : groups) maximum = Math.max(maximum, group.totals.tokens());
            if (maximum > 0) body.addView(InsightsViews.note(ui, "条长表示与本组最高 Token 用量的比值。"));
            for (int i = 0; i < Math.min(groupLimit, groups.size()); i++) {
                LedgerSnapshot.Share group = groups.get(i);
                LedgerSnapshot.Totals totals = group.totals;
                LinearLayout entry = ui.column();
                entry.setPadding(0, ui.dp(10), 0, ui.dp(10));
                entry.setMinimumHeight(ui.dp(48));
                entry.setBackground(ui.ripple(Color.TRANSPARENT, 12));
                entry.addView(ui.text(group.name.isEmpty() ? "未记录" : group.name, 16, ui.ink, true));
                entry.addView(InsightsViews.note(ui, InsightsViews.count(totals.tokens()) + " Token · "
                        + InsightsViews.count(totals.calls) + " 次 · " + InsightsViews.price(totals.cost, totals.unpriced)));
                if (maximum > 0) {
                    ui.space(entry, 7);
                    entry.addView(new DistributionBar(ui, new double[]{(double) totals.tokens() / maximum},
                            new int[]{ui.muted}), new LinearLayout.LayoutParams(-1, ui.dp(5)));
                }
                if (!group.id.isEmpty()) {
                    entry.setOnClickListener(view -> drilldown(query.day(null).with(dimension, group.id)));
                }
                body.addView(entry);
                ui.rule(body, 0);
            }
            if (groups.size() > groupLimit) {
                body.addView(InsightsViews.action(ui, "显示更多分组", () -> {
                    groupLimit += BREAKDOWN_PAGE;
                    render(scroll.getScrollY());
                }));
            }
        } catch (JSONException malformed) {
            body.addView(InsightsViews.note(ui, "当前服务端未返回可用的分组数据。"));
        }
    }

    private void renderCalendar(UsageHeatmap heat) {
        body.addView(InsightsViews.title(ui, "每日用量", 26));
        ui.space(body, 6);
        body.addView(InsightsViews.note(ui, heat.from + " 至 " + heat.to + " · " + scope.zone));
        ui.space(body, 8);
        if (month == null || month.isBefore(YearMonth.from(heat.from)) || month.isAfter(YearMonth.from(heat.to))) {
            month = YearMonth.from(heat.to);
            calendarDay = heat.to;
        }
        TextView monthTitle = InsightsViews.title(ui, month.format(MONTH_FORMAT), 18);
        body.addView(monthTitle);
        ui.space(body, 6);
        Button previous = ui.button("上个月", false, () -> moveMonth(-1));
        Button next = ui.button("下个月", false, () -> moveMonth(1));
        previous.setEnabled(month.isAfter(YearMonth.from(heat.from)));
        next.setEnabled(month.isBefore(YearMonth.from(heat.to)));
        previous.setAlpha(previous.isEnabled() ? 1f : .45f);
        next.setAlpha(next.isEnabled() ? 1f : .45f);
        InsightsViews.pair(ui, body, previous, next);
        body.addView(segments(METRICS, calendarMetric, value -> {
            calendarMetric = value;
            render(scroll.getScrollY());
        }));
        body.addView(new MonthHeatmap(ui, heat, month, calendarDay, calendarMetric, day -> {
            calendarDay = day;
            render(scroll.getScrollY());
        }));
        if (calendarDay == null) return;
        for (UsageHeatmap.Day day : heat.recent(53)) {
            if (!day.date.equals(calendarDay)) continue;
            ui.space(body, 12);
            body.addView(InsightsViews.title(ui, calendarDay.toString(), 18));
            body.addView(InsightsViews.note(ui, MonthHeatmap.stats(day)));
            body.addView(InsightsViews.action(ui, "查看当天请求", () -> drilldown(query.day(day.date))));
            break;
        }
    }

    private void moveMonth(int step) {
        month = month.plusMonths(step);
        calendarDay = null;
        render(scroll.getScrollY());
    }

    private void page(long pageOffset) {
        if (!scope.isCurrent(ui.a)) return;
        offset = pageOffset;
        positions[REQUESTS] = 0;
        read();
    }

    private void drilldown(LedgerQuery selected) {
        if (!scope.isCurrent(ui.a)) return;
        if (tab != REQUESTS) returnTo = new ReturnTo(tab, query, offset);
        query = selected;
        offset = 0;
        selectTab(REQUESTS, true);
    }

    private LinearLayout segments(String[] choices, int current, java.util.function.IntConsumer change) {
        LinearLayout row = ui.row();
        for (int i = 0; i < choices.length; i++) {
            int index = i;
            TextView item = segment(choices[i], () -> change.accept(index));
            selected(item, i == current);
            ui.grow(row, item);
        }
        return row;
    }

    private TextView segment(String label, Runnable change) {
        TextView button = InsightsViews.action(ui, label, change);
        button.setGravity(Gravity.CENTER);
        button.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        return button;
    }

    private void selected(TextView item, boolean selected) {
        item.setSelected(selected);
        item.setTextColor(selected ? ui.ink : ui.muted);
        item.setBackground(ui.ripple(selected ? ui.soft : Color.TRANSPARENT, 12));
    }

    private void choosePeriod() {
        if (!active) return;
        int current = query.day == null ? indexOf(PERIODS, query.period) : -1;
        show(new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle("统计范围")
                .setSingleChoiceItems(PERIOD_LABELS, current, (choice, index) -> {
                    choice.dismiss();
                    if (active) apply(query.period(PERIODS[index]));
                }).setNegativeButton("取消", null));
    }

    private void chooseFilter() {
        if (!scope.isCurrent(ui.a)) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> choices = new ArrayList<>();
        filterChoice(labels, choices, "搜索", query.value("q"), () -> chooseText("搜索请求", "q"));
        filterChoice(labels, choices, "调用状态", statusLabel(), this::chooseStatus);
        filterChoice(labels, choices, "来源", query.value("via").isEmpty() ? ""
                : SOURCE_LABELS[indexOf(SOURCES, query.value("via"))], this::chooseSource);
        LedgerSnapshot data = ledgerCache.get(tab == CALENDAR ? summaryPath() : path());
        for (int i = 0; i < FACETS.length; i++) {
            String field = FACETS[i], label = FACET_LABELS[i];
            List<LedgerSnapshot.Choice> values = facet(data, field);
            String value = query.value(field);
            for (LedgerSnapshot.Choice option : values) if (option.id.equals(value)) value = option.name;
            filterChoice(labels, choices, label, value, () -> chooseFacet(label, field, values));
        }
        if (tab == REQUESTS && query.day != null) {
            filterChoice(labels, choices, "清除日期", query.day.toString(), () -> apply(query.day(null)));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle("筛选用量")
                .setItems(labels.toArray(new String[0]), (picker, index) -> choices.get(index).run())
                .setNegativeButton("关闭", null);
        if ((tab == REQUESTS ? query : query.day(null)).filterCount() > 0) {
            builder.setNeutralButton("清除筛选", (picker, which) -> {
                LedgerQuery cleared = query.clear();
                apply(tab == REQUESTS ? cleared : cleared.day(query.day));
            });
        }
        show(builder);
    }

    private void filterChoice(List<String> labels, List<Runnable> choices, String label, String value, Runnable select) {
        labels.add(value.isEmpty() ? label : label + " · " + value);
        choices.add(select);
    }

    private static List<LedgerSnapshot.Choice> facet(LedgerSnapshot data, String field) {
        if (data != null) {
            try {
                return data.choices(field);
            } catch (JSONException malformed) {
                // The caller can enter an identifier directly when the server does not provide choices.
            }
        }
        return java.util.Collections.emptyList();
    }

    private void chooseFacet(String label, String field, List<LedgerSnapshot.Choice> choices) {
        if (!scope.isCurrent(ui.a)) return;
        if (choices.isEmpty()) {
            chooseText(label, field);
            return;
        }
        String[] labels = new String[choices.size() + 1];
        labels[0] = "全部";
        int selected = query.value(field).isEmpty() ? 0 : -1;
        for (int i = 0; i < choices.size(); i++) {
            LedgerSnapshot.Choice choice = choices.get(i);
            labels[i + 1] = choice.name;
            if (choice.id.equals(query.value(field))) selected = i + 1;
        }
        show(new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle(label)
                .setSingleChoiceItems(labels, selected, (picker, index) -> {
                    picker.dismiss();
                    apply(query.with(field, index == 0 ? "" : choices.get(index - 1).id));
                }).setNeutralButton("输入标识", (picker, which) -> chooseText(label, field))
                .setNegativeButton("取消", null));
    }

    private String statusLabel() {
        if (query.value("retried").equals("1")) return "重试后成功";
        if (query.value("failed").equals("1")) return "有失败尝试";
        return "";
    }

    private void chooseStatus() {
        String[] labels = {"全部状态", "有失败尝试（含重试后成功）", "重试后成功"};
        int current = query.value("retried").equals("1") ? 2 : query.value("failed").equals("1") ? 1 : 0;
        show(new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle("调用状态")
                .setSingleChoiceItems(labels, current, (picker, index) -> {
                    picker.dismiss();
                    apply(query.with("failed", index == 1 ? "1" : "").with("retried", index == 2 ? "1" : ""));
                }).setNegativeButton("取消", null));
    }

    private void chooseSource() {
        show(new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle("记录来源")
                .setSingleChoiceItems(SOURCE_LABELS, indexOf(SOURCES, query.value("via")), (picker, index) -> {
                    picker.dismiss();
                    apply(query.with("via", SOURCES[index]));
                }).setNegativeButton("取消", null));
    }

    private void chooseText(String label, String field) {
        if (!scope.isCurrent(ui.a)) return;
        EditText entry = new EditText(ui.a);
        entry.setSingleLine(true);
        entry.setInputType(InputType.TYPE_CLASS_TEXT);
        entry.setText(query.value(field));
        entry.setHint(label);
        entry.setSelection(entry.length());
        LinearLayout container = ui.column();
        container.setPadding(ui.dp(20), 0, ui.dp(20), 0);
        container.addView(entry);
        show(new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle(label).setView(container)
                .setPositiveButton("应用", (picker, which) -> apply(query.with(field, entry.getText().toString().trim())))
                .setNegativeButton("取消", null));
        entry.requestFocus();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void apply(LedgerQuery selected) {
        if (!active) return;
        if (updateScope()) {
            read();
            return;
        }
        if (query.equals(selected)) return;
        query = selected;
        offset = 0;
        positions[tab] = 0;
        positions[REQUESTS] = 0;
        groupLimit = BREAKDOWN_PAGE;
        trendTime = null;
        read();
    }

    private void show(AlertDialog.Builder builder) {
        if (!active) return;
        closeDialog();
        AlertDialog next = builder.create();
        dialog = next;
        next.setOnDismissListener(closed -> {
            if (dialog == next) dialog = null;
        });
        next.show();
    }

    private void closeDialog() {
        AlertDialog shown = dialog;
        dialog = null;
        if (shown != null) shown.dismiss();
    }

    private static int indexOf(String[] choices, String value) {
        for (int i = 0; i < choices.length; i++) if (choices[i].equals(value)) return i;
        return 0;
    }
}
