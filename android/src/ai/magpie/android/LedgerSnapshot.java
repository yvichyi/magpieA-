package ai.magpie.android;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One ledger page and the server's aggregates over all matching rows. */
final class LedgerSnapshot {
    final String bucket;
    final long offset, total, speedMin;
    final Totals totals;
    final List<Row> rows;
    final List<Point> series;
    final List<Speed> speeds;
    private final JSONObject shares, filters;

    static final class Totals {
        final long calls, errors, retried, canceled, input, output, cacheRead, cacheWrite;
        final long cacheUnknown, cacheUnknownCalls, timed, ttft, decodeMs, decodeOut, decodeN, unpriced;
        final double cost;

        Totals(JSONObject object) throws JSONException {
            calls = requiredCount(object, "calls");
            errors = requiredCount(object, "errors");
            retried = count(object, "retried");
            canceled = count(object, "canceled");
            input = requiredCount(object, "input");
            output = requiredCount(object, "output");
            cacheRead = requiredCount(object, "cache_read");
            cacheWrite = requiredCount(object, "cache_write");
            cacheUnknown = count(object, "cache_unknown");
            cacheUnknownCalls = count(object, "cache_unknown_calls");
            timed = count(object, "timed");
            ttft = count(object, "ttft_ms");
            decodeMs = count(object, "decode_ms");
            decodeOut = count(object, "decode_out");
            decodeN = count(object, "decode_n");
            unpriced = requiredCount(object, "unpriced");
            cost = UsageSnapshot.amount(object, true);
            if (errors > calls || canceled > calls - errors || retried > completed()) {
                throw new JSONException("调用状态数量不一致");
            }
        }

        long tokens() { return input + output + cacheRead + cacheWrite; }
        long completed() { return calls - errors - canceled; }
        Double cacheRate() {
            long known = input + cacheRead + cacheWrite - cacheUnknown;
            return known <= 0 ? null : (double) Math.min(cacheRead, known) / known;
        }
        Double meanFirstTokenMs() { return timed == 0 || ttft == 0 ? null : (double) ttft / timed; }
        Double decodeRate() { return decodeN == 0 || decodeMs == 0 ? null : decodeOut * 1000d / decodeMs; }
    }

    static final class Point {
        final String label;
        final Instant time;
        final Totals totals;

        Point(JSONObject object) throws JSONException {
            label = object.getString("label");
            try {
                time = OffsetDateTime.parse(object.getString("time")).toInstant();
            } catch (DateTimeParseException error) {
                throw new JSONException("趋势时间无效");
            }
            totals = new Totals(object);
        }
    }

    static final class Choice {
        final String id, name;

        Choice(String id, String name) {
            this.id = id;
            this.name = name.isEmpty() ? id : name;
        }
    }

    static final class Share {
        final String id, name;
        final Totals totals;

        Share(JSONObject object) throws JSONException {
            id = object.getString("id");
            name = object.optString("name", id);
            totals = new Totals(object);
        }
    }

    static final class Speed {
        final String name;
        final long requests, speedSamples;
        final Double tokensPerSecond;
        final Long firstTokenMs;

        Speed(JSONObject object) throws JSONException {
            name = object.optString("name", object.getString("model"));
            requests = requiredCount(object, "requests");
            speedSamples = count(object, "tps_n");
            double speed = object.optDouble("tps", 0);
            tokensPerSecond = speedSamples > 0 && Double.isFinite(speed) && speed > 0 ? speed : null;
            long ttft = count(object, "ttft_ms");
            firstTokenMs = requests > 0 && ttft > 0 ? ttft : null;
        }
    }

    static final class Row {
        final OffsetDateTime time;
        final String agent, agentName, provider, providerName, model, requested, served;
        final String source, computer, computerName, session, nativeSession, account, callerKey, error;
        final long routeId, status, tries, input, output, cacheRead, cacheWrite;
        final boolean canceled, handedOn, priced;
        final double cost;
        private final long millis, firstToken;
        private final boolean unknownCache;

        Row(JSONObject object) throws JSONException {
            try {
                time = OffsetDateTime.parse(object.getString("t"));
            } catch (DateTimeParseException error) {
                throw new JSONException("请求时间无效");
            }
            agent = object.optString("agent");
            agentName = object.optString("agentName", agent);
            provider = object.optString("provider");
            providerName = object.optString("providerName", provider);
            model = object.optString("model");
            requested = object.optString("req");
            served = object.optString("served");
            source = object.optString("source");
            computer = object.optString("computer");
            computerName = object.optString("computerName", computer);
            session = object.optString("session");
            nativeSession = object.optString("native_session");
            account = object.optString("providerAccount", object.optString("session_account"));
            callerKey = object.optString("callerKeyLabel", object.optString("callerKeyName"));
            error = object.optString("err");
            routeId = count(object, "route_id");
            status = count(object, "status");
            tries = count(object, "tries");
            millis = count(object, "ms");
            firstToken = count(object, "ttft_ms");
            input = requiredCount(object, "in");
            output = requiredCount(object, "out");
            cacheRead = count(object, "cache_read");
            cacheWrite = count(object, "cache_write");
            unknownCache = object.optBoolean("cache_unknown")
                    || !object.has("cache_read") || !object.has("cache_write");
            canceled = object.optBoolean("canceled");
            handedOn = object.optBoolean("handed_on");
            priced = object.optBoolean("priced");
            cost = UsageSnapshot.amount(object, true);
        }

