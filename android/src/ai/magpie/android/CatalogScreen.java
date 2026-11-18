package ai.magpie.android;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
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
    private static final int GROUPS = 1;
    private static final int PROVIDERS = 2;
    private static final int PAGE_SIZE = 30;
    private static final String[] TABS = {"模型", "分组", "服务商"};
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
    private final LinearLayout filters, results, focus;
    private final TextView[] tabButtons = new TextView[TABS.length];
    private final TextView[] filterButtons = new TextView[FILTERS.length];
    private final EditText search;
    private final TextView summary, providerFocus;
    private final Button add;
    private final View refresh;
    private final Runnable observer = () -> render(false);
    private WorkspaceView workspace;
    private CatalogData shown;
    private String shownWorkspace = "";
    private CatalogLoad.Scope setupScope;
    private int found;
    private boolean settingQuery, needsRender = true, shownReady, shownFailed, restoringScroll;

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
            tabButtons[i] = CatalogViews.action(ui, TABS[i], () -> selectTab(selected));
            tabButtons[i].setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(6));
            tabs.addView(tabButtons[i], new LinearLayout.LayoutParams(0, -2, 1));
        }
        body.addView(tabs);
        ui.space(body, 12);

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
        body.addView(search, new LinearLayout.LayoutParams(-1, -2));
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

        filters = ui.row();
        for (int i = 0; i < FILTERS.length; i++) {
            int filter = i;
            filterButtons[i] = CatalogViews.action(ui, FILTERS[i], () -> selectFilter(filter));
            filterButtons[i].setPadding(ui.dp(2), ui.dp(6), ui.dp(2), ui.dp(6));
            filters.addView(filterButtons[i], new LinearLayout.LayoutParams(0, -2, 1));
        }
        body.addView(filters);

        providerFocus = ui.text("", 14, ui.ink, false);
        focus = ui.row();
        ui.grow(focus, providerFocus);
        focus.addView(CatalogViews.action(ui, "查看全部", this::clearProviderFocus));
        body.addView(focus);

        LinearLayout toolbar = ui.row();
        summary = ui.text("", 14, ui.muted, false);
        summary.setMinHeight(ui.dp(40));
        summary.setGravity(android.view.Gravity.CENTER_VERTICAL);
        summary.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        ui.grow(toolbar, summary);
        refresh = ui.iconButton("refresh", "刷新目录", repository::refresh);
        toolbar.addView(refresh, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        add = ui.button("添加服务商", false, this::addSelected);
        body.addView(new AdaptivePair(ui, toolbar, add, 8));
        ui.space(body, 8);
        ui.rule(body, 0);
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
        needsRender = true;
        repository.resume(observer);
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
        if (workspace.selected == MODELS && !workspace.provider.isEmpty()) {
            clearProviderFocus();
            return true;
        }
        return false;
    }

    void refresh() { repository.refresh(); }

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
        renderSummary();
    }

    private void renderControls() {
        boolean models = workspace.selected == MODELS;
        for (int i = 0; i < tabButtons.length; i++) styleSelection(tabButtons[i], i == workspace.selected);
        for (int i = 0; i < filterButtons.length; i++) styleSelection(filterButtons[i], i == workspace.filter);
        filters.setVisibility(models ? View.VISIBLE : View.GONE);
        boolean focused = models && !workspace.provider.isEmpty();
        focus.setVisibility(focused ? View.VISIBLE : View.GONE);
        if (focused) {
            CatalogData.Provider provider = shown == null ? null : shown.provider(workspace.provider);
            providerFocus.setText("当前服务商 · " + (provider == null ? workspace.provider : provider.provider.name));
        }
        add.setText(workspace.selected == GROUPS ? "新建分组" : "添加服务商");
        add.setEnabled(shownReady);
        add.setAlpha(shownReady ? 1 : .5f);
    }

    private void styleSelection(TextView button, boolean selected) {
        button.setSelected(selected);
        button.setTextColor(selected ? ui.accent : ui.muted);
        button.setTypeface(ui.font(false, selected ? 600 : 400));
        button.setBackground(ui.ripple(selected ? (ui.swiss() ? ui.accentSoft : ui.soft) : Color.TRANSPARENT, 12));
    }

    private void renderSummary() {
        String label;
        if (shown == null) {
            label = shownFailed ? "读取失败" : shownReady ? "正在读取目录…" : "工作空间未就绪";
        } else {
            label = found + " 个" + TABS[workspace.selected];
            if (shownFailed || !shownReady) label += " · 上次读取";
        }
        summary.setText(label);
        boolean refreshEnabled = shownReady && !repository.loading();
        refresh.setEnabled(refreshEnabled);
        refresh.setAlpha(refreshEnabled ? 1 : .5f);
        refresh.setContentDescription(shownFailed ? "重试读取目录" : "刷新目录");
    }

    private void renderResults() {
        results.removeAllViews();
        found = 0;
        if (shown == null) {
            if (shownFailed) empty("暂时无法读取目录", "可刷新重试，或检查当前工作空间的连接。");
            else if (!shownReady) {
                empty("等待网关就绪", "启动本机网关或连接远程工作空间后，即可查看目录。");
                results.addView(CatalogViews.action(ui, "前往接入", () -> host.openRoot("connect")));
            }
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
                    addRow(group.group.name, group.group.reference + "\n" + group.strategy() + " · "
                            + group.status(), () -> openGroup(group, scope));
                }
                break;
            }
            case PROVIDERS: {
                List<CatalogData.Provider> providers = shown.providers(query);
                found = providers.size();
                for (int i = 0; i < Math.min(limit, found); i++) {
                    CatalogData.Provider provider = providers.get(i);
                    addRow(provider.provider.name, provider.provider.id + "\n" + provider.status()
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
                    addRow(model.model.name, model.model.reference + "\n" + note, () -> openModel(model, scope));
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
            empty("该服务商暂未列出模型", "可刷新目录或在服务商编辑器中检查配置。");
        } else if (workspace.selected == GROUPS) {
            empty("暂无分组", "新建分组后，可将多个模型作为一个路由接入。");
        } else {
            empty("暂无" + TABS[workspace.selected], "添加服务商后，可读取并配置模型。");
        }
    }

    private void empty(String title, String note) {
        ui.space(results, 24);
        results.addView(ui.text(title, 20, ui.ink, true));
        if (!note.isEmpty()) {
            ui.space(results, 8);
            results.addView(ui.text(note, 15, ui.muted, false));
        }
        ui.space(results, 12);
    }

    private void addRow(String title, String note, Runnable open) {
        results.addView(CatalogViews.row(ui, title, note, open));
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
