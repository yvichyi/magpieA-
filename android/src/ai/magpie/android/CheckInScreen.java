package ai.magpie.android;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

/** Native check-in actions and latest responses for the current management origin. */
final class CheckInScreen implements AppScreen {
    private static final String SETTINGS = "/api/settings";
    private static final String DISABLED = "/api/usage/checkins/off";
    private static final String CHECK_IN = "/api/usage/checkin-account";
    private static final String ACCOUNT_SETTING = "/api/settings/checkin-account";

    private final ScreenHost host;
    private final Ui ui;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScrollView scroll;
    private final LinearLayout body;
    private final Map<CheckInSnapshot.AccountId, Attempt> attempts = new HashMap<>();
    private volatile InsightsScope scope;
    private volatile boolean disposed;
    private CheckInSnapshot directory;
    private String disabledAccounts = "[]";
    private String error = "", notice = "", warning = "";
    private boolean active, loading, writing;
    private int total, received, checkedIn;
    private final Runnable checkReadiness = new Runnable() {
        @Override public void run() {
            if (!active || disposed) return;
            if (updateScope()) {
                if (scope.ready) refresh();
                else render();
            }
            main.postDelayed(this, 1000);
        }
    };

    private record Attempt(CheckInSnapshot.AccountId id, CheckInSnapshot.Result result, String error) {}

    CheckInScreen(ScreenHost host) {
        this.host = host;
        ui = host.ui();
        scroll = CatalogViews.scroll(ui);
        body = CatalogViews.body(ui, scroll, 28);
    }

    @Override public View view() { return scroll; }

    @Override public void onResume() {
        active = true;
        updateScope();
        if (scope.ready && !writing) refresh();
        else render();
        main.removeCallbacks(checkReadiness);
        main.postDelayed(checkReadiness, 1000);
    }

    private boolean updateScope() {
        InsightsScope next = InsightsScope.current(host.activity());
        if (scope != null && scope.equals(next) && scope.ready == next.ready) return false;
        scope = next;
        directory = null;
        disabledAccounts = "[]";
        error = notice = warning = "";
        loading = writing = false;
        total = 0;
        attempts.clear();
        return true;
    }

    @Override public void onPause() {
        active = false;
        main.removeCallbacks(checkReadiness);
    }

    @Override public void dispose() {
        disposed = true;
        onPause();
    }

    private boolean current(InsightsScope expected) {
        return !disposed && scope == expected && expected.isCurrent(host.activity());
    }

    private void refresh() {
        InsightsScope expected = scope;
        if (!current(expected) || loading || writing) return;
        loading = true;
        error = "";
        render();
        new Thread(() -> {
            try {
                CoreApi api = new CoreApi(expected.origin, expected.key);
                String settings = api.get(SETTINGS);
                String off = "[]";
                String offWarning = "";
                try { off = api.get(DISABLED); }
                catch (Exception ignored) { offWarning = "暂时无法确认哪些服务商已禁用。"; }
                String offReply = off;
                String offStatus = offWarning;
                CheckInSnapshot snapshot = CheckInSnapshot.parse(settings, off);
                main.post(() -> {
                    if (!current(expected)) return;
                    directory = snapshot;
                    disabledAccounts = offReply;
                    warning = offStatus;
                    loading = false;
                    render();
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (!current(expected)) return;
                    loading = false;
                    error = "读取签到账号失败 · " + describe(failure, expected);
                    render();
                });
            }
        }, "magpie-checkin-read").start();
    }

