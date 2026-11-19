package ai.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.json.JSONObject;

/** The complete connection task, with explicit test dispatch and workspace-scoped results. */
final class ConnectScreen implements AppScreen {
    private static final String[] PROTOCOLS = {"OpenAI", "Anthropic", "Gemini"};
    private final ScreenHost host;
    private final Activity activity;
    private final Ui ui;
    private final SharedPreferences preferences;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScrollView scroll;
    private final LinearLayout body;
    private final TextView status;
    private final LinearLayout configuration;
    private CatalogData catalog;
    private Scope loadedScope;
    private boolean active, reading, testing, readFailed;
    private int generation;
    private int protocol;
    private String reference;
    private String result = "";
    private boolean successfulTest;

    private record AppearanceState(CatalogData catalog, String result, boolean successful, int scroll) {}

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active) return;
            updateStatus();
            if (catalog == null && !readFailed) load();
            main.postDelayed(this, 1000);
        }
    };

    private static final class Scope {
        final String workspace, origin, key;
        Scope(Activity activity) {
            workspace = AppNavigation.workspace(activity);
            origin = AppNavigation.origin(activity);
            key = AppNavigation.key(activity);
        }
        boolean matches(Scope other) {
            return other != null && workspace.equals(other.workspace) && origin.equals(other.origin) && key.equals(other.key);
        }
    }

    ConnectScreen(ScreenHost host) {
        this.host = host;
        activity = host.activity();
        ui = host.ui();
        preferences = activity.getSharedPreferences("connection", 0);
        protocol = Math.max(0, Math.min(PROTOCOLS.length - 1, preferences.getInt(preferenceKey("protocol"), 0)));
        reference = preferences.getString(preferenceKey("target"), "");
        scroll = new InsetScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        body = ui.column();
        body.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(96));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        LinearLayout heading = ui.row();
        TextView title = ui.text("接入", Ui.Type.PAGE);
        ui.grow(heading, title);
        heading.addView(ui.iconButton("network", "网关与访问权限", () -> host.advanced("gateway")));
        body.addView(heading);
        status = ui.text("", Ui.Type.SUPPORT);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(status);
        ui.space(body, 20);
        configuration = ui.column();
        body.addView(configuration);
        render();
    }

    @Override public View view() { return scroll; }

    @Override public Object saveAppearanceState() {
        if (loadedScope == null || !loadedScope.matches(new Scope(activity))) return null;
        return new AppearanceState(catalog, result, successfulTest, scroll.getScrollY());
    }

    @Override public void restoreAppearanceState(Object state) {
        if (!(state instanceof AppearanceState saved)) return;
        catalog = saved.catalog();
        loadedScope = new Scope(activity);
        result = saved.result();
        successfulTest = saved.successful();
        render();
        scroll.post(() -> scroll.scrollTo(0, saved.scroll()));
    }

    @Override public void onResume() {
        active = true;
        readFailed = false;
        updateStatus();
        load();
        main.removeCallbacks(tick);
        main.postDelayed(tick, 1000);
    }

    @Override public void onPause() {
        active = false;
        generation++;
        reading = false;
        main.removeCallbacks(tick);
    }

    void setTarget(String selected) {
        if (selected == null || selected.equals(reference)) return;
        reference = selected;
        result = "";
        successfulTest = false;
        preferences.edit().putString(preferenceKey("target"), selected).apply();
        render();
        scroll.scrollTo(0, 0);
    }

    private String preferenceKey(String name) { return name + ":" + AppNavigation.workspace(activity); }

    private void updateStatus() {
        String label;
        if (AppNavigation.remote(activity)) label = catalog != null && !readFailed
                ? "远程工作空间 · 管理端已连接" : "远程工作空间";
        else if (catalog != null && Boolean.FALSE.equals(catalog.snapshot.gatewayRunning)) label = "本机管理端已就绪 · 网关未运行";
        else label = "本机网关 · " + GatewayService.state;
        if (!label.contentEquals(status.getText())) status.setText(label);
    }

    private void load() {
        Scope scope = new Scope(activity);
        if (!active || reading || !AppNavigation.ready(activity) || scope.origin.isEmpty()) return;
        reading = true;
        int request = generation;
        if (catalog == null) render();
        new Thread(() -> {
            try {
                CoreApi api = new CoreApi(scope.origin, scope.key);
                CatalogData data = CatalogData.parse(api.get("/api/providers"), api.get("/api/groups"));
                main.post(() -> finishRead(scope, request, data));
            } catch (Exception error) {
                main.post(() -> finishRead(scope, request, null));
            }
        }, "magpie-connect-catalog").start();
    }

    private void finishRead(Scope scope, int request, CatalogData data) {
        if (!active || request != generation || !scope.matches(new Scope(activity))) return;
        reading = false;
        readFailed = data == null;
        if (data != null) {
            catalog = data;
            loadedScope = scope;
            if (reference.isEmpty()) {
                List<Target> targets = choices(data);
                if (!targets.isEmpty()) {
                    reference = targets.get(0).reference;
                    preferences.edit().putString(preferenceKey("target"), reference).apply();
                }
            }
        }
        updateStatus();
        render();
    }

    private void render() {
        configuration.removeAllViews();
        boolean remote = AppNavigation.remote(activity);
        if (!AppNavigation.ready(activity)) {
            configuration.addView(ui.text("启动本机网关", 20, ui.ink, true));
            ui.space(configuration, 8);
            configuration.addView(ui.text("启动后即可选择模型，复制配置并测试调用。", 15, ui.muted, false));
            ui.space(configuration, 16);
            Button start = ui.button("启动网关", true, () -> new HostActions(activity, ui, host::refresh).start());
            start.setEnabled(!GatewayService.state.startsWith("正在启动"));
            configuration.addView(start);
            return;
        }
        if (catalog == null) {
            configuration.addView(ui.text(readFailed ? "无法读取模型目录" : "正在读取接入目标…", 18, ui.ink, true));
            if (readFailed) {
                ui.space(configuration, 8);
                configuration.addView(ui.text("检查网关状态或工作空间的管理地址和密钥。", 15, ui.muted, false));
                ui.space(configuration, 12);
                configuration.addView(ui.button("重新读取", true, this::load));
            }
            return;
        }

        List<Target> targets = choices(catalog);
        Target target = null;
        for (Target candidate : targets) if (candidate.reference.equals(reference)) target = candidate;
        if (targets.isEmpty()) {
            configuration.addView(ui.text("添加第一个服务商", 20, ui.ink, true));
            ui.space(configuration, 8);
            configuration.addView(ui.text("添加模型来源并启用模型，即可生成接入配置。", 15, ui.muted, false));
            ui.space(configuration, 16);
            configuration.addView(ui.button("添加服务商", true, this::addProvider));
            configuration.addView(ui.button("查看已有模型", false, () -> host.openRoot("home")));
            return;
        }

        configuration.addView(new PeriodTabs(ui, PROTOCOLS, protocol, index -> {
            protocol = index;
            preferences.edit().putInt(preferenceKey("protocol"), index).apply();
            result = "";
            successfulTest = false;
            render();
        }));
        ui.space(configuration, 14);
        Scope displayedScope = loadedScope;
        View targetRow = CatalogViews.row(ui, target == null ? "选择接入目标" : target.name,
                target == null ? "当前模型或分组不可接入，选择其他目标" : target.reference,
                () -> host.openDetail("选择接入目标", new ConnectionTargetPicker(host, targets,
                        () -> displayedScope != null && displayedScope.matches(new Scope(activity)) && AppNavigation.ready(activity),
                        selected -> { setTarget(selected); activity.onBackPressed(); })));
        targetRow.setBackground(ui.glassControl(false));
        targetRow.setPadding(ui.dp(14), ui.dp(9), ui.dp(10), ui.dp(9));
        targetRow.setContentDescription("选择接入目标");
        configuration.addView(targetRow);
        if (readFailed) configuration.addView(ui.text("目录更新失败，显示上次读取结果。", 13, ui.muted, false));
        if (target == null) return;

        String origin = catalog.snapshot.connectionAddress(remote);
        boolean local = !remote && !catalog.snapshot.onNetwork && ConnectionSheet.loopback(origin);
        String base = ConnectionSheet.base(origin, PROTOCOLS[protocol]);
        ui.space(configuration, 20);
        TextView section = ui.text("接入配置", 18, ui.ink, true);
        section.setAccessibilityHeading(true);
        configuration.addView(section);
        ui.space(configuration, 6);
        ui.rule(configuration, 0);
        if (origin.isEmpty()) field("Base URL", "在网关设置中确认接入地址", "确认接入地址", () -> host.advanced("gateway"));
        else field("Base URL", base, "复制 Base URL", () -> copyCurrent(base));
        field("模型标识", reference, "复制模型标识", () -> copyCurrent(reference));
        if (local) field("API Key · 本机占位", "magpie", "复制 API Key", () -> copyCurrent("magpie"));
        else field("API Key", "在网关中选择模型访问密钥", "网关与访问权限", () -> host.advanced("gateway"));

        ui.space(configuration, 12);
        String copyLabel = local ? "复制接入配置" : origin.isEmpty() ? "复制模型标识" : "复制地址与模型";
        Button copy = ui.button(copyLabel, true, () -> {
            if (local && protocol == 0) copyCurrent(ClientConfig.openAI(origin, reference));
            else if (origin.isEmpty()) copyCurrent(reference);
            else copyCurrent("Base URL: " + base + "\n模型: " + reference + (local ? "\nAPI Key: magpie" : ""));
        });
        Button test = ui.button(testing ? "正在测试…" : "测试调用", false, () -> {
            if (local) probe(origin);
            else host.advanced("gateway");
        });
        test.setText(local ? (testing ? "正在测试…" : "测试调用") : "访问密钥");
        copy.setEnabled(!readFailed);
        test.setEnabled(!testing && !readFailed && (!local || !Boolean.FALSE.equals(catalog.snapshot.gatewayRunning)));
        configuration.addView(new AdaptivePair(ui, copy, test, 8));
        ui.space(configuration, 8);
        configuration.addView(ui.text(local ? "测试发送一条短消息，并计入用量。" : "远程和局域网接入使用模型访问密钥。", 13, ui.muted, false));
        if (!result.isEmpty()) {
            TextView feedback = ui.text(result, 14, successfulTest ? ui.ink : ui.muted, true);
            feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            ui.space(configuration, 10);
            configuration.addView(feedback);
        }
        if (local && protocol == 0) {
            TextView json = CatalogViews.action(ui, "查看 JSON", () -> showJson(origin));
            json.setGravity(Gravity.CENTER_VERTICAL);
            configuration.addView(json);
        }
    }

    private void field(String label, String value, String action, Runnable click) {
        LinearLayout row = ui.row(), info = ui.column();
        row.setPadding(0, ui.dp(8), 0, ui.dp(8));
        info.addView(ui.text(label, Ui.Type.LABEL));
        ui.space(info, 4);
        info.addView(ui.text(value, Ui.Type.IDENTIFIER));
        ui.grow(row, info);
        ui.gap(row, 6);
        row.addView(action.startsWith("复制") ? ui.copyButton(action, click) : ui.iconButton("chevron", action, click));
        configuration.addView(row);
        ui.rule(configuration, 0);
    }

    private boolean current() {
        return active && !readFailed && loadedScope != null && loadedScope.matches(new Scope(activity)) && AppNavigation.ready(activity);
    }

    private void copyCurrent(String value) {
        if (!current()) {
            Toast.makeText(activity, "工作空间已变化，请更新配置", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Magpie 接入", value));
        Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show();
    }

    private void showJson(String gateway) {
        if (!current()) return;
        String json = ClientConfig.openAI(gateway, reference);
        TextView text = ui.text(json, 14, ui.ink, false);
        text.setTextIsSelectable(true);
        text.setPadding(ui.dp(22), ui.dp(14), ui.dp(22), ui.dp(12));
        ScrollView content = new ScrollView(activity);
        content.addView(text);
        new AlertDialog.Builder(activity, ui.dialogTheme).setTitle("OpenAI 接入配置").setView(content)
                .setNegativeButton("关闭", null).setPositiveButton("复制 JSON", (dialog, which) -> copyCurrent(json)).show();
    }

    private void probe(String origin) {
        if (testing || !current()) return;
        final Scope scope = loadedScope;
        final String model = reference, useProtocol = PROTOCOLS[protocol];
        testing = true;
        result = "";
        render();
        new Thread(() -> {
            GatewayProbe.Result response = GatewayProbe.call(origin, model, useProtocol);
            main.post(() -> {
                testing = false;
                if (!scope.matches(new Scope(activity))) return;
                if (Objects.equals(reference, model) && PROTOCOLS[protocol].equals(useProtocol)) {
                    result = response.message;
                    successfulTest = response.ok;
                }
                if (active) render();
            });
        }, "magpie-connect-probe").start();
    }

    private void addProvider() {
        UiMotion.result(activity, new Intent(activity, ProviderSetupActivity.class)
                .putExtra("remote", AppNavigation.remote(activity)), CatalogScreen.SETUP_PROVIDER);
    }

    static final class Target {
        final String name, reference, group;
        Target(String name, String reference, String group) {
            this.name = name;
            this.reference = reference;
            this.group = group;
        }
    }

    private static List<Target> choices(CatalogData data) {
        List<Target> targets = new ArrayList<>();
        for (WorkspaceSnapshot.Provider provider : data.snapshot.providers) {
            for (WorkspaceSnapshot.Model model : provider.models) {
                CatalogData.Model item = data.model(model.reference);
                if (item != null && item.connectable) targets.add(new Target(model.name, model.reference, provider.name));
            }
        }
        for (WorkspaceSnapshot.Group group : data.snapshot.groups) {
            CatalogData.Group item = data.group(group.reference);
            if (item != null && item.connectable) targets.add(new Target(group.name, group.reference, "分组"));
        }
        return targets;
    }
}
