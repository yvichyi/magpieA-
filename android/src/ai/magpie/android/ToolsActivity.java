package ai.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
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
    private TextView workspace, status;
    private EditText search;
    private ScrollView scroll;
    private Button start;
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
        root.addView(header(ui, ui.text("设置", Ui.Type.SECTION),
            "返回" + NavigationModel.title(parentRoot), this::onBackPressed));
        ui.rule(root, 0);
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(32));
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        workspace = ui.text("", 15, ui.ink, true);
        content.addView(directoryRow(workspace, "切换工作空间", actions::workspace));
        start = ui.button("启动本机网关", true, actions::start);
        content.addView(start);
        ui.space(content, 16);
        search = new EditText(this);
        search.setSingleLine();
        search.setTextSize(16);
        search.setTypeface(ui.font(false, 400));
        search.setTextColor(ui.ink);
        search.setHintTextColor(ui.muted);
        search.setHint("搜索设置");
        search.setContentDescription("搜索设置");
        search.setMinimumHeight(ui.dp(48));
        search.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
        search.setBackground(ui.field(ui.card, 12));
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));

        scope = workspaceScope();
        search.setText(saved == null ? navigation.getString(queryKey(scope), "") : saved.getString("query", ""));
        status = ui.text("", 14, ui.muted, false);
        status.setVisibility(View.GONE);
        status.setPadding(0, ui.dp(12), 0, 0);
        content.addView(status);
        rows = ui.column();
        content.addView(rows);
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
        boolean remote = AppNavigation.remote(this);
        boolean ready = AppNavigation.ready(this);
        String label = remote ? "远程工作空间 · " + next.address() : "本机工作空间 · " + GatewayService.state;
        if(!label.contentEquals(workspace.getText())) workspace.setText(label);
        start.setVisibility(remote || ready ? View.GONE : View.VISIBLE);
        boolean launching = GatewayService.state.startsWith("正在启动");
        start.setEnabled(!launching);
        start.setText(launching ? "正在启动…" : "启动本机网关");
        if(ready && !wasReady && active) loadPages();
        wasReady = ready;
    }

    private void render() {
        rows.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int count = appearanceSection(query) + accessibilitySection(query);
        count += section("网关与隐私", query, new String[][] {
            {"gateway", "网关与访问密钥", "接入地址、访问密钥和调用配额"},
            {"network", "网络与 LAN 共享", "局域网访问、代理和远程连接配置"},
            {"masking", "隐私脱敏", "管理请求内容中的隐私保护"},
            {"routing", "请求路由", "配置请求的分配、回退和转换"}
        });
        count += section("应用与数据", query, new String[][] {
            {"settings", "核心设置", "语言、模型行为和工作空间偏好"},
            {"backup", "设置与备份", "创建加密备份、恢复和同步"},
            {"diagnostics", "运行诊断", "本机核心状态和脱敏日志"},
            {"about", "关于与开源许可", "Android " + HostActions.APP_VERSION + " · 核心 v" + HostActions.CORE_VERSION}
        });
        count += section("服务商与扩展", query, new String[][] {
            {"subscription", "订阅登录与高级添加", "登录订阅，或填写完整服务商参数"},
            {"library", "资料库", "指令、MCP、技能和子 Agent"},
            {"agents", "Agent 配置", "管理远程电脑的 Agent 接入"},
            {"sessions", "会话", "查看远程工作空间的会话"}
        });
        if(!pages.isEmpty()) {
            List<String[]> entries = new ArrayList<>();
            for(Page page : pages) entries.add(new String[] {page.id(), page.title(), ""});
            count += section("插件页面", query, entries.toArray(new String[0][]), true);
        }
        if(count == 0) {
            ui.space(rows, 24);
            rows.addView(ui.text("没有匹配的设置", 16, ui.ink, true));
            ui.space(rows, 8);
            rows.addView(ui.button("清除搜索", false, () -> search.setText("")));
        }
    }

    private int appearanceSection(String query) {
        if (!matches(query, "外观", "界面主题 液态玻璃 瑞士风格")) return 0;
        heading("外观");
        LinearLayout label = ui.column();
        label.addView(ui.text("界面主题", Ui.Type.ROW));
        ui.space(label, 4);
        label.addView(ui.text(ui.appearance.label, Ui.Type.SUPPORT));
        rows.addView(directoryRow(label, "选择界面主题", this::chooseAppearance));
        return 1;
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

    private int accessibilitySection(String query) {
        String title = "辅助功能";
        String transparency = "降低透明度";
        String motion = "减少动态效果";
        boolean showTransparency = matches(query, title, transparency);
        boolean showMotion = matches(query, title, motion);
        if(!showTransparency && !showMotion) return 0;
        heading(title);
        if(showTransparency) preference(transparency, GlassOptions.reduceTransparency(this),
            value -> GlassOptions.setReduceTransparency(this, value));
        if(showMotion) preference(motion, GlassOptions.reduceMotion(this),
            value -> GlassOptions.setReduceMotion(this, value));
        return (showTransparency ? 1 : 0) + (showMotion ? 1 : 0);
    }

    private void preference(String label, boolean checked, java.util.function.Consumer<Boolean> persist) {
        Switch control = new Switch(this);
        control.setText(label);
        ui.typography(control, Ui.Type.ROW);
        control.setTypeface(ui.font(false, 500));
        control.setTextColor(ui.ink);
        control.setGravity(Gravity.CENTER_VERTICAL);
        control.setPadding(0, ui.dp(12), 0, ui.dp(12));
        control.setSwitchPadding(ui.dp(12));
        control.setMinHeight(ui.dp(52));
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
        ui.space(rows, 25);
        TextView label = ui.text(title, Ui.Type.SECTION);
        rows.addView(label);
        ui.space(rows, 6);
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
            LinearLayout copy = ui.column();
            copy.addView(ui.text(item[1], Ui.Type.ROW));
            if(!item[2].isEmpty()) {
                ui.space(copy, 4);
                copy.addView(ui.text(item[2], Ui.Type.SUPPORT));
            }
            rows.addView(directoryRow(copy, "打开" + item[1], () -> {
                if(pluginPage) openAdvanced(item[0], item[1], null);
                else open(item[0], item[1]);
            }));
            ui.rule(rows, 0);
        }
        return count;
    }

    private LinearLayout directoryRow(View text, String description, Runnable click) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(12), 0, ui.dp(12));
        row.setMinimumHeight(ui.dp(52));
        ui.grow(row, text);
        ui.gap(row, 12);
        row.addView(new LineIcon(this, "chevron", ui.muted), new LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)));
        row.setBackground(ui.ripple(android.graphics.Color.TRANSPARENT, 8));
        row.setContentDescription(description);
        row.setOnClickListener(v -> click.run());
        return row;
    }

    private void open(String id, String title) {
        switch(id) {
            case "diagnostics": actions.diagnostics(); return;
            case "about": actions.about(); return;
            case "agents": case "sessions":
                if(!AppNavigation.remote(this)) {
                    new AlertDialog.Builder(this, ui.dialogTheme)
                        .setTitle(title)
                        .setMessage("此功能运行在电脑或服务器上。请在工作空间中连接远程 Magpie 后再打开。")
                        .setPositiveButton("知道了", null)
                        .show();
                    return;
                }
                break;
            case "subscription": openAdvanced("providers", title, null); return;
            case "network": openAdvanced("settings", title, "network"); return;
            case "backup": openAdvanced("settings", title, "sync"); return;
            case "settings": openAdvanced("settings", title, "general"); return;
        }
        openAdvanced(id, title, null);
    }

    private void openAdvanced(String view, String title, String tab) {
        if(!AppNavigation.ready(this)) {
            new AlertDialog.Builder(this, ui.dialogTheme)
                .setTitle(title)
                .setMessage("请先启动本机网关，或连接远程工作空间。")
                .setPositiveButton("知道了", null)
                .show();
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
