package ai.magpie.android;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class RequestDetailScreen implements AppScreen {
    private final Ui ui;
    private final InsightsScope scope;
    private final LedgerSnapshot.Row request;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScrollView scroll;
    private final LinearLayout content;
    private LinearLayout conversation, route;
    private boolean active;
    private int generation;

    RequestDetailScreen(ScreenHost host, InsightsScope scope, LedgerSnapshot.Row request) {
        ui = host.ui();
        this.scope = scope;
        this.request = request;
        scroll = new ScrollView(ui.a);
        content = InsightsViews.content(ui, scroll, 30);
        scroll.setSaveEnabled(false);
    }

    @Override
    public View view() { return scroll; }

    @Override
    public void onResume() {
        active = true;
        content.removeAllViews();
        if (!scope.isCurrent(ui.a)) {
            content.addView(InsightsViews.note(ui, "工作空间当前不可读取。返回请求列表刷新。"));
            return;
        }
        renderMetadata();
        route = ui.column();
        conversation = ui.column();
        content.addView(route);
        content.addView(conversation);
        resetRoute();
        resetConversation();
    }

    @Override
    public void onPause() {
        active = false;
        generation++;
        // Sensitive results only live in the visible detail view.
        if (route != null) route.removeAllViews();
        if (conversation != null) conversation.removeAllViews();
        content.removeAllViews();
    }

    private void renderMetadata() {
        content.addView(InsightsViews.title(ui, request.model.isEmpty() ? "未记录模型" : request.model, 20));
        ui.space(content, 6);
        content.addView(new AdaptivePair(ui, ui.text(request.statusLabel(), Ui.Type.ROW),
                InsightsViews.note(ui, request.sourceLabel()), 8));
        field(content, "请求时间", request.time.atZoneSameInstant(ZoneId.of(scope.zone))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX", Locale.ROOT)));
        if (request.status > 0) field(content, "HTTP 状态", Long.toString(request.status));
        field(content, "错误", request.error);

        InsightsViews.section(ui, content, "用量与耗时");
        InsightsViews.pair(ui, content,
                InsightsViews.metric(ui, "Token 总量（含已知缓存）", InsightsViews.count(request.tokens())),
                InsightsViews.metric(ui, "总耗时", InsightsViews.time(request.durationMs())));
        ui.space(content, 6);
        field(content, "首 Token 耗时", InsightsViews.time(request.firstTokenMs()));
        field(content, "输入 / 输出", InsightsViews.count(request.input) + " / " + InsightsViews.count(request.output));
        field(content, "缓存读取 / 写入", request.cacheKnown()
                ? InsightsViews.count(request.cacheRead) + " / " + InsightsViews.count(request.cacheWrite) : "缓存计量未知");
        field(content, "费用", request.priceLabel() + (request.priced ? " USD" : ""));

        InsightsViews.section(ui, content, "请求信息");
        field(content, "服务商", request.providerName);
        field(content, "请求的模型", request.requested);
        field(content, "应答模型", request.served);
        field(content, "客户端", request.agentName);
        field(content, "账号", request.account);
        field(content, "网关密钥名称", request.callerKey);
        field(content, "设备", request.computerName);
        field(content, "会话标识", request.session);
        if (request.routeId > 0) field(content, "路由标识", Long.toString(request.routeId));
        if (request.tries > 0) field(content, "尝试次数", Long.toString(request.tries));
    }

    private void resetRoute() {
        route.removeAllViews();
        if (request.routePath() == null) return;
        InsightsViews.section(ui, route, "网关路由");
        route.addView(InsightsViews.action(ui, "读取路由尝试", this::loadRoute));
    }

    private void loadRoute() {
        LinearLayout target = route;
        load(request.routePath(), target, "正在读取路由…", "路由记录不可用，点击重试", this::loadRoute, object -> {
            target.removeAllViews();
            InsightsViews.section(ui, target, "网关路由");
            field(target, "请求的模型", object.optString("model"));
            field(target, "服务商", object.optString("provider"));
            JSONObject group = object.optJSONObject("group");
            if (group != null) field(target, "路由组", group.optString("name", group.optString("id")));
            JSONArray tries = object.optJSONArray("tries");
            if (tries == null || tries.length() == 0) {
                target.addView(InsightsViews.note(ui, "当前路由未记录尝试。"));
            } else {
                for (int i = 0; i < tries.length(); i++) {
                    JSONObject attempt = tries.optJSONObject(i);
                    if (attempt == null) continue;
                    ui.rule(target, 0);
                    ui.space(target, 10);
                    target.addView(InsightsViews.title(ui, "尝试 " + (i + 1), 16));
                    field(target, "目标", attempt.optString("model", attempt.optString("id")));
                    field(target, "应答模型", attempt.optString("served"));
                    int status = attempt.optInt("status");
                    if (status != 0) field(target, "HTTP 状态", Integer.toString(status));
                    field(target, "耗时", positiveTime(attempt.optLong("ms")));
                    field(target, "首 Token 耗时", positiveTime(attempt.optLong("ttft")));
                    field(target, "失败原因", attempt.optString("error", attempt.optString("fail")));
                }
            }
            target.addView(InsightsViews.action(ui, "收起路由", this::resetRoute));
        });
    }

    private void resetConversation() {
        conversation.removeAllViews();
        InsightsViews.section(ui, conversation, "会话内容");
        String unavailable = request.contentUnavailable();
        if (unavailable.isEmpty()) {
            conversation.addView(InsightsViews.action(ui, "显示请求和响应内容", this::loadConversation));
        } else {
            ui.space(conversation, 8);
            conversation.addView(InsightsViews.note(ui, unavailable));
        }
    }

    private void loadConversation() {
        LinearLayout target = conversation;
        load(request.contentPath(), target, "正在读取会话内容…", "会话读取失败，点击重试",
                this::loadConversation, object -> {
            target.removeAllViews();
            InsightsViews.section(ui, target, "会话内容");
            if (object.optBoolean("found")) {
                showParts(target, "请求", object.optJSONArray("input"));
                showParts(target, "响应", object.optJSONArray("output"));
                if (object.optBoolean("cut")) {
                    target.addView(InsightsViews.note(ui, "服务端省略了部分内容。"));
                }
                target.addView(InsightsViews.action(ui, "隐藏会话内容", this::resetConversation));
            } else {
                String why = object.optString("why");
                String message = "会话文件未找到这条请求。";
                if (why.equals("session")) message = "这条请求没有会话标识。";
                else if (why.equals("agent")) message = "此客户端的会话文件不可读取。";
                else if (why.equals("read")) message = "无法读取会话文件。";
                target.addView(InsightsViews.note(ui, message));
                target.addView(InsightsViews.action(ui, "重新读取", this::loadConversation));
            }
        });
    }

    private void showParts(LinearLayout target, String heading, JSONArray parts) {
        ui.space(target, 10);
        target.addView(InsightsViews.title(ui, heading, 16));
        if (parts == null || parts.length() == 0) {
            target.addView(InsightsViews.note(ui, "没有记录内容。"));
            return;
        }
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;
            String label = part.optString("role") + " · " + part.optString("kind");
            String name = part.optString("name");
            if (!name.isEmpty()) label += " · " + name;
            String text = part.optString("text");
            if (part.optInt("cut") > 0) text += "\n…";
            field(target, label, text);
        }
    }

    private void load(String path, LinearLayout target, String loading, String failed,
                      Runnable retry, Consumer<JSONObject> complete) {
        if (path == null || !active || !scope.isCurrent(ui.a)) {
            target.removeAllViews();
            target.addView(InsightsViews.note(ui, "当前工作空间无法读取这条记录。"));
            return;
        }
        int stamp = generation;
        target.removeAllViews();
        target.addView(InsightsViews.note(ui, loading));
        new Thread(() -> {
            try {
                JSONObject result = new JSONObject(new CoreApi(scope.origin, scope.key).get(path));
                main.post(() -> {
                    if (accept(stamp)) complete.accept(result);
                });
            } catch (java.io.IOException | JSONException error) {
                main.post(() -> {
                    if (!accept(stamp)) return;
                    target.removeAllViews();
                    target.addView(InsightsViews.action(ui, failed, retry));
                });
            }
        }, "magpie-request-detail").start();
    }

    private boolean accept(int stamp) {
        return active && stamp == generation && !ui.a.isFinishing() && !ui.a.isDestroyed()
                && scope.isCurrent(ui.a);
    }

    private void field(LinearLayout target, String label, String value) {
        InsightsViews.field(ui, target, label, value);
    }

    private static String positiveTime(long value) {
        return InsightsViews.time(value > 0 ? value : null);
    }
}