        long tokens() { return input + output + cacheRead + cacheWrite; }
        Long durationMs() { return millis == 0 ? null : millis; }
        Long firstTokenMs() { return firstToken == 0 ? null : firstToken; }
        boolean cacheKnown() { return !unknownCache; }
        String priceLabel() {
            if (priced) return UsageSnapshot.money(cost);
            return tokens() == 0 ? "—" : "待定价";
        }
        String statusLabel() {
            if (handedOn) return "尝试失败 · 已转交";
            if (canceled) return "已取消";
            if (status >= 400 || !error.isEmpty()) return "最终失败";
            if (tries > 1) return "重试后成功";
            return "已完成";
        }

        String sourceLabel() {
            if (source.equals("log")) return "会话日志";
            if (source.equals("reported")) return "客户端上报";
            return "网关";
        }

        String routePath() {
            if (routeId == 0 || !computer.isEmpty()) return null;
            return "/api/gateway/route?id=" + routeId + "&day=" + time.toLocalDate();
        }

        String contentUnavailable() {
            if (!computer.isEmpty()) return "会话内容保留在 " + computerName + "。";
            if (nativeSession.isEmpty() && session.isEmpty()) return "这条请求没有会话标识。";
            if (!agent.equals("codex") && !agent.equals("claude") && !agent.equals("claude-desktop")) {
                return "此客户端的会话文件不可读取。";
            }
            return "";
        }

        String contentPath() {
            if (!contentUnavailable().isEmpty()) return null;
            Instant start = time.toInstant();
            boolean log = source.equals("log");
            Instant at = log ? start : start.plusMillis(millis);
            Instant from = start.minusMillis(log ? 1 : 5000);
            Instant to = at.plusMillis(log ? 1 : 30000);
            String sessionId = nativeSession.isEmpty() ? session : nativeSession;
            return "/api/usage/requests/content?agent=" + LedgerQuery.encode(agent)
                    + "&session=" + LedgerQuery.encode(sessionId) + "&from=" + LedgerQuery.encode(from.toString())
                    + "&to=" + LedgerQuery.encode(to.toString()) + "&at=" + LedgerQuery.encode(at.toString());
        }
    }

    private LedgerSnapshot(JSONObject object) throws JSONException {
        totals = new Totals(object);
        offset = requiredCount(object, "offset");
        total = requiredCount(object, "total");
        speedMin = count(object, "speed_min");
        bucket = object.getString("bucket");
        JSONArray entries = array(object, "rows");
        if (offset > total || entries.length() > total - offset) throw new JSONException("请求分页数据不一致");
        List<Row> readRows = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) readRows.add(new Row(entries.getJSONObject(i)));
        rows = Collections.unmodifiableList(readRows);
        entries = array(object, "series");
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) points.add(new Point(entries.getJSONObject(i)));
        series = Collections.unmodifiableList(points);
        entries = array(object, "speeds");
        List<Speed> measured = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) measured.add(new Speed(entries.getJSONObject(i)));
        speeds = Collections.unmodifiableList(measured);
        shares = object.getJSONObject("by");
        filters = object;
    }

    static LedgerSnapshot parse(String json) throws JSONException {
        return new LedgerSnapshot(new JSONObject(json));
    }

    long nextOffset() { return offset + rows.size(); }
    boolean hasNext() { return !rows.isEmpty() && nextOffset() < total; }

    int latestActivePoint() {
        for (int i = series.size() - 1; i >= 0; i--) {
            Totals value = series.get(i).totals;
            if (value.calls > 0 || value.tokens() > 0 || value.cost > 0 || value.unpriced > 0) return i;
        }
        return series.size() - 1;
    }

    List<Share> by(String dimension) throws JSONException {
        JSONArray entries = array(shares, dimension);
        List<Share> groups = new ArrayList<>();
        for (int i = 0; i < entries.length(); i++) groups.add(new Share(entries.getJSONObject(i)));
        return groups;
    }

    List<Choice> choices(String field) throws JSONException {
        Map<String, Choice> result = new LinkedHashMap<>();
        if (field.equals("model")) {
            for (Share share : by("model")) result.put(share.id, new Choice(share.id, share.name));
        } else {
            String key = field.equals("callerKey") ? "callerKeys" : field.equals("purpose") ? "purposes"
                    : field.equals("computer") ? "computers" : field + "s";
            JSONArray entries = array(filters, key);
            for (int i = 0; i < entries.length(); i++) {
                if (field.equals("purpose")) {
                    String purpose = entries.getString(i);
                    result.put(purpose, new Choice(purpose, purpose));
                } else {
                    JSONObject item = entries.getJSONObject(i);
                    String id = item.optString(field.equals("callerKey") ? "callerKeyId" : "id");
                    if (!id.isEmpty()) result.put(id, new Choice(id, item.optString("name", id)));
                }
            }
        }
        if (field.equals("callerKey")) result.put("none", new Choice("none", "无网关密钥"));
        return new ArrayList<>(result.values());
    }

    static JSONArray array(JSONObject object, String key) throws JSONException {
        return !object.has(key) || object.isNull(key) ? new JSONArray() : object.getJSONArray(key);
    }

    private static long requiredCount(JSONObject object, String key) throws JSONException {
        return UsageSnapshot.count(object, key, true);
    }

    private static long count(JSONObject object, String key) throws JSONException {
        return UsageSnapshot.count(object, key, false);
    }
}
