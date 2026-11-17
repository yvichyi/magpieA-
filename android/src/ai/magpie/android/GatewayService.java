package ai.magpie.android;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.Process;

/** Owns exactly one bundled gateway; never executes downloaded binaries. */
public final class GatewayService extends Service {
    public static volatile String state = "未启动";
    public static volatile boolean ready = false;
    public static volatile String managementOrigin = "";
    private static String runKey = "";
    private static final StringBuilder log = new StringBuilder();
    private final AtomicBoolean starting = new AtomicBoolean();
    private volatile Process process;
    private final CoreProcess owned = new CoreProcess();
    private volatile boolean stopping;

    public static synchronized String key(Context c) {
        if (runKey.isEmpty()) runKey = CoreConfig.newKey();
        return runKey;
    }

    public static synchronized String logs() { return log.toString(); }
    private static synchronized void record(String line) {
        // Upstream errors may include URLs or account material. Keep diagnostics local
        // and strip query credentials and known API-key shapes before presentation.
        String clean = line.replaceAll("(?i)([?&](?:k|key|token|api_key)=)[^&\\s]+", "$1[已隐藏]")
            .replaceAll("sk-[A-Za-z0-9_-]{8,}", "[密钥已隐藏]")
            .replaceAll("(?i)(Bearer\\s+)[A-Za-z0-9._~-]+", "$1[已隐藏]");
        log.append(clean).append('\n');
        if (log.length() > 16000) log.delete(0, log.length() - 12000);
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("gateway", "本机模型网关", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && "STOP".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        Notification n = notice("本机网关正在启动");
        if (Build.VERSION.SDK_INT >= 34) startForeground(42, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(42, n);
        if (starting.compareAndSet(false, true)) {
            stopping = false;
            new Thread(this::runCore, "magpie-core").start();
        }
        return START_NOT_STICKY;
    }

    private Notification notice(String detail) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, GatewayService.class).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "gateway").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Magpie · 本机网关").setContentText(detail).setContentIntent(open)
            .setOngoing(true).addAction(new Notification.Action.Builder(null, "停止网关", stop).build()).build();
    }

    private void runCore() {
        state = "正在启动"; ready = false; managementOrigin = "";
        synchronized (GatewayService.class) { runKey = CoreConfig.newKey(); }
        try {
            File home = new File(getFilesDir(), "core");
            File config = new File(home, ".config/magpie");
            if (!config.isDirectory() && !config.mkdirs()) throw new IOException("无法创建本机配置目录");
            File downloads = new File(home, "Downloads");
            if (!downloads.isDirectory() && !downloads.mkdirs()) throw new IOException("无法创建备份目录");
            File settings = new File(config, "settings.json");
            if (!settings.exists()) Files.write(settings.toPath(), "{\"lang\":\"zh\",\"noAutoUpdate\":true,\"noUpdatePill\":true}".getBytes(StandardCharsets.UTF_8));
            File executable = new File(getApplicationInfo().nativeLibraryDir, "libmagpie.so");
            if (!executable.isFile()) throw new IOException("安装包没有此设备架构的核心；请安装匹配架构的版本");
            File pluginRuntime = new File(getApplicationInfo().nativeLibraryDir, "libmagpie-bun.so");
            if (!pluginRuntime.isFile()) throw new IOException("安装包缺少插件运行环境，请重新安装完整 APK");
            ProcessBuilder pb = new ProcessBuilder(executable.getAbsolutePath(), "serve", "--web=127.0.0.1:0");
            pb.directory(home).redirectErrorStream(true);
            Map<String, String> env = pb.environment();
            env.put("HOME", home.getAbsolutePath());
            env.put("XDG_CONFIG_HOME", new File(home, ".config").getAbsolutePath());
            env.put("XDG_CACHE_HOME", getCacheDir().getAbsolutePath());
            env.put("XDG_DATA_HOME", new File(home, ".local/share").getAbsolutePath());
            env.put("XDG_STATE_HOME", new File(home, ".local/state").getAbsolutePath());
            env.put("TMPDIR", getCacheDir().getAbsolutePath());
            // Android installs this executable in its read-only native-code directory.
            // Never execute a runtime downloaded into writable app data.
            env.put("MAGPIE_BUN", pluginRuntime.getAbsolutePath());
            env.put("MAGPIE_WEB_KEY", key(this));
            env.put("DO_NOT_TRACK", "1"); env.put("MAGPIE_NO_STATS", "1"); env.put("NO_COLOR", "1");
            // No inherited proxy credentials or desktop paths are introduced here.
            process = owned.start(pb);
            new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line; while ((line = in.readLine()) != null) {
                        String bound = CoreConfig.boundOrigin(line, key(this));
                        if (!bound.isEmpty() && !stopping) managementOrigin = bound;
                        record(line);
                    }
                } catch (IOException ignored) { }
            }, "magpie-log").start();
            for (int i = 0; i < 120 && !stopping && process.isAlive(); i++) {
                if (health() && owned.isAlive() && !stopping) { ready = true; state = "运行中"; break; }
                Thread.sleep(500);
            }
            if (!ready && !stopping) {
                state = "启动失败，请查看诊断";
                record("管理接口未能在 60 秒内就绪。请检查端口占用、设备架构与运行日志。");
                process.destroy();
            }
            if (ready) getSystemService(NotificationManager.class).notify(42, notice("运行中 · 127.0.0.1:3425 · 点击管理"));
            int exit = process.waitFor();
            if (!stopping) { state = "核心已退出（" + exit + "）"; record(state); }
        } catch (Exception e) { state = "启动失败：" + e.getMessage(); record(state); }
        finally { owned.close(); ready = false; managementOrigin = ""; starting.set(false); stopSelf(); }
    }

    private boolean health() {
        HttpURLConnection c = null;
        try {
            String address = managementOrigin;
            if (address.isEmpty() || !owned.isAlive()) return false;
            c = (HttpURLConnection) new URL(address + "/manifest.json").openConnection();
            c.setConnectTimeout(800); c.setReadTimeout(800); c.setInstanceFollowRedirects(false);
            int code = c.getResponseCode();
            return code == 200;
        } catch (IOException e) { return false; }
        finally { if (c != null) c.disconnect(); }
    }

    @Override public void onDestroy() {
        stopping = true; ready = false;
        owned.stop();
        if (!state.startsWith("启动失败") && !state.startsWith("核心已退出")) state = "已停止";
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
