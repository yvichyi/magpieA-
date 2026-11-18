package ai.magpie.android;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** The four native destinations retain their own detail and scroll context. */
public final class MainActivity extends Activity implements ScreenHost {
    private final Map<String, Destination> roots = new LinkedHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable checkWorkspace = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            resetIfWorkspaceChanged();
            main.postDelayed(this, 1000);
        }
    };
    private Ui ui;
    private GlassFrame glass;
    private GlassNavigation navigation;
    private FrameLayout content;
    private LinearLayout header;
    private String rootId = "connect";
    private CatalogLoad.Scope workspaceScope;
    private Map<String, Object> retainedAppearanceState;
    private boolean resumed, launchingSettings;

    private enum PageMotion { NONE, TAB, PUSH, POP }
    private record RetainedAppearance(CatalogLoad.Scope scope, Map<String, Object> screens) {}

    private static final class Page {
        final AppScreen screen;
        final String title;

        Page(AppScreen screen, String title) {
            this.screen = screen;
            this.title = title;
        }
    }

    private static final class Destination {
        final ArrayDeque<Page> pages = new ArrayDeque<>();

        Destination(AppScreen root) { pages.push(new Page(root, "")); }
        Page page() { return pages.getFirst(); }
        AppScreen base() { return pages.getLast().screen; }
        boolean detail() { return pages.size() > 1; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        if (state != null) rootId = normalize(state.getString("root"));
        else rootId = initialRoot(getIntent(), "connect");
        glass = new GlassFrame(this);
        header = ui.row();
        header.setMinimumHeight(ui.dp(60));
        header.setPadding(ui.dp(ui.swiss() ? 20 : 14), ui.dp(4), ui.dp(6), ui.dp(4));
        content = new FrameLayout(this);
        glass.setScene(content);
        glass.setToolbar(header);
        header.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> updatePageInset());
        navigation = new GlassNavigation(ui, this::openRoot);
        setContentView(glass);
        workspaceScope = scope();
        if (getLastNonConfigurationInstance() instanceof RetainedAppearance previous
                && previous.scope().matches(workspaceScope)) retainedAppearanceState = previous.screens();
        showRoot(rootId, PageMotion.NONE);

        SharedPreferences prefs = getSharedPreferences("host", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("askedNotifications", false)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("askedNotifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (!AppNavigation.remote(this) && !prefs.getBoolean("launched", false)) {
            prefs.edit().putBoolean("launched", true).apply();
            new HostActions(this, ui, this::refresh).start();
        }
        if (state == null && getIntent().getBooleanExtra("showFunctions", false)) main.post(this::settings);
    }

    @Override protected void onResume() {
        super.onResume();
        if (applyAppearance()) return;
        UiMotion.enter(this);
        resumed = true;
        launchingSettings = false;
        glass.refreshOptions();
        navigation.select(rootId, false);
        if (!resetIfWorkspaceChanged()) destination().page().screen.onResume();
        main.removeCallbacks(checkWorkspace);
        main.postDelayed(checkWorkspace, 1000);
    }

    @Override protected void onPause() {
        pausePage();
        UiMotion.pause(this);
        resumed = false;
        main.removeCallbacks(checkWorkspace);
        super.onPause();
    }

    @Override protected void onDestroy() {
        disposePages();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("root", rootId);
        super.onSaveInstanceState(state);
    }

    @Override public Object onRetainNonConfigurationInstance() {
        if (Ui.appearance(this) == ui.appearance) return null;
        Map<String, Object> screens = new LinkedHashMap<>();
        if (retainedAppearanceState != null) screens.putAll(retainedAppearanceState);
        for (Map.Entry<String, Destination> entry : roots.entrySet()) {
            Object state = entry.getValue().base().saveAppearanceState();
            if (state != null) screens.put(entry.getKey(), state);
        }
        return new RetainedAppearance(workspaceScope, screens);
    }

    private boolean applyAppearance() {
        if (Ui.appearance(this) == ui.appearance || destination().detail()) return false;
        recreate();
        return true;
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("showFunctions", false)) settings();
        else openRoot(initialRoot(intent, rootId));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (LedgerExport.onActivityResult(this, request, result, data)) return;
        if (request == CatalogScreen.SETUP_PROVIDER && result == RESULT_OK) {
            openRoot("home");
            if (data != null) ((CatalogScreen) destination().base()).focusProvider(data.getStringExtra("providerId"));
        }
    }

    @Override public void onBackPressed() {
        Destination root = destination();
        if (root.page().screen.onBack()) return;
        if (root.detail()) {
            root.pages.pop().screen.dispose();
            if (!applyAppearance()) attach(PageMotion.POP);
        } else if (!rootId.equals("connect")) {
            openRoot("connect");
        } else {
            super.onBackPressed();
        }
    }

    private static String normalize(String id) {
        return Arrays.asList(NavigationModel.ROOTS).contains(id) ? id : "connect";
    }

    private static String initialRoot(Intent intent, String fallback) {
        if (intent.hasExtra("root")) return normalize(intent.getStringExtra("root"));
        return intent.hasExtra("catalogTab") ? "home" : fallback;
    }

    private CatalogLoad.Scope scope() {
        return new CatalogLoad.Scope(AppNavigation.workspace(this), AppNavigation.origin(this), AppNavigation.key(this));
    }

    private Destination destination() { return roots.get(rootId); }

    private boolean resetIfWorkspaceChanged() {
        CatalogLoad.Scope current = scope();
        if (current.matches(workspaceScope)) return false;
        disposePages();
        retainedAppearanceState = null;
        workspaceScope = current;
        showRoot(rootId, PageMotion.NONE);
        return true;
    }

    private void disposePages() {
        for (Destination destination : roots.values()) {
            for (Page page : destination.pages) page.screen.dispose();
        }
        roots.clear();
    }

    private void pausePage() {
        if (resumed && destination() != null) destination().page().screen.onPause();
    }

    private AppScreen create(String id) {
        AppScreen screen = switch (id) {
            case "home" -> new CatalogScreen(this);
            case "plugins" -> new PluginsScreen(this);
            case "usage" -> new InsightsScreen(this);
            default -> new ConnectScreen(this);
        };
        if (retainedAppearanceState != null && workspaceScope.matches(scope())) {
            screen.restoreAppearanceState(retainedAppearanceState.remove(id));
        }
        return screen;
    }

    @Override public Activity activity() { return this; }
    @Override public Ui ui() { return ui; }
    public String currentRootId() { return rootId; }
    String catalogSection() { return rootId; }

    @Override public void openRoot(String id) {
        if ("tools".equals(id)) {
            settings();
            return;
        }
        String target = normalize(id);
        if (target.equals(rootId)) return;
        launchingSettings = false;
        UiMotion.stay(this);
        pausePage();
        showRoot(target, PageMotion.TAB);
        applyAppearance();
    }

    private void showRoot(String id, PageMotion motion) {
        rootId = id;
        roots.computeIfAbsent(id, key -> new Destination(create(key)));
        attach(motion);
    }

    @Override public void openDetail(String title, AppScreen screen) {
        UiMotion.stay(this);
        pausePage();
        destination().pages.push(new Page(screen, title));
        attach(PageMotion.PUSH);
    }

    private void attach(PageMotion motion) {
        Destination root = destination();
        View page = root.page().screen.view();
        View previous = content.getChildAt(0);
        if (previous != null) UiMotion.reset(previous);
        if (page.getParent() instanceof ViewGroup) ((ViewGroup) page.getParent()).removeView(page);
        content.removeAllViews();
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        renderHeader(root);
        glass.setDock(root.detail() ? null : navigation);
        navigation.select(rootId, motion == PageMotion.TAB && !root.detail());
        updatePageInset();
        int direction = page.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
        if (motion != PageMotion.NONE) UiMotion.page(page,
                motion == PageMotion.TAB ? 0 : ui.dp(30) * direction * (motion == PageMotion.POP ? -1 : 1),
                motion == PageMotion.TAB ? ui.dp(14) : 0);
        if (resumed) root.page().screen.onResume();
    }

    private void updatePageInset() {
        View page = content.getChildAt(0);
        if (page == null) return;
        int inset = Math.max(header.getMinimumHeight(), header.getHeight()) + glass.topGap() + ui.dp(3);
        if (page instanceof InsetScrollView) {
            content.setPadding(0, 0, 0, 0);
            ((InsetScrollView) page).setToolbarInset(inset);
        } else if (content.getPaddingTop() != inset) {
            content.setPadding(0, inset, 0, 0);
        }
    }

    private void renderHeader(Destination root) {
        header.removeAllViews();
        if (root.detail()) {
            header.addView(ui.iconButton("back", "返回上一页", this::onBackPressed));
        } else {
            LineIcon bird = new LineIcon(this, "brand", ui.ink);
            header.addView(bird, new LinearLayout.LayoutParams(ui.dp(24), ui.dp(24)));
            ui.gap(header, 10);
        }
        TextView title = ui.text(root.detail() ? root.page().title : "Magpie", 20, ui.ink, true);
        if (root.detail()) {
            title.setMaxLines(2);
        } else {
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            title.setSingleLine(true);
            if (ui.swiss()) title.setTypeface(ui.font(true, 700));
        }
        title.setEllipsize(TextUtils.TruncateAt.END);
        ui.grow(header, title);
        if (root.detail()) return;
        TextView workspace = ui.text(AppNavigation.remote(this) ? "远程 ⌄" : "本机 ⌄", 13, ui.ink, true);
        workspace.setGravity(Gravity.CENTER);
        workspace.setPadding(ui.dp(12), 0, ui.dp(12), 0);
        workspace.setMinHeight(ui.dp(48));
        workspace.setBackground(ui.glassControl(false));
        workspace.setContentDescription("选择工作空间");
        workspace.setOnClickListener(view -> new HostActions(this, ui, this::refresh).workspace());
        UiMotion.press(workspace);
        header.addView(workspace);
        ui.gap(header, 4);
        header.addView(ui.iconButton("settings", "打开设置", this::settings));
    }

    private void settings() {
        if (launchingSettings || isFinishing()) return;
        launchingSettings = true;
        UiMotion.go(this, new Intent(this, ToolsActivity.class).putExtra("parentRoot", rootId), false);
    }

    @Override public void advanced(String view) { AppNavigation.detail(this, view); }
    @Override public void advanced(String view, String key, String value) { AppNavigation.detail(this, view, key, value); }

    @Override public void connect(String reference) {
        pausePage();
        rootId = "connect";
        Destination root = roots.computeIfAbsent(rootId, key -> new Destination(create(key)));
        while (root.detail()) root.pages.pop().screen.dispose();
        ((ConnectScreen) root.base()).setTarget(reference);
        attach(PageMotion.TAB);
    }

    @Override public void refresh() {
        if (resetIfWorkspaceChanged()) return;
        pausePage();
        renderHeader(destination());
        glass.refreshOptions();
        if (resumed) destination().page().screen.onResume();
    }
}
