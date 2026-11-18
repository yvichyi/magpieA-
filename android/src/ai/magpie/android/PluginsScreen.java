package ai.magpie.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

/** Retained plugin workspace shared by the root and its native details. */
final class PluginsScreen implements AppScreen {
    private static final long POLL = 20_000L;
    private static final long MARKET_POLL = 300_000L;
    private static final int BATCH = 30;
    private static final String[] TABS = {"已安装", "发现", "内置"};
    final ScreenHost host;
    final Ui ui;
    final Activity activity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScrollView scroll;
    private final LinearLayout body, tabs, results, operationPanel, find;
    private final EditText query;
    private final Button installButton;
    private final Resource<PluginSnapshot.Installed> installed = new Resource<>();
    private final Resource<List<PluginSnapshot.Builtin>> builtins = new Resource<>();
    private final Resource<List<PluginSnapshot.Page>> pages = new Resource<>();
    private final Resource<PluginSnapshot.Discoveries> listings = new Resource<>();
    private final Resource<PluginSnapshot.Discoveries> github = new Resource<>();
    private final Resource<PluginSnapshot.Discoveries> search = new Resource<>();
    private final Resource<PluginSnapshot.Installed> updates = new Resource<>();
    private final Resource<?>[] resources = {installed, builtins, pages, listings, github, search, updates};
    private final Runnable poll = this::poll;
    private Scope scope;
    private Object visible;
    private Runnable redraw;
    private AlertDialog dialog;
    private Attempt attempt;
    private int generation, tab, limit = BATCH;
    private boolean disposed;
    private String requestedQuery = "";

    static final class Scope {
        final String workspace, origin, key;
        Scope(Activity activity) {
            workspace = AppNavigation.workspace(activity);
            origin = AppNavigation.origin(activity);
            key = AppNavigation.key(activity);
        }
        boolean same(Scope other) {
            return other != null && workspace.equals(other.workspace) && origin.equals(other.origin) && key.equals(other.key);
        }
        CoreApi api() { return new CoreApi(origin, key); }
    }

    private static final class Resource<T> {
        T value;
        String error = "";
        int request;
        boolean pending;
        long readAt;
        void invalidate() { request++; pending = false; readAt = 0; }
        void clear() { invalidate(); value = null; error = ""; }
    }

    private enum Outcome { RUNNING, SUCCESS, FAILED, UNCERTAIN }
    private static final class Attempt {
        final PluginOperation operation;
        final long startedAt = SystemClock.elapsedRealtime();
        Outcome outcome = Outcome.RUNNING;
        String detail = "";
        boolean checkedAfterDeadline;
        Attempt(PluginOperation operation) { this.operation = operation; }
        boolean blocks() { return outcome == Outcome.RUNNING || outcome == Outcome.UNCERTAIN && !checkedAfterDeadline; }
    }

    @FunctionalInterface interface Read<T> { T read(CoreApi api) throws Exception; }
    @FunctionalInterface interface ReadResult<T> { void accept(T value, String error); }

    PluginsScreen(ScreenHost host) {
        this.host = host;
        ui = host.ui();
        activity = host.activity();
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        body = ui.column();
        body.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(96));
        body.setFocusableInTouchMode(true);
        body.setBackgroundColor(ui.bg);
        scroll.addView(body);

        LinearLayout heading = ui.row();
        TextView title = ui.text("插件", 28, ui.ink, true);
        title.setAccessibilityHeading(true);
        ui.grow(heading, title);
        View refresh = ui.iconButton("refresh", "刷新插件", () -> refresh(true));
        heading.addView(refresh, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        body.addView(heading);
        ui.space(body, 8);
        installButton = ui.button("从 npm 安装", true, this::installDialog);
        body.addView(installButton);
        ui.space(body, 16);
        tabs = ui.row();
        body.addView(tabs);
        operationPanel = ui.column();
        operationPanel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(operationPanel);
        find = ui.column();
        query = input("搜索插件名称或 npm 包", EditorInfo.IME_ACTION_SEARCH, 180);
        query.setOnEditorActionListener((v, a, event) -> {
            if (a != EditorInfo.IME_ACTION_SEARCH) return false;
            search();
            return true;
        });
        find.addView(new AdaptivePair(ui, query, ui.button("搜索 npm", false, this::search), 8));
        body.addView(find);
        results = ui.column();
        body.addView(results);
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                limit = BATCH;
                if (!requestedQuery.equals(queryText())) {
                    requestedQuery = "";
                    search.clear();
                }
                if (visible == PluginsScreen.this) renderResults();
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        render();
    }

