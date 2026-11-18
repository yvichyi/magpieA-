package ai.magpie.android;

/** Retains data through same-scope read failures and rejects obsolete reads. Used on the UI thread. */
final class CatalogLoad {
    static final class Scope {
        final String workspace, origin, key;

        Scope(String workspace, String origin, String key) {
            this.workspace = workspace;
            this.origin = origin;
            this.key = key;
        }

        boolean matches(Scope other) {
            return other != null && workspace.equals(other.workspace) && origin.equals(other.origin)
                    && key.equals(other.key);
        }

        boolean configured() { return !origin.isEmpty() && !key.isEmpty(); }
    }

    static final class Attempt {
        final Scope scope;
        private Attempt(Scope scope) { this.scope = scope; }
    }

    CatalogData data;
    Scope scope;
    boolean failed;
    long scopeVersion;
    private boolean active;
    private Attempt pending;

    boolean updateScope(Scope current) {
        if (current.matches(scope)) return false;
        scope = current;
        scopeVersion++;
        pending = null;
        data = null;
        failed = false;
        return true;
    }

    void setActive(boolean active) {
        this.active = active;
        if (!active) pending = null;
    }

    void cancel() { pending = null; }

    Attempt begin() {
        if (!active || pending != null || scope == null || !scope.configured()) return null;
        pending = new Attempt(scope);
        return pending;
    }

    boolean complete(Attempt attempt, Scope observed, CatalogData result) {
        if (!active || pending != attempt || attempt == null || !attempt.scope.matches(observed)) return false;
        pending = null;
        failed = result == null;
        if (result != null && !result.sameContent(data)) data = result;
        return true;
    }
}