    private void render() {
        if (!active) return;
        int y = scroll.getScrollY();
        body.removeAllViews();
        if (!scope.ready) {
            body.addView(ui.text("连接工作区后查看可签到的账号。", Ui.Type.BODY));
            addButton("连接工作区", true, host::openWorkspace);
            return;
        }

        String status = writing ? total > 0 ? "正在签到 " + received + "/" + total : "正在保存自动签到设置…"
                : loading ? "正在读取签到账号…" : !error.isEmpty() ? error : notice;
        if (!status.isEmpty()) {
            TextView state = ui.text(status, Ui.Type.SUPPORT);
            state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            body.addView(state);
            ui.space(body, 10);
        }
        if (directory == null) {
            if (!loading) {
                addButton("重新读取", true, this::refresh);
                addButton("更改工作区", false, host::openWorkspace);
            }
            return;
        }
        if (!warning.isEmpty()) {
            body.addView(ui.text(warning, Ui.Type.SUPPORT));
            ui.space(body, 8);
        }
        if (directory.vendors().isEmpty()) {
            body.addView(ui.text("暂无可签到的账号", Ui.Type.SECTION));
            ui.space(body, 6);
            body.addView(ui.text("登录支持签到的订阅服务商后，账号会显示在这里。", Ui.Type.BODY));
            if (!loading) addButton("查看服务商", true, () -> host.advanced("providers"));
            return;
        }

        LocalDate today = CheckInSnapshot.today();
        List<CheckInSnapshot.Account> due = new ArrayList<>();
        for (CheckInSnapshot.Vendor vendor : directory.vendors()) {
            for (CheckInSnapshot.Account account : vendor.accounts()) {
                CheckInSnapshot.Result result = result(account);
                if (result == null || result.due(account.id().by(), today)) due.add(account);
            }
        }
        boolean available = !loading && !writing;
        View reload = ui.button("刷新状态", false, this::refresh);
        reload.setEnabled(available);
        if (due.size() > 1) {
            View all = ui.button("全部签到", true, () -> confirmAll(due));
            all.setEnabled(available);
            body.addView(new AdaptivePair(ui, all, reload, 8));
        } else body.addView(reload);

        for (CheckInSnapshot.Vendor vendor : directory.vendors()) {
            ui.space(body, 20);
            TextView heading = ui.text(vendor.name(), Ui.Type.SECTION);
            heading.setAccessibilityHeading(true);
            body.addView(heading);
            if (!vendor.settingsPath().isEmpty()) {
                automaticSwitch("服务商自动签到", vendor.automatic(), available,
                        value -> saveAutomatic(vendor, null, value));
            }
            for (CheckInSnapshot.Account account : vendor.accounts()) {
                ui.rule(body, 0);
                renderAccount(vendor, account, today, available);
            }
        }
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void renderAccount(CheckInSnapshot.Vendor vendor, CheckInSnapshot.Account account,
                               LocalDate today, boolean available) {
        LinearLayout details = ui.column();
        String user = account.user().isEmpty() ? "订阅账号" : account.user();
        String title = account.provider().isEmpty() || account.provider().equals(vendor.name()) ? user : account.provider() + " · " + user;
        details.addView(ui.text(title, Ui.Type.ROW));
        ui.space(details, 4);
        Attempt attempt = attempts.get(account.id());
        CheckInSnapshot.Result result = result(account);
        String status = result == null ? "今日尚未签到" : result.label(today);
        if (attempt != null && !attempt.error().isEmpty()) status = attempt.error();
        if (account.providerOff()) status = "服务商已禁用 · " + status;
        details.addView(ui.text(clean(status, scope), Ui.Type.SUPPORT));
        ui.space(body, 10);
        if (result == null || result.due(account.id().by(), today)) {
            View check = ui.button("签到", true, () -> performCheckIns(java.util.Collections.singletonList(account)));
            check.setEnabled(available);
            check.setContentDescription("签到 " + title);
            body.addView(new AdaptivePair(ui, details, check, 10));
        } else body.addView(details);
        if (!vendor.settingsPath().isEmpty()) {
            automaticSwitch("参与自动签到", account.automatic(), available,
                    value -> saveAutomatic(vendor, account, value));
        } else ui.space(body, 10);
    }

    private void automaticSwitch(String label, boolean value, boolean enabled, java.util.function.Consumer<Boolean> save) {
        Switch control = new Switch(host.activity());
        control.setText(label);
        ui.typography(control, Ui.Type.ROW);
        control.setTextColor(ui.ink);
        control.setMinHeight(ui.dp(48));
        control.setSwitchPadding(ui.dp(12));
        control.setChecked(value);
        control.setEnabled(enabled);
        control.setOnCheckedChangeListener((button, checked) -> save.accept(checked));
        body.addView(control, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addButton(String title, boolean primary, Runnable action) {
        ui.space(body, 8);
        body.addView(ui.button(title, primary, action));
    }

    private CheckInSnapshot.Result result(CheckInSnapshot.Account account) {
        Attempt attempt = attempts.get(account.id());
        return attempt == null || attempt.result() == null ? account.result() : attempt.result();
    }

    private void confirmAll(List<CheckInSnapshot.Account> due) {
        InsightsScope expected = scope;
        new AlertDialog.Builder(host.activity(), ui.dialogTheme).setTitle("全部签到")
                .setMessage("为 " + due.size() + " 个待签到账号提交签到。范围包含已禁用服务商的账号。")
                .setNegativeButton("取消", null)
                .setPositiveButton("签到", (dialog, which) -> {
                    if (current(expected)) performCheckIns(due);
                }).show();
    }

    private void performCheckIns(List<CheckInSnapshot.Account> accounts) {
        InsightsScope expected = scope;
        if (!current(expected) || writing || loading || accounts.isEmpty()) return;
        writing = true;
        total = accounts.size();
        received = checkedIn = 0;
        error = notice = "";
        render();
        new Thread(() -> {
            ExecutorService workers = Executors.newFixedThreadPool(Math.min(3, accounts.size()));
            CompletionService<Attempt> results = new ExecutorCompletionService<>(workers);
            try {
                for (CheckInSnapshot.Account account : accounts) results.submit(() -> checkOne(expected, account.id()));
                for (int i = 0; i < accounts.size(); i++) {
                    Attempt result = results.take().get();
                    main.post(() -> recordAttempt(expected, result));
                }
            } catch (Exception failure) {
                main.post(() -> {
                    if (!current(expected)) return;
                    error = "签到结果待确认 · " + describe(failure, expected);
                });
            } finally {
                workers.shutdownNow();
                main.post(() -> {
                    if (!current(expected)) return;
                    notice = total == 1 ? "" : "已处理 " + received + " 个账号 · " + checkedIn + " 个今日已签到";
                    writing = false;
                    total = 0;
                    render();
                });
            }
        }, "magpie-checkin-submit").start();
    }

    private Attempt checkOne(InsightsScope expected, CheckInSnapshot.AccountId account) {
        if (!current(expected)) return new Attempt(account, null, "");
        try {
            JSONObject json = new JSONObject().put("by", account.by()).put("key", account.key());
            CoreApi api = new CoreApi(expected.origin, expected.key);
            CheckInSnapshot.Result result = CheckInSnapshot.Result.parse(new JSONObject(api.post(CHECK_IN, json.toString())));
            return new Attempt(account, result, "");
        } catch (Exception failure) {
            String prefix = failure instanceof CoreApi.HttpFailure ? "签到失败" : "签到结果待确认";
            return new Attempt(account, null, prefix + " · " + describe(failure, expected));
        }
    }

    private void recordAttempt(InsightsScope expected, Attempt attempt) {
        if (!current(expected)) return;
        attempts.put(attempt.id(), attempt);
        received++;
        CheckInSnapshot.Result result = attempt.result();
        if (result != null && !result.again() && result.day().equals(CheckInSnapshot.today().toString())
                && (result.outcome().equals("done") || result.outcome().equals("claimed"))) checkedIn++;
        render();
    }

    private void saveAutomatic(CheckInSnapshot.Vendor vendor, CheckInSnapshot.Account account, boolean on) {
        InsightsScope expected = scope;
        if (!current(expected) || writing || loading) return;
        String path = account == null ? vendor.settingsPath() : ACCOUNT_SETTING;
        String disabled = disabledAccounts;
        writing = true;
        error = notice = "";
        render();
        new Thread(() -> {
            try {
                if (!current(expected)) return;
                JSONObject json = new JSONObject().put("on", on);
                if (account != null) json.put("key", account.id().key());
                else if (path.equals("/api/settings/plugin-checkin")) json.put("provider", vendor.by().substring("plugin:".length()));
                CoreApi api = new CoreApi(expected.origin, expected.key);
                CheckInSnapshot snapshot = CheckInSnapshot.parse(api.post(path, json.toString()), disabled);
                main.post(() -> {
                    if (!current(expected)) return;
                    directory = snapshot;
                    notice = "自动签到设置已保存";
                });
            } catch (Exception failure) {
                main.post(() -> {
                    if (!current(expected)) return;
                    error = "无法确认设置已保存 · " + describe(failure, expected);
                });
            } finally {
                main.post(() -> {
                    if (!current(expected)) return;
                    writing = false;
                    render();
                });
            }
        }, "magpie-checkin-settings").start();
    }

    private static String describe(Exception failure, InsightsScope scope) {
        if (failure instanceof JSONException) return "网关返回了无法识别的签到数据";
        if (failure instanceof CoreApi.HttpFailure response) {
            try {
                String error = new JSONObject(response.body).optString("error");
                if (!error.isEmpty()) return clean(error, scope);
            } catch (JSONException ignored) {}
        }
        return failure.getMessage() == null ? "请求未完成" : clean(failure.getMessage(), scope);
    }

    private static String clean(String message, InsightsScope scope) {
        String value = scope.key.isEmpty() ? message : message.replace(scope.key, "[密钥]");
        return value.length() <= 380 ? value : value.substring(0, 380) + "…";
    }
}
