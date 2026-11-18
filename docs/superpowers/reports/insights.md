# Task 5 · Native Insights

The retained 用量 root provides 概览, 分析, 日历, and 请求. The overview uses the ledger's aggregate Token counts, known USD and unpriced counts, final failures, cancellations, successful retries, and measured time to first Token. The trend supports visual selection and previous/next controls that announce the selected bucket. Analysis shows model and provider breakdowns, known cache rates, and measured mean and median response timings. The calendar uses the core's daily heatmap, supports month navigation and metric selection, and opens the ledger for a selected date. Returning from a drilldown restores the source tab and its query.

The request list uses server pagination and supports search, facets, source, failure and successful retry filters, and detailed ledger metadata. Route attempts and session content load only when requested. Revealed session content is discarded when the detail pauses. The Android document picker exports every matching CSV row through an origin-bound stream. Reads are scoped to workspace, origin, key, timezone, and query. The page labels cached results during an exact-query refresh or failure and discards results from an invalidated scope.

## Files

- Root and shared display helpers in `InsightsScreen.java`, `InsightsScope.java`, `InsightsViews.java`, `InsightChart.java`, and `MonthHeatmap.java`.
- Ledger and request views in `LedgerSnapshot.java`, `LedgerQuery.java`, `LedgerScreen.java`, and `RequestDetailScreen.java`.
- Export handling in `LedgerExport.java` and `LedgerCsv.java`.
- Focused tests in `LedgerSnapshotTest.java` and `LedgerCsvTest.java`.

## Verification

- JDK 17 compiled the focused JVM tests. `LedgerSnapshotTest`, `LedgerCsvTest`, `UsageSnapshotTest`, and `UsageHeatmapTest` all passed. The CSV test streamed a 9 MiB authenticated response, rejected redirects, and stopped writing after scope invalidation. The chart regression failed compilation before the data model provided the most recent active bucket and passed after implementation.
- SDK 35 Java compilation succeeded against the current `MainActivity`, `ScreenHost`, and `Ui` using the generated app resources. Temporary stubs represented the in-progress Management and Plugins modules during this isolated compile.
- The Android API-floor test passed. Bytecode inspection confirmed the new ledger query encoder uses the compatible `URLEncoder.encode(String, String)` overload.
- Checked endpoint and JSON field names against the pinned core source at `593517bf00bc8b0ab00c8ddef3f4d31b310a71b8`.

## Integration coverage

The chart links day and hour buckets to their calendar day's request list. Weekly buckets support point navigation and display the week's totals. Live core responses, installed touch and accessibility behavior, large-font layout, and the complete document-picker flow remain part of the parent integration run. This task did not modify shared APIs or core source.
