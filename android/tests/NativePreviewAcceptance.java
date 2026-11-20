package ai.magpie.android;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Installed preview smoke test using a real bundled core and loopback-only fixtures. */
public final class NativePreviewAcceptance extends Instrumentation {
    private final AtomicReference<Activity> resumed = new AtomicReference<>();
    private Context target;
    private String fixture, mode, plugin;
    private File screenshots;
    private MainActivity home;
    private CoreApi core;
    private String reference;
    private boolean clipboardVerified;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        fixture = args.getString("fixture", "");
        mode = args.getString("mode", "flow");
        plugin = args.getString("plugin", "magpie-android-preview-fixture");
        start();
    }

    @Override public void onStart() {
        try {
            require(fixture.startsWith("http://127.0.0.1:"), "Loopback fixture only");
            target = getTargetContext();
            screenshots = new File(target.getFilesDir(), "preview-evidence/" + mode);
            require(screenshots.isDirectory() || screenshots.mkdirs(), "Cannot store evidence");
            boolean checkIn = mode.equals("checkin");
            SharedPreferences.Editor prefs = target.getSharedPreferences("host", 0).edit().putBoolean("launched", true)
                    .putBoolean("askedNotifications", true).putBoolean("remote", checkIn);
            if (checkIn) prefs.putString("remoteAddress", fixture).putString("remoteKey", "android-checkin-test-key");
            if (mode.equals("render-benchmark")) {
                Ui.setAppearance(target, Appearance.GLASS);
                prefs.putBoolean("reduceTransparency", false).putBoolean("reduceMotion", false).putBoolean("glassEnabled", true);
            }
            prefs.commit();
            ((Application) target.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity activity) { resumed.set(activity); }
                @Override public void onActivityCreated(Activity activity, Bundle state) {}
                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            });
            home = (MainActivity) startActivitySync(new Intent(target, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            if (!mode.equals("workflow") && !mode.equals("glass") && !mode.equals("themes")
                    && !mode.equals("render-benchmark") && !mode.equals("checkin") && !mode.startsWith("typography")) {
                if (!GatewayService.ready) click("启动网关");
                await(() -> GatewayService.ready, 180_000, "Bundled gateway did not start");
            }
            if (checkIn) {
                testCheckIn();
            } else if (mode.equals("workflow")) {
                testWorkspaceWorkflow();
            } else if (mode.equals("flow")) {
                core = new CoreApi(GatewayService.managementOrigin, GatewayService.key(target));
                require(core.get("/api/providers").contains("android-preview-preserved"), "1.12 provider absent after upgrade");
                stage("UPGRADE_PROVIDER_RETAINED");
                addProvider();
                testConnection();
                createGroup();
                testPlugin();
                testInsights(true);
                testSettings();
                require(clipboardVerified, "Clipboard value was unavailable to the SDK instrumentation");
            } else if (mode.equals("small")) {
                android.content.res.Configuration screen = home.getResources().getConfiguration();
                require(screen.screenWidthDp <= 320 && screen.fontScale >= 1.99f,
                        "Populated narrow checks require 320 dp and double font size");
                singleLine("Magpie");
                capture("01-connect-large");
                click("模型");
                visible("搜索模型");
                singleLine("服务商");
                capture("02-models-large");
                click("插件");
                click("已安装");
                visible("从 npm 安装");
                capture("03-plugins-large");
                testInsights(false);
                click("打开设置");
                waitActivity(ToolsActivity.class);
                capture("08-settings-large");
                back();
            } else if (mode.equals("glass")) {
                testNavigationMotion();
                testGlassBackdrop();
            } else if (mode.equals("render-benchmark")) {
                testRenderBenchmark();
            } else if (mode.equals("themes")) {
                testThemeSelection();
            } else if (mode.startsWith("typography")) {
                testTypography();
            } else if (mode.equals("plugins")) {
                core = new CoreApi(GatewayService.managementOrigin, GatewayService.key(target));
                testPlugin();
            } else if (mode.equals("stats")) {
                capture("04-connection");
                testInsights(true);
                testSettings();
            }
            Bundle result = new Bundle();
            result.putString("stream", "PASS preview " + mode + "\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            try { capture("failure"); } catch (Throwable ignored) {}
            Bundle result = new Bundle();
            result.putString("stream", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void addProvider() throws Exception {
        click("模型");
        click("服务商");
        click("添加服务商");
        saveFixtureProvider("保存并查看模型");
        awaitText(reference, 40_000);
        capture("02-model-directory");
        clickContains(reference);
        awaitText("模型详情", 20_000);
        capture("03-model-detail");
        click("使用此目标接入");
        awaitText(reference, 30_000);
        stage("NATIVE_PROVIDER_FORM_AND_MODEL_DETAIL");
    }

    private void saveFixtureProvider(String saveLabel) throws Exception {
        waitActivity(ProviderSetupActivity.class);
        capture("01-provider-setup");
        fill("服务商地址", fixture + "/v1");
        fill("API Key", "local-fixture");
        String sourceName = "预览测试来源 " + Long.toString(System.currentTimeMillis(), 36);
        fill("名称（可选）", sourceName);
        click("读取模型");
        awaitText("preview-echo", 60_000);
        click("全选");
        click(saveLabel);
        waitActivity(MainActivity.class);
        JSONArray sources = new JSONObject(core.get("/api/providers")).getJSONArray("providers");
        for (int i = 0; i < sources.length(); i++) {
            JSONObject source = sources.getJSONObject(i);
            if (source.optString("name").equals(sourceName)) reference = source.getString("id") + "/preview-echo";
        }
        require(reference != null, "Native form did not save fixture provider");
    }

    private void testWorkspaceWorkflow() throws Exception {
        click("模型");
        capture("01-models-offline");
        assertWorkspaceChooser("home");
        click("插件");
        click("已安装");
        capture("02-plugins-offline");
        assertWorkspaceChooser("plugins");
        click("用量");
        capture("03-usage-offline");
        assertWorkspaceChooser("usage");
        stage("WORKSPACE_CHOOSER_STAYS_IN_CONTEXT");
        click("接入");
        click("连接远程工作区");
        waitAccessibility("保存并连接", 20_000);
        dismissDialog();
        click("启动网关");
        await(() -> GatewayService.ready, 180_000, "Bundled gateway did not start");
        core = new CoreApi(GatewayService.managementOrigin, GatewayService.key(target));
        click("添加服务商");
        saveFixtureProvider("保存并接入");
        awaitText(reference, 40_000);
        require("connect".equals(home.currentRootId()), "Provider save left the connection screen");
        selectProtocol("OpenAI");
        capture("04-connection-ready");
        click("测试调用");
        awaitText("OpenAI 已收到响应", 55_000);
        capture("05-call-success");
        stage("PROVIDER_SAVE_CONTINUES_TO_REAL_MODEL_CALL");
        click("模型");
        awaitText(reference, 40_000);
        capture("06-populated-models");
        clickContains(reference);
        awaitText("模型详情", 20_000);
        capture("07-model-detail");
        back();
        click("用量");
        awaitText("Token 总量", 40_000);
        capture("08-populated-usage");
        click("分析");
        awaitText("Token 构成", 35_000);
        capture("09-populated-analysis");
        click("请求");
        awaitText("条匹配记录", 35_000);
        capture("10-populated-requests");
        clickContains("preview-echo");
        awaitText("请求明细", 30_000);
        capture("11-request-detail");
        back();
        click("打开设置");
        waitActivity(ToolsActivity.class);
        capture("12-settings");
        back();
        stage("POPULATED_MODEL_USAGE_AND_REQUEST_DETAIL");
        target.getSharedPreferences("host", 0).edit().remove("launched").commit();
        home = (MainActivity) startActivitySync(new Intent(target, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitActivity(MainActivity.class);
        ui(() -> {
            View tab = find(decor(), "服务商", false, true);
            require("home".equals(home.currentRootId()) && tab != null && tab.isSelected(),
                    "Provider tab is not selected when launching for the first time");
        });
        stage("FIRST_LAUNCH_OPENS_PROVIDERS");
    }

    private void testCheckIn() throws Exception {
        click("用量");
        click("签到");
        awaitText("验收账号", 40_000);
        capture("01-checkin-account");
        click("签到 验收账号");
        awaitText("签到成功 · +10 积分", 40_000);
        require(!exists("签到 验收账号", false, true), "Already signed in today still offers a repeated write");
        click("参与自动签到");
        await(() -> find(decor(), "参与自动签到", false, true) instanceof Switch toggle && !toggle.isChecked(),
                30_000, "Per-account switch did not reflect the saved setting");
        capture("02-checkin-saved");
        back();
        click("打开设置");
        waitActivity(ToolsActivity.class);
        click("打开签到");
        waitActivity(MainActivity.class);
        awaitText("验收账号", 40_000);
        await(() -> find(decor(), "参与自动签到", false, true) instanceof Switch toggle && !toggle.isChecked(),
                30_000, "Reopened account did not reflect the saved setting");
        stage("CHECKIN_FROM_USAGE_AND_SETTINGS");
    }

    private void assertWorkspaceChooser(String root) throws Exception {
        click("连接工作区");
        waitAccessibility("连接远程 Magpie", 20_000);
        require(root.equals(home.currentRootId()), "Workspace chooser switched away from " + root);
        dismissDialog();
    }

    private void dismissDialog() throws Exception {
        require(getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK),
                "Could not close the workspace dialog");
        await(() -> decor().hasWindowFocus(), 20_000, "Workspace dialog did not close");
    }

    private void testConnection() throws Exception {
        selectProtocol("OpenAI");
        capture("04-connection");
        click("复制接入配置");
        try {
            await(() -> {
                ClipboardManager clipboard = home.getSystemService(ClipboardManager.class);
                ClipData data = clipboard.getPrimaryClip();
                if (data == null) return false;
                try {
                    JSONObject config = new JSONObject(data.getItemAt(0).coerceToText(home).toString());
                    return config.optString("model").equals(reference) && config.optString("api_key").equals("magpie")
                            && config.optString("base_url").endsWith("/v1");
                } catch (org.json.JSONException invalid) {
                    return false;
                }
            }, 6000, "SDK clipboard read unavailable after paste");
            clipboardVerified = true;
        } catch (AssertionError unavailable) {
            AtomicReference<String> trace = new AtomicReference<>("");
            ui(() -> {
                ClipboardManager cb = home.getSystemService(ClipboardManager.class);
                ClipData data = cb.getPrimaryClip();
                CharSequence value = data == null ? "" : data.getItemAt(0).coerceToText(home);
                trace.set("present=" + (data != null) + " size=" + value.length() + " focused=" + decor().hasWindowFocus()
                        + " emittedConfig=" + value.toString().contains("base_url"));
            });
            stage("CLIPBOARD_READ_CHECK " + trace.get());
        }
        for (String protocol : new String[]{"OpenAI", "Anthropic", "Gemini"}) {
            if (!protocol.equals("OpenAI")) selectProtocol(protocol);
            click("测试调用");
            awaitText(protocol + " 已收到响应", 55_000);
        }
        capture("05-connection-test");
        stage("THREE_ACTUAL_PROTOCOLS clipboard=" + clipboardVerified);
    }

    private void selectProtocol(String protocol) throws Exception {
        clickContains("切换接口协议");
        accessibilityClick(protocol, 20_000);
        await(() -> decor().hasWindowFocus(), 20_000, "Protocol dialog did not close");
    }

    private void createGroup() throws Exception {
        click("模型");
        if (exists("返回上一页", false, true)) back();
        click("分组");
        click("新建分组");
        String groupName = "预览验证分组 " + Long.toString(System.currentTimeMillis(), 36);
        fill("分组名称", groupName);
        clickContains(reference);
        capture("06-group-create");
        click("保存分组");
        await(() -> !exists("保存分组", false, false), 45_000, "Group form did not finish saving");
        awaitText(groupName, 45_000);
        clickContains(groupName);
        awaitText("固定成员", 30_000);
        awaitText(reference, 20_000);
        capture("07-group-detail");
        back();
        stage("NATIVE_GROUP_SAVE_AND_MEMBERS");
    }

    private void testPlugin() throws Exception {
        click("插件");
        click("已安装");
        awaitText("Bun", 45_000);
        capture("08-plugins");
        click("从 npm 安装");
        waitAccessibility("从 npm 安装", 12_000);
        accessibilityFill(plugin);
        accessibilityClick("安装", 15_000);
        awaitText("安装完成", 330_000);
        capture("09-plugin-installed");
        clickContains(plugin);
        awaitText("停用插件", 30_000);
        capture("10-plugin-detail");
        click("停用插件");
        accessibilityClick("停用", 20_000);
        awaitText("启用插件", 40_000);
        click("启用插件");
        accessibilityClick("启用", 20_000);
        awaitText("停用插件", 40_000);
        stage("REAL_NPM_INSTALL_ENABLE_DISABLE");
        back();
        localGet("/_advance");
        click("检查更新");
        awaitText("有更新", 75_000);
        clickContains(plugin);
        click("更新插件");
        accessibilityClick("更新", 20_000);
        awaitText("更新完成", 330_000);
        require(core.get("/api/plugins").contains("1.0.2"), "Plugin update not confirmed by core");
        capture("11-plugin-updated");
        back();
        stage("REAL_PLUGIN_UPGRADE");
    }

    private void testInsights(boolean details) throws Exception {
        click("用量");
        awaitText("Token 总量", 40_000);
        capture("12-usage-overview");
        click("分析");
        awaitText("Token 构成", 35_000);
        capture("13-usage-analysis");
        click("日历");
        awaitText("上个月", 35_000);
        capture("14-usage-calendar");
        click("请求");
        awaitText("条匹配记录", 35_000);
        capture("15-requests");
        if (!details) return;
        clickContains("preview-echo");
        awaitText("请求明细", 30_000);
        capture("16-request-detail");
        back();
        awaitText("条匹配记录", 20_000);
        click("导出筛选结果 CSV");
        accessibilityClick("Save", "保存", 40_000);
        stage("LEDGER_DRILLDOWN_AND_DOCUMENT_EXPORT");
        await(() -> resumed.get() == home, 25_000, "Export returned to another screen");
    }

    private void testSettings() throws Exception {
        click("打开设置");
        waitActivity(ToolsActivity.class);
        capture("17-settings");
        back();
        waitActivity(MainActivity.class);
        awaitText("条匹配记录", 30_000);
        stage("SETTINGS_RETURN_PRESERVES_REQUEST_LIST");
    }

    private void testTypography() throws Exception {
        waitActivity(MainActivity.class);
        ui(() -> {
            Ui ui = home.ui();
            boolean compact = mode.equals("typography-small");
            if (compact) {
                android.content.res.Configuration config = home.getResources().getConfiguration();
                require(config.fontScale >= 1.99f && config.screenWidthDp <= 320,
                        "Compact acceptance needs 320 dp and 200 percent font scaling");
            }
            int width = ui.dp(compact ? 280 : 354);
            int exact = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
            int unbounded = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            String title = "多语言模型与长上下文 Multilingual Reasoning and Vision";
            String id = "preview-provider/reasoning-model-with-a-long-version-id-2026-11-18";
            View row = CatalogViews.row(ui, title, id, "模型已启用", () -> {});
            row.measure(exact, unbounded);
            row.layout(0, 0, width, row.getMeasuredHeight());
            ViewGroup levels = (ViewGroup) ((ViewGroup) row).getChildAt(0);
            TextView name = (TextView) levels.getChildAt(0);
            TextView reference = (TextView) levels.getChildAt(2);
            require(name.getLineCount() <= 2 && reference.getLineCount() == 1, "Directory row line limits");
            require(row.getContentDescription().toString().contains(id), "Full model identifier must stay available to accessibility");

            TextView count = ui.text(compact ? "123,456,789,012" : "128,560", Ui.Type.METRIC);
            TextView calls = ui.text("2,032", Ui.Type.METRIC);
            AdaptivePair metrics = new AdaptivePair(ui, count, calls, 14, true);
            metrics.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            metrics.measure(exact, unbounded);
            metrics.layout(0, 0, width, metrics.getMeasuredHeight());
            require(metrics.getOrientation() == (compact ? android.widget.LinearLayout.VERTICAL : android.widget.LinearLayout.HORIZONTAL),
                    "Metric layout must keep a separate column when the two values cannot fit");
            require((compact ? calls.getTop() - count.getBottom() : calls.getLeft() - count.getRight()) >= ui.dp(14),
                    "Measured metrics must leave room between their text regions");
            require(count.getRight() <= width && calls.getRight() <= width, "Metrics extend past the content width");
        });
        capture("01-connection");
        click("模型");
        capture("02-models");
        click("插件");
        capture("03-plugins");
        click("用量");
        capture("04-usage");
        click("打开设置");
        waitActivity(ToolsActivity.class);
        capture("05-settings");
        stage("DIRECTORY_AND_METRICS_FIT_" + mode);
    }

    private void testThemeSelection() throws Exception {
        waitActivity(MainActivity.class);
        ui(() -> require(glassFrame().getBackground() instanceof MaterialBackdrop,
                "Glass appearance must use the continuous material background"));
        capture("01-glass-connection");
        click("模型");
        click("服务商");
        changeTheme(Appearance.SWISS);
        ui(() -> {
            require("home".equals(home.currentRootId()), "Theme change lost the selected root");
            View tab = find(decor(), "服务商", false, true);
            require(tab != null && tab.isSelected(), "Theme change lost the provider tab");
            require(glassFrame().getBackground() instanceof android.graphics.drawable.ColorDrawable,
                    "Swiss appearance must use a flat reading surface");
        });
        capture("02-swiss-models");
        click("插件");
        capture("03-swiss-plugins");
        click("用量");
        capture("04-swiss-usage");
        click("模型");
        changeTheme(Appearance.GLASS);
        ui(() -> require(glassFrame().getBackground() instanceof MaterialBackdrop,
                "Returning to glass must restore the gradient"));
        stage("THEMES_SWITCH_BOTH_WAYS_AND_KEEP_PROVIDER_TAB");
    }

    private void changeTheme(Appearance theme) throws Exception {
        MainActivity previous = home;
        click("打开设置");
        waitActivity(ToolsActivity.class);
        Activity settings = resumed.get();
        click("选择界面主题");
        accessibilityClick(theme.label, 20_000);
        await(() -> resumed.get() instanceof ToolsActivity && resumed.get() != settings
                && decor().hasWindowFocus(), 45_000, "Settings did not adopt the theme");
        if (theme == Appearance.SWISS) capture("05-swiss-settings");
        back();
        await(() -> resumed.get() instanceof MainActivity && resumed.get() != previous
                && decor().hasWindowFocus(), 45_000, "Workspace did not adopt the theme");
        home = (MainActivity) resumed.get();
        require(Ui.appearance(target) == theme, "Theme preference did not persist");
    }

    private void testNavigationMotion() throws Exception {
        ui(() -> {
            target.getSharedPreferences("host", 0).edit().putBoolean("reduceMotion", false)
                    .putBoolean("reduceTransparency", false).putBoolean("glassEnabled", true).commit();
            home.openRoot("connect");
        });
        waitActivity(MainActivity.class);
        await(() -> visibleDock().getWidth() > 0, 25_000, "Navigation dock did not lay out");
        ui(() -> {
            require(UiMotion.enabled(home), "System animation scale is zero");
            GlassNavigation before = visibleDock();
            before.getChildAt(1).performClick();
            require(visibleDock() == before, "A tab switch replaced the moving dock");
            require(Math.abs(currentPage().getTranslationX()) >= home.ui().dp(40),
                    "Root arrived without a visible slide");
            require(currentPage().getAlpha() == 1, "Full-page alpha needs an extra compositing layer");
        });
        await(() -> currentPage().getTranslationX() == 0 && currentPage().getAlpha() == 1,
                10_000, "Tab transition did not settle");
        capture("21-models-glass");
        ui(() -> {
            target.getSharedPreferences("host", 0).edit().putBoolean("reduceMotion", true).commit();
            visibleDock().getChildAt(2).performClick();
            require(currentPage().getTranslationX() == 0 && currentPage().getAlpha() == 1,
                    "Reduced motion still moves the page");
        });
        capture("22-plugins-glass");
        stage("RETAINED_DOCK_AND_VISIBLE_TAB_TRANSITION");
    }

    private void testRenderBenchmark() throws Exception {
        waitActivity(MainActivity.class);
        ui(() -> {
            require(decor().isHardwareAccelerated(), "Frame metrics need a hardware canvas");
            require(UiMotion.enabled(home), "Frame benchmark requires motion enabled");
            home.openRoot("connect");
        });
        switchBenchmarkTabs(4);
        List<Long> timings = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger droppedReports = new AtomicInteger();
        Window window = home.getWindow();
        HandlerThread collector = new HandlerThread("magpie-frame-metrics");
        collector.start();
        Window.OnFrameMetricsAvailableListener listener = (owner, frame, dropped) -> {
            long duration = frame.getMetric(FrameMetrics.TOTAL_DURATION);
            if (duration > 0) timings.add(duration);
            droppedReports.addAndGet(dropped);
        };
        ui(() -> window.addOnFrameMetricsAvailableListener(listener, new Handler(collector.getLooper())));
        try {
            switchBenchmarkTabs(16);
        } finally {
            ui(() -> window.removeOnFrameMetricsAvailableListener(listener));
            collector.quitSafely();
            collector.join(5_000);
        }
        var frames = new ArrayList<>(timings);
        Collections.sort(frames);
        require(frames.size() >= 16, "Insufficient frame samples");
        long median = frames.get(frames.size() / 2);
        long p90 = frames.get((int) Math.ceil(frames.size() * .9) - 1);
        long overBudget = frames.stream().filter(frame -> frame > 16_666_667).count();
        stage("FRAME_TIMINGS count=" + frames.size() + " median_ns=" + median + " p90_ns=" + p90
                + " over_16ms=" + overBudget + " undelivered_reports=" + droppedReports.get());
    }

    private void switchBenchmarkTabs(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            int tab = (i + 1) % 2;
            ui(() -> visibleDock().getChildAt(tab).performClick());
            Thread.sleep(400);
        }
    }

    private GlassFrame glassFrame() {
        return (GlassFrame) ((ViewGroup) home.findViewById(android.R.id.content)).getChildAt(0);
    }

    private GlassNavigation visibleDock() {
        return (GlassNavigation) ((ViewGroup) glassFrame().getChildAt(1)).getChildAt(0);
    }

    private View currentPage() {
        return ((ViewGroup) glassFrame().getChildAt(0)).getChildAt(0);
    }

    private void testGlassBackdrop() throws Exception {
        AtomicReference<GlassFrame> frame = new AtomicReference<>();
        AtomicReference<View> source = new AtomicReference<>();
        ui(() -> {
            target.getSharedPreferences("host", 0).edit().putBoolean("reduceTransparency", false)
                    .putBoolean("reduceMotion", true).putBoolean("glassEnabled", true).commit();
            GlassFrame glass = new GlassFrame(home);
            View scene = new View(home);
            scene.setBackgroundColor(0xff278748);
            glass.setScene(scene);
            glass.setDock(new View(home));
            View toolbar = new View(home);
            toolbar.setMinimumHeight(54);
            glass.setToolbar(toolbar);
            home.setContentView(glass);
            frame.set(glass);
            source.set(scene);
        });
        await(() -> frame.get().getChildAt(1).getHeight() >= 48 && frame.get().getChildAt(2).getHeight() >= 48,
                20_000, "Both glass surfaces must lay out");
        Bitmap first = screenshot();
        int[] dockCenter = new int[2], toolbarCenter = new int[2];
        ui(() -> {
            center(frame.get().getChildAt(1), dockCenter);
            center(frame.get().getChildAt(2), toolbarCenter);
            source.get().setBackgroundColor(0xff3d46b8);
        });
        Bitmap second = screenshot();
        boolean dockChanged = pixel(first, dockCenter) != pixel(second, dockCenter);
        boolean toolbarChanged = pixel(first, toolbarCenter) != pixel(second, toolbarCenter);
        first.recycle();
        second.recycle();
        java.lang.reflect.Field mode = GlassFrame.class.getDeclaredField("mode");
        mode.setAccessible(true);
        require(mode.get(frame.get()) == GlassPolicy.Mode.REFRACT, "AGSL is unavailable under current SDK renderer settings");
        require(dockChanged && toolbarChanged, "Each lens must respond to the changing native backdrop");
        target.getSharedPreferences("host", 0).edit().putBoolean("reduceMotion", false).commit();
        stage("BOTH_LENSES_REFRACT_WITH_REDUCED_MOTION");
    }

    private static void center(View view, int[] point) {
        view.getLocationOnScreen(point);
        point[0] += view.getWidth() / 2;
        point[1] += view.getHeight() / 2;
    }

    private static int pixel(Bitmap bitmap, int[] point) { return bitmap.getPixel(point[0], point[1]); }

    private void localGet(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(fixture + path).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        try { require(connection.getResponseCode() == 200, "Fixture update failed"); }
        finally { connection.disconnect(); }
    }

    private void waitActivity(Class<?> type) throws Exception {
        await(() -> resumed.get() != null && type.isInstance(resumed.get()) && decor().hasWindowFocus(), 45_000, "Expected " + type.getSimpleName());
    }

    private View decor() { return resumed.get().getWindow().getDecorView(); }

    private void back() { ui(() -> resumed.get().onBackPressed()); }

    private void click(String label) throws Exception { click(label, false); }
    private void clickContains(String label) throws Exception { click(label, true); }

    private void click(String label, boolean partial) throws Exception {
        await(() -> exists(label, partial, true), 45_000, "Control unavailable: " + label);
        ui(() -> {
            View action = find(decor(), label, partial, true);
            require(action != null, "Control moved: " + label);
            action.requestRectangleOnScreen(new Rect(0, 0, action.getWidth(), action.getHeight()), true);
            action.performClick();
        });
    }

    private void visible(String label) throws Exception {
        await(() -> {
            View view = find(decor(), label, false, false);
            Rect rect = new Rect();
            return view != null && view.getGlobalVisibleRect(rect) && rect.height() >= view.getHeight() - 1;
        }, 25_000, "Clipped control: " + label);
    }

    private void singleLine(String label) throws Exception {
        visible(label);
        ui(() -> {
            View view = find(decor(), label, false, false);
            require(view instanceof TextView && ((TextView) view).getLineCount() == 1,
                    "Wrapped label: " + label);
        });
    }

    private void fill(String description, String value) throws Exception {
        await(() -> input(decor(), description) != null, 25_000, "Input missing: " + description);
        ui(() -> input(decor(), description).setText(value));
    }

    private static EditText input(View view, String description) {
        if (view instanceof EditText && description.equals(view.getContentDescription())) return (EditText) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = input(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void awaitText(String label, long timeout) throws Exception {
        await(() -> exists(label, true, false), timeout, "Missing visible text: " + label);
    }

    private boolean exists(String label, boolean partial, boolean clickable) {
        return resumed.get() != null && find(decor(), label, partial, clickable) != null;
    }

    private static View find(View view, String value, boolean partial, boolean clickable) {
        if (view.getVisibility() != View.VISIBLE) return null;
        CharSequence label = view.getContentDescription();
        CharSequence text = view instanceof TextView ? ((TextView) view).getText() : null;
        if (matches(label, value, partial) || matches(text, value, partial)) {
            View selected = view;
            while (clickable && !selected.isClickable() && selected.getParent() instanceof View) selected = (View) selected.getParent();
            if (!clickable || (selected.isClickable() && selected.isEnabled())) return selected;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = find(group.getChildAt(i), value, partial, clickable);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static boolean matches(CharSequence text, String value, boolean partial) {
        return text != null && (partial ? text.toString().contains(value) : value.contentEquals(text));
    }

    private void accessibilityClick(String label, long timeout) throws Exception {
        accessibilityClick(label, label, timeout);
    }

    private void accessibilityClick(String first, String second, long timeout) throws Exception {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < end) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) for (String label : new String[]{first, second}) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
                    if (node.getText() != null && label.equalsIgnoreCase(node.getText().toString())
                            && node.isEnabled() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return;
                }
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Dialog action unavailable: " + first);
    }

    private void accessibilityFill(String value) throws Exception {
        long end = SystemClock.elapsedRealtime() + 15_000;
        while (SystemClock.elapsedRealtime() < end) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo editable = editable(root);
            if (editable != null) {
                Bundle text = new Bundle();
                text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
                if (editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Plugin dialog package input missing");
    }

    private static AccessibilityNodeInfo editable(AccessibilityNodeInfo node) {
        if (node == null || !node.isVisibleToUser()) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = editable(node.getChild(i));
            if (child != null) return child;
        }
        return null;
    }

    private void waitAccessibility(String label, long timeout) throws Exception {
        long end = SystemClock.elapsedRealtime() + timeout;
        while (SystemClock.elapsedRealtime() < end) {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null && !root.findAccessibilityNodeInfosByText(label).isEmpty()) return;
            Thread.sleep(200);
        }
        throw new AssertionError("Dialog missing: " + label);
    }

    private Bitmap screenshot() throws Exception {
        getUiAutomation().waitForIdle(250, 10_000);
        Thread.sleep(900);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        require(bitmap != null, "Screenshot unavailable");
        return bitmap;
    }

    private void capture(String name) throws Exception {
        Bitmap bitmap = screenshot();
        try (FileOutputStream out = new FileOutputStream(new File(screenshots, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { bitmap.recycle(); }
    }

    private void stage(String name) {
        Bundle trace = new Bundle();
        trace.putString("stream", name + "\n");
        sendStatus(1, trace);
    }

    private void ui(Runnable action) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try { action.run(); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private void await(BooleanSupplier condition, long timeout, String failure) throws Exception {
        long end = SystemClock.elapsedRealtime() + timeout;
        AtomicReference<Boolean> accepted = new AtomicReference<>(false);
        do {
            ui(() -> accepted.set(condition.getAsBoolean()));
            if (accepted.get()) return;
            Thread.sleep(160);
        } while (SystemClock.elapsedRealtime() < end);
        throw new AssertionError(failure);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
