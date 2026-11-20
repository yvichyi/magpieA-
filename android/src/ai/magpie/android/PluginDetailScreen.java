package ai.magpie.android;

import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Native plugin details and explicit operations against the owning workspace. */
final class PluginDetailScreen implements AppScreen {
    private final PluginsScreen owner;
    private final PluginsScreen.Scope scope;
    private final Ui ui;
    private final String id;
    private final boolean builtin;
    private final PluginSnapshot.Discovery discovery;
    private final ScrollView scroll;
    private final LinearLayout content;

    static PluginDetailScreen installed(PluginsScreen owner, String spec, PluginsScreen.Scope scope) {
        return new PluginDetailScreen(owner, spec, false, null, scope);
    }

    static PluginDetailScreen builtin(PluginsScreen owner, String id, PluginsScreen.Scope scope) {
        return new PluginDetailScreen(owner, id, true, null, scope);
    }

    static PluginDetailScreen discovery(PluginsScreen owner, PluginSnapshot.Discovery entry, PluginsScreen.Scope scope) {
        return new PluginDetailScreen(owner, entry.spec(), false, entry, scope);
    }

    private PluginDetailScreen(PluginsScreen owner, String id, boolean builtin, PluginSnapshot.Discovery discovery,
                               PluginsScreen.Scope scope) {
        this.owner = owner;
        this.id = id;
        this.builtin = builtin;
        this.discovery = discovery;
        this.scope = scope;
        ui = new Ui(owner.activity);
        scroll = CatalogViews.scroll(ui);
        content = CatalogViews.body(ui, scroll, 28);
    }

    @Override public View view() { return scroll; }
    @Override public void onResume() { owner.activate(this, this::render); }
    @Override public void onPause() { owner.deactivate(this); }

