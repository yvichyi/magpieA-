package ai.magpie.android;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The management API's plugin data, with option values left to the upstream editor. */
final class PluginSnapshot {
    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$");
    private static final Pattern NPM = Pattern.compile("(@[a-z0-9][a-z0-9._~-]*/)?[a-z0-9][a-z0-9._~-]*");
    private static final Pattern GIT = Pattern.compile("(?i)(?:(?:github|gitlab|bitbucket):|git\\+[a-z]+://|git://)\\S+|https?://(?:www\\.)?(?:github\\.com|gitlab\\.com|bitbucket\\.org)/\\S+|[a-z0-9][a-z0-9_.-]*/[a-z0-9_.-]+(?:#\\S+)?");
    private static final Pattern GITHUB = Pattern.compile("(?i)(?:github:|(?:git\\+)?(?:https?|ssh|git)://(?:[^@/\\s]+@)?(?:www\\.|ssh\\.)?github\\.com(?::\\d+(?=/))?[/:])([^/#\\s]+/[^/#\\s]+?)(?:\\.git)?(?:[/#].*)?");

    record Installed(List<Entry> entries, boolean bun, String bunVersion, String registry, String error) {
        Entry find(String spec) {
            for (Entry entry : entries) if (entry.spec.equals(spec)) return entry;
            return null;
        }

        Entry match(Discovery candidate) {
            String repo = gitRepo(candidate.spec);
            for (Entry entry : entries) {
                if (entry.spec.equals(candidate.spec)) return entry;
                if (!repo.isEmpty() && repo.equals(gitRepo(entry.spec))) return entry;
                if (repo.isEmpty() && entry.npm() && packageName(entry.spec).equals(candidate.spec)) return entry;
                if (!candidate.repo.isEmpty() && candidate.repo.equalsIgnoreCase(gitRepo(entry.spec))) return entry;
            }
            return null;
        }
    }

    record Entry(String spec, String name, boolean off, String version, String latest, String commit, String head,
                 String error, List<String> providers, List<String> moved, List<String> pageReads, List<String> tools,
                 String page, String agent, String agentNotice, boolean middleware, boolean gateway, boolean configurable) {
        boolean npm() { return !isPath(spec) && !isGit(spec); }
        boolean updateAvailable() {
            if (isGit(spec)) return !commit.isEmpty() && !head.isEmpty() && !sameCommit(commit, head);
            return npm() && newer(latest, version);
        }
        String kind() {
            List<String> kinds = new ArrayList<>();
            if (!providers.isEmpty()) kinds.add("服务商");
            if (middleware || gateway) kinds.add("网关扩展");
            if (!agent.isEmpty()) kinds.add("Agent");
            if (!page.isEmpty()) kinds.add("插件页面");
            if (!tools.isEmpty()) kinds.add("MCP 工具");
            return String.join(" · ", kinds);
        }
    }

    enum Feed {
        LISTINGS("listings"), GITHUB("repos"), SEARCH("hits");
        final String key;
        Feed(String key) { this.key = key; }
    }

    record Discovery(String spec, String name, String description, String version, String kind, String source,
                     String publisher, String license, String repo) {
        boolean matches(String query) {
            return (name + "\n" + spec + "\n" + description + "\n" + repo).toLowerCase(Locale.ROOT)
                    .contains(query.toLowerCase(Locale.ROOT));
        }
        String kindLabel() {
            switch (kind) {
                case "middleware": case "hooks": return "网关扩展";
                case "agent": return "Agent";
                case "tools": return "MCP 工具";
                case "page": return "插件页面";
                default: return "";
            }
        }
    }

    record Discoveries(List<Discovery> entries, String error) {}
    record UpdateCheck(Installed state, int available, int unknown) {}