    @Override public View view() { return scroll; }
    @Override public void onResume() { activate(this, this::render); }
    @Override public void onPause() { deactivate(this); }
    @Override public boolean onBack() {
        if (query.hasFocus()) { hideKeyboard(query); return true; }
        return false;
    }
    @Override public void dispose() {
        disposed = true;
        if (visible != null) deactivate(visible);
        for (Resource<?> resource : resources) resource.clear();
        scope = null;
        attempt = null;
    }

    void activate(Object observer, Runnable redraw) {
        if (disposed) return;
        visible = observer;
        this.redraw = redraw;
        updateScope();
        notifyChange();
        refresh(true);
        schedulePoll();
    }

    void deactivate(Object observer) {
        if (visible != observer) return;
        visible = null;
        redraw = null;
        generation++;
        main.removeCallbacks(poll);
        for (Resource<?> resource : resources) resource.invalidate();
        if (dialog != null) dialog.dismiss();
    }

    private boolean updateScope() {
        Scope next = new Scope(activity);
        if (next.same(scope)) return false;
        if (dialog != null) dialog.dismiss();
        generation++;
        scope = next;
        attempt = null;
        for (Resource<?> resource : resources) resource.clear();
        requestedQuery = "";
        limit = BATCH;
        if (query.length() != 0) query.setText("");
        return true;
    }

    boolean current(Scope desired) {
        return !disposed && visible != null && desired != null && desired.same(scope) && desired.same(new Scope(activity));
    }

    private boolean ready() { return AppNavigation.ready(activity) && scope != null && !scope.origin.isEmpty(); }

    private void poll() {
        if (visible == null || disposed) return;
        if (updateScope()) notifyChange();
        refresh(false);
        schedulePoll();
    }

    private void schedulePoll() {
        main.removeCallbacks(poll);
        if (visible != null && !disposed) main.postDelayed(poll, ready() ? POLL : 2_000L);
    }

    private void refresh(boolean force) {
        if (visible == null || disposed) return;
        if (updateScope()) notifyChange();
        if (!ready()) return;
        read(installed, POLL, force, api -> PluginSnapshot.installed(api.get("/api/plugins")));
        read(builtins, POLL, force, api -> PluginSnapshot.builtins(api.get("/api/plugins/builtin")));
        read(pages, POLL, force, api -> PluginSnapshot.pages(api.get("/api/plugin-pages")));
        if (tab == 1) {
            read(listings, MARKET_POLL, force, api -> PluginSnapshot.discovery(api.get("/api/plugins/listings"), PluginSnapshot.Feed.LISTINGS));
            read(github, MARKET_POLL, force, api -> PluginSnapshot.discovery(api.get("/api/plugins/github"), PluginSnapshot.Feed.GITHUB));
            if (force && !requestedQuery.isEmpty() && search.value == null) requestSearch();
        }
    }

    private <T> void read(Resource<T> resource, long interval, boolean force, Read<T> work) {
        long now = SystemClock.elapsedRealtime();
        if (resource.pending || !force && resource.readAt != 0 && now - resource.readAt < interval - 500) return;
        resource.pending = true;
        resource.readAt = now;
        int request = ++resource.request;
        notifyChange();
        request(scope, work, (value, error) -> {
            if (request != resource.request) return;
            resource.pending = false;
            resource.error = error;
            if (value != null) {
                resource.value = value;
                if (resource == installed) updates.value = null;
                if (resource == installed || resource == builtins) reconcile(resource == builtins, now);
            }
            notifyChange();
        });
    }

    <T> void request(Scope desired, Read<T> work, ReadResult<T> receive) {
        if (!current(desired) || !ready()) return;
        int epoch = generation;
        new Thread(() -> {
            T value = null;
            String error = "";
            try { value = work.read(desired.api()); }
            catch (Exception e) { error = safeMessage(e, desired); }
            T result = value;
            String failure = error;
            main.post(() -> {
                if (generation == epoch && current(desired) && ready()) receive.accept(result, failure);
            });
        }, "magpie-plugin-read").start();
    }

