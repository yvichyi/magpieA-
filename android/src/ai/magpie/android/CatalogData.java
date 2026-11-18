package ai.magpie.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Catalog-only fields from the core. The shared snapshot still supplies the advertised references. */
final class CatalogData {
    final WorkspaceSnapshot snapshot;
    private final Map<String, Provider> providers = new LinkedHashMap<>();
    private final Map<String, Model> models = new LinkedHashMap<>();
    private final Map<String, Group> groups = new LinkedHashMap<>();

    private CatalogData(WorkspaceSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    static CatalogData parse(String providersJson, String groupsJson) throws JSONException {
        CatalogData data = new CatalogData(WorkspaceSnapshot.parse(providersJson, groupsJson));
        JSONArray providers = array(new JSONObject(providersJson), "providers");
        JSONObject routing = new JSONObject(groupsJson);
        JSONArray groups = array(routing, "groups");
        JSONArray offered = array(routing, "models");
        Set<String> direct = new HashSet<>();
        for (int i = 0; i < offered.length(); i++) direct.add(offered.getJSONObject(i).getString("id"));
        boolean hasOfferings = !routing.isNull("models");
        for (int i = 0; i < providers.length(); i++) {
            JSONObject entry = providers.getJSONObject(i);
            Provider provider = new Provider(data.snapshot.providers.get(i), flag(entry, "ready"));
            data.providers.put(provider.provider.id, provider);
            JSONArray models = array(entry, "models");
            for (int j = 0; j < models.length(); j++) {
                JSONObject options = models.getJSONObject(j);
                WorkspaceSnapshot.Model model = provider.provider.models.get(j);
                boolean connectable = model.on && !provider.provider.off && !Boolean.FALSE.equals(provider.ready)
                        && !options.optBoolean("noAPI") && (!hasOfferings || direct.contains(model.reference));
                data.models.put(model.reference, new Model(provider, model, options.optString("by", ""), connectable));
            }
        }
        for (int i = 0; i < groups.length(); i++) {
            Group group = new Group(data.snapshot.groups.get(i), groups.getJSONObject(i));
            data.groups.put(group.group.reference, group);
        }
        return data;
    }

    private static JSONArray array(JSONObject object, String key) throws JSONException {
        return object.isNull(key) ? new JSONArray() : object.getJSONArray(key);
    }

    private static Boolean flag(JSONObject object, String key) throws JSONException {
        return object.isNull(key) ? null : object.getBoolean(key);
    }

    Provider provider(String id) { return providers.get(id); }
    Model model(String reference) { return models.get(reference); }
    Group group(String reference) { return groups.get(reference); }

    List<Model> models(String query, int filter, Set<String> favorites, String providerId) {
        List<Model> results = new ArrayList<>();
        for (Provider source : providers.values()) {
            if (!providerId.isEmpty() && !providerId.equals(source.provider.id)) continue;
            for (WorkspaceSnapshot.Model entry : CatalogSelection.models(source.provider, query, filter, favorites)) {
                Model model = models.get(entry.reference);
                if (filter != 1 || model.connectable) results.add(model);
            }
        }
        return results;
    }

    List<Provider> providers(String query) {
        List<Provider> results = new ArrayList<>();
        for (Provider provider : providers.values()) {
            if (provider.provider.matches(query)) results.add(provider);
        }
        return results;
    }

    List<Group> groups(String query) {
        List<Group> results = new ArrayList<>();
        String term = query.trim().toLowerCase(Locale.ROOT);
        for (Group group : groups.values()) {
            if (group.group.matches(query) || group.patterns.stream()
                    .anyMatch(pattern -> pattern.text.toLowerCase(Locale.ROOT).contains(term))) results.add(group);
        }
        return results;
    }

    boolean sameContent(CatalogData other) {
        if (other == null || !snapshot.sameContent(other.snapshot)) return false;
        for (Provider provider : providers.values()) {
            if (!Objects.equals(provider.ready, other.providers.get(provider.provider.id).ready)) return false;
        }
        for (Model model : models.values()) {
            Model previous = other.models.get(model.model.reference);
            if (model.connectable != previous.connectable || !model.upstream.equals(previous.upstream)) return false;
        }
        for (Group group : groups.values()) {
            if (!group.sameDetails(other.groups.get(group.group.reference))) return false;
        }
        return true;
    }

    static final class Provider {
        final WorkspaceSnapshot.Provider provider;
        final Boolean ready;

        Provider(WorkspaceSnapshot.Provider provider, Boolean ready) {
            this.provider = provider;
            this.ready = ready;
        }

        String status() {
            if (provider.off) return "服务商已停用";
            if (Boolean.FALSE.equals(ready)) return "服务商待配置";
            return "服务商已启用";
        }
    }

    static final class Model {
        final Provider source;
        final WorkspaceSnapshot.Model model;
        final String upstream;
        final boolean connectable;

        Model(Provider source, WorkspaceSnapshot.Model model, String upstream, boolean connectable) {
            this.source = source;
            this.model = model;
            this.upstream = upstream;
            this.connectable = connectable;
        }

        String status() {
            if (source.provider.off || Boolean.FALSE.equals(source.ready)) return source.status();
            if (!model.on) return "模型未启用";
            return connectable ? "模型已启用" : "已启用 · 暂不可直接接入";
        }
    }

    static final class Pattern {
        final String text;
        final int matches;

        Pattern(String text, int matches) {
            this.text = text;
            this.matches = matches;
        }
    }

    static final class Group {
        final WorkspaceSnapshot.Group group;
        final List<String> fixed;
        final Set<String> off;
        final List<Pattern> patterns;
        final Map<String, Boolean> memberReady;
        final boolean automatic, decisive, connectable;
        private final Boolean ready;

        Group(WorkspaceSnapshot.Group group, JSONObject entry) throws JSONException {
            this.group = group;
            Set<String> dynamic = new HashSet<>(group.matched);
            List<String> fixed = new ArrayList<>();
            for (String member : group.members) if (!dynamic.contains(member)) fixed.add(member);
            this.fixed = Collections.unmodifiableList(fixed);
            JSONArray off = array(entry, "off");
            Set<String> disabled = new HashSet<>();
            for (int i = 0; i < off.length(); i++) disabled.add(off.getString(i));
            this.off = Collections.unmodifiableSet(disabled);
            JSONArray info = array(entry, "memberInfo");
            Map<String, Boolean> availability = new LinkedHashMap<>();
            for (int i = 0; i < info.length(); i++) {
                JSONObject member = info.getJSONObject(i);
                Boolean ready = flag(member, "ready");
                if (ready != null) availability.put(member.getString("id"), ready);
            }
            memberReady = Collections.unmodifiableMap(availability);
            JSONArray hits = array(entry, "patterns");
            List<Pattern> patterns = new ArrayList<>();
            for (int i = 0; i < hits.length(); i++) {
                JSONObject hit = hits.getJSONObject(i);
                patterns.add(new Pattern(hit.getString("pattern"), hit.getInt("models")));
            }
            if (patterns.isEmpty()) {
                JSONArray rules = array(entry, "match");
                for (int i = 0; i < rules.length(); i++) patterns.add(new Pattern(rules.getString(i), -1));
            }
            this.patterns = Collections.unmodifiableList(patterns);
            automatic = entry.optBoolean("auto") || group.reference.startsWith("auto/");
            decisive = entry.optBoolean("decides");
            ready = flag(entry, "ready");
            connectable = Boolean.TRUE.equals(ready) && !decisive;
        }

        String status() {
            if (decisive) return "判定模型分组";
            if (ready == null) return "接入状态未知";
            return connectable ? "可作为分组接入" : "暂无可路由的成员";
        }

        String strategy() {
            switch (group.routing) {
                case "":
                case "smart": return "智能调度";
                case "rotate": return "轮询";
                case "usage": return "最少使用";
                case "pace": return "均衡用量";
                case "weight": return "加权轮询";
                case "free": return "免费优先";
                case "fast": return "速度优先";
                case "cheap": return "价格优先";
                case "drain": return "用完再换";
                case "expiring": return "免费额度即将到期优先";
                case "manual": return "手动选择";
                default: return group.strategy();
            }
        }

        private boolean sameDetails(Group other) {
            if (automatic != other.automatic || decisive != other.decisive || !Objects.equals(ready, other.ready)
                    || !off.equals(other.off) || !memberReady.equals(other.memberReady)
                    || patterns.size() != other.patterns.size()) return false;
            for (int i = 0; i < patterns.size(); i++) {
                Pattern a = patterns.get(i), b = other.patterns.get(i);
                if (a.matches != b.matches || !a.text.equals(b.text)) return false;
            }
            return true;
        }
    }
}
