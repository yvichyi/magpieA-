package ai.magpie.android;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** One active reader shared by the Catalog root and its detail stack. */
final class CatalogRepository {
    final CatalogLoad load = new CatalogLoad();
    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2, job -> new Thread(job, "magpie-catalog"));
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (observer == null) return;
            pulse(false);
            handler.postDelayed(this, 1000);
        }
    };
    private Runnable observer;
    private Future<?> fetch;
    private boolean ready;
    private long nextRefresh;

    CatalogRepository(Activity activity) { this.activity = activity; }

    CatalogLoad.Scope observedScope() {
        try {
            return new CatalogLoad.Scope(AppNavigation.workspace(activity), AppNavigation.origin(activity),
                    AppNavigation.key(activity));
        } catch (IllegalArgumentException invalidRemote) {
            return new CatalogLoad.Scope("remote", "", "");
        }
    }

    void resume(Runnable observer) {
        resume(observer, true);
    }

    void resume(Runnable observer, boolean refresh) {
        this.observer = observer;
        load.setActive(true);
        pulse(refresh, true);
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, 1000);
    }

    void pause(Runnable observer) {
        if (this.observer != observer) return;
        this.observer = null;
        load.setActive(false);
        handler.removeCallbacks(poll);
        cancelFetch();
    }

    void dispose() {
        pause(observer);
        executor.shutdownNow();
    }

    void refresh() {
        if (observer != null) pulse(true);
    }

    boolean ready() { return ready; }
    boolean loading() { return fetch != null; }

    boolean current(long scopeVersion, boolean requireReady) {
        if (observer == null || activity.isFinishing() || activity.isDestroyed()) return false;
        CatalogLoad.Scope observed = observedScope();
        if (!observed.matches(load.scope) || AppNavigation.ready(activity) != ready && observed.configured()) {
            pulse(false);
        }
        return load.scopeVersion == scopeVersion && (!requireReady || ready);
    }

    private void pulse(boolean refresh) {
        pulse(refresh, false);
    }

    private void pulse(boolean refresh, boolean notify) {
        CatalogLoad.Scope observed = observedScope();
        boolean connected = AppNavigation.ready(activity) && observed.configured();
        boolean changed = load.updateScope(observed) || ready != connected;
        if (changed) {
            load.cancel();
            cancelFetch();
        }
        ready = connected;
        if (refresh || changed) nextRefresh = 0;
        if (ready && SystemClock.elapsedRealtime() >= nextRefresh && fetch == null) request();
        else if (changed || refresh || notify) observer.run();
    }

    private void request() {
        CatalogLoad.Attempt attempt = load.begin();
        if (attempt == null) return;
        fetch = executor.submit(() -> {
            CatalogData result = null;
            try {
                CoreApi api = new CoreApi(attempt.scope.origin, attempt.scope.key);
                String providers = api.get("/api/providers");
                if (Thread.currentThread().isInterrupted()) return;
                result = CatalogData.parse(providers, api.get("/api/groups"));
            } catch (Exception readFailure) {
                // The view offers a scoped retry. Raw API errors can contain private configuration.
            }
            CatalogData received = result;
            handler.post(() -> complete(attempt, received));
        });
        observer.run();
    }

    private void complete(CatalogLoad.Attempt attempt, CatalogData data) {
        if (observer == null || activity.isFinishing() || activity.isDestroyed()) return;
        CatalogLoad.Scope observed = observedScope();
        if (!observed.matches(load.scope) || !AppNavigation.ready(activity)) {
            pulse(false);
            return;
        }
        if (!load.complete(attempt, observed, data)) return;
        fetch = null;
        nextRefresh = SystemClock.elapsedRealtime() + 20_000;
        observer.run();
    }

    private void cancelFetch() {
        if (fetch != null) fetch.cancel(true);
        fetch = null;
    }
}
