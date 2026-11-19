package ai.magpie.android;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Retained models, groups and providers in the current workspace. */
final class CatalogScreen implements AppScreen {
    static final int SETUP_PROVIDER = 17;
    private static final int MODELS = 0;
    private static final int PROVIDERS = 1;
    private static final int GROUPS = 2;
    private static final int PAGE_SIZE = 30;
    private static final String[] TABS = {"模型", "服务商", "分组"};
    private static final String[] FILTERS = {"全部", "可接入", "收藏"};

    private static final class Tab {
        String query = "";
        int scroll;
        int limit = PAGE_SIZE;
    }

    private static final class WorkspaceView {
        final Tab[] tabs = {new Tab(), new Tab(), new Tab()};
        int selected = MODELS;
        int filter;
        String provider = "";
    }

    final ScreenHost host;
    final Ui ui;
    final CatalogRepository repository;
    private final SharedPreferences favoriteStore;
    private final Map<String, WorkspaceView> workspaces = new HashMap<>();
    private final ScrollView scroll;
    private final LinearLayout results, stale;
    private final TextView[] tabButtons = new TextView[TABS.length];
    private final EditText search;
    private final TextView filter, status, providerFocus;
    private final Button add;
    private final View searchTools, focus, refresh, reconnect, retry;
    private final Runnable observer = () -> render(false);
    private WorkspaceView workspace;
    private CatalogData shown;
    private String shownWorkspace = "";
    private CatalogLoad.Scope setupScope;
    private int found;
    private boolean settingQuery, needsRender = true, shownReady, shownFailed, restoringScroll, refreshAfterGroupEditor;

    private record AppearanceState(Map<String, WorkspaceView> workspaces) {}