    private void render() {
        int y = scroll.getScrollY();
        content.removeAllViews();
        if (!owner.current(scope)) return;
        if (builtin) renderBuiltin(owner.builtin(id));
        else {
            PluginSnapshot.Entry entry = discovery == null ? owner.entry(id) : owner.entry(discovery);
            if (entry != null) renderInstalled(entry);
            else if (discovery != null) renderDiscovery(discovery);
            else {
                owner.note(content, owner.detailStatus(false), true);
                showOperation();
            }
        }
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void showOperation() {
        LinearLayout operation = ui.column();
        operation.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(operation);
        owner.showOperation(operation);
    }

    private void title(String text, String note) {
        TextView title = ui.text(PluginsScreen.cap(text, 180), Ui.Type.PAGE);
        if (!text.contentEquals(title.getText())) title.setContentDescription(text);
        content.addView(title);
        owner.note(content, note, false);
        ui.space(content, 12);
    }

    private void field(String label, String value) {
        if (value.isEmpty()) return;
        ui.rule(content, 0);
        LinearLayout line = ui.column();
        line.setPadding(0, ui.dp(10), 0, ui.dp(10));
        line.addView(ui.text(label, Ui.Type.LABEL));
        ui.space(line, 3);
        Ui.Type type = label.equals("安装来源") || label.equals("代码仓库") ? Ui.Type.IDENTIFIER : Ui.Type.BODY;
        TextView data = ui.text(PluginsScreen.cap(value, 1500), type);
        data.setTextIsSelectable(true);
        line.addView(data);
        content.addView(line);
    }

    private void action(String name, boolean primary, boolean allowed, Runnable action) {
        Button button = primary ? ui.button(name, true, action) : ui.textButton(name, action);
        button.setEnabled(allowed);
        if (primary) ui.space(content, 8);
        else {
            button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            button.setPadding(0, ui.dp(6), 0, ui.dp(6));
        }
        content.addView(button);
    }

    private void renderInstalled(PluginSnapshot.Entry entry) {
        String state = entry.off() ? "已停用" : entry.error().isEmpty() ? "已启用" : "加载出错";
        title(entry.name(), PluginsScreen.join(state, entry.version().isEmpty() ? "" : "v" + entry.version()));
        if (owner.stale(false)) owner.note(content, owner.detailStatus(false), true);
        if (!entry.error().isEmpty()) owner.note(content, PluginsScreen.cap(entry.error(), 1800), true);
        field("安装来源", PluginSnapshot.displaySpec(entry.spec()));

        boolean writable = owner.writable(false);
        boolean updateFirst = entry.updateAvailable() && writable;
        boolean enableFirst = !updateFirst && entry.off() && writable;
        PluginSnapshot.Page page = owner.page(entry.spec());
        boolean openFirst = !updateFirst && !entry.off() && page != null;
        Runnable toggle = () -> owner.confirm(scope, PluginOperation.toggle(entry, !entry.off()),
                entry.off() ? "启用后恢复插件功能。" : "此插件提供的功能会暂停。");
        String toggleLabel = entry.off() ? "启用插件" : "停用插件";
        if (entry.updateAvailable()) action("更新插件", updateFirst, writable, () -> owner.confirm(scope,
                PluginOperation.upgrade(entry), "从当前安装来源更新此插件。"));
        if (enableFirst) action(toggleLabel, true, true, toggle);
        if (page != null) action("打开" + page.title(), openFirst, !entry.off(), () -> owner.openPage(page));
        String manage = entry.configurable() || !entry.providers().isEmpty() ? "插件配置与登录" : "高级插件管理";
        action(manage, !updateFirst && !enableFirst && !openFirst, !owner.activeAttempt(),
                () -> owner.advancedPlugin("pluginSpec", entry.spec()));
        if (!enableFirst) action(toggleLabel, false, writable, toggle);
        showOperation();
        ui.space(content, 12);
        field("提供的功能", entry.kind());
        field("服务商", String.join("、", entry.providers()));
        field("MCP 工具", String.join("、", entry.tools()));
        field("Agent", PluginsScreen.join(entry.agent(), entry.agentNotice()));
    }

    private void renderDiscovery(PluginSnapshot.Discovery entry) {
        title(entry.name(), PluginsScreen.join(entry.source(), entry.kindLabel(), entry.version()));
        owner.note(content, PluginsScreen.cap(entry.description(), 1400), false);
        field("安装来源", PluginSnapshot.displaySpec(entry.spec()));
        action("安装插件", true, owner.writable(false), () -> {
            try {
                owner.confirm(scope, PluginOperation.install(entry), "插件将在当前工作空间运行，请核对来源与发布者。");
            } catch (IllegalArgumentException error) {
                owner.note(content, error.getMessage(), true);
            }
        });
        showOperation();
        ui.space(content, 16);
        field("发布者", entry.publisher());
        field("开源许可", entry.license());
        field("代码仓库", entry.repo());
    }

    private void renderBuiltin(PluginSnapshot.Builtin item) {
        if (item == null) {
            owner.note(content, owner.detailStatus(true), true);
            showOperation();
            return;
        }
        title(item.name(), item.on() ? "已启用" : "已停用");
        owner.note(content, item.description(), false);
        owner.note(content, PluginsScreen.join(item.why(), item.use(), item.broken()), true);
        boolean canOpen = item.on() && !item.destination().isEmpty();
        boolean canToggle = item.canToggle() && owner.writable(true);
        String toggleLabel = item.chosen() ? "停用" : "启用";
        Runnable toggle = () -> owner.confirm(scope, PluginOperation.toggle(item, !item.chosen()),
                "此设置会立即应用到当前工作空间。");
        if (!canOpen) action(toggleLabel, canToggle, canToggle, toggle);
        if (!item.destination().isEmpty()) action("打开功能", canOpen, canOpen, () -> {
            String view = item.destination();
            if (view.equals("agents") || view.equals("sessions")) owner.openRemote(view);
            else owner.advanced(view, null, null);
        });
        if (canOpen) action(toggleLabel, false, canToggle, toggle);
        showOperation();
        if (!item.fields().isEmpty()) {
            ui.space(content, 18);
            owner.heading(content, "设置");
            for (PluginSnapshot.Field field : item.fields()) {
                String value = field.value() == null ? "" : String.valueOf(field.value());
                if (field.value() instanceof Boolean) value = Boolean.TRUE.equals(field.value()) ? "开" : "关";
                ui.rule(content, 0);
                content.addView(CatalogViews.row(ui, field.title(), PluginsScreen.join(value, field.description()),
                        owner.writable(true) ? () -> edit(item, field) : null));
            }
        }
        if (item.advancedConfig() || !item.settings().isEmpty()) action("高级配置", false, !owner.activeAttempt(),
                () -> owner.advancedPlugin("builtinId", item.id()));
    }

    private void edit(PluginSnapshot.Builtin item, PluginSnapshot.Field field) {
        if (!owner.current(scope) || !owner.writable(true)) return;
        if (field.value() instanceof Boolean && field.choices().isEmpty()) {
            owner.confirm(scope, PluginOperation.configure(item, field, !Boolean.TRUE.equals(field.value())), field.title());
            return;
        }
        if (!field.choices().isEmpty()) {
            String[] labels = new String[field.choices().size()];
            for (int i = 0; i < labels.length; i++) labels[i] = String.valueOf(field.choices().get(i));
            owner.show(new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle(field.title()).setItems(labels,
                    (d, i) -> owner.confirm(scope, PluginOperation.configure(item, field, field.choices().get(i)), labels[i]))
                    .setNegativeButton("取消", null).create());
            return;
        }
        EditText input = new EditText(ui.a);
        input.setSingleLine(true);
        input.setSaveEnabled(false);
        input.setText(field.value() == null ? "" : String.valueOf(field.value()));
        if (!field.type().equals("string")) input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout form = ui.column();
        form.setPadding(ui.dp(20), ui.dp(12), ui.dp(20), 0);
        form.addView(input);
        AlertDialog dialog = new AlertDialog.Builder(ui.a, ui.dialogTheme).setTitle(field.title()).setView(form)
                .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
        owner.show(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!owner.current(scope) || !owner.writable(true)) { dialog.dismiss(); return; }
            try {
                String text = input.getText().toString();
                Object value = field.type().equals("string") ? text : Double.parseDouble(text);
                PluginOperation operation = PluginOperation.configure(item, field, value);
                dialog.dismiss();
                owner.submit(scope, operation);
            } catch (IllegalArgumentException error) {
                input.setError("请输入有效设置值");
            }
        });
    }
}
