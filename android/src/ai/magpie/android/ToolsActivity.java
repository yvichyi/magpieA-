package ai.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** App-wide settings and access to workspace management pages. */
public final class ToolsActivity extends Activity {
    private Ui ui;
    private HostActions actions;
    private SharedPreferences navigation;
    private LinearLayout rows;
    private TextView status;
    private EditText search;
    private ScrollView scroll;
    private String parentRoot;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean active, busy, wasReady;
    private int generation;
    private WorkspaceScope scope;
    private List<Page> pages = java.util.Collections.emptyList();

    private record WorkspaceScope(String workspace, String address, String key) {}
    private record Page(String id, String title) {}

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            updateWorkspace();
            main.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ui = new Ui(this);
        navigation = getSharedPreferences("navigation", MODE_PRIVATE);
        parentRoot = AppNavigation.parent(this, "settings");
        actions = new HostActions(this, ui, this::updateWorkspace);

        LinearLayout root = ui.column();
        root.setBackgroundColor(ui.bg);
        root.addView(header(ui, ui.text("设置", Ui.Type.PAGE),
            "返回" + NavigationModel.title(parentRoot), this::onBackPressed));
        ui.rule(root, 0);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(32));
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        search = new EditText(this);
        search.setSingleLine();
        ui.typography(search, Ui.Type.BODY);
        search.setTextColor(ui.ink);
        search.setHintTextColor(ui.muted);
        search.setHint("搜索设置");
        search.setContentDescription("搜索设置");
        search.setMinimumHeight(ui.dp(48));
        search.setPadding(0, ui.dp(8), 0, ui.dp(8));
        search.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout searchField = ui.row();
        searchField.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        searchField.setBackground(ui.field(ui.card, 10));
        View searchIcon = new LineIcon(this, "search", ui.muted);
        searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchField.addView(searchIcon, new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
        ui.gap(searchField, 10);
        ui.grow(searchField, search);
        content.addView(searchField, new LinearLayout.LayoutParams(-1, -2));

        scope = workspaceScope();
        search.setText(saved == null ? navigation.getString(queryKey(scope), "") : saved.getString("query", ""));
        status = ui.text("", Ui.Type.SUPPORT);
        status.setVisibility(View.GONE);
        status.setPadding(0, ui.dp(12), 0, 0);
        rows = ui.column();
        content.addView(rows);
        content.addView(status);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            public void afterTextChanged(Editable e) {}
        });
        setContentView(root);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        render();
    }

    static LinearLayout header(Ui ui, TextView heading, String backLabel, Runnable backAction) {
        LinearLayout header = ui.row();
        header.setPadding(ui.dp(8), ui.dp(4), ui.dp(20), ui.dp(4));
        header.setMinimumHeight(ui.dp(60));
        header.setBackgroundColor(ui.card);
        View back = ui.iconButton("back", backLabel, backAction);
        back.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        header.addView(back);
        ui.gap(header, 4);
        heading.setTypeface(ui.font(false, ui.swiss() ? 700 : 650));
        heading.setAccessibilityHeading(true);
        ui.grow(header, heading);
        return header;
    }

    String parentRoot() { return parentRoot; }

    @Override protected void onResume() {
        super.onResume();
        UiMotion.enter(this);
        active = true;
        updateWorkspace();
        loadPages();
        main.postDelayed(poll, 1000);
    }

    @Override protected void onPause() {
        saveQuery();
        active = false;
        generation++;
        busy = false;
        main.removeCallbacks(poll);
        UiMotion.pause(this);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("query", search.getText().toString());
        super.onSaveInstanceState(out);
    }

    @Override public void onBackPressed() {
        if(search.hasFocus()) {
            search.clearFocus();
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if(keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
            return;
        }
        UiMotion.close(this);
    }

    private WorkspaceScope workspaceScope() {
        return new WorkspaceScope(AppNavigation.workspace(this), AppNavigation.origin(this), AppNavigation.key(this));
    }

    private static String queryKey(WorkspaceScope scope) { return "toolsQuery:" + scope.workspace(); }

    private void saveQuery() {
        navigation.edit().putString(queryKey(scope), search.getText().toString()).apply();
    }

    private void updateWorkspace() {
        WorkspaceScope next = workspaceScope();
        if(!next.equals(scope)) {
            generation++;
            busy = false;
            wasReady = false;
            pages = java.util.Collections.emptyList();
            showStatus("", null);
            boolean changedWorkspace = !next.workspace().equals(scope.workspace());
            if(changedWorkspace) saveQuery();
            scope = next;
            if(changedWorkspace) search.setText(navigation.getString(queryKey(scope), ""));
            render();
        }
        boolean ready = AppNavigation.ready(this);
        if(ready && !wasReady && active) loadPages();
        wasReady = ready;
    }

    private void render() {
        rows.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = general(query);
        count += section("用量", query, new String[][] {
            {"checkIn", "签到", "给订阅账号签到，或设置每日自动签到"},
            {"usagePreferences", "额度与用量设置", "额度显示、币种和提醒"}
        });
        count += section("网络与共享", query, new String[][] {
            {"gateway", "网关与访问密钥", "接入地址、访问密钥和调用配额"},
            {"network", "网络与 LAN 共享", "局域网访问和代理设置"},
            {"workspace", "工作区", "切换本机网关或远程工作空间"}
        });
        count += section("模型", query, new String[][] {
            {"subscription", "订阅登录与高级添加", "登录订阅，或填写完整服务商参数"},
            {"routing", "请求路由", "配置请求的分配、回退和转换"}
        });
        count += section("隐私", query, new String[][] {
            {"masking", "隐私脱敏", "管理请求内容中的隐私保护"}
        });
        count += section("同步与备份", query, new String[][] {
            {"backup", "设置与备份", "创建加密备份、恢复和同步"}
        });
        count += section("扩展", query, new String[][] {
            {"library", "资料库", "指令、MCP、技能和子 Agent"},
            {"agents", "Agent 配置", "管理远程电脑的 Agent 接入"},
            {"sessions", "会话", "查看远程工作空间的会话"}
        });
        if(!pages.isEmpty()) {
            List<String[]> entries = new ArrayList<>();
            for(Page page : pages) entries.add(new String[] {page.id(), page.title(), ""});
            count += section("插件页面", query, entries.toArray(new String[0][]), true);
        }
        count += section("关于", query, new String[][] {
            {"diagnostics", "运行诊断", "本机核心状态和脱敏日志"},
            {"about", "关于与开源许可", "Android " + HostActions.APP_VERSION + " · 核心 v" + HostActions.CORE_VERSION}
        });
        if(count == 0) {
            ui.space(rows, 24);
            rows.addView(ui.text("没有匹配的设置", Ui.Type.ROW));
            ui.space(rows, 8);
            rows.addView(ui.button("清除搜索", false, () -> search.setText("")),
                    new LinearLayout.LayoutParams(-2, -2));
        }
    }

    private int general(String query) {
        String title = "常规";
        String transparency = "降低透明度";
        String motion = "减少动态效果";
        boolean theme = matches(query, title, "外观 界面主题 液态玻璃 瑞士风格");
        boolean core = matches(query, title, "核心设置 语言 通用偏好");
        boolean showTransparency = matches(query, title, "辅助功能 " + transparency);
        boolean showMotion = matches(query, title, "辅助功能 " + motion);
        int count = (theme ? 1 : 0) + (core ? 1 : 0) + (showTransparency ? 1 : 0) + (showMotion ? 1 : 0);
        if (count == 0) return 0;
        heading(title);
        if (theme) setting("界面主题", ui.appearance.label, "选择界面主题", this::chooseAppearance);
        if (showTransparency) preference(transparency, GlassOptions.reduceTransparency(this),
                value -> GlassOptions.setReduceTransparency(this, value));
        if (showMotion) preference(motion, GlassOptions.reduceMotion(this),
                value -> GlassOptions.setReduceMotion(this, value));
        if (core) setting("核心设置", "语言和通用偏好", "打开核心设置", () -> open("settings", "核心设置"));
        return count;
    }

    private void chooseAppearance() {
        Appearance[] themes = Appearance.values();
        CharSequence[] labels = new CharSequence[themes.length];
        for (int i = 0; i < themes.length; i++) labels[i] = themes[i].label;
        new AlertDialog.Builder(this, ui.dialogTheme).setTitle("界面主题")
                .setSingleChoiceItems(labels, ui.appearance.ordinal(), (dialog, selected) -> {
                    dialog.dismiss();
                    if (themes[selected] == ui.appearance) return;
                    Ui.setAppearance(this, themes[selected]);
                    recreate();
                }).setNegativeButton("取消", null).show();
    }

    private void preference(String label, boolean checked, java.util.function.Consumer<Boolean> persist) {
        Switch control = new Switch(this);
        control.setText(label);
        ui.typography(control, Ui.Type.ROW);
        control.setTypeface(ui.font(false, 500));
        control.setTextColor(ui.ink);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(0, ui.dp(8), 0, ui.dp(8));
        control.setSwitchPadding(ui.dp(12));
        control.setMinHeight(ui.dp(48));
        control.setThumbTintList(ColorStateList.valueOf(ui.card));
        control.setTrackTintList(new ColorStateList(
            new int[][] {{android.R.attr.state_checked}, {}},
            new int[] {ui.accent, ui.muted}));
        control.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT, 8));
        control.setChecked(checked);
        control.setOnCheckedChangeListener((button, value) -> persist.accept(value));
        rows.addView(control, new LinearLayout.LayoutParams(-1, -2));
        ui.rule(rows, 0);
    }

    private void heading(String title) {
        ui.space(rows, 24);
        TextView label = ui.text(title, Ui.Type.LABEL);
        label.setTypeface(ui.font(false, 650));
        label.setAccessibilityHeading(true);
        rows.addView(label);
        ui.space(rows, 4);
    }

    private static boolean matches(String query, String heading, String title) {
        return (heading + " " + title).toLowerCase(Locale.ROOT).contains(query);
    }

    private int section(String title, String query, String[][] entries) {
        return section(title, query, entries, false);
    }

    private int section(String title, String query, String[][] entries, boolean pluginPage) {
        int count = 0;
        for(String[] item : entries) {
            if(!matches(query, title, item[1] + " " + item[2])) continue;
            if(count++ == 0) heading(title);
            setting(item[1], item[2], "打开" + item[1], () -> {
                if(pluginPage) openAdvanced(item[0], item[1], null);
                else open(item[0], item[1]);
            });
        }
        return count;
    }

    private void setting(String title, String detail, String description, Runnable click) {
        LinearLayout copy = ui.column();
        copy.addView(ui.text(title, Ui.Type.ROW));
        if (!detail.isEmpty()) {
            ui.space(copy, 4);
            copy.addView(ui.text(detail, Ui.Type.SUPPORT));
        }
        rows.addView(directoryRow(copy, description, click));
        ui.rule(rows, 0);
    }

    private LinearLayout directoryRow(View text, String description, Runnable click) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(10), 0, ui.dp(10));
        row.setMinimumHeight(ui.dp(48));
        ui.grow(row, text);
        ui.gap(row, 10);
        View chevron = new LineIcon(this, "chevron", ui.muted);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron, new LinearLayout.LayoutParams(ui.dp(16), ui.dp(16)));
        row.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT, 8));
        row.setContentDescription(description);
        row.setFocusable(true);
        row.setOnClickListener(v -> click.run());
        return row;
    }

    private void open(String id, String title) {
        switch(id) {
            case "diagnostics": actions.diagnostics(); return;
            case "about": actions.about(); return;
            case "workspace": actions.workspace(); return;
            case "checkIn":
                Intent destination = new Intent(this, MainActivity.class)
                        .putExtra("root", "usage").putExtra(MainActivity.EXTRA_CHECK_IN, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                UiMotion.go(this, destination, true);
                return;
            case "agents": case "sessions":
                if(!AppNavigation.remote(this)) {
                    new AlertDialog.Builder(this, ui.dialogTheme)
                        .setTitle(title)
                        .setMessage("此功能运行在电脑或服务器上的 Magpie 中。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("连接远程工作区", (dialog, button) -> actions.remote())
                        .show();
                    return;
                }
                break;
            case "subscription": openAdvanced("providers", title, null); return;
            case "network": openAdvanced("settings", title, "network"); return;
            case "backup": openAdvanced("settings", title, "sync"); return;
            case "settings": openAdvanced("settings", title, "general"); return;
            case "usagePreferences": openAdvanced("settings", title, "usage"); return;
        }
        openAdvanced(id, title, null);
    }

    private void openAdvanced(String view, String title, String tab) {
        if(!AppNavigation.ready(this)) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this, ui.dialogTheme)
                .setTitle(title)
                .setMessage("连接本机或远程工作区后可打开此项设置。")
                .setNegativeButton("取消", null);
            if (GatewayService.state.startsWith("正在启动")) {
                dialog.setMessage("本机网关正在启动，启动完成后即可打开此项设置。")
                        .setPositiveButton("连接远程工作区", (choice, button) -> actions.remote());
            } else {
                dialog.setNeutralButton("连接远程工作区", (choice, button) -> actions.remote())
                        .setPositiveButton("启动本机网关", (choice, button) -> actions.start());
            }
            dialog.show();
            return;
        }
        Intent target = AppNavigation.detailIntent(this, view)
            .putExtra("parentRoot", parentRoot)
            .putExtra("parentSettings", true)
            .putExtra("pageTitle", title);
        if(tab != null) target.putExtra("settingsTab", tab);
        if("providers".equals(view)) target.putExtra("addProvider", true);
        UiMotion.go(this, target, false);
    }

    private static List<Page> parse(String raw) throws JSONException {
        JSONArray values = new JSONArray(raw);
        List<Page> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if(values.length() > 256) throw new JSONException("Too many plugin pages");
        for(int i = 0; i < values.length(); i++) {
            JSONObject item = values.getJSONObject(i);
            String id = item.getString("key");
            String title = item.getString("title");
            JSONObject words = item.optJSONObject("titles");
            if(words != null) title = words.optString("zh", title);
            if(id.isEmpty() || id.length() > 160 || title.trim().isEmpty()
                || title.length() > 180 || id.indexOf('\n') >= 0) {
                throw new JSONException("Invalid plugin page");
            }
            if(seen.add(id)) result.add(new Page(id, title));
        }
        return result;
    }

    private void showStatus(String text, Runnable retry) {
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        status.setOnClickListener(retry == null ? null : v -> retry.run());
        status.setClickable(retry != null);
        status.setMinHeight(retry == null ? 0 : ui.dp(48));
    }

    private void loadPages() {
        if(!active || busy || !AppNavigation.ready(this)) return;
        busy = true;
        int request = ++generation;
        WorkspaceScope requested = scope;
        showStatus("", null);
        new Thread(() -> {
            try {
                List<Page> list = parse(new CoreApi(requested.address(), requested.key()).get("/api/plugin-pages"));
                main.post(() -> {
                    if(!applies(request, requested)) return;
                    busy = false;
                    boolean changed = !list.equals(pages);
                    pages = list;
                    if(changed) render();
                });
            } catch(Exception e) {
                main.post(() -> {
                    if(!applies(request, requested)) return;
                    busy = false;
                    showStatus(pages.isEmpty() ? "插件页面未加载。点此重试。"
                        : "插件页面未更新，显示上次加载的内容。点此重试。", this::loadPages);
                });
            }
        }, "magpie-navigation-pages").start();
    }

    private boolean applies(int request, WorkspaceScope requested) {
        return active && generation == request && requested.equals(workspaceScope());
    }
}
