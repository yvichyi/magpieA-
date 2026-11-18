package ai.magpie.android;

public final class CatalogLoadTest {
    public static void main(String[] args) throws Exception {
        CatalogData data = CatalogData.parse("{\"providers\":[]}", "{\"groups\":[],\"models\":[]}");
        CatalogLoad.Scope local = new CatalogLoad.Scope("local", "http://127.0.0.1:4000", "local-key");
        CatalogLoad.Scope rerun = new CatalogLoad.Scope("local", "http://127.0.0.1:4001", "local-key");
        CatalogLoad.Scope remote = new CatalogLoad.Scope("remote", "https://example.org", "remote-key");
        CatalogLoad.Scope rotated = new CatalogLoad.Scope("remote", "https://example.org", "rotated-key");
        CatalogLoad.Scope otherWorkspace = new CatalogLoad.Scope("another", "https://example.org", "rotated-key");
        CatalogLoad load = new CatalogLoad();
        check(load.updateScope(local), "Initial scope change was missed");
        check(load.begin() == null, "Inactive catalog starts a request");
        load.setActive(true);
        CatalogLoad.Attempt first = load.begin();
        check(load.begin() == null, "Two requests started in one active scope");
        check(load.complete(first, local, data) && load.data == data, "Fresh result discarded");
        check(!load.updateScope(new CatalogLoad.Scope("local", "http://127.0.0.1:4000", "local-key")),
                "Equivalent scope discards retained data");
        check(load.complete(load.begin(), local, null) && load.data == data && load.failed,
                "Same-scope failure loses retained data or stale indicator");

        CatalogLoad.Attempt earlier = load.begin();
        long beforeRestart = load.scopeVersion;
        check(!load.complete(earlier, rerun, data), "Old listener result accepted after origin changed");
        check(load.updateScope(rerun) && load.scopeVersion > beforeRestart && load.data == null && !load.failed,
                "Listener restart retains old data");
        CatalogLoad.Attempt backgrounded = load.begin();
        load.setActive(false);
        load.setActive(true);
        CatalogLoad.Attempt foregrounded = load.begin();
        check(!load.complete(backgrounded, rerun, data), "Previous active period overwrites resumed view");
        check(load.complete(foregrounded, rerun, data), "Current active period result discarded");
        CatalogLoad.Attempt oldWorkspace = load.begin();
        check(load.updateScope(remote) && load.data == null, "Changing workspace retains old records");
        check(!load.complete(oldWorkspace, remote, data), "Local result enters remote workspace");
        CatalogLoad.Attempt oldKey = load.begin();
        check(!load.complete(oldKey, rotated, data), "Old credentials accepted before rotation is rendered");
        check(load.updateScope(rotated) && load.data == null, "Rotated credentials retain previous data");
        CatalogLoad.Attempt wrongWorkspace = load.begin();
        check(!load.complete(wrongWorkspace, otherWorkspace, data), "Workspace identity was ignored");
        check(load.complete(wrongWorkspace, rotated, data), "New credentials result discarded");
        System.out.println("PASS catalog request lifecycle, same-scope failure, and workspace, origin and credential changes");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
