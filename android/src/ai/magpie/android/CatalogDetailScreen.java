package ai.magpie.android;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

/** The model, group and provider detail pages read the same scoped catalog as their directory. */
final class CatalogDetailScreen implements AppScreen {
    enum Kind { MODEL, GROUP, PROVIDER }

    private final CatalogScreen owner;
    private final ScreenHost host;
    private final Ui ui;
    private final Kind kind;
    private final String id;
    private final long scope;
    private final ScrollView scroll;
    private final LinearLayout body;
    private final Runnable observer = this::update;
    private CatalogData displayed;
    private boolean displayedReady, displayedFailed, refreshNeeded = true;
    private int limit = 40;

    CatalogDetailScreen(CatalogScreen owner, Kind kind, String id, long scope) {
        this.owner = owner;
        host = owner.host;
        ui = owner.ui;
        this.kind = kind;
        this.id = id;
        this.scope = scope;
        scroll = CatalogViews.scroll(ui);
        body = CatalogViews.body(ui, scroll, 28);
    }

    @Override public View view() { return scroll; }
    @Override public void onResume() { refreshNeeded = true; owner.repository.resume(observer); }
    @Override public void onPause() { owner.repository.pause(observer); }

    private void update() {
        CatalogLoad load = owner.repository.load;
        boolean ready = owner.repository.ready();
        if (!refreshNeeded && displayed == load.data && displayedReady == ready && displayedFailed == load.failed) return;
        refreshNeeded = false;
        displayed = load.data;
        displayedReady = ready;
        displayedFailed = load.failed;
        int y = scroll.getScrollY();
        body.removeAllViews();
        if (load.scopeVersion != scope || displayed == null) {
            body.addView(ui.text("当前对象暂不可读取", Ui.Type.SECTION));
            return;
        }
        if (load.failed) {
            body.addView(new AdaptivePair(ui, ui.text("更新失败，显示上次读取的内容。", Ui.Type.SUPPORT),
                    CatalogViews.action(ui, "重新读取", owner.repository::refresh), 8));
            ui.space(body, 12);
        }
        boolean present;
        switch (kind) {
            case MODEL: present = model(displayed.model(id)); break;
            case GROUP: present = group(displayed.group(id)); break;
            default: present = provider(displayed.provider(id));
        }
        if (!present) body.addView(ui.text("该对象已从当前工作空间移除", Ui.Type.SECTION));
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void heading(String title, String state) {
        TextView name = ui.text(title, Ui.Type.PAGE);
        body.addView(name);
        ui.space(body, 5);
        body.addView(ui.text(state, Ui.Type.LABEL));
        ui.space(body, 16);
    }

    private void section(String title) {
        section(title, -1);
    }

    private void section(String title, int count) {
        ui.space(body, 24);
        TextView heading = ui.text(title, Ui.Type.SECTION);
        if (count < 0) body.addView(heading);
        else body.addView(new AdaptivePair(ui, heading, ui.text(count + " 项", Ui.Type.LABEL), 10));
        ui.space(body, 8);
        ui.rule(body, 0);
    }

    private void row(String title, String note, Runnable action) {
        row(title, "", note, action);
    }

    private void row(String title, String reference, String note, Runnable action) {
        body.addView(CatalogViews.fullRow(ui, title, reference, note, action));
        ui.rule(body, 0);
    }

    private boolean valid() { return owner.repository.current(scope, true) && !displayedFailed; }

    private void reference(String value) { reference("接入标识", value); }

    private void reference(String label, String value) {
        LinearLayout row = ui.row();
        LinearLayout valueBlock = ui.column();
        valueBlock.addView(ui.text(label, Ui.Type.LABEL));
        ui.space(valueBlock, 5);
        TextView text = ui.text(value, Ui.Type.IDENTIFIER);
        text.setTextIsSelectable(true);
        valueBlock.addView(text);
        ui.grow(row, valueBlock);
        ui.gap(row, 6);
        row.addView(ui.copyButton("复制" + label, () -> {
            if (owner.repository.current(scope, false)) CatalogViews.copy(ui, label, value);
        }));
        body.addView(row);
        ui.space(body, 16);
    }

    private Button connection(String reference, boolean available) {
        Button connect = ui.button("使用此目标接入", true, () -> {
            if (valid()) host.connect(reference);
        });
        connect.setEnabled(available && displayedReady && !displayedFailed);
        return connect;
    }

    private boolean model(CatalogData.Model selected) {
        if (selected == null) return false;
        WorkspaceSnapshot.Model model = selected.model;
        WorkspaceSnapshot.Provider source = selected.source.provider;
        heading(model.name, selected.status());
        reference(model.reference);
        TextView favorite = CatalogViews.action(ui, owner.favorite(model.reference) ? "取消收藏" : "收藏模型", () -> {
            owner.toggleFavorite(scope, model.reference);
            refreshNeeded = true;
            update();
        });
        body.addView(new AdaptivePair(ui, connection(model.reference, selected.connectable), favorite, 10));
        section("模型来源");
        row(source.name, selected.source.status(), () -> {
            if (owner.repository.current(scope, false)) host.openDetail("服务商详情",
                    new CatalogDetailScreen(owner, Kind.PROVIDER, source.id, scope));
        });
        if (!selected.upstream.isEmpty()) row("上游模型", selected.upstream, null);
        row("编辑模型配置", "在服务商中管理模型", () -> {
            if (valid()) host.advanced("providers", "providerId", source.id);
        });
        return true;
    }

    private boolean provider(CatalogData.Provider selected) {
        if (selected == null) return false;
        WorkspaceSnapshot.Provider source = selected.provider;
        heading(source.name, selected.status());
        reference("服务商标识", source.id);
        body.addView(ui.button("编辑服务商", true, () -> {
            if (valid()) host.advanced("providers", "providerId", source.id);
        }));
        section("模型", source.models.size());
        if (source.models.isEmpty()) row("尚未添加模型", "进入服务商配置读取或添加模型。", null);
        for (int i = 0; i < Math.min(limit, source.models.size()); i++) {
            WorkspaceSnapshot.Model model = source.models.get(i);
            CatalogData.Model entry = displayed.model(model.reference);
            if (entry != null) {
                row(model.name, model.reference, entry.status(), () -> owner.openModel(entry, scope));
            }
        }
        more(source.models.size());
        return true;
    }

    private boolean group(CatalogData.Group selected) {
        if (selected == null) return false;
        WorkspaceSnapshot.Group group = selected.group;
        heading(group.name, selected.status());
        body.addView(new AdaptivePair(ui, ui.text("路由策略", Ui.Type.LABEL),
                ui.text(selected.strategy(), Ui.Type.BODY), 10));
        ui.space(body, 14);
        reference(group.reference);
        if (!selected.decisive) body.addView(connection(group.reference, selected.connectable));
        row("编辑分组", "成员、匹配规则与路由策略", () -> {
            if (valid()) host.advanced("groups", "groupId", group.id);
        });
        members("固定成员", selected.fixed, selected);
        members("动态匹配成员", group.matched, selected);
        if (!selected.patterns.isEmpty()) {
            section("匹配规则");
            for (int i = 0; i < Math.min(limit, selected.patterns.size()); i++) {
                CatalogData.Pattern pattern = selected.patterns.get(i);
                row(pattern.text, pattern.matches < 0 ? "" : "匹配 " + pattern.matches + " 个模型", null);
            }
        }
        more(Math.max(Math.max(selected.fixed.size(), group.matched.size()), selected.patterns.size()));
        return true;
    }

    private void members(String name, List<String> references, CatalogData.Group group) {
        section(name, references.size());
        if (references.isEmpty()) row("暂无成员", "", null);
        for (int i = 0; i < Math.min(limit, references.size()); i++) {
            String reference = references.get(i);
            CatalogData.Model model = displayed.model(reference);
            String status = group.off.contains(reference) ? "已从此分组停用"
                    : Boolean.FALSE.equals(group.memberReady.get(reference)) ? "当前不可路由" : "";
            if (model == null) row(reference, status, null);
            else row(model.model.name, reference, status, () -> owner.openModel(model, scope));
        }
    }

    private void more(int total) {
        if (total <= limit) return;
        body.addView(CatalogViews.action(ui, "继续显示", () -> { limit += 40; refreshNeeded = true; update(); }));
    }
}