    static UpdateCheck checked(String raw) throws JSONException {
        JSONObject response = new JSONObject(raw);
        JSONObject state = response.getJSONObject("state");
        JSONArray plugins = state.getJSONArray("plugins");
        JSONArray checks = response.getJSONArray("plugins");
        java.util.Map<String, JSONObject> bySpec = new java.util.HashMap<>();
        int available = 0, unknown = 0;
        for (int i = 0; i < checks.length(); i++) {
            JSONObject item = checks.getJSONObject(i);
            bySpec.put(item.getString("spec"), item);
            String status = item.optString("status");
            if (status.equals("update") && !item.optBoolean("off")) available++;
            if (status.equals("unknown")) unknown++;
        }
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject entry = plugins.getJSONObject(i);
            JSONObject result = bySpec.get(entry.getString("spec"));
            if (result == null) continue;
            if (result.has("latest")) entry.put("latest", result.getString("latest"));
            if (result.has("head")) entry.put("head", result.getString("head"));
        }
        return new UpdateCheck(installed(state.toString()), available, unknown);
    }

    record Page(String spec, String key, String title) {}
    record Builtin(String id, String name, String description, boolean on, boolean chosen, int inUse, String use,
                   String why, String broken, String destination, String settings, List<Field> fields,
                   boolean advancedConfig) {
        boolean canToggle() { return broken.isEmpty() && inUse == 0; }
    }

    record Field(String key, String title, String description, String type, Object value, List<Object> choices,
                 Double min, Double max) {
        boolean accepts(Object candidate) {
            if (!validType(type, candidate)) return false;
            if (candidate instanceof Number) {
                double number = ((Number) candidate).doubleValue();
                if ((min != null && number < min) || (max != null && number > max)) return false;
            }
            if (choices.isEmpty()) return true;
            for (Object choice : choices) if (sameValue(choice, candidate)) return true;
            return false;
        }
    }

    static Installed installed(String raw) throws JSONException {
        JSONObject data = new JSONObject(raw);
        JSONArray values = data.getJSONArray("plugins");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            String spec = item.getString("spec");
            if (spec.isEmpty()) throw new JSONException("插件标识为空");
            List<String> providers = strings(item.optJSONArray("providers"));
            String name = first(word(item, "ownName"), providers.size() == 1 ? providers.get(0) : "",
                    word(item, "package"), displaySpec(spec));
            JSONObject page = item.optJSONObject("page"), agent = item.optJSONObject("agent");
            List<String> tools = new ArrayList<>();
            JSONArray rawTools = item.optJSONArray("tools");
            if (rawTools != null) for (int at = 0; at < rawTools.length(); at++) {
                String tool = word(rawTools.optJSONObject(at), "name");
                if (!tool.isEmpty()) tools.add(tool);
            }
            entries.add(new Entry(spec, name, item.optBoolean("off"), word(item, "version"), word(item, "latest"),
                    word(item, "commit"), word(item, "head"), word(item, "error"), providers,
                    strings(item.optJSONArray("moved")), strings(page == null ? null : page.optJSONArray("reads")),
                    Collections.unmodifiableList(tools), page == null ? "" : first(word(page, "title"), name),
                    first(word(agent, "name"), item.optBoolean("isAgent") ? "Agent" : ""),
                    first(word(agent, "error"), word(agent, "notice"), word(agent, "looks")),
                    item.optBoolean("isMiddleware"), item.optBoolean("isGateway"),
                    item.optJSONObject("options") != null || item.optJSONObject("optionsExample") != null));
        }
        return new Installed(Collections.unmodifiableList(entries), data.optBoolean("bun"), word(data, "bunVersion"),
                word(data.optJSONObject("registry"), "url"), word(data, "error"));
    }

    static Discoveries discovery(String raw, Feed feed) throws JSONException {
        JSONObject data = new JSONObject(raw);
        JSONArray values = data.optJSONArray(feed.key);
        if (values == null && !(feed == Feed.GITHUB && data.isNull(feed.key))) values = data.getJSONArray(feed.key);
        List<Discovery> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (values != null) for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            JSONObject npm = feed == Feed.LISTINGS ? item.optJSONObject("npm") : item;
            String spec = word(item, feed == Feed.GITHUB ? "spec" : "package");
            if ((!npmName(spec) && !(feed == Feed.GITHUB && !gitRepo(spec).isEmpty())) || !seen.add(spec)) continue;
            JSONObject summary = item.optJSONObject("summary");
            String source = feed == Feed.GITHUB ? "GitHub · 作者发布" : item.optBoolean("community") ? "Magpie 社区" : "npm";
            entries.add(new Discovery(spec, first(word(item, "name"), word(item, "repo"), spec),
                    first(word(summary, "zh"), word(summary, "zh-CN"), word(summary, "en"), word(npm, "description")),
                    word(npm, "version"), word(item, "kind"), source, first(word(npm, "publisher"), word(item, "owner")),
                    first(word(npm, "license"), word(item, "pkgLicense")), word(item, "repo")));
        }
        return new Discoveries(Collections.unmodifiableList(entries), word(data, "error"));
    }

    static List<Page> pages(String raw) throws JSONException {
        JSONArray values = new JSONArray(raw);
        List<Page> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            String spec = word(item, "spec"), key = word(item, "key");
            if (spec.isEmpty() || !key.matches("plugin-[a-z0-9-]{0,160}") || !seen.add(key)) continue;
            JSONObject titles = item.optJSONObject("titles");
            out.add(new Page(spec, key, first(word(titles, "zh"), word(titles, "zh-CN"), word(item, "title"), key)));
        }
        return Collections.unmodifiableList(out);
    }

    static List<Builtin> builtins(String raw) throws JSONException {
        JSONArray values = new JSONArray(raw);
        List<Builtin> out = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            String id = item.getString("id");
            if (id.isEmpty()) throw new JSONException("内置插件标识为空");
            JSONObject schema = item.optJSONObject("schema");
            List<Field> fields = fields(schema, item.optJSONObject("config"));
            boolean on = item.getBoolean("on");
            out.add(new Builtin(id, builtinName(id, first(word(item, "name"), id)), word(item, "sub"), on,
                    item.optBoolean("chosen", on), item.optInt("inUse"), word(item, "use"), word(item, "why"),
                    word(item, "broken"), first(word(item, "page"), word(item, "opens")), word(item, "setAt"),
                    fields == null ? Collections.emptyList() : fields, fields == null));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<Field> fields(JSONObject schema, JSONObject config) {
        if (schema == null) return Collections.emptyList();
        JSONObject properties = schema.optJSONObject("properties");
        if (!word(schema, "type").equals("object") || properties == null) return null;
        List<String> order = new ArrayList<>(strings(schema.optJSONArray("x-order")));
        for (Iterator<String> keys = properties.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!order.contains(key)) order.add(key);
        }
        List<Field> fields = new ArrayList<>();
        for (String key : order) {
            JSONObject property = properties.optJSONObject(key);
            if (property == null) return null;
            String type = word(property, "type");
            Object value = config != null && config.has(key) ? config.opt(key) : property.opt("default");
            List<Object> choices = new ArrayList<>();
            JSONArray enumeration = property.optJSONArray("enum");
            if (enumeration != null) for (int i = 0; i < enumeration.length(); i++) {
                Object choice = enumeration.opt(i);
                if (!validType(type, choice)) return null;
                choices.add(choice);
            }
            String title = first(word(property, "title"), key);
            String description = word(property, "description");
            if (key.equals("callsPage") && title.equals("Recent calls at a time")) {
                title = "每页最近调用数";
                description = "设置网关页面初次显示的记录数，以及每次加载更多时的新增条数。";
            }
            Field field = new Field(key, title, description, type, value, Collections.unmodifiableList(choices),
                    number(property.opt("minimum")), number(property.opt("maximum")));
            if (!field.accepts(value)) return null;
            fields.add(field);
        }
        return Collections.unmodifiableList(fields);
    }

    private static String builtinName(String id, String name) {
        switch (id) {
            case "gateway": return "网关页面";
            case "routing": return "请求路由";
            case "groups": return "路由分组";
            case "rotation": return "路由策略";
            case "sessions": return "会话";
            case "library": return "资料库";
            case "otel": return "可观测性";
            case "warmups": return "预热与签到";
            case "usage": return "用量";
            case "redact": return "隐私脱敏";
            case "budget": return "密钥限额";
            case "search": return "网页搜索";
            case "tools": return "插件工具";
            case "middleware": return "JavaScript 网关插件";
            default: return name;
        }
    }

    static List<String> strings(JSONArray data) {
        List<String> values = new ArrayList<>();
        if (data != null) for (int i = 0; i < data.length(); i++) {
            Object value = data.opt(i);
            if (value instanceof String && !((String) value).isEmpty()) values.add((String) value);
        }
        return Collections.unmodifiableList(values);
    }

    static String word(JSONObject object, String key) {
        Object value = object == null ? null : object.opt(key);
        return value instanceof String ? (String) value : "";
    }

    static String first(String... words) {
        for (String word : words) if (!word.trim().isEmpty()) return word;
        return "";
    }

    private static Double number(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : null;
    }

    private static boolean validType(String type, Object value) {
        switch (type) {
            case "boolean": return value instanceof Boolean;
            case "string": return value instanceof String;
            case "integer": case "number":
                Double n = number(value);
                return n != null && Double.isFinite(n) && (!type.equals("integer") || n == Math.rint(n));
            default: return false;
        }
    }

    static boolean sameValue(Object a, Object b) {
        if (a instanceof Number && b instanceof Number) return number(a).equals(number(b));
        return a.equals(b);
    }

    static boolean npmName(String spec) { return spec.length() <= 214 && NPM.matcher(spec).matches(); }
    static boolean isVersion(String text) { return VERSION.matcher(text).matches(); }
    static boolean sameCommit(String a, String b) { return !a.isEmpty() && !b.isEmpty() && (a.startsWith(b) || b.startsWith(a)); }
    static boolean isGit(String spec) { return !isPath(spec) && GIT.matcher(spec).matches(); }

    static String gitRepo(String spec) {
        if (!isGit(spec)) return "";
        Matcher matcher = GITHUB.matcher(spec);
        if (matcher.matches()) return matcher.group(1).toLowerCase(Locale.ROOT);
        if (spec.indexOf(':') < 0) return spec.split("#", 2)[0].toLowerCase(Locale.ROOT);
        return "";
    }

    private static boolean isPath(String spec) {
        return spec.startsWith("file://") || spec.startsWith(".") || spec.startsWith("/") || spec.startsWith("\\") ||
                spec.matches("^[A-Za-z]:[\\\\/].*");
    }

    static String packageName(String spec) {
        if (isGit(spec) || isPath(spec)) return spec;
        int at = spec.lastIndexOf('@');
        return at > 0 ? spec.substring(0, at) : spec;
    }

    static String displaySpec(String spec) {
        if (!isPath(spec)) return packageName(spec);
        String[] pieces = spec.split("[\\\\/]+");
        return pieces.length > 0 ? pieces[pieces.length - 1] : spec;
    }

    static boolean newer(String proposed, String installed) {
        Matcher a = VERSION.matcher(proposed.trim()), b = VERSION.matcher(installed.trim());
        if (!a.matches() || !b.matches()) return false;
        for (int i = 1; i <= 3; i++) {
            int comparison = new BigInteger(a.group(i)).compareTo(new BigInteger(b.group(i)));
            if (comparison != 0) return comparison > 0;
        }
        String preA = a.group(4), preB = b.group(4);
        if (preA == null || preB == null) return preA == null && preB != null;
        String[] left = preA.split("\\."), right = preB.split("\\.");
        for (int i = 0; i < Math.min(left.length, right.length); i++) {
            boolean leftNumber = left[i].matches("\\d+"), rightNumber = right[i].matches("\\d+");
            if (leftNumber != rightNumber) return rightNumber;
            int comparison = leftNumber ? new BigInteger(left[i]).compareTo(new BigInteger(right[i])) : left[i].compareTo(right[i]);
            if (comparison != 0) return comparison > 0;
        }
        return left.length > right.length;
    }
}