    CatalogScreen(ScreenHost host) {
        this.host = host;
        ui = host.ui();
        repository = new CatalogRepository(host.activity());
        favoriteStore = host.activity().getSharedPreferences("model-favorites", 0);
        scroll = CatalogViews.scroll(ui);
        LinearLayout body = CatalogViews.body(ui, scroll, 96);

        LinearLayout tabs = ui.row();
        for (int i = 0; i < TABS.length; i++) {
            int selected = i;
            tabButtons[i] = CatalogViews.tab(ui, TABS[i], () -> selectTab(selected));
            tabs.addView(tabButtons[i], new LinearLayout.LayoutParams(0, -2, 1));
        }
        body.addView(tabs);
        ui.rule(body, 0);

        search = new EditText(host.activity());
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(ui.ink);
        search.setHintTextColor(ui.muted);
        search.setTypeface(ui.font(false, 400));
        search.setMinHeight(ui.dp(48));
        search.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8));
        search.setBackground(ui.field(ui.soft, 12));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            dismissKeyboard();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (settingQuery || workspace == null) return;
                tab().query = s.toString();
                tab().limit = PAGE_SIZE;
                render(true);
                scroll.scrollTo(0, 0);
            }
            @Override public void afterTextChanged(Editable value) {}
        });

        LinearLayout actions = ui.row();
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        filter = CatalogViews.action(ui, "", this::chooseFilter);
        actions.addView(filter);
        add = ui.button("添加服务商", false, this::addSelected);
        actions.addView(add);
        refresh = ui.iconButton("refresh", "刷新目录", repository::refresh);
        actions.addView(refresh, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        searchTools = new AdaptivePair(ui, search, actions, 6);
        searchTools.setPadding(0, ui.dp(12), 0, 0);
        body.addView(searchTools);

        providerFocus = ui.text("", Ui.Type.LABEL);
        focus = new AdaptivePair(ui, providerFocus,
                CatalogViews.action(ui, "查看全部", this::clearProviderFocus), 8);
        body.addView(focus);

        stale = ui.column();
        stale.setPadding(0, ui.dp(12), 0, 0);
        status = ui.text("", Ui.Type.SUPPORT);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        stale.addView(status);
        reconnect = CatalogViews.action(ui, "连接工作区", host::openWorkspace);
        stale.addView(reconnect);
        retry = CatalogViews.action(ui, "重试读取目录", repository::refresh);
        stale.addView(retry);
        body.addView(stale);
        results = ui.column();
        body.addView(results);
        scroll.requestFocus();
        render(true);
    }

    @Override public View view() { return scroll; }

    @Override public Object saveAppearanceState() { return new AppearanceState(new HashMap<>(workspaces)); }

    @Override public void restoreAppearanceState(Object state) {
        if (!(state instanceof AppearanceState saved)) return;
        workspaces.clear();
        workspaces.putAll(saved.workspaces());
        workspace = null;
        restoringScroll = true;
        render(false);
    }

    @Override public void onResume() {
        boolean refresh = refreshAfterGroupEditor;
        refreshAfterGroupEditor = false;
        repository.resume(observer, refresh);
    }

    @Override public void onPause() {
        if (workspace != null) tab().scroll = scroll.getScrollY();
        repository.pause(observer);
    }

    @Override public void dispose() { repository.dispose(); }

    @Override public boolean onBack() {
        if (search.hasFocus()) {
            dismissKeyboard();
            return true;
        }
        if (workspace != null && workspace.selected == MODELS && !workspace.provider.isEmpty()) {
            clearProviderFocus();
            return true;
        }
        return false;
    }

    void refresh() { repository.refresh(); }

    int selectedTab() { return workspace.selected; }

    void openTab(int index) {
        if (index >= 0 && index < TABS.length) selectTab(index);
    }

    void openProviders() { selectTab(PROVIDERS); }

    void focusProvider(String id) {
        if (id == null || id.isEmpty() || id.length() > 160) return;
        CatalogLoad.Scope current = repository.observedScope();
        CatalogLoad.Scope expected = setupScope;
        setupScope = null;
        if (expected != null && !expected.matches(current)) return;
        WorkspaceView target = workspaces.computeIfAbsent(current.workspace, key -> new WorkspaceView());
        target.selected = MODELS;
        target.provider = id;
        target.filter = 0;
        Tab models = target.tabs[MODELS];
        models.query = "";
        models.scroll = 0;
        models.limit = PAGE_SIZE;
        if (workspace == target) {
            dismissKeyboard();
            bindSearch();
            render(true);
            scroll.scrollTo(0, 0);
        }
        repository.refresh();
    }

    private Tab tab() { return workspace.tabs[workspace.selected]; }

    private void selectTab(int index) {
        if (workspace.selected == index) return;
        tab().scroll = scroll.getScrollY();
        dismissKeyboard();
        workspace.selected = index;
        bindSearch();
        render(true);
        restoreScroll(tab().scroll);
    }

    private void selectFilter(int filter) {
        if (workspace.filter == filter) return;
        workspace.filter = filter;
        tab().limit = PAGE_SIZE;
        render(true);
        scroll.scrollTo(0, 0);
    }

    private void chooseFilter() {
        dismissKeyboard();
        new AlertDialog.Builder(host.activity(), ui.dialogTheme).setTitle("筛选模型")
                .setSingleChoiceItems(FILTERS, workspace.filter, (dialog, selected) -> {
                    dialog.dismiss();
                    selectFilter(selected);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void clearProviderFocus() {
        workspace.provider = "";
        tab().limit = PAGE_SIZE;
        render(true);
        scroll.scrollTo(0, 0);
    }

    private void bindSearch() {
        settingQuery = true;
        String hint = "搜索" + TABS[workspace.selected];
        search.setHint(hint);
        search.setContentDescription(hint);
        if (!tab().query.contentEquals(search.getText())) search.setText(tab().query);
        settingQuery = false;
    }

    private void dismissKeyboard() {
        if (search.hasFocus()) {
            InputMethodManager ime = host.activity().getSystemService(InputMethodManager.class);
            if (ime != null) ime.hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
        }
        scroll.requestFocus();
    }

    private void restoreScroll(int position) {
        Tab target = tab();
        scroll.post(() -> {
            if (tab() == target) scroll.scrollTo(0, position);
        });
    }

    private void render(boolean force) {
        CatalogLoad load = repository.load;
        String name = load.scope == null ? repository.observedScope().workspace : load.scope.workspace;
        boolean newWorkspace = workspace == null || !shownWorkspace.equals(name);
        if (newWorkspace) {
            if (workspace != null) tab().scroll = scroll.getScrollY();
            shownWorkspace = name;
            workspace = workspaces.computeIfAbsent(name, key -> new WorkspaceView());
            bindSearch();
        }
        boolean ready = repository.ready();
        if (force || newWorkspace || needsRender || shown != load.data || shownReady != ready || shownFailed != load.failed) {
            int position = newWorkspace || restoringScroll ? tab().scroll : scroll.getScrollY();
            shown = load.data;
            shownReady = ready;
            shownFailed = load.failed;
            needsRender = false;
            renderControls();
            renderResults();
            if (shown != null) restoringScroll = false;
            if (!force) restoreScroll(position);
        }
        refresh.setEnabled(!repository.loading());
        refresh.setAlpha(repository.loading() ? .5f : 1);
    }

    private void renderControls() {
        boolean models = workspace.selected == MODELS;
        for (int i = 0; i < tabButtons.length; i++) {
            CatalogViews.selectTab(ui, tabButtons[i], i == workspace.selected);
        }
        boolean searchable = shown != null && (hasEntries() || !tab().query.trim().isEmpty());
        searchTools.setVisibility(searchable ? View.VISIBLE : View.GONE);
        filter.setVisibility(models ? View.VISIBLE : View.GONE);
        filter.setText(FILTERS[workspace.filter] + " ⌄");
        filter.setContentDescription("筛选模型，当前" + FILTERS[workspace.filter]);
        add.setText(workspace.selected == GROUPS ? "新建分组" : "添加服务商");
        add.setVisibility(!models && canAdd() ? View.VISIBLE : View.GONE);
        refresh.setVisibility(shownReady ? View.VISIBLE : View.GONE);
        refresh.setContentDescription(shownFailed ? "重试读取目录" : "刷新目录");

        boolean focused = models && !workspace.provider.isEmpty();
        focus.setVisibility(focused && shown != null ? View.VISIBLE : View.GONE);
        if (focused) {
            CatalogData.Provider provider = shown == null ? null : shown.provider(workspace.provider);
            providerFocus.setText("服务商 · " + (provider == null ? workspace.provider : provider.provider.name));
        }
        stale.setVisibility(shown != null && (!shownReady || shownFailed) ? View.VISIBLE : View.GONE);
        status.setText(shownReady ? "读取失败 · 显示上次读取" : "工作区未连接 · 显示上次读取");
        reconnect.setVisibility(!shownReady ? View.VISIBLE : View.GONE);
        retry.setVisibility(shownReady && !searchable ? View.VISIBLE : View.GONE);
    }

    private boolean hasEntries() {
        return switch (workspace.selected) {
            case GROUPS -> !shown.snapshot.groups.isEmpty();
            case PROVIDERS -> !shown.snapshot.providers.isEmpty();
            default -> {
                CatalogData.Provider provider = shown.provider(workspace.provider);
                if (!workspace.provider.isEmpty()) yield provider != null && !provider.provider.models.isEmpty();
                yield shown.snapshot.providers.stream().anyMatch(source -> !source.models.isEmpty());
            }
        };
    }

    private void renderResults() {
        results.removeAllViews();
        found = 0;
        if (shown == null) {
            if (!shownReady) {
                empty("连接后查看" + TABS[workspace.selected], "启动本机网关，或连接已有的远程工作区。");
                results.addView(ui.button("连接工作区", true, host::openWorkspace));
            } else if (shownFailed) {
                empty("暂时无法读取目录", "重试读取，或检查工作区连接。");
                results.addView(ui.button("重试读取", true, repository::refresh));
                results.addView(CatalogViews.action(ui, "连接工作区", host::openWorkspace));
            } else empty("正在读取" + TABS[workspace.selected] + "…", "");
            return;
        }
        long scope = repository.load.scopeVersion;
        String query = tab().query;
        int limit = tab().limit;
        switch (workspace.selected) {
            case GROUPS: {
                List<CatalogData.Group> groups = shown.groups(query);
                found = groups.size();
                for (int i = 0; i < Math.min(limit, found); i++) {
                    CatalogData.Group group = groups.get(i);
                    addRow(group.group.name, group.group.reference, group.strategy() + " · "
                            + group.status(), () -> openGroup(group, scope));
                }
                break;
            }
            case PROVIDERS: {
                List<CatalogData.Provider> providers = shown.providers(query);
                found = providers.size();
                for (int i = 0; i < Math.min(limit, found); i++) {
                    CatalogData.Provider provider = providers.get(i);
                    addRow(provider.provider.name, provider.provider.id, provider.status()
                            + " · " + provider.provider.models.size() + " 个模型", () -> openProvider(provider, scope));
                }
                break;
            }
            default: {
                Set<String> favorites = favorites();
                List<CatalogData.Model> models = shown.models(query, workspace.filter, favorites, workspace.provider);
                found = models.size();
                for (int i = 0; i < Math.min(limit, found); i++) {
                    CatalogData.Model model = models.get(i);
                    String note = model.status();
                    if (favorites.contains(model.model.reference)) note += " · 已收藏";
                    addRow(model.model.name, model.model.reference, note, () -> openModel(model, scope));
                }
                break;
            }
        }
        if (found == 0) renderEmptyResults();
        if (found > limit) results.addView(CatalogViews.action(ui, "显示更多（剩余 " + (found - limit) + " 项）", () -> {
            tab().limit += PAGE_SIZE;
            render(true);
        }));
    }

    private void renderEmptyResults() {
        boolean filtered = !tab().query.trim().isEmpty();
        if (filtered) {
            empty("没有匹配的" + TABS[workspace.selected], "可搜索名称或完整标识。");
            results.addView(CatalogViews.action(ui, "清除搜索", () -> search.setText("")));
        } else if (workspace.selected == MODELS && workspace.filter != 0) {
            empty(workspace.filter == 2 ? "还没有收藏的模型" : "暂无可直接接入的模型", "");
            results.addView(CatalogViews.action(ui, "查看全部模型", () -> selectFilter(0)));
        } else if (workspace.selected == MODELS && !workspace.provider.isEmpty()) {
            empty("该服务商暂未列出模型", "打开服务商详情可检查模型配置。");
            CatalogData.Provider provider = shown.provider(workspace.provider);
            if (provider != null) {
                long scope = repository.load.scopeVersion;
                results.addView(CatalogViews.action(ui, "查看服务商", () -> openProvider(provider, scope)));
            }
        } else if (workspace.selected == GROUPS) {
            empty("暂无分组", "新建分组后，可将多个模型作为一个路由接入。");
            if (canAdd()) results.addView(ui.button("新建分组", true, this::addSelected));
        } else if (workspace.selected == PROVIDERS || shown.snapshot.providers.isEmpty()) {
            empty("尚未配置服务商", "添加服务商后，可读取并管理模型。");
            if (canAdd()) results.addView(ui.button("添加服务商", true, this::addSelected));
        } else {
            empty("暂无模型", "打开服务商配置可检查或更新模型清单。");
            results.addView(ui.button("管理服务商", true, this::openProviders));
        }
    }

    private boolean canAdd() {
        return shownReady && (workspace.selected != GROUPS || !shownFailed);
    }

    private void empty(String title, String note) {
        ui.space(results, 24);
        results.addView(ui.text(title, Ui.Type.SECTION));
        if (!note.isEmpty()) {
            ui.space(results, 8);
            results.addView(ui.text(note, Ui.Type.SUPPORT));
        }
        ui.space(results, 12);
    }

    private void addRow(String title, String reference, String note, Runnable open) {
        results.addView(CatalogViews.row(ui, title, reference, note, open));
        ui.rule(results, 0);
    }

    private void addSelected() {
        long scope = repository.load.scopeVersion;
        if (!repository.current(scope, true)) {
            Toast.makeText(host.activity(), "请等待当前工作空间就绪", Toast.LENGTH_SHORT).show();
            return;
        }
        dismissKeyboard();
        if (workspace.selected == GROUPS) {
            if (shown == null || shownFailed) { repository.refresh(); return; }
            refreshAfterGroupEditor = true;
            host.openDetail("新建分组", new GroupCreateScreen(this, scope));
        } else {
            setupScope = repository.load.scope;
            UiMotion.result(host.activity(), new Intent(host.activity(), ProviderSetupActivity.class)
                    .putExtra("remote", AppNavigation.remote(host.activity())), SETUP_PROVIDER);
        }
    }

    private Set<String> favorites() {
        return favoriteStore.getStringSet(shownWorkspace, Collections.emptySet());
    }

    boolean favorite(String reference) { return favorites().contains(reference); }

    void toggleFavorite(long scope, String reference) {
        if (!repository.current(scope, false)) return;
        Set<String> updated = new HashSet<>(favorites());
        if (!updated.remove(reference)) updated.add(reference);
        favoriteStore.edit().putStringSet(shownWorkspace, updated).apply();
        needsRender = true;
    }

    void openModel(CatalogData.Model model, long scope) {
        if (!repository.current(scope, false)) return;
        dismissKeyboard();
        host.openDetail("模型详情", new CatalogDetailScreen(this, CatalogDetailScreen.Kind.MODEL, model.model.reference, scope));
    }

    private void openGroup(CatalogData.Group group, long scope) {
        if (!repository.current(scope, false)) return;
        dismissKeyboard();
        host.openDetail("分组详情", new CatalogDetailScreen(this, CatalogDetailScreen.Kind.GROUP, group.group.reference, scope));
    }

    private void openProvider(CatalogData.Provider provider, long scope) {
        if (!repository.current(scope, false)) return;
        dismissKeyboard();
        host.openDetail("服务商详情", new CatalogDetailScreen(this, CatalogDetailScreen.Kind.PROVIDER, provider.provider.id, scope));
    }
}
