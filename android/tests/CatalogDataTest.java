package ai.magpie.android;

import java.util.List;
import java.util.Set;

public final class CatalogDataTest {
    private static final String PROVIDERS = """
        {"providers":[
          {"id":"relay","name":"工作服务商","ready":true,"off":false,"key":"secret","models":[
            {"id":"vendor/alpha","name":"Alpha","on":true,"by":"upstream"},
            {"id":"beta","on":true},
            {"id":"hidden","on":false}]},
          {"id":"off","ready":true,"off":true,"models":[{"id":"alpha","on":true}]},
          {"id":"setup","ready":false,"off":false,"models":[{"id":"alpha","on":true}]}]}
        """;
    private static final String GROUPS = """
        {"models":[{"id":"relay/vendor/alpha","provider":"relay"}],"groups":[
          {"id":"fallback","ref":"group/fallback","name":"备用","routing":"rotate","ready":true,
           "members":["relay/vendor/alpha","relay/beta:high#work","relay/hidden"],
           "matched":["relay/beta:high#work"],"off":["relay/hidden"],
           "patterns":[{"pattern":"relay/b*","models":1},{"pattern":"elsewhere/*","models":0}]},
          {"id":"auto-alpha","ref":"auto/vendor/alpha","auto":true,"ready":false,"members":[]},
          {"id":"classify","ref":"group/classify","ready":true,"decides":true,"members":["relay/hidden"]}]}
        """;

    public static void main(String[] args) throws Exception {
        CatalogData catalog = CatalogData.parse(PROVIDERS, GROUPS);
        check(catalog.model("relay/vendor/alpha").connectable, "Offered model unavailable");
        check(!catalog.model("relay/beta").connectable, "Enabled model absent from gateway offerings can connect");
        check(!catalog.model("off/alpha").connectable, "Stopped provider model can connect");
        check(!catalog.model("setup/alpha").connectable, "Unconfigured provider model can connect");
        check(catalog.model("relay/vendor/alpha").upstream.equals("upstream"), "Remote source lost");
        check(catalog.models("工作", 1, Set.of(), "").size() == 1, "Available filter disagrees with connect");
        check(catalog.models("", 2, Set.of("relay/hidden"), "").get(0).model.reference.equals("relay/hidden"),
                "Favorites hide disabled models");
        check(catalog.models(" ALPHA ", 0, Set.of(), "relay").size() == 1, "Focused provider leaks other models");
        check(catalog.providers("vendor/alpha").size() == 1, "Provider search loses matches from its catalog");

        CatalogData.Group group = catalog.group("group/fallback");
        check(group.fixed.equals(List.of("relay/vendor/alpha", "relay/hidden")),
                "Dynamic member shown again as a fixed member");
        check(group.group.matched.equals(List.of("relay/beta:high#work")), "Dynamic model reference changed");
        check(group.off.equals(Set.of("relay/hidden")), "Disabled group member lost");
        check(group.patterns.size() == 2 && group.patterns.get(1).matches == 0, "Zero-match pattern lost");
        check(group.strategy().equals("轮询"), "Core rotate policy mislabeled");
        check(catalog.groups("elsewhere/*").size() == 1, "Group search loses zero-match patterns");
        check(group.connectable, "Ready group unavailable");
        check(!catalog.group("group/classify").connectable, "Decision group exposed as client model");
        check(!catalog.group("auto/vendor/alpha").connectable && catalog.group("auto/vendor/alpha").automatic,
                "Derived group readiness or source lost");
        check(catalog.sameContent(CatalogData.parse(PROVIDERS, GROUPS)), "Equal data forces view refresh");
        check(!catalog.sameContent(CatalogData.parse(PROVIDERS, GROUPS.replace("\"ready\":true", "\"ready\":false"))),
                "Readiness change misses view refresh");
        check(!catalog.sameContent(CatalogData.parse(PROVIDERS, GROUPS.replace("elsewhere/*", "another/*"))),
                "Changed member rule misses view refresh");
        System.out.println("PASS catalog references, configuration and availability, fixed and dynamic members, search and favorites");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
