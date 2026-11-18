package ai.magpie.android;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Immutable query shared by insights, request pages and CSV export. */
final class LedgerQuery {
    static final int PAGE_SIZE = 50;
    private static final Set<String> FILTERS = new HashSet<>(Arrays.asList("agent", "provider", "model", "purpose",
            "account", "callerKey", "failed", "retried", "q", "computer", "via"));
    final String period;
    final LocalDate day;
    private final Map<String, String> filters;

    LedgerQuery() {
        this("30d", null, Collections.emptyMap());
    }

    private LedgerQuery(String period, LocalDate day, Map<String, String> filters) {
        this.period = period;
        this.day = day;
        this.filters = filters;
    }

    LedgerQuery period(String value) {
        UsageSnapshot.validatePeriod(value);
        return new LedgerQuery(value, null, filters);
    }

    LedgerQuery day(LocalDate value) {
        return new LedgerQuery(period, value, filters);
    }

    LedgerQuery with(String field, String value) {
        if (!FILTERS.contains(field)) throw new IllegalArgumentException("Unknown ledger filter");
        Map<String, String> copy = new TreeMap<>(filters);
        if (value.isEmpty()) copy.remove(field);
        else copy.put(field, value);
        return new LedgerQuery(period, day, Collections.unmodifiableMap(copy));
    }

    LedgerQuery clear() {
        return new LedgerQuery(period, null, Collections.emptyMap());
    }

    String value(String field) {
        return filters.getOrDefault(field, "");
    }

    int filterCount() {
        return filters.size() + (day == null ? 0 : 1);
    }

    String requests(String zone, long offset) {
        if (offset < 0) throw new IllegalArgumentException("Negative ledger offset");
        return path("/api/usage/requests", zone, true) + "&offset=" + offset + "&limit=" + PAGE_SIZE;
    }

    String heatmap(String zone) {
        return path("/api/usage/heatmap", zone, false);
    }

    String csv(String zone) {
        return path("/api/usage/requests.csv", zone, true);
    }

    private String path(String endpoint, String zone, boolean dateRange) {
        StringBuilder url = new StringBuilder(endpoint).append('?');
        if (dateRange) {
            url.append("period=").append(day == null ? period : day + "%2E%2E" + day).append('&');
        }
        url.append("tz=").append(encode(zone));
        Map<String, String> values = new TreeMap<>(filters);
        if (dateRange && day != null) values.put("day", day.toString());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            url.append('&').append(entry.getKey()).append('=').append(encode(entry.getValue()));
        }
        return url.toString();
    }

    static String encode(String value) {
        try {
            // The Charset overload was added after the app's Android 28 minimum.
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("..", "%2E%2E");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LedgerQuery)) return false;
        LedgerQuery query = (LedgerQuery) other;
        return period.equals(query.period) && Objects.equals(day, query.day) && filters.equals(query.filters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(period, day, filters);
    }
}