    private void notifyChange() { if (redraw != null) redraw.run(); }

    private void select(int next) {
        if (next == tab) return;
        hideKeyboard(query);
        tab = next;
        limit = BATCH;
        if (tab == 1) refresh(false);
        render();
        scroll.smoothScrollTo(0, 0);
    }

    private void render() {
        installButton.setEnabled(writable(false));
        tabs.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            int index = i;
            Button item = ui.button(TABS[i], i == tab, () -> select(index));
            item.setSelected(i == tab);
            item.setMinWidth(0);
            item.setMinimumWidth(0);
            item.setPadding(ui.dp(3), 0, ui.dp(3), 0);
            if (i != 0) ui.gap(tabs, 6);
            tabs.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        showOperation(operationPanel);
        find.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        renderResults();
    }

    private void renderResults() {
        results.removeAllViews();
        ui.space(results, 20);
        if (!ready()) {
            heading(results, "工作空间尚未连接");
            note(results, "在“接入”连接远程工作空间，或启动本机网关。", false);
            button(results, "前往接入", false, () -> host.openRoot("connect"));
            return;
        }
        switch (tab) {
            case 0: renderInstalled(); break;
            case 1: renderDiscovery(); break;
            default: renderBuiltins();
        }
        if (tab != 1) renderExtensions();
    }

    private void renderInstalled() {
        PluginSnapshot.Installed data = installed.value;
        sourceStatus(results, installed, "已安装插件");
        if (data == null) return;
        if (!data.error().isEmpty()) note(results, "插件运行时：" + cap(data.error(), 1800), true);
        note(results, data.bun() ? "Bun" + (data.bunVersion().isEmpty() ? " 已就绪" : " " + data.bunVersion())
                : "Bun 尚未就绪，首次安装时网关会尝试下载运行时。", false);
        if (data.entries().isEmpty()) {
            heading(results, "还没有安装插件");
            button(results, "发现插件", false, () -> select(1));
            return;
        }
        Button check = ui.button(updates.pending ? "正在检查更新…" : "检查更新", false, this::checkUpdates);
        check.setEnabled(!updates.pending);
        results.addView(check);
        if (!updates.error.isEmpty()) note(results, "检查更新失败：" + updates.error, true);
        List<PluginSnapshot.Entry> entries = (updates.value == null ? data : updates.value).entries();
        for (int i = 0; i < Math.min(limit, entries.size()); i++) {
            PluginSnapshot.Entry item = entries.get(i);
            String label = item.off() ? "已停用" : item.error().isEmpty() ? "已启用" : "加载出错";
            String version = item.version().isEmpty() ? "" : "v" + item.version();
            String update = item.updateAvailable() ? "有更新" : "";
            row(results, "plug", item.name(), item.spec(), join(label, version, item.kind(), update),
                    () -> open(PluginDetailScreen.installed(this, item.spec(), scope)));
        }
        more(results, entries.size());
    }

    private void renderDiscovery() {
        String text = queryText();
        if (installed.value != null && !installed.value.registry().isEmpty()) {
            note(results, "npm 镜像 · " + installed.value.registry(), false);
        }
        if (!text.isEmpty() && text.equals(requestedQuery)) {
            heading(results, "npm 搜索结果");
            sourceStatus(results, search, "npm 搜索结果");
            if (search.value != null) discoveryRows(search.value.entries(), "npm 没有返回匹配的插件");
        }
        heading(results, "社区目录");
        sourceStatus(results, listings, "社区目录");
        if (listings.value != null) discoveryRows(filter(listings.value.entries(), text, true), "目录中没有匹配的插件");
        ui.space(results, 20);
        heading(results, "GitHub 插件");
        sourceStatus(results, github, "GitHub 目录");
        if (github.value != null) {
            if (!github.value.error().isEmpty()) note(results, "GitHub 目录暂不完整：" + cap(github.value.error(), 1000), true);
            discoveryRows(filter(github.value.entries(), text, false), github.value.error().isEmpty() ? "暂无匹配的 GitHub 插件" : "");
        }
    }

