package ai.magpie.android;

import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Explicit core writes and the evidence that can resolve an interrupted response. */
final class PluginOperation {
    enum Kind { ADD, UPGRADE, OFF, BUILTIN, CONFIG }

    final Kind kind;
    final String target;
    final String name;
    private final String expectedVersion;
    private final String field;
    private final Object value;

    private PluginOperation(Kind kind, String target, String name, String expectedVersion, String field, Object value) {
        this.kind = kind;
        this.target = target;
        this.name = name;
        this.expectedVersion = expectedVersion;
        this.field = field;
        this.value = value;
    }

    static PluginOperation installNpm(String input) {
        String spec = input.trim();
        if (spec.startsWith("npm:")) spec = spec.substring(4);
        String name = PluginSnapshot.packageName(spec);
        String suffix = spec.substring(name.length());
        if (!PluginSnapshot.npmName(name) || (!suffix.isEmpty() && !suffix.matches("@[0-9A-Za-z][0-9A-Za-z._+-]{0,127}"))) {
            throw new IllegalArgumentException("请输入 npm 包名，或使用 包名@版本 / 标签。");
        }
        String version = suffix.isEmpty() ? "" : suffix.substring(1);
        return new PluginOperation(Kind.ADD, "npm:" + spec, spec, version, "", null);
    }

    static PluginOperation install(PluginSnapshot.Discovery entry) {
        if (PluginSnapshot.npmName(entry.spec())) {
            return new PluginOperation(Kind.ADD, "npm:" + entry.spec(), entry.name(), "", "", null);
        }
        if (PluginSnapshot.gitRepo(entry.spec()).isEmpty()) throw new IllegalArgumentException("安装来源无效。");
        return new PluginOperation(Kind.ADD, entry.spec(), entry.name(), "", "", null);
    }

    static PluginOperation upgrade(PluginSnapshot.Entry entry) {
        String expected = entry.npm() ? entry.latest() : entry.head();
        return new PluginOperation(Kind.UPGRADE, entry.spec(), entry.name(), expected, "", null);
    }

    static PluginOperation toggle(PluginSnapshot.Entry entry, boolean off) {
        return new PluginOperation(Kind.OFF, entry.spec(), entry.name(), "", "", off);
    }

    static PluginOperation toggle(PluginSnapshot.Builtin entry, boolean on) {
        return new PluginOperation(Kind.BUILTIN, entry.id(), entry.name(), "", "", on);
    }

    static PluginOperation configure(PluginSnapshot.Builtin entry, PluginSnapshot.Field field, Object value) {
        if (!entry.fields().contains(field) || !field.accepts(value)) throw new IllegalArgumentException("请检查设置值。");
        return new PluginOperation(Kind.CONFIG, entry.id(), entry.name(), "", field.key(), value);
    }

    boolean builtin() { return kind == Kind.BUILTIN || kind == Kind.CONFIG; }

    String path() {
        switch (kind) {
            case ADD: return "/api/plugins/add";
            case UPGRADE: return "/api/plugins/upgrade";
            case OFF: return "/api/plugins/off";
            default: return "/api/plugins/builtin";
        }
    }

    JSONObject body() throws JSONException {
        JSONObject json = new JSONObject().put(builtin() ? "id" : "spec", target);
        switch (kind) {
            case OFF: return json.put("off", value);
            case BUILTIN: return json.put("on", value);
            case CONFIG: return json.put("config", new JSONObject().put(field, value));
            default: return json;
        }
    }

    String action() {
        switch (kind) {
            case ADD: return "安装";
            case UPGRADE: return "更新";
            case OFF: return Boolean.TRUE.equals(value) ? "停用" : "启用";
            case BUILTIN: return Boolean.TRUE.equals(value) ? "启用" : "停用";
            default: return "保存设置";
        }
    }

    long unknownUntil(long startedAt) {
        return startedAt + (kind == Kind.ADD || kind == Kind.UPGRADE ? 310_000L : 30_000L);
    }

    PluginSnapshot.Entry installedEntry(PluginSnapshot.Installed installed) {
        if (installed == null || builtin()) return null;
        if (kind == Kind.OFF) return installed.find(target);
        boolean npmInstall = target.startsWith("npm:");
        String spec = npmInstall ? target.substring(4) : target;
        String repo = PluginSnapshot.gitRepo(spec);
        for (PluginSnapshot.Entry entry : installed.entries()) {
            if (entry.spec().equals(spec)) return entry;
            if (!repo.isEmpty() && repo.equals(PluginSnapshot.gitRepo(entry.spec()))) return entry;
            if (entry.npm() && PluginSnapshot.packageName(spec).equals(PluginSnapshot.packageName(entry.spec()))) return entry;
        }
        return null;
    }

    boolean observed(PluginSnapshot.Installed installed, List<PluginSnapshot.Builtin> builtins) {
        if (builtin()) {
            for (PluginSnapshot.Builtin entry : builtins) if (entry.id().equals(target)) {
                if (kind == Kind.BUILTIN) return entry.broken().isEmpty() && value.equals(entry.chosen());
                for (PluginSnapshot.Field setting : entry.fields()) {
                    if (setting.key().equals(field)) return PluginSnapshot.sameValue(setting.value(), value);
                }
            }
            return false;
        }
        PluginSnapshot.Entry entry = installedEntry(installed);
        if (entry == null) return false;
        switch (kind) {
            case OFF: return value.equals(entry.off());
            case UPGRADE:
                return !expectedVersion.isEmpty() && (entry.npm()
                        ? entry.version().equals(expectedVersion) || PluginSnapshot.newer(entry.version(), expectedVersion)
                        : PluginSnapshot.sameCommit(entry.commit(), expectedVersion));
            case ADD:
                // When npm resolves a tag, its version cannot be reconstructed from the tag alone.
                if (expectedVersion.isEmpty()) return true;
                return PluginSnapshot.isVersion(expectedVersion) &&
                        expectedVersion.replaceFirst("^v", "").equals(entry.version().replaceFirst("^v", ""));
            default: return false;
        }
    }
}
