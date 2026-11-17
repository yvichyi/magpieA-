package ai.magpie.android;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** Serializes launch against Stop, including Stop arriving before the child exists. */
final class CoreProcess {
    private Process child;
    private boolean stopped;
    synchronized Process start(ProcessBuilder builder) throws IOException {
        if (stopped) throw new IOException("网关启动已取消");
        child = builder.start();
        return child;
    }
    synchronized boolean isAlive() { return child != null && child.isAlive(); }
    synchronized void stop() { stopped = true; if (child != null && child.isAlive()) child.destroy(); }
    void close() {
        stop();
        Process p;
        synchronized (this) { p = child; }
        if (p == null) return;
        try { if (!p.waitFor(3, TimeUnit.SECONDS)) { p.destroyForcibly(); p.waitFor(3, TimeUnit.SECONDS); } }
        catch (InterruptedException e) { p.destroyForcibly(); Thread.currentThread().interrupt(); }
    }
}