    private static List<PluginSnapshot.Discovery> filter(List<PluginSnapshot.Discovery> source, String query, boolean community) {
        List<PluginSnapshot.Discovery> out = new ArrayList<>();
        for (PluginSnapshot.Discovery item : source) {
            if (item.matches(query) && (!community || !query.isEmpty() || item.source().equals("Magpie 社区"))) out.add(item);
        }
        return out;
    }

    private void discoveryRows(List<PluginSnapshot.Discovery> items, String empty) {
        for (int i = 0; i < Math.min(limit, items.size()); i++) {
            PluginSnapshot.Discovery item = items.get(i);
            String state = installed.value != null && installed.value.match(item) != null ? "已安装" : "";
            row(results, "plug", item.name(), item.description().isEmpty() ? item.spec() : item.description(),
                    join(state, item.kindLabel(), item.source()), () -> open(PluginDetailScreen.discovery(this, item, scope)));
        }
        if (items.isEmpty() && !empty.isEmpty()) note(results, empty, false);
        more(results, items.size());
    }

    private void renderBuiltins() {
        sourceStatus(results, builtins, "内置插件");
        if (builtins.value == null) return;
        if (builtins.value.isEmpty()) note(results, "当前网关没有返回内置插件。", false);
        for (PluginSnapshot.Builtin item : builtins.value) {
            String state = item.on() ? "已启用" : "已停用";
            if (!item.broken().isEmpty()) state = join(state, "配置只读");
            else if (item.inUse() > 0) state = join(state, "使用中");
            row(results, "settings", item.name(), item.description(), state,
                    () -> open(PluginDetailScreen.builtin(this, item.id(), scope)));
        }
    }

    private void renderExtensions() {
        ui.space(results, 24);
        heading(results, "扩展功能");
        sourceStatus(results, pages, "插件页面");
        if (pages.value != null) for (PluginSnapshot.Page page : pages.value) {
            row(results, "plug", page.title(), "", "", () -> openPage(page));
        }
        PluginSnapshot.Builtin library = builtin("library");
        if (library == null || library.on()) row(results, "library", "资料库", "管理指令、MCP、技能和资源", "", () -> advanced("library", null, null));
        row(results, "server", "Agent 配置", AppNavigation.remote(activity) ? "管理远程电脑上的 Agent" : "连接远程工作空间后可管理电脑上的 Agent", "", () -> openRemote("agents"));
        PluginSnapshot.Builtin sessions = builtin("sessions");
        if (sessions == null || sessions.on()) row(results, "clock", "会话", AppNavigation.remote(activity)
                ? "查看远程工作空间的会话" : "连接远程工作空间后可查看会话", "", () -> openRemote("sessions"));
        button(results, "高级插件管理", false, () -> advanced("plugins", null, null));
    }

    private void more(LinearLayout target, int total) {
        if (total > limit) button(target, "显示更多", false, () -> { limit += BATCH; renderResults(); });
    }

    private void sourceStatus(LinearLayout target, Resource<?> resource, String name) {
        if (!resource.error.isEmpty()) {
            note(target, name + "读取失败" + (resource.value == null ? "" : "，显示上次读取的数据") + "：" + resource.error, true);
            button(target, "重新读取", false, () -> {
                if (resource == search) search();
                else refresh(true);
            });
        } else if (resource.value == null && resource.pending) note(target, "正在读取" + name + "…", false);
    }

    private void search() {
        hideKeyboard(query);
        requestedQuery = queryText();
        search.clear();
        if (!requestedQuery.isEmpty()) requestSearch();
        renderResults();
    }

    private void requestSearch() {
        if (!ready()) return;
        String wanted = requestedQuery;
        read(search, MARKET_POLL, true, api -> PluginSnapshot.discovery(api.get("/api/plugins/search?q=" + encode(wanted)), PluginSnapshot.Feed.SEARCH));
    }

    private String queryText() { return query.getText().toString().trim(); }

    private void checkUpdates() {
        if (!ready() || !current(scope) || installed.value == null) return;
        read(updates, POLL, true, api -> {
            JSONObject checked = new JSONObject(api.post("/api/plugins/check", "{}"));
            return PluginSnapshot.installed(checked.getJSONObject("state").toString());
        });
    }

