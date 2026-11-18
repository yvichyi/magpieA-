package ai.magpie.android;

import java.util.List;
import org.json.JSONObject;

public final class PluginSnapshotTest {
    public static void main(String[] args) throws Exception {
        var installed = PluginSnapshot.installed("""
                {"bun":true,"bunVersion":"1.3.0","registry":{"url":"https://npm.example/"},"plugins":[
                  {"spec":"@demo/plugin@1.2.0","ownName":"Demo","off":true,"version":"1.2.0","latest":"1.2.1",
                   "options":{"token":"private"},"providers":["Acme"],"page":{"title":"Panel","reads":["usage:summary"]}},
                  {"spec":"github:owner/repo","package":"repo-plugin","version":"4.0.0","commit":"abc123","head":"abc123456"},
                  {"spec":"/tmp/name@sample","version":"0.1.0","latest":"1.0.0","error":"Startup failed"}
                ]}
                """);
        var plugin = installed.entries().get(0);
        check(installed.bun() && installed.registry().equals("https://npm.example/"), "runtime and registry");
        check(plugin.off() && plugin.configurable() && plugin.name().equals("Demo"), "installed entry identity");
        check(plugin.providers().equals(List.of("Acme")) && plugin.pageReads().equals(List.of("usage:summary")), "plugin capabilities");
        check(plugin.updateAvailable(), "semver update");
        check(!installed.entries().get(1).updateAvailable(), "short and long git commit identify same update");
        check(!installed.entries().get(2).updateAvailable(), "paths cannot be upgraded on npm");
        check(!installed.toString().contains("private"), "options are not retained outside their editor");
        check(!PluginSnapshot.newer("1.2.0-beta.2", "1.2.0-beta.10"), "numeric prerelease ordering");
        check(PluginSnapshot.newer("1.2.0", "1.2.0-rc.1"), "stable after prerelease");
        check(!PluginSnapshot.newer("1.2.0+build2", "1.2.0+build1"), "build metadata never an update");

        var listings = PluginSnapshot.discovery("""
                {"listings":[{"package":"@demo/plugin","name":"Demo","community":true,"kind":"page",
                  "summary":{"zh":"介绍","en":"Summary"},"npm":{"version":"1.2.1"}}]}
                """, PluginSnapshot.Feed.LISTINGS);
        var found = listings.entries().get(0);
        check(found.description().equals("介绍") && found.version().equals("1.2.1"), "translated catalog fields");
        check(installed.match(found) == plugin, "pinned npm entry matches discovery package");
        var tagged = PluginSnapshot.discovery("""
                {"repos":[{"spec":"github:owner/repo","repo":"owner/repo","package":"repo-plugin","stars":5}],"error":"partial"}
                """, PluginSnapshot.Feed.GITHUB);
        check(tagged.error().equals("partial") && installed.match(tagged.entries().get(0)) != null, "partial GitHub response and Git identity");
        var search = PluginSnapshot.discovery("""
                {"hits":[{"package":"external-plugin","description":"Registry metadata","version":"2.0.0","publisher":"author"}]}
                """, PluginSnapshot.Feed.SEARCH);
        check(search.entries().get(0).version().equals("2.0.0"), "search metadata is flattened");
        var pages = PluginSnapshot.pages("""
                [{"spec":"@demo/plugin@1.2.0","key":"plugin-demo","title":"Demo panel","titles":{"zh":"演示"}},
                 {"spec":"other","key":"unexpected","title":"Invalid"}]
                """);
        check(pages.size() == 1 && pages.get(0).title().equals("演示"), "only core plugin page routes are exposed");

        var builtins = PluginSnapshot.builtins("""
                [{"id":"gateway","name":"Gateway page","on":true,"chosen":true,"state":"active","inUse":1,
                  "schema":{"type":"object","x-order":["callsPage","show"],"properties":{
                     "callsPage":{"type":"integer","title":"Calls","default":20,"enum":[10,20,50]},
                     "show":{"type":"boolean","title":"Show","default":true}}},"config":{"callsPage":50}},
                 {"id":"future","name":"Future","on":true,"chosen":true,
                   "schema":{"type":"object","properties":{"list":{"type":"array","default":[]}}}},
                 {"id":"broken","name":"Broken","on":true,"broken":"invalid yaml"}]
                """);
        var gateway = builtins.get(0);
        check(!gateway.canToggle() && gateway.fields().get(1).value().equals(true), "locked runtime and schema default");
        check(gateway.fields().get(0).value().equals(50), "schema with effective config");
        check(builtins.get(1).advancedConfig() && builtins.get(1).fields().isEmpty(), "schema fallback");
        check(!builtins.get(2).canToggle(), "broken config cannot be toggled");

        var add = PluginOperation.installNpm("  @demo/plugin@1.2.0 ");
        check(add.path().equals("/api/plugins/add") && add.body().getString("spec").equals("npm:@demo/plugin@1.2.0"), "explicit npm spec");
        check(add.observed(installed, builtins), "installed pinned version proves mutation");
        check(!PluginOperation.installNpm("@demo/plugin@1.2.1").observed(installed, builtins), "different pinned version does not prove mutation");
        check(PluginOperation.install(tagged.entries().get(0)).body().getString("spec").equals("github:owner/repo"), "GitHub discovery uses actual core install spec");
        expectBad(() -> PluginOperation.installNpm("https://example.test/evil.tgz"));
        expectBad(() -> PluginOperation.installNpm("--foo"));
        var disable = PluginOperation.toggle(plugin, true);
        check(disable.path().equals("/api/plugins/off") && disable.body().getBoolean("off") && disable.observed(installed, builtins), "off uses exact spec and observed state");
        check(!PluginOperation.toggle(plugin, false).observed(installed, builtins), "requested state is not inferred from existence");
        check(!PluginOperation.upgrade(plugin).observed(installed, builtins), "current version does not satisfy upgrade");
        var config = PluginOperation.configure(gateway, gateway.fields().get(0), 50);
        JSONObject payload = config.body();
        check(config.path().equals("/api/plugins/builtin") && payload.length() == 2 && payload.getJSONObject("config").getInt("callsPage") == 50,
                "config sends a single typed property");
        check(config.observed(installed, builtins), "effective config can reconcile a write");
        expectBad(() -> PluginOperation.configure(gateway, gateway.fields().get(0), "50"));
        expectBad(() -> PluginOperation.configure(gateway, gateway.fields().get(0), 12));
        check(PluginOperation.installNpm("plain@latest").unknownUntil(1000) == 311000, "install uncertainty follows core deadline");
        check(disable.unknownUntil(1000) == 31000, "non-install writes use short grace");
        System.out.println("PluginSnapshotTest passed");
    }

    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
    }

    private static void expectBad(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected invalid input");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
