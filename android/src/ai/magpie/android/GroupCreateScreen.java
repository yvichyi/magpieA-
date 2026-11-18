package ai.magpie.android;

import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Creates a common routing group using the existing core save contract. */
final class GroupCreateScreen implements AppScreen {
    private static final String[] STRATEGIES = {"智能调度", "顺序备用", "轮询", "免费优先"};
    private static final String[] ROUTING = {"", "order", "rotate", "free"};
    private final CatalogScreen catalog;
    private final Ui ui;
    private final long scope;
    private final CatalogLoad.Scope origin;
    private final ScrollView scroll;
    private final LinearLayout members;
    private final TextView feedback;
    private final EditText name;
    private final Button save;
    private final Set<String> selected = new LinkedHashSet<>();
    private final String id = "android-" + UUID.randomUUID().toString();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable observer = this::modelsChanged;
    private CatalogData displayed;
    private int strategy;
    private int limit = 30;
    private boolean active, pending, uncertain, saved, discard;

    GroupCreateScreen(CatalogScreen catalog, long scope) {
        this.catalog = catalog;
        ui = catalog.ui;
        this.scope = scope;
        origin = catalog.repository.load.scope;
        scroll = CatalogViews.scroll(ui);
        LinearLayout body = CatalogViews.body(ui, scroll, 32);
        body.addView(ui.text("分组名称", 15, ui.ink, true));
        name = new EditText(ui.a);
        name.setSingleLine(true);
        name.setTextSize(16);
        name.setContentDescription("分组名称");
        name.setHint("例如：常用模型");
        name.setTextColor(ui.ink);
        name.setHintTextColor(ui.muted);
        name.setMinHeight(ui.dp(48));
        name.setPadding(ui.dp(14), 0, ui.dp(14), 0);
        name.setBackground(ui.shape(ui.card, 12, true));
        body.addView(name, new LinearLayout.LayoutParams(-1, -2));
        ui.space(body, 18);
        body.addView(ui.text("路由策略", 15, ui.ink, true));
        Button route = ui.button(STRATEGIES[0], false, () -> {});
        route.setOnClickListener(v -> new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle("路由策略")
                .setSingleChoiceItems(STRATEGIES, strategy, (d, which) -> {
                    strategy = which;
                    route.setText(STRATEGIES[which]);
                    d.dismiss();
                }).setNegativeButton("取消", null).show());
        body.addView(route);
        ui.space(body, 18);
        body.addView(ui.text("成员模型", 18, ui.ink, true));
        body.addView(ui.text("选择顺序决定顺序备用策略的优先级。", 13, ui.muted, false));
        members = ui.column();
        body.addView(members);
        ui.space(body, 12);
        save = ui.button("保存分组", true, this::save);
        body.addView(save);
        feedback = ui.text("", 14, ui.muted, false);
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        body.addView(feedback);
        body.addView(CatalogViews.action(ui, "高级分组编辑器", () -> {
            if (!pending && valid()) catalog.host.advanced("groups", "addGroup", "true");
        }));
        modelsChanged();
    }

    @Override public View view() { return scroll; }
    @Override public void onResume() { active = true; catalog.repository.resume(observer); }
    @Override public void onPause() { active = false; catalog.repository.pause(observer); }

    private boolean valid() { return catalog.repository.current(scope, true) && !catalog.repository.load.failed; }

    private void modelsChanged() {
        CatalogData data = catalog.repository.load.data;
        if (uncertain && data != null) {
            for (CatalogData.Group group : data.groups("")) {
                if (group.group.id.equals(id)) {
                    uncertain = false;
                    saved = true;
                    feedback.setText("分组已保存，可返回查看。");
                    break;
                }
            }
        }
        save.setEnabled(!pending && !uncertain && !saved && data != null && !catalog.repository.load.failed);
        if (data == displayed && members.getChildCount() > 0) return;
        displayed = data;
        members.removeAllViews();
        if (data == null) return;
        List<CatalogData.Model> available = data.models("", 1, java.util.Collections.emptySet(), "");
        if (available.isEmpty()) members.addView(ui.text("当前没有可接入的模型。", 14, ui.muted, false));
        for (int i = 0; i < Math.min(limit, available.size()); i++) {
            CatalogData.Model entry = available.get(i);
            CheckBox option = new CheckBox(ui.a);
            option.setMinHeight(ui.dp(48));
            option.setText(entry.model.name + "\n" + entry.model.reference);
            option.setTextSize(14);
            option.setTextColor(ui.ink);
            option.setChecked(selected.contains(entry.model.reference));
            option.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(entry.model.reference);
                else selected.remove(entry.model.reference);
            });
            members.addView(option);
        }
        if (available.size() > limit) members.addView(CatalogViews.action(ui, "显示更多模型", () -> {
            limit += 30;
            displayed = null;
            modelsChanged();
        }));
    }

    private void save() {
        if (pending || uncertain || saved || !valid()) return;
        String label = name.getText().toString().trim();
        if (label.isEmpty() || label.length() > 100) { name.setError("请输入 1–100 字的分组名称"); return; }
        if (selected.isEmpty()) { feedback.setText("至少选择一个模型。"); return; }
        CatalogData data = catalog.repository.load.data;
        for (String ref : selected) {
            CatalogData.Model model = data == null ? null : data.model(ref);
            if (model == null || !model.connectable) { feedback.setText("有成员已不可接入，请更新选择。"); return; }
        }
        final String payload;
        try {
            payload = new JSONObject().put("id", id).put("name", label)
                    .put("routing", ROUTING[strategy]).put("members", new JSONArray(selected)).toString();
        } catch (Exception invalid) {
            feedback.setText("分组内容无效。");
            return;
        }
        pending = true;
        save.setEnabled(false);
        feedback.setText("正在保存分组…");
        new Thread(() -> {
            String status = "保存结果待确认。请返回分组目录刷新查看。";
            boolean confirmed = false, definitive = false;
            try {
                CoreApi api = new CoreApi(origin.origin, origin.key);
                JSONArray groups = new JSONObject(api.post("/api/groups/save", payload)).getJSONArray("groups");
                for (int i = 0; i < groups.length(); i++) if (id.equals(groups.getJSONObject(i).optString("id"))) confirmed = true;
            } catch (CoreApi.HttpFailure response) {
                if (response.status == 400) { status = "网关拒绝了分组配置，请检查名称和成员。"; definitive = true; }
            } catch (Exception ignored) { /* No replay after a write with an unknown outcome. */ }
            boolean done = confirmed, failed = definitive;
            String message = status;
            main.post(() -> {
                pending = false;
                saved = done;
                uncertain = !done && !failed;
                if (!active || !origin.matches(catalog.repository.observedScope())) return;
                if (done) {
                    Toast.makeText(ui.a, "分组已保存", Toast.LENGTH_SHORT).show();
                    ui.a.onBackPressed();
                } else {
                    feedback.setText(message);
                    save.setEnabled(failed);
                    if (uncertain) catalog.repository.refresh();
                }
            });
        }, "magpie-group-save").start();
    }

    @Override public boolean onBack() {
        if (pending) { Toast.makeText(ui.a, "请等待保存完成", Toast.LENGTH_SHORT).show(); return true; }
        if (discard || saved || uncertain || (name.getText().length() == 0 && selected.isEmpty() && strategy == 0)) return false;
        new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle("放弃本次编辑？")
                .setNegativeButton("继续编辑", null).setPositiveButton("放弃", (d, w) -> {
                    discard = true;
                    ui.a.onBackPressed();
                }).show();
        return true;
    }
}
