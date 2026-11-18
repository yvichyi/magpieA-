package ai.magpie.android;

import android.content.Context;
import java.util.Objects;
import java.util.TimeZone;

/** Captures every dashboard setting that determines what one read can see. */
final class InsightsScope {
    final String workspace, origin, key, zone;
    final boolean ready;

    private InsightsScope(String workspace, String origin, String key, String zone, boolean ready) {
        this.workspace = workspace;
        this.origin = origin;
        this.key = key;
        this.zone = zone;
        this.ready = ready && !origin.isEmpty() && !key.isEmpty();
    }

    static InsightsScope current(Context context) {
        return new InsightsScope(AppNavigation.workspace(context), AppNavigation.origin(context),
                AppNavigation.key(context), TimeZone.getDefault().getID(), AppNavigation.ready(context));
    }

    boolean isCurrent(Context context) {
        InsightsScope now = current(context);
        return ready && now.ready && equals(now);
    }

    boolean sameWorkspace(InsightsScope scope) {
        return workspace.equals(scope.workspace) && origin.equals(scope.origin) && key.equals(scope.key);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof InsightsScope)) return false;
        InsightsScope scope = (InsightsScope) other;
        return sameWorkspace(scope) && zone.equals(scope.zone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspace, origin, key, zone);
    }
}