    private void open(AppScreen screen) {
        if (current(scope) && ready()) host.openDetail("插件详情", screen);
    }

    private void installDialog() {
        if (!writable(false)) return;
        Scope expected = scope;
        LinearLayout content = ui.column();
        content.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0);
        EditText input = input("npm 包名或 包名@版本", EditorInfo.IME_ACTION_DONE, 350);
        content.addView(input);
        String destination = AppNavigation.remote(activity) ? "远程工作空间" : "本机工作空间";
        note(content, "插件将在" + destination + "中运行。请核对包名与发布者。", false);
        AlertDialog install = new AlertDialog.Builder(activity, ui.dialogTheme).setTitle("从 npm 安装").setView(content)
                .setNegativeButton("取消", null).setPositiveButton("安装", null).create();
        show(install);
        install.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!current(expected) || !writable(false)) { install.dismiss(); return; }
            try {
                PluginOperation operation = PluginOperation.installNpm(input.getText().toString());
                if (operation.observed(installed.value, Collections.emptyList())) { input.setError("该插件已安装，可在已安装列表查看。"); return; }
                install.dismiss();
                select(0);
                submit(expected, operation);
            } catch (IllegalArgumentException e) { input.setError(e.getMessage()); }
        });
    }

    void confirm(Scope expected, PluginOperation operation, String message) {
        if (!current(expected) || !writable(operation.builtin())) return;
        show(new AlertDialog.Builder(activity, ui.dialogTheme).setTitle(operation.action() + " · " + cap(operation.name, 90))
                .setMessage(message).setNegativeButton("取消", null)
                .setPositiveButton(operation.action(), (d,w) -> submit(expected, operation)).create());
    }

    void show(AlertDialog next) {
        if (dialog != null) dialog.dismiss();
        dialog = next;
        next.setOnDismissListener(d -> { if (dialog == next) dialog = null; });
        next.show();
    }

    boolean writable(boolean builtin) {
        Resource<?> source = builtin ? builtins : installed;
        return current(scope) && ready() && source.value != null && source.error.isEmpty() && (attempt == null || !attempt.blocks());
    }

    void submit(Scope expected, PluginOperation operation) {
        if (!current(expected) || !writable(operation.builtin())) return;
        Attempt next = new Attempt(operation);
        attempt = next;
        notifyChange();
        new Thread(() -> {
            PluginSnapshot.Installed newInstalled = null;
            List<PluginSnapshot.Builtin> newBuiltins = null;
            Outcome outcome;
            String detail = "";
            try {
                String response = expected.api().post(operation.path(), operation.body().toString());
                if (operation.builtin()) newBuiltins = PluginSnapshot.builtins(response);
                else newInstalled = PluginSnapshot.installed(response);
                if (newBuiltins != null && newBuiltins.isEmpty()) throw new IllegalStateException("返回的内置插件列表为空");
                outcome = Outcome.SUCCESS;
                if (newInstalled != null) {
                    PluginSnapshot.Entry entry = operation.installedEntry(newInstalled);
                    detail = PluginSnapshot.first(entry == null ? "" : entry.error(), newInstalled.error());
                }
            } catch (CoreApi.HttpFailure e) {
                detail = coreError(e.body);
                outcome = e.status == 400 && !detail.isEmpty() ? Outcome.FAILED : Outcome.UNCERTAIN;
                if (detail.isEmpty()) detail = e.getMessage();
            } catch (Exception e) {
                outcome = Outcome.UNCERTAIN;
                detail = safeMessage(e, expected);
            }
            PluginSnapshot.Installed installedResult = newInstalled;
            List<PluginSnapshot.Builtin> builtinResult = newBuiltins;
            Outcome result = outcome;
            String info = redact(detail, expected);
            main.post(() -> finish(expected, next, result, info, installedResult, builtinResult));
        }, "magpie-plugin-operation").start();
    }

    private void finish(Scope expected, Attempt operation, Outcome result, String detail,
                        PluginSnapshot.Installed installedResult, List<PluginSnapshot.Builtin> builtinResult) {
        if (disposed || attempt != operation || !expected.same(scope) || !expected.same(new Scope(activity))) return;
        operation.outcome = result;
        operation.detail = detail;
        Resource<?> affected = operation.operation.builtin() ? builtins : installed;
        affected.invalidate();
        updates.clear();
        if (visible == null) return;
        if (result == Outcome.SUCCESS) {
            if (installedResult != null) { installed.value = installedResult; installed.error = ""; }
            if (builtinResult != null) { builtins.value = builtinResult; builtins.error = ""; }
        }
        notifyChange();
        refresh(true);
    }

    private void reconcile(boolean builtin, long readAt) {
        if (attempt == null || attempt.outcome != Outcome.UNCERTAIN || builtin != attempt.operation.builtin()) return;
        List<PluginSnapshot.Builtin> list = builtins.value == null ? Collections.emptyList() : builtins.value;
        if (attempt.operation.observed(installed.value, list)) {
            PluginSnapshot.Entry entry = attempt.operation.installedEntry(installed.value);
            attempt.outcome = Outcome.SUCCESS;
            attempt.detail = entry == null ? "" : entry.error();
        } else if (readAt >= attempt.operation.unknownUntil(attempt.startedAt)) attempt.checkedAfterDeadline = true;
    }

    void showOperation(LinearLayout target) {
        target.removeAllViews();
        if (attempt == null) return;
        ui.space(target, 14);
        String activity = attempt.operation.action();
        switch (attempt.outcome) {
            case RUNNING:
                heading(target, "正在" + activity + " · " + cap(attempt.operation.name, 100));
                ProgressBar progress = new ProgressBar(ui.a, null, android.R.attr.progressBarStyleHorizontal);
                progress.setIndeterminate(true);
                progress.setIndeterminateTintList(ColorStateList.valueOf(ui.accent));
                target.addView(progress, new LinearLayout.LayoutParams(-1, ui.dp(12)));
                break;
            case SUCCESS:
                note(target, activity + "完成 · " + cap(attempt.operation.name, 100), true);
                if (!attempt.detail.isEmpty()) note(target, "运行时提示：" + cap(attempt.detail, 1800), false);
                break;
            case FAILED:
                note(target, activity + "失败 · " + cap(attempt.operation.name, 100), true);
                note(target, attempt.detail, false);
                break;
            case UNCERTAIN:
                note(target, activity + "结果待确认 · " + cap(attempt.operation.name, 100), true);
                note(target, attempt.checkedAfterDeadline ? "当前列表尚未显示预期结果，请核对实际状态。"
                        : "网关可能仍在处理。可以刷新查看实际状态。", false);
                if (!attempt.detail.isEmpty()) note(target, attempt.detail, false);
                button(target, "确认当前状态", false, () -> refresh(true));
                break;
        }
    }

    PluginSnapshot.Entry entry(String spec) {
        PluginSnapshot.Installed data = updates.value == null ? installed.value : updates.value;
        return data == null ? null : data.find(spec);
    }

    PluginSnapshot.Entry entry(PluginSnapshot.Discovery plugin) { return installed.value == null ? null : installed.value.match(plugin); }

    PluginSnapshot.Builtin builtin(String id) {
        if (builtins.value != null) for (PluginSnapshot.Builtin item : builtins.value) if (item.id().equals(id)) return item;
        return null;
    }

    PluginSnapshot.Page page(String spec) {
        if (pages.value != null) for (PluginSnapshot.Page page : pages.value) if (page.spec().equals(spec)) return page;
        return null;
    }

    String detailStatus(boolean builtin) {
        Resource<?> data = builtin ? builtins : installed;
        if (!ready()) return "工作空间暂不可用。";
        if (!data.error.isEmpty()) return "读取失败" + (data.value == null ? "" : "，显示上次读取的数据") + "：" + data.error;
        return data.value == null ? "正在读取插件状态…" : "当前工作空间找不到此插件。";
    }

    boolean stale(boolean builtin) { return !(builtin ? builtins : installed).error.isEmpty(); }
    boolean activeAttempt() { return attempt != null && attempt.blocks(); }

    void openPage(PluginSnapshot.Page page) { advanced(page.key(), "pageTitle", page.title()); }

    void openRemote(String destination) {
        if (!current(scope)) return;
        if (AppNavigation.remote(activity)) { advanced(destination, null, null); return; }
        String title = destination.equals("agents") ? "Agent 配置" : "会话";
        show(new AlertDialog.Builder(activity, ui.dialogTheme).setTitle(title)
                .setMessage("此功能用于远程电脑。连接远程工作空间后即可打开。")
                .setPositiveButton("确定", null).create());
    }

    void advanced(String view, String key, String value) {
        if (!current(scope) || !ready()) return;
        if (key == null) host.advanced(view);
        else host.advanced(view, key, value);
    }

    void advancedPlugin(String key, String value) {
        if (activeAttempt()) {
            Toast.makeText(activity, "请先确认当前插件操作的结果。", Toast.LENGTH_SHORT).show();
            return;
        }
        advanced("plugins", key, value);
    }

    void button(LinearLayout target, String label, boolean primary, Runnable action) {
        ui.space(target, 8);
        target.addView(ui.button(label, primary, action));
    }

    void heading(LinearLayout target, String text) {
        TextView title = ui.text(text, 18, ui.ink, true);
        title.setAccessibilityHeading(true);
        target.addView(title);
    }

    void note(LinearLayout target, String text, boolean prominent) {
        if (text.isEmpty()) return;
        TextView label = ui.text(text, 15, prominent ? ui.ink : ui.muted, prominent);
        label.setPadding(0, ui.dp(5), 0, ui.dp(5));
        target.addView(label);
    }

    private void row(LinearLayout target, String icon, String title, String subtitle, String detail, Runnable open) {
        LinearLayout row = ui.row();
        row.setPadding(0, ui.dp(12), 0, ui.dp(12));
        row.setMinimumHeight(ui.dp(56));
        row.setBackground(ui.ripple(Color.TRANSPARENT, 12));
        row.addView(ui.iconTile(icon, 38));
        ui.gap(row, 10);
        LinearLayout labels = ui.column();
        TextView name = ui.text(title, 16, ui.ink, true);
        name.setMaxLines(3);
        name.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(name);
        if (!subtitle.isEmpty()) {
            ui.space(labels, 4);
            TextView sub = ui.text(subtitle, 15, ui.muted, false);
            sub.setMaxLines(2);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(sub);
        }
        if (!detail.isEmpty()) {
            ui.space(labels, 4);
            labels.addView(ui.text(detail, 14, ui.muted, false));
        }
        ui.grow(row, labels);
        ui.gap(row, 8);
        row.addView(new LineIcon(activity, "chevron", ui.muted), new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)));
        row.setContentDescription(join(title, subtitle, detail));
        row.setOnClickListener(v -> open.run());
        target.addView(row);
        ui.rule(target, 0);
    }

    EditText input(String hint, int ime, int max) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setTextColor(ui.ink);
        input.setHintTextColor(ui.muted);
        input.setTextSize(16);
        input.setTypeface(ui.font(false, 400));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setSingleLine(true);
        input.setMinHeight(ui.dp(48));
        input.setImeOptions(ime);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        return input;
    }

    private void hideKeyboard(EditText input) {
        if (input.hasFocus()) {
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(input.getWindowToken(), 0);
            input.clearFocus();
        }
    }

    static String encode(String text) throws java.io.UnsupportedEncodingException {
        return URLEncoder.encode(text, StandardCharsets.UTF_8.name()).replace(".", "%2E");
    }

    static String cap(String text, int max) { return text.length() <= max ? text : text.substring(0, max) + "…"; }

    static String join(String... text) {
        List<String> parts = new ArrayList<>();
        for (String word : text) if (!word.isEmpty()) parts.add(word);
        return String.join(" · ", parts);
    }

    private static String safeMessage(Exception error, Scope scope) {
        return redact(PluginSnapshot.first(error.getMessage() == null ? "" : error.getMessage(), "请求未完成"), scope);
    }

    private static String redact(String text, Scope scope) {
        return cap(scope.key.isEmpty() ? text : text.replace(scope.key, "[密钥]"), 1800);
    }

    private static String coreError(String body) {
        try { return PluginSnapshot.word(new JSONObject(body), "error"); }
        catch (Exception ignored) { return ""; }
    }
}
