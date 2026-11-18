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
    private FrameLayout content;
    private LinearLayout header;
    private String rootId = "connect";
    private CatalogLoad.Scope workspaceScope;
    private boolean resumed, launchingSettings;

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
        LinearLayout scene = ui.column();
        scene.setBackgroundColor(ui.bg);
        header = ui.row();
        header.setMinimumHeight(ui.dp(60));
        header.setPadding(ui.dp(16), ui.dp(4), ui.dp(12), ui.dp(4));
        scene.addView(header);
        content = new FrameLayout(this);
        scene.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        glass.setScene(scene);
        setContentView(glass);
        workspaceScope = scope();
        showRoot(rootId);

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
        if (getIntent().getBooleanExtra("showFunctions", false)) main.post(this::settings);
    }

    @Override protected void onResume() {
        super.onResume();
        UiMotion.enter(this);
        resumed = true;
        launchingSettings = false;
        glass.refreshOptions();
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
            attach(false);
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
        workspaceScope = current;
        showRoot(rootId);
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
        switch (id) {
            case "home": return new CatalogScreen(this);
            case "plugins": return new PluginsScreen(this);
            case "usage": return new InsightsScreen(this);
            default: return new ConnectScreen(this);
        }
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
        showRoot(target);
    }

    private void showRoot(String id) {
        rootId = id;
        roots.computeIfAbsent(id, key -> new Destination(create(key)));
        attach(false);
    }

    @Override public void openDetail(String title, AppScreen screen) {
        UiMotion.stay(this);
        pausePage();
        destination().pages.push(new Page(screen, title));
        attach(true);
    }

    private void attach(boolean enteringDetail) {
        Destination root = destination();
        View page = root.page().screen.view();
        if (page.getParent() instanceof ViewGroup) ((ViewGroup) page.getParent()).removeView(page);
        content.removeAllViews();
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        renderHeader(root);
        glass.setDock(root.detail() ? null : ui.bottomNav(rootId, this::openRoot));
        if (enteringDetail && UiMotion.enabled(this)) {
            page.setAlpha(.93f);
            page.animate().alpha(1f).setDuration(120).setInterpolator(UiMotion.EASE).start();
        }
        if (resumed) root.page().screen.onResume();
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
        attach(false);
    }

    @Override public void refresh() {
        if (resetIfWorkspaceChanged()) return;
        pausePage();
        renderHeader(destination());
        glass.refreshOptions();
        if (resumed) destination().page().screen.onResume();
    }
}
