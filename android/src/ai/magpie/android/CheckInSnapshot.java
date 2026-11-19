package ai.magpie.android;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Check-in capability comes from the workspace's own settings and disabled-account lists. */
record CheckInSnapshot(List<Vendor> vendors) {
    record AccountId(String by, String key) {}
    record Account(AccountId id, String user, String provider, boolean automatic,
                   boolean providerOff, Result result) {}
    record Vendor(String by, String name, boolean automatic, String settingsPath, List<Account> accounts) {}
    private record Name(String by, String user) {}
    private record VendorSettings(String name, boolean automatic, String path) {}

    record Result(String day, String outcome, boolean asked, boolean again, double credit, int streak, String message) {
        static Result parse(JSONObject object) throws JSONException {
            return new Result(word(object, "day"), object.getString("outcome"), object.optBoolean("asked"),
                    object.optBoolean("again"), object.optDouble("credit", 0), object.optInt("streak"), word(object, "msg"));
        }

        boolean due(String by, LocalDate today) {
            if (!day.equals(today.toString()) || again) return true;
            return switch (outcome) {
                case "claimed", "done", "ineligible", "captcha", "own-app" -> false;
                case "inactive" -> switch (by) {
                    case "qoder", "qoder-cn", "plugin:qoder", "plugin:qoder-cn" -> true;
                    default -> false;
                };
                default -> true;
            };
        }

        String label(LocalDate today) {
            if (!day.equals(today.toString()) && !day.isEmpty()) {
                if (outcome.equals("claimed") || outcome.equals("done")) return "上次签到 " + day;
                return "今日尚未签到";
            }
            if (again) return "服务商称已签到，今日结果待确认";
            String brief = switch (outcome) {
                case "claimed" -> asked ? "签到成功" : "今日已签到";
                case "done" -> "今日已签到";
                case "ineligible" -> "账号暂不符合签到条件";
                case "inactive" -> "服务商当前没有签到活动";
                case "captcha" -> "需要验证码，请到服务商客户端完成签到";
                case "own-app" -> "请在服务商客户端签到";
                default -> "签到失败";
            };
            if (outcome.equals("claimed") || outcome.equals("done")) {
                if (credit > 0 && Double.isFinite(credit)) {
                    brief += " · +" + BigDecimal.valueOf(credit).stripTrailingZeros().toPlainString() + " 积分";
                }
                if (streak > 0) brief += " · 连续 " + streak + " 天";
                return brief;
            }
            return message.isEmpty() ? brief : brief + " · " + message;
        }
    }

    static LocalDate today() { return LocalDate.now(ZoneOffset.ofHours(8)); }

    static CheckInSnapshot parse(String settingsJson, String disabledJson) throws JSONException {
        JSONObject settings = new JSONObject(settingsJson);
        Map<String, VendorSettings> vendors = new LinkedHashMap<>();
        Map<Name, Result> namedResults = new HashMap<>();
        builtin(vendors, namedResults, settings, "", "WorkBuddy", "workbuddy");
        builtin(vendors, namedResults, settings, "trae", "Trae CN", "trae");
        builtin(vendors, namedResults, settings, "minimax", "MiniMax Code", "minimax");
        builtin(vendors, namedResults, settings, "qoder", "Qoder", "qoder");
        JSONArray plugins = settings.optJSONArray("checkinPlugins");
        if (plugins != null) for (int i = 0; i < plugins.length(); i++) {
            JSONObject plugin = plugins.getJSONObject(i);
            String id = word(plugin, "id");
            if (id.isEmpty()) continue;
            String by = "plugin:" + id;
            vendors.put(by, new VendorSettings(first(word(plugin, "name"), id),
                    plugin.optBoolean("on"), "/api/settings/plugin-checkin"));
            named(namedResults, by, plugin.optJSONArray("checkins"));
        }

        Map<AccountId, Account> accounts = new LinkedHashMap<>();
        JSONArray known = settings.optJSONArray("checkinAccounts");
        if (known != null) for (int i = 0; i < known.length(); i++) {
            JSONObject object = known.getJSONObject(i);
            AccountId id = new AccountId(word(object, "by"), word(object, "key"));
            if (id.key().isEmpty()) continue;
            accounts.putIfAbsent(id, new Account(id, word(object, "user"), "", object.optBoolean("on"), false, null));
        }
        JSONArray disabled = new JSONArray(disabledJson);
        for (int i = 0; i < disabled.length(); i++) {
            JSONObject object = disabled.getJSONObject(i);
            AccountId id = new AccountId(word(object, "checkinBy"), word(object, "checkinKey"));
            if (!object.optBoolean("checkins") || id.key().isEmpty()) continue;
            Account original = accounts.get(id);
            Result result = result(object.optJSONObject("checkin"));
            accounts.put(id, new Account(id, word(object, "user"), word(object, "name"),
                    original == null ? !object.optBoolean("checkinOff") : original.automatic(), true, result));
        }

        Map<Name, Integer> counts = new HashMap<>();
        for (Account a : accounts.values()) counts.merge(new Name(a.id().by(), a.user()), 1, Integer::sum);
        Map<String, List<Account>> grouped = new LinkedHashMap<>();
        for (Account a : accounts.values()) {
            Name name = new Name(a.id().by(), a.user());
            Result last = a.result() == null && !a.user().isEmpty() && counts.get(name) == 1 ? namedResults.get(name) : a.result();
            grouped.computeIfAbsent(a.id().by(), ignored -> new ArrayList<>()).add(
                    new Account(a.id(), a.user(), a.provider(), a.automatic(), a.providerOff(), last));
        }
        List<Vendor> result = new ArrayList<>();
        for (var group : grouped.entrySet()) {
            String by = group.getKey();
            VendorSettings vendor = vendors.get(by);
            result.add(new Vendor(by, vendor == null ? first(
                    group.getValue().get(0).provider(), by.replaceFirst("^plugin:", ""), "服务商") : vendor.name(),
                    vendor != null && vendor.automatic(), vendor == null ? "" : vendor.path(), Collections.unmodifiableList(group.getValue())));
        }
        return new CheckInSnapshot(Collections.unmodifiableList(result));
    }

    private static void builtin(Map<String, VendorSettings> vendors, Map<Name, Result> results, JSONObject settings,
                                String by, String name, String setting) throws JSONException {
        vendors.put(by, new VendorSettings(name, settings.optBoolean(setting + "Checkin"),
                "/api/settings/" + setting + "-checkin"));
        named(results, by, settings.optJSONArray(setting + "Checkins"));
    }

    private static void named(Map<Name, Result> results, String by, JSONArray entries) throws JSONException {
        if (entries == null) return;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject row = entries.getJSONObject(i);
            Result result = result(row);
            String user = word(row, "user");
            if (user.isEmpty() || result == null) continue;
            Name name = new Name(by, user);
            if (results.containsKey(name)) results.put(name, null);
            else results.put(name, result);
        }
    }

    private static Result result(JSONObject row) throws JSONException {
        return word(row, "outcome").isEmpty() ? null : Result.parse(row);
    }

    private static String word(JSONObject object, String key) {
        Object value = object == null ? null : object.opt(key);
        return value instanceof String text ? text : "";
    }

    private static String first(String... names) {
        for (String name : names) if (!name.isEmpty()) return name;
        return "";
    }
}
