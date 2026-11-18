package ai.magpie.android;

import java.time.LocalDate;
import org.json.JSONException;

public final class LedgerSnapshotTest {
    public static void main(String[] args) throws Exception {
        aggregatesAreIndependentOfThePage();
        missingMeasurementsStayMissing();
        chartStartsAtTheLatestBucketWithActivity();
        filtersCarryAcrossTheLedgerCalendarAndExport();
        calendarDrilldownCoversTheWholeDay();
        malformedTotalsFailClosed();
        System.out.println("LedgerSnapshotTest passed");
    }

    private static void aggregatesAreIndependentOfThePage() throws Exception {
        LedgerSnapshot page = LedgerSnapshot.parse(fixture());
        eq(931L, page.total, "matching row count is not capped at 500");
        eq(601L, page.nextOffset(), "paging uses returned offset and actual row count");
        eq(922L, page.totals.calls, "logical calls can differ from matching rows");
        eq(70L, page.totals.tokens(), "top-level tokens include input, output and both caches");
        eq(9L, page.totals.errors, "final errors");
        eq(33L, page.totals.retried, "successful retries remain independent");
        eq(1.25, page.totals.cost, "known USD");
        eq(7L, page.totals.unpriced, "unpriced calls");
        eq(12L, page.series.get(0).totals.tokens(), "series tokens include cache");
        eq(40L, page.by("provider").get(0).totals.tokens(), "breakdown tokens include cache");
        eq("重试后成功", page.rows.get(0).statusLabel(), "successful retries are not final failures");
        eq(Long.valueOf(510), page.rows.get(0).durationMs(), "total request duration");
        eq(Long.valueOf(80), page.rows.get(0).firstTokenMs(), "time to first token");
        String content = page.rows.get(0).contentPath();
        if (!content.contains("session=native%2Fabc") || !content.contains("at=2026-11-18T10%3A00%3A00.510Z")) {
            throw new AssertionError("content must use the native session and request end: " + content);
        }
        eq("/api/gateway/route?id=782&day=2026-11-18", page.rows.get(0).routePath(), "route day uses the recorded offset");
    }

    private static void missingMeasurementsStayMissing() throws Exception {
        LedgerSnapshot page = LedgerSnapshot.parse(fixture());
        eq(null, page.totals.cacheRate(), "fully unknown cache");
        eq(null, page.totals.meanFirstTokenMs(), "no timed replies");
        eq(null, page.speeds.get(0).tokensPerSecond, "no measured decode speed");
        eq(Long.valueOf(85), page.speeds.get(0).firstTokenMs, "median TTFT independent of decode speed");
        eq("待定价", page.rows.get(0).priceLabel(), "unknown price");
        if (page.rows.get(0).cacheKnown()) throw new AssertionError("unknown row cache was treated as measured");
        eq(0.4, LedgerSnapshot.parse(fixture().replace("\"cache_unknown\":50", "\"cache_unknown\":0"))
                .totals.cacheRate(), "known cache rate uses known prompt only");
    }

    private static void filtersCarryAcrossTheLedgerCalendarAndExport() {
        LedgerQuery query = new LedgerQuery().period("7d").with("provider", "供应商 A")
                .with("q", "a+b#c").with("failed", "1").with("retried", "1");
        String filters = "tz=Asia%2FShanghai&failed=1&provider=%E4%BE%9B%E5%BA%94%E5%95%86+A&q=a%2Bb%23c&retried=1";
        eq("/api/usage/requests?period=7d&" + filters + "&offset=601&limit=50",
                query.requests("Asia/Shanghai", 601), "ledger path");
        eq("/api/usage/heatmap?" + filters, query.heatmap("Asia/Shanghai"), "calendar keeps filters");
        eq("/api/usage/requests.csv?period=7d&" + filters, query.csv("Asia/Shanghai"), "export has all matching pages");
        eq("", query.with("failed", "").value("failed"), "clearing filters");
        eq("1", query.value("failed"), "query is immutable");
    }

    private static void chartStartsAtTheLatestBucketWithActivity() throws Exception {
        org.json.JSONObject json = new org.json.JSONObject(fixture());
        org.json.JSONArray series = json.getJSONArray("series");
        series.put(new org.json.JSONObject(series.getJSONObject(0).toString())
                .put("time", "2026-11-19T00:00:00+08:00").put("calls", 0));
        series.put(new org.json.JSONObject(series.getJSONObject(0).toString())
                .put("time", "2026-11-20T00:00:00+08:00").put("calls", 0).put("input", 0)
                .put("output", 0).put("cache_read", 0).put("cache_write", 0).put("unpriced", 0));
        eq(1, LedgerSnapshot.parse(json.toString()).latestActivePoint(),
                "billed attempts count as activity even when they did not end a call");
        eq(-1, LedgerSnapshot.parse(json.put("series", new org.json.JSONArray()).toString()).latestActivePoint(),
                "an empty trend has no selection");
    }

    private static void calendarDrilldownCoversTheWholeDay() {
        LedgerQuery day = new LedgerQuery().period("today").with("agent", "codex")
                .day(LocalDate.of(2026, 1, 8));
        eq("/api/usage/requests?period=2026-01-08%2E%2E2026-01-08&tz=UTC&agent=codex&day=2026-01-08&offset=0&limit=50",
                day.requests("UTC", 0), "a calendar date works outside the selected period");
        eq("/api/usage/heatmap?tz=UTC&agent=codex", day.heatmap("UTC"), "calendar keeps every day");
    }

    private static void malformedTotalsFailClosed() throws Exception {
        try {
            LedgerSnapshot.parse(fixture().replace("\"calls\":922,", ""));
        } catch (JSONException expected) {
            return;
        }
        throw new AssertionError("missing authoritative counters appeared as zero");
    }

    private static void eq(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + " expected " + expected + " but got " + actual);
        }
    }

    private static String fixture() {
        return ("""
                {"period":"7d","offset":600,"total":931,"bucket":"day",
                 "calls":922,"errors":9,"canceled":4,"retried":33,"input":25,"output":20,
                 "cache_read":20,"cache_write":5,"cache_unknown":50,"unpriced":7,"cost":1.25,
                 "speed_min":100,
                 "series":[{"time":"2026-11-18T00:00:00+08:00","label":"11/18","calls":1,"errors":0,
                     "input":3,"output":4,"cache_read":2,"cache_write":3,"cost":0,"unpriced":1}],
                 "by":{"provider":[{"id":"p","name":"Provider","calls":1,"errors":0,"input":20,
                     "output":10,"cache_read":8,"cache_write":2,"unpriced":0,"cost":0.7}]},
                 "speeds":[{"model":"m","name":"Model","requests":5,"ttft_ms":85}],
                 "rows":[{"t":"2026-11-18T18:00:00+08:00","agent":"codex","provider":"p",
                     "model":"m","in":4,"out":5,"cache_read":0,"cache_write":0,"cache_unknown":true,
                     "native_session":"native/abc","session":"mapped","cost":0,"priced":false,
                     "ms":510,"ttft_ms":80,"status":200,"tries":2,"route_id":782}]}
                """);
    }
}
